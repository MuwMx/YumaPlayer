/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.toMediaMetadata
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtmSync
    @Inject
    constructor(
        private val state: SyncState,
    ) {
        suspend fun performFullSync(authoritative: Boolean = false) =
            withContext(Dispatchers.IO) {
                if (authoritative) {
                    state.syncGeneration.incrementAndGet()
                    Log.d("SPLIT_STRESS", "LOCK syncMutex acquire (auth) thread=${Thread.currentThread().name}")
                    state.syncMutex.lock()
                } else if (!state.syncMutex.tryLock()) {
                    Log.d("SPLIT_STRESS", "LOCK syncMutex tryLock FAILED thread=${Thread.currentThread().name}")
                    Timber.d("Sync already in progress, skipping")
                    return@withContext
                } else {
                    Log.d("SPLIT_STRESS", "LOCK syncMutex tryLock SUCCESS thread=${Thread.currentThread().name}")
                }

                try {
                    if (!state.isLoggedIn()) {
                        Timber.w("Skipping full sync - user not logged in")
                        return@withContext
                    }
                    if (!state.isYtmSyncEnabled()) {
                        Timber.w("Skipping full sync - sync disabled")
                        return@withContext
                    }

                    supervisorScope {
                        syncLikedSongs(authoritative = authoritative)
                        syncLibrarySongs(authoritative = authoritative)

                        listOf(
                            async { syncLikedAlbums(authoritative = authoritative) },
                            async { syncArtistsSubscriptions(authoritative = authoritative) },
                        ).awaitAll()

                        syncSavedPlaylists(authoritative = authoritative)
                        if (!authoritative) {
                            syncAutoSyncPlaylists()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during full sync")
                } finally {
                    Log.d("SPLIT_STRESS", "LOCK syncMutex release thread=${Thread.currentThread().name}")
                    state.syncMutex.unlock()
                }
            }

        suspend fun cleanupDuplicatePlaylists() =
            withContext(Dispatchers.IO) {
                try {
                    val allPlaylists = state.database.playlistsByNameAsc().first()
                    val browseIdGroups =
                        allPlaylists
                            .filter { it.playlist.browseId != null }
                            .groupBy { it.playlist.browseId }

                    for ((browseId, playlists) in browseIdGroups) {
                        if (playlists.size > 1) {
                            Timber.w("Found ${playlists.size} duplicate playlists for browseId: $browseId")
                            val toKeep =
                                playlists.maxByOrNull { it.songCount }
                                    ?: playlists.first()

                            playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                                Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                                state.database.clearPlaylist(duplicate.id)
                                state.database.delete(duplicate.playlist)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up duplicate playlists")
                }
            }

        private fun preferredPlaylistCopy(
            current: Playlist,
            candidate: Playlist,
        ): Playlist {
            val bySongCount = candidate.songCount.compareTo(current.songCount)
            if (bySongCount != 0) {
                return if (bySongCount > 0) candidate else current
            }

            val byLastUpdated = compareNullableDateTimes(candidate.playlist.lastUpdateTime, current.playlist.lastUpdateTime)
            if (byLastUpdated != 0) {
                return if (byLastUpdated > 0) candidate else current
            }

            val byCreatedAt = compareNullableDateTimes(candidate.playlist.createdAt, current.playlist.createdAt)
            if (byCreatedAt != 0) {
                return if (byCreatedAt > 0) candidate else current
            }

            if (candidate.playlist.isEditable != current.playlist.isEditable) {
                return if (candidate.playlist.isEditable) candidate else current
            }

            return if (candidate.id < current.id) candidate else current
        }

        private fun compareNullableDateTimes(
            first: LocalDateTime?,
            second: LocalDateTime?,
        ): Int {
            if (first == second) return 0
            if (first == null) return -1
            if (second == null) return 1
            return first.compareTo(second)
        }

        private fun canonicalPlaylistCopies(playlists: List<Playlist>): List<Playlist> =
            playlists
                .groupBy { it.playlist.browseId ?: it.id }
                .values
                .map { copies ->
                    copies.reduce(::preferredPlaylistCopy)
                }

        suspend fun syncLikedSongs(authoritative: Boolean = false) =
            coroutineScope {
                if (!state.isLoggedIn()) {
                    Timber.w("Skipping syncLikedSongs - user not logged in")
                    return@coroutineScope
                }
                if (!state.isYtmSyncEnabled()) {
                    Timber.w("Skipping syncLikedSongs - sync disabled")
                    return@coroutineScope
                }
                val gen = state.syncGeneration.get()
                YouTube
                    .playlist("LM")
                    .completed()
                    .onSuccess { page ->
                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val remoteSongs = page.songs.orEmpty()
                        if (remoteSongs.isEmpty() && !authoritative) {
                            Timber.w("syncLikedSongs: Remote playlist is empty")
                            return@onSuccess
                        }
                        val remoteIds = remoteSongs.map { it.id }.toSet()
                        if (authoritative) {
                            val localLikedSongs = state.database.likedSongsByNameAsc(LikeSource.YTM).first()
                            if (!state.isSyncStillEnabled(gen)) return@onSuccess
                            val staleLikedSongs =
                                localLikedSongs
                                    .asSequence()
                                    .map { it.song }
                                    .filterNot { it.isLocal }
                                    .filterNot { it.id in remoteIds }
                                    .map { it.copy(liked = false, likedYtm = false, likedDate = if (it.likedSpotify) it.likedDate else null) }
                                    .toList()
                            if (staleLikedSongs.isNotEmpty()) {
                                state.database.withTransaction {
                                    staleLikedSongs.forEach { update(it) }
                                }
                            }
                        }
                        val baseTimestamp = LocalDateTime.now()

                        remoteSongs.forEachIndexed { index, song ->
                            val timestamp = likedSongTimestamp(baseTimestamp, index)
                            launch {
                                if (!state.isSyncStillEnabled(gen)) return@launch
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore acquire (syncLikedSongs) thread=${Thread.currentThread().name}")
                                state.dbWriteSemaphore.withPermit {
                                    if (!state.isSyncStillEnabled(gen)) return@withPermit
                                    val dbSong = state.database.song(song.id).firstOrNull()
                                    state.database.withTransaction {
                                        if (!state.isSyncStillEnabled(gen)) return@withTransaction
                                        if (dbSong == null) {
                                            insert(song.toMediaMetadata()) { it.copy(liked = true, likedYtm = true, likedDate = timestamp) }
                                        } else if (!dbSong.song.likedYtm || dbSong.song.likedDate != timestamp) {
                                            update(dbSong.song.copy(liked = true, likedYtm = true, likedDate = timestamp))
                                        }
                                    }
                                }
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore release (syncLikedSongs) thread=${Thread.currentThread().name}")
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncLikedSongs: Failed to sync liked songs")
                    }
            }

        suspend fun syncLibrarySongs(authoritative: Boolean = false) =
            coroutineScope {
                if (!state.isLoggedIn()) {
                    Timber.w("Skipping syncLibrarySongs - user not logged in")
                    return@coroutineScope
                }
                if (!state.isYtmSyncEnabled()) {
                    Timber.w("Skipping syncLibrarySongs - sync disabled")
                    return@coroutineScope
                }
                val gen = state.syncGeneration.get()
                YouTube
                    .library("FEmusic_liked_videos")
                    .completed()
                    .onSuccess { page ->
                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                        if (remoteSongs.isEmpty() && !authoritative) {
                            Timber.w("syncLibrarySongs: Remote library is empty")
                            return@onSuccess
                        }
                        val remoteIds = remoteSongs.map { it.id }.toSet()
                        val localSongs = state.database.songsByNameAsc().first()

                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val staleLibrarySongs =
                            localSongs
                                .asSequence()
                                .filter { !authoritative || !it.song.isLocal }
                                .filterNot { it.id in remoteIds }
                                .map { it.song.copy(inLibrary = null) }
                                .toList()
                        if (staleLibrarySongs.isNotEmpty()) {
                            state.database.withTransaction {
                                staleLibrarySongs.forEach { update(it) }
                            }
                        }

                        remoteSongs.forEach { song ->
                            launch {
                                if (!state.isSyncStillEnabled(gen)) return@launch
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore acquire (syncLibrarySongs) thread=${Thread.currentThread().name}")
                                state.dbWriteSemaphore.withPermit {
                                    if (!state.isSyncStillEnabled(gen)) return@withPermit
                                    val dbSong = state.database.song(song.id).firstOrNull()
                                    state.database.withTransaction {
                                        if (!state.isSyncStillEnabled(gen)) return@withTransaction
                                        if (dbSong == null) {
                                            insert(song.toMediaMetadata()) { it.toggleLibrary() }
                                        } else if (dbSong.song.inLibrary == null) {
                                            update(dbSong.song.toggleLibrary())
                                        }
                                    }
                                }
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore release (syncLibrarySongs) thread=${Thread.currentThread().name}")
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncLibrarySongs: Failed to sync library songs")
                    }
            }

        suspend fun syncLikedAlbums(authoritative: Boolean = false) =
            coroutineScope {
                if (!state.isLoggedIn()) {
                    Timber.w("Skipping syncLikedAlbums - user not logged in")
                    return@coroutineScope
                }
                if (!state.isYtmSyncEnabled()) {
                    Timber.w("Skipping syncLikedAlbums - sync disabled")
                    return@coroutineScope
                }
                val gen = state.syncGeneration.get()
                YouTube
                    .library("FEmusic_liked_albums")
                    .completed()
                    .onSuccess { page ->
                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                        if (remoteAlbums.isEmpty() && !authoritative) {
                            Timber.w("syncLikedAlbums: No liked albums found")
                            return@onSuccess
                        }
                        val remoteIds = remoteAlbums.map { it.id }.toSet()
                        val localAlbums = state.database.albumsLikedByNameAsc().first()

                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val staleAlbums =
                            localAlbums
                                .asSequence()
                                .filter { !authoritative || !it.album.isLocal }
                                .filterNot { it.id in remoteIds }
                                .map { it.album.localToggleLike() }
                                .toList()
                        if (staleAlbums.isNotEmpty()) {
                            state.database.withTransaction {
                                staleAlbums.forEach { update(it) }
                            }
                        }

                        remoteAlbums.forEach { album ->
                            launch {
                                if (!state.isSyncStillEnabled(gen)) return@launch
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore acquire (syncLikedAlbums) thread=${Thread.currentThread().name}")
                                state.dbWriteSemaphore.withPermit {
                                    if (!state.isSyncStillEnabled(gen)) return@withPermit
                                    val dbAlbum = state.database.album(album.id).firstOrNull()
                                    YouTube
                                        .album(album.browseId)
                                        .onSuccess { albumPage ->
                                            if (!state.isSyncStillEnabled(gen)) return@onSuccess
                                            if (dbAlbum == null) {
                                                try {
                                                    state.database.insert(albumPage)
                                                    state.database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                                        state.database.update(newDbAlbum.album.localToggleLike())
                                                    }
                                                } catch (e: Exception) {
                                                    Timber.w("syncLikedAlbums: Failed to insert album ${album.id}", e)
                                                }
                                            } else if (dbAlbum.album.bookmarkedAt == null) {
                                                state.database.update(dbAlbum.album.localToggleLike())
                                            }
                                        }.onFailure { e ->
                                            Timber.w("syncLikedAlbums: Failed to fetch album ${album.id}", e)
                                        }
                                }
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore release (syncLikedAlbums) thread=${Thread.currentThread().name}")
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncLikedAlbums: Failed to sync liked albums")
                    }
            }

        suspend fun syncArtistsSubscriptions(authoritative: Boolean = false) =
            coroutineScope {
                if (!state.isLoggedIn()) {
                    Timber.w("Skipping syncArtistsSubscriptions - user not logged in")
                    return@coroutineScope
                }
                if (!state.isYtmSyncEnabled()) {
                    Timber.w("Skipping syncArtistsSubscriptions - sync disabled")
                    return@coroutineScope
                }
                val gen = state.syncGeneration.get()
                YouTube
                    .library("FEmusic_library_corpus_artists")
                    .completed()
                    .onSuccess { page ->
                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val remoteArtists = page.items.filterIsInstance<ArtistItem>().distinctBy { it.id }
                        if (remoteArtists.isEmpty() && !authoritative) {
                            Timber.w("syncArtistsSubscriptions: No artist subscriptions found")
                            return@onSuccess
                        }
                        val now = LocalDateTime.now()
                        val remoteIds = remoteArtists.map { it.id }.toSet()
                        val localArtists = state.database.artistsBookmarkedByNameAsc().first()

                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val staleArtists =
                            localArtists
                                .asSequence()
                                .filter { !authoritative || !it.artist.isLocal }
                                .filterNot { it.id in remoteIds }
                                .map { it.artist.copy(bookmarkedAt = null, lastUpdateTime = now) }
                                .toList()
                        if (staleArtists.isNotEmpty()) {
                            state.database.withTransaction {
                                staleArtists.forEach { update(it) }
                            }
                        }

                        remoteArtists.forEachIndexed { index, artist ->
                            launch {
                                if (!state.isSyncStillEnabled(gen)) return@launch
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore acquire (syncArtistsSubscriptions) thread=${Thread.currentThread().name}")
                                state.dbWriteSemaphore.withPermit {
                                    if (!state.isSyncStillEnabled(gen)) return@withPermit
                                    val dbArtist = state.database.artist(artist.id).firstOrNull()
                                    val bookmarkedAt = now.minusSeconds(index.toLong())
                                    state.database.withTransaction {
                                        if (!state.isSyncStillEnabled(gen)) return@withTransaction
                                        if (dbArtist == null) {
                                            insert(
                                                ArtistEntity(
                                                    id = artist.id,
                                                    name = artist.title,
                                                    thumbnailUrl = artist.thumbnail,
                                                    channelId = artist.channelId,
                                                    bookmarkedAt = bookmarkedAt,
                                                ),
                                            )
                                        } else {
                                            val existing = dbArtist.artist
                                            val syncedThumbnail = artist.thumbnail ?: existing.thumbnailUrl
                                            val syncedChannelId = artist.channelId ?: existing.channelId
                                            val metadataChanged =
                                                existing.name != artist.title ||
                                                    existing.thumbnailUrl != syncedThumbnail ||
                                                    existing.channelId != syncedChannelId
                                            val needsBookmark = existing.bookmarkedAt == null

                                            if (metadataChanged || needsBookmark) {
                                                update(
                                                    existing.copy(
                                                        name = artist.title,
                                                        thumbnailUrl = syncedThumbnail,
                                                        channelId = syncedChannelId,
                                                        lastUpdateTime = now,
                                                        bookmarkedAt = existing.bookmarkedAt ?: bookmarkedAt,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore release (syncArtistsSubscriptions) thread=${Thread.currentThread().name}")
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncArtistsSubscriptions: Failed to sync artist subscriptions")
                    }
            }

        suspend fun syncSavedPlaylists(authoritative: Boolean = false) =
            state.playlistSyncMutex.withLock {
                Log.d("SPLIT_STRESS", "LOCK playlistMutex acquire (syncSavedPlaylists) thread=${Thread.currentThread().name}")
                try {
                    if (!state.isLoggedIn()) {
                        Timber.w("Skipping syncSavedPlaylists - user not logged in")
                        return@withLock
                    }
                if (!state.isYtmSyncEnabled()) {
                    Timber.w("Skipping syncSavedPlaylists - sync disabled")
                    return@withLock
                }

                cleanupDuplicatePlaylists()

                val gen = state.syncGeneration.get()

                YouTube
                    .library("FEmusic_liked_playlists")
                    .completed()
                    .onSuccess { page ->
                        if (!state.isSyncStillEnabled(gen)) return@onSuccess
                        val remotePlaylists =
                            page.items
                                .filterIsInstance<PlaylistItem>()
                                .filterNot { it.id == "LM" || it.id == "SE" }
                                .reversed()

                        if (remotePlaylists.isEmpty() && !authoritative) {
                            Timber.w("syncSavedPlaylists: No playlists found")
                            return@onSuccess
                        }

                        val selectedCsv = state.context.dataStore.data.first()[SelectedYtmPlaylistsKey] ?: ""
                        val selectedIds =
                            selectedCsv
                                .split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()

                        val localPlaylists = state.database.playlistsByNameAsc().first()
                        if (!state.isSyncStillEnabled(gen)) return@onSuccess

                        val now = LocalDateTime.now()
                        val remoteLikedIds = remotePlaylists.map { it.id }.toSet()

                        val stalePlaylists =
                            localPlaylists
                                .asSequence()
                                .map { it.playlist }
                                .filter { it.browseId != null }
                                .filter { it.browseId !in remoteLikedIds }
                                .map { it.copy(bookmarkedAt = null, lastUpdateTime = now) }
                                .toList()
                        if (stalePlaylists.isNotEmpty()) {
                            state.database.withTransaction {
                                stalePlaylists.forEach { update(it) }
                            }
                        }

                        val localPlaylistIdByBrowseId = HashMap<String, String>(remotePlaylists.size)
                        for (playlist in remotePlaylists) {
                            if (!state.isSyncStillEnabled(gen)) return@onSuccess
                            try {
                                val existingPlaylist = state.database.playlistByBrowseId(playlist.id).firstOrNull()
                                if (existingPlaylist == null) {
                                    val playlistEntity =
                                        PlaylistEntity(
                                            name = playlist.title,
                                            browseId = playlist.id,
                                            thumbnailUrl = playlist.thumbnail,
                                            isEditable = playlist.isEditable,
                                            bookmarkedAt = now,
                                            remoteSongCount =
                                                playlist.songCountText?.let {
                                                    Regex(
                                                        """\d+""",
                                                    ).find(it)?.value?.toIntOrNull()
                                                },
                                            playEndpointParams = playlist.playEndpoint?.params,
                                            shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                            radioEndpointParams = playlist.radioEndpoint?.params,
                                        )
                                    state.database.insert(playlistEntity)
                                    localPlaylistIdByBrowseId[playlist.id] = playlistEntity.id
                                    Timber.d("syncSavedPlaylists: Created new playlist ${playlist.title} (${playlist.id})")
                                } else {
                                    val baseEntity = existingPlaylist.playlist
                                    val likedEntity =
                                        if (baseEntity.bookmarkedAt == null) {
                                            baseEntity.copy(bookmarkedAt = now, lastUpdateTime = now)
                                        } else {
                                            baseEntity
                                        }
                                    state.database.update(likedEntity, playlist)
                                    localPlaylistIdByBrowseId[playlist.id] = likedEntity.id
                                    Timber.d("syncSavedPlaylists: Updated existing playlist ${playlist.title} (${playlist.id})")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "syncSavedPlaylists: Failed to upsert playlist ${playlist.title}")
                            }
                        }

                        val playlistsToSync =
                            if (selectedIds.isNotEmpty()) remotePlaylists.filter { it.id in selectedIds } else remotePlaylists
                        if (selectedIds.isNotEmpty() && playlistsToSync.isEmpty()) {
                            Timber.w(
                                "syncSavedPlaylists: Selected playlists not found in remote library; skipping song sync (selected=${selectedIds.size}, remote=${remotePlaylists.size})",
                            )
                        }

                        for (playlist in playlistsToSync) {
                            if (!state.isSyncStillEnabled(gen)) return@onSuccess
                            try {
                                val playlistId =
                                    localPlaylistIdByBrowseId[playlist.id]
                                        ?: state.database
                                            .playlistByBrowseId(playlist.id)
                                            .firstOrNull()
                                            ?.playlist
                                            ?.id
                                        ?: continue
                                syncPlaylist(
                                    browseId = playlist.id,
                                    playlistId = playlistId,
                                    authoritative = authoritative,
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to sync playlist ${playlist.title}")
                            }
                        }
                        }.onFailure { e ->
                            Timber.e(e, "syncSavedPlaylists: Failed to fetch playlists from YouTube")
                        }
                } finally {
                    Log.d("SPLIT_STRESS", "LOCK playlistMutex release (syncSavedPlaylists) thread=${Thread.currentThread().name}")
                }
            }

        suspend fun syncAutoSyncPlaylists() =
            coroutineScope {
                if (!state.isLoggedIn()) {
                    Timber.w("Skipping syncAutoSyncPlaylists - user not logged in")
                    return@coroutineScope
                }
                if (!state.isYtmSyncEnabled()) {
                    Timber.w("Skipping syncAutoSyncPlaylists - sync disabled")
                    return@coroutineScope
                }

                cleanupDuplicatePlaylists()

                val gen = state.syncGeneration.get()
                val autoSyncPlaylists =
                    try {
                        canonicalPlaylistCopies(
                            state.database
                                .playlistsByNameAsc()
                                .first()
                                .filter { it.playlist.isAutoSync && it.playlist.browseId != null },
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "syncAutoSyncPlaylists: Failed to fetch auto-sync playlists")
                        return@coroutineScope
                    }

                Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

                autoSyncPlaylists.forEach { playlist ->
                    launch {
                        if (!state.isSyncStillEnabled(gen)) return@launch
                        try {
                            Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore acquire (syncAutoSyncPlaylists) thread=${Thread.currentThread().name}")
                            state.dbWriteSemaphore.withPermit {
                                if (!state.isSyncStillEnabled(gen)) return@withPermit
                                val browseId =
                                    playlist.playlist.browseId ?: run {
                                        Timber.w("syncAutoSyncPlaylists: browseId is null for playlist ${playlist.playlist.name}")
                                        return@withPermit
                                    }
                                syncPlaylist(browseId, playlist.playlist.id)
                            }
                            Log.d("SPLIT_STRESS", "SEMA dbWriteSemaphore release (syncAutoSyncPlaylists) thread=${Thread.currentThread().name}")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                        }
                    }
                }
            }

        suspend fun syncPlaylistNow(
            browseId: String,
            playlistId: String,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = state.playlistSyncMutex.withLock {
            Log.d("SPLIT_STRESS", "LOCK playlistMutex acquire (syncPlaylistNow) playlistId=$playlistId thread=${Thread.currentThread().name}")
            try {
                syncPlaylist(
                    browseId = browseId,
                    playlistId = playlistId,
                    propagateFailures = propagateFailures,
                    onProgress = onProgress,
                )
            } finally {
                Log.d("SPLIT_STRESS", "LOCK playlistMutex release (syncPlaylistNow) playlistId=$playlistId thread=${Thread.currentThread().name}")
            }
        }

        private suspend fun syncPlaylist(
            browseId: String,
            playlistId: String,
            authoritative: Boolean = false,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = coroutineScope {
            if (!state.isYtmSyncEnabled()) {
                Timber.w("syncPlaylist: Skipping - sync disabled")
                return@coroutineScope
            }
            val gen = state.syncGeneration.get()
            if (!state.isSyncStillEnabled(gen)) return@coroutineScope
            Timber.d("syncPlaylist: Starting sync for browseId=$browseId, playlistId=$playlistId")

            val cleanBrowseId = if (browseId.startsWith("VL")) browseId else "VL$browseId"
            val page =
                YouTube.playlist(cleanBrowseId).completed().getOrElse { e ->
                    Timber.e(e, "syncPlaylist: Failed to fetch playlist from YouTube")
                    if (propagateFailures) {
                        throw e
                    }
                    return@coroutineScope
                }
            if (!state.isSyncStillEnabled(gen)) return@coroutineScope
            val songs =
                page.songs
                    .orEmpty()
                    .filter { song -> song.setVideoId?.isNotBlank() == true }
                    .map(SongItem::toMediaMetadata)
            Timber.d("syncPlaylist: Fetched ${songs.size} songs from remote")

            if (songs.isEmpty()) {
                if (authoritative) {
                    state.database.withTransaction {
                        if (!state.isSyncStillEnabled(gen)) return@withTransaction
                        state.database.clearPlaylist(playlistId)
                    }
                }
                Timber.w("syncPlaylist: Remote playlist is empty, skipping sync")
                return@coroutineScope
            }

            val remoteIds = songs.mapNotNull { it.id }
            if (remoteIds.isEmpty()) {
                Timber.w("syncPlaylist: No valid song IDs found, skipping sync")
                return@coroutineScope
            }

            val localIds =
                try {
                    state.database
                        .playlistSongs(playlistId)
                        .first()
                        .sortedBy { it.map.position }
                        .map { it.song.id }
                } catch (e: Exception) {
                    Timber.w("syncPlaylist: Failed to fetch local songs", e)
                    emptyList()
                }

            if (remoteIds == localIds) {
                Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                onProgress(remoteIds.size, remoteIds.size)
                return@coroutineScope
            }

            Timber.d("syncPlaylist: Updating local playlist (remote: ${remoteIds.size}, local: ${localIds.size})")

            try {
                onProgress(0, remoteIds.size)
                state.database.withTransaction {
                    if (!state.isSyncStillEnabled(gen)) return@withTransaction
                    state.database.clearPlaylist(playlistId)
                    var completedSongs = 0
                    songs.forEachIndexed { idx, song ->
                        if (!state.isSyncStillEnabled(gen)) return@withTransaction
                        val songId = song.id
                        if (state.database.song(songId).firstOrNull() == null) {
                            state.database.insert(song)
                        }
                        state.database.insert(
                            PlaylistSongMap(
                                songId = songId,
                                playlistId = playlistId,
                                position = idx,
                                setVideoId = song.setVideoId,
                            ),
                        )
                        completedSongs += 1
                        onProgress(completedSongs, remoteIds.size)
                    }
                }
                Timber.d("syncPlaylist: Successfully synced playlist")
                Log.d("SPLIT_STRESS", "YTM_DONE syncPlaylist songs=${songs.size} playlistId=$playlistId browseId=$cleanBrowseId")
            } catch (e: Exception) {
                Timber.e(e, "syncPlaylist: Error during database transaction")
                if (propagateFailures) {
                    throw e
                }
            }
        }
    }
