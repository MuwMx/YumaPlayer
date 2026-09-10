/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.LastSpotifySyncKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.SpotifySyncLikesKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifySync
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils
    @Inject
    constructor(
        private val state: SyncState,
        private val syncLikes: SyncLikes,
        private val ytmSync: YtmSync,
    ) {
        private val database get() = state.database
        private val spotifyRepository get() = state.spotifyRepository
        private val context get() = state.context
        private val syncScope get() = state.syncScope
        private val playlistSyncMutex get() = state.playlistSyncMutex

        suspend fun performFullSync(authoritative: Boolean = false) = ytmSync.performFullSync(authoritative)

        suspend fun cleanupDuplicatePlaylists() = ytmSync.cleanupDuplicatePlaylists()

        fun likeSong(s: SongEntity, explicitSpotifyId: String? = null) = syncLikes.likeSong(s, explicitSpotifyId)

        fun likeSongs(songs: List<SongEntity>) = syncLikes.likeSongs(songs)

        suspend fun syncLikedSongs(authoritative: Boolean = false) = ytmSync.syncLikedSongs(authoritative)

        suspend fun syncLibrarySongs(authoritative: Boolean = false) = ytmSync.syncLibrarySongs(authoritative)

        suspend fun syncLikedAlbums(authoritative: Boolean = false) = ytmSync.syncLikedAlbums(authoritative)

        suspend fun syncArtistsSubscriptions(authoritative: Boolean = false) = ytmSync.syncArtistsSubscriptions(authoritative)

        suspend fun syncSavedPlaylists(authoritative: Boolean = false) = ytmSync.syncSavedPlaylists(authoritative)

        suspend fun syncAutoSyncPlaylists() = ytmSync.syncAutoSyncPlaylists()

        suspend fun syncPlaylistNow(
            browseId: String,
            playlistId: String,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = ytmSync.syncPlaylistNow(
            browseId = browseId,
            playlistId = playlistId,
            propagateFailures = propagateFailures,
            onProgress = onProgress,
        )

        suspend fun syncSpotifyPlaylists(authoritative: Boolean = false) =
            playlistSyncMutex.withLock {
                try {
                    val session = spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) {
                        Timber.w("Skipping syncSpotifyPlaylists - user not logged in to Spotify")
                        return@withLock
                    }

                    val remotePlaylists = spotifyRepository.refreshPlaylists()
                    val remotePlaylistIds = remotePlaylists.map { it.id }.toSet()
                    val now = LocalDateTime.now()

                    if (authoritative) {
                        val localPlaylists = database.playlistsByNameAsc().first()
                        val stalePlaylists = localPlaylists
                            .map { it.playlist }
                            .filter { it.spotifyId != null && it.spotifyId !in remotePlaylistIds }
                        
                        if (stalePlaylists.isNotEmpty()) {
                            database.withTransaction {
                                stalePlaylists.forEach { playlist ->
                                    database.clearPlaylist(playlist.id)
                                    database.delete(playlist)
                                }
                            }
                        }
                    }

                    val resolveSemaphore = Semaphore(4)

                    for (playlist in remotePlaylists) {
                        try {
                            val existingPlaylist = database.playlistBySpotifyId(playlist.id).firstOrNull()
                            val playlistEntity = if (existingPlaylist == null) {
                                val newEntity = PlaylistEntity(
                                    name = playlist.name,
                                    spotifyId = playlist.id,
                                    thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist),
                                    remoteSongCount = playlist.tracks?.total,
                                    isEditable = false,
                                    bookmarkedAt = now
                                )
                                database.insert(newEntity)
                                database.playlistBySpotifyId(playlist.id).firstOrNull()?.playlist ?: newEntity
                            } else {
                                val updatedEntity = existingPlaylist.playlist.copy(
                                    name = playlist.name,
                                    thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist),
                                    remoteSongCount = playlist.tracks?.total,
                                    lastUpdateTime = now
                                )
                                database.update(updatedEntity)
                                updatedEntity
                            }

                            val tracks = spotifyRepository.playlistTracks(playlist.id)
                            val resolvedTracks = coroutineScope {
                                tracks.map { track ->
                                    async {
                                        resolveSemaphore.withPermit {
                                            val metadata = SpotifyPlaybackResolver.resolveToMetadata(track)
                                            if (metadata != null) {
                                                track to metadata
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                }.awaitAll().filterNotNull()
                            }

                            database.withTransaction {
                                database.clearPlaylist(playlistEntity.id)
                                resolvedTracks.forEachIndexed { idx, (track, metadata) ->
                                    val dbSong = database.getSongByIdBlocking(metadata.id)
                                    if (dbSong == null) {
                                        database.insert(metadata)
                                    } else {
                                        database.update(dbSong, metadata)
                                    }

                                    database.insert(
                                        PlaylistSongMap(
                                            playlistId = playlistEntity.id,
                                            songId = metadata.id,
                                            position = idx
                                        )
                                    )

                                    database.insert(
                                        SpotifyMatchEntity(
                                            spotifyId = track.id,
                                            youtubeId = metadata.id,
                                            title = track.name,
                                            artist = track.artists.joinToString(" ") { it.name },
                                            matchScore = 1.0
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync Spotify playlist ${playlist.name}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during syncSpotifyPlaylists")
                }
            }

        suspend fun syncSpotifyLikedSongs(
            authoritative: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) =
            playlistSyncMutex.withLock {
                try {
                    val session = spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) {
                        Timber.w("Skipping syncSpotifyLikedSongs - user not logged in to Spotify")
                        return@withLock
                    }

                    val tracks = mutableListOf<SpotifyTrack>()
                    var offset = 0
                    val limit = 50
                    val maxPages = 60

                    for (page in 0 until maxPages) {
                        val result = Spotify.likedSongs(limit = limit, offset = offset).getOrNull()
                        if (result == null || result.items.isEmpty()) break
                        tracks.addAll(result.items.mapNotNull { it.track })
                        offset += result.items.size
                        if (offset >= result.total || result.items.size < limit) break
                    }

                    val resolveSemaphore = Semaphore(4)
                    data class Resolved(val track: SpotifyTrack, val metadata: MediaMetadata)

                    val completed = java.util.concurrent.atomic.AtomicInteger(0)
                    val total = tracks.size
                    onProgress(0, total)

                    val resolvedTracks = coroutineScope {
                        tracks.map { track ->
                            async {
                                resolveSemaphore.withPermit {
                                    val metadata = SpotifyPlaybackResolver.resolveToMetadata(track)
                                    val result = if (metadata != null) {
                                        Resolved(track, metadata)
                                    } else {
                                        null
                                    }
                                    onProgress(completed.incrementAndGet(), total)
                                    result
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }

                    val localLikedSongs = database.likedSongsByNameAsc().first()
                    val resolvedYoutubeIds = resolvedTracks.map { it.metadata.id }.toSet()
                    val resolvedSpotifyIds = resolvedTracks.map { it.track.id }.toSet()

                    val unlikeCandidateIds = localLikedSongs.map { it.id }.filter { it !in resolvedYoutubeIds }
                    val matchEntities = database.getSpotifyMatchesByYouTubeIds(unlikeCandidateIds)
                    val matchByYtId = matchEntities.associateBy { it.youtubeId }

                    val now = LocalDateTime.now()

                    database.withTransaction {
                        resolvedTracks.forEachIndexed { index, resolved ->
                            val timestamp = likedSongTimestamp(now, index)
                            val dbSong = database.getSongByIdBlocking(resolved.metadata.id)

                            if (dbSong == null) {
                                database.insert(resolved.metadata) { it.copy(liked = false, likedDate = null) }
                            }

                            database.insert(
                                SpotifyMatchEntity(
                                    spotifyId = resolved.track.id,
                                    youtubeId = resolved.metadata.id,
                                    title = resolved.track.name,
                                    artist = resolved.track.artists.joinToString(" ") { it.name },
                                    matchScore = 1.0
                                )
                            )
                        }

                        if (authoritative) {
                            for (song in localLikedSongs) {
                                if (song.id in resolvedYoutubeIds) continue
                                val match = matchByYtId[song.id] ?: continue
                                if (match.spotifyId !in resolvedSpotifyIds) {
                                    database.update(song.song.copy(liked = false, likedDate = null))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during syncSpotifyLikedSongs")
                }
            }

        fun trySpotifyAutoSync(authoritative: Boolean = false) {
            syncScope.launch {
                try {
                    val session = spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) return@launch

                    val lastSync = context.dataStore.data.map { it[LastSpotifySyncKey] ?: 0L }.first()
                    val currentTime = System.currentTimeMillis()
                    if (!authoritative && lastSync > 0 && (currentTime - lastSync) < SPOTIFY_SYNC_COOLDOWN_MS) {
                        Timber.d("Skipping Spotify auto-sync - cooldown active")
                        return@launch
                    }

                    syncSpotifyPlaylists(authoritative = authoritative)
                    syncSpotifyLikedSongs(authoritative = authoritative)

                    context.dataStore.edit { prefs ->
                        prefs[LastSpotifySyncKey] = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed trySpotifyAutoSync")
                }
            }
        }

        companion object {
            private const val SPOTIFY_SYNC_COOLDOWN_MS = 30 * 60 * 1000L
        }
    }
