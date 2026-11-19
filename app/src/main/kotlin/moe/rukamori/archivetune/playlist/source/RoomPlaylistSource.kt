/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.playlist.remote.removeSongFromRemotePlaylist
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed source for local playlists (the "local_playlist/{playlistId}"
 * route). Sorting mirrors LocalPlaylistViewModel verbatim; reorder/remove use
 * the DAO's position-shift helpers the legacy wiring used (database.move +
 * delete-at-MAX_VALUE) with remote setVideoId bookkeeping intact. Remote sync
 * (YouTube.moveSongPlaylist / removeSongFromRemotePlaylist) travels with the
 * mutations like the legacy wiring; the local-only UI mutations (rename /
 * delete / pick-cover / like / remove-downloads) live in
 * RoomPlaylistMutations.kt beside this class.
 */
@Singleton
class RoomPlaylistSource
    @Inject
    constructor(
        internal val playlistId: String,
        internal val database: MusicDatabase,
        private val syncUtils: SyncUtils,
    ) : PlaylistTracksSource {
        override val id: String = "local:$playlistId"
        override val capabilities: PlaylistCapabilities = PlaylistCapabilities.Room

        /**
         * LOCAL PLAYLIST SLOT — per-playlist editability + browseId linkage +
         * the live playlist row, all live flows: hero buttons and the top bar
         * key on them (edit/delete vs like, edit vs sync icon), and they can
         * flip without a song change, so observe() alone can't carry them.
         */
        val browseId: Flow<String?> = database.playlist(playlistId).map { it?.playlist?.browseId }

        val editable: Flow<Boolean> = database.playlist(playlistId).map { it?.playlist?.isEditable == true }

        val playlist: Flow<moe.rukamori.archivetune.db.entities.Playlist?> = database.playlist(playlistId)

        override fun observe(): Flow<PlaylistTracksResult> =
            combine(
                database.playlist(playlistId),
                database.playlistSongs(playlistId),
            ) { playlist, rawSongs ->
                val sortType =
                    playlist?.playlist?.songSortType.toEnum(PlaylistSongSortType.CUSTOM)
                val sortDescending = playlist?.playlist?.songSortDescending ?: true
                val visibleSongs =
                    rawSongs.filter { ps -> ps.song.artists.none { it.blockedAt != null } }
                val sorted = sortInto(sortType, sortDescending, visibleSongs)
                PlaylistTracksResult(
                    header =
                        playlist?.let {
                            // Legacy hero (LocalPlaylistHeroSection.kt:262-268):
                            // an unsynced remote playlist shows the REMOTE song
                            // count while the local copy is still empty.
                            val remoteSongCount = it.playlist.remoteSongCount
                            PlaylistHeaderInfo(
                                id = it.id,
                                title = it.playlist.name,
                                author = null,
                                thumbnailUrl = it.thumbnails.firstOrNull(),
                                songCount =
                                    if (it.songCount == 0 && remoteSongCount != null) {
                                        remoteSongCount
                                    } else {
                                        it.songCount
                                    },
                                // Legacy hero: the duration chip sums the songs
                                // the list SHOWS (LocalPlaylistScreen
                                // playlistLength = fastSumBy over the sorted list).
                                totalDurationMs = sorted.sumOf { it.song.song.duration * 1000L },
                            )
                        },
                    tracks = sorted.map { it.toPlaylistTrack() },
                    sortInfo = PlaylistSortInfo(sortType, sortDescending),
                )
            }.flowOn(Dispatchers.Default)

        private fun sortInto(
            sortType: PlaylistSongSortType,
            sortDescending: Boolean,
            visibleSongs: List<PlaylistSong>,
        ): List<PlaylistSong> =
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> visibleSongs
                PlaylistSongSortType.CREATE_DATE -> visibleSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> visibleSongs.sortedBy { it.song.song.title }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    visibleSongs
                        .sortedWith(compareBy(collator) { ps -> ps.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, byAlbum) ->
                            byAlbum.sortedBy { ps -> ps.song.artists.joinToString("") { it.name } }
                        }
                }
                PlaylistSongSortType.PLAY_TIME -> visibleSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)

        override suspend fun refresh(): Result<Unit> =
            runCatching {
                val playlistRow =
                    database
                        .playlist(playlistId)
                        .first() ?: return Result.success(Unit)
                val browseId = playlistRow.playlist.browseId ?: return Result.success(Unit)
                syncUtils.syncPlaylistNow(browseId = browseId, playlistId = playlistId)
            }

        /**
         * Reorder with remote sync, verbatim commitLocalPlaylistReorder
         * (Wiring.kt:220-258): DB move commits first, then a synced playlist
         * propagates the move between the moved setVideoId and its
         * successor's; remote failures report (legacy snackbars, keeps move).
         */
        override suspend fun reorder(
            from: Int,
            to: Int,
        ): Result<Unit> =
            runCatching {
                val orderedBeforeMove = currentOrderSongs()
                val browseId =
                    database
                        .playlist(playlistId)
                        .first()
                        ?.playlist
                        ?.browseId
                val movedSetVideoId = orderedBeforeMove.getOrNull(from)?.map?.setVideoId
                val successorIndex = if (from > to) to else to + 1
                val successorSetVideoId = orderedBeforeMove.getOrNull(successorIndex)?.map?.setVideoId

                database.withTransaction {
                    move(playlistId, from, to)
                }

                if (browseId != null && movedSetVideoId != null) {
                    // Legacy keeps the local move and snackbars the failure
                    // (LocalPlaylistWiring.kt:248-255) — a failed Result here
                    // plays that snackbar without rolling the move back.
                    runCatching {
                        moe.rukamori.archivetune.innertube.YouTube
                            .moveSongPlaylist(
                                browseId,
                                movedSetVideoId,
                                successorSetVideoId,
                            ).getOrThrow()
                    }.onFailure { reportException(it) }
                }
            }

        /**
         * Removes the song at [position], remote-first: a synced playlist
         * calls removeSongFromRemotePlaylist first and aborts the local
         * delete on remote failure (verbatim deleteSongFromLocalPlaylist,
         * Wiring.kt:260-287).
         */
        override suspend fun removeSong(position: Int): Result<Unit> =
            runCatching {
                val song = currentOrderSongs().getOrNull(position) ?: return@runCatching
                val map = song.map
                val browseId = database.playlist(playlistId).first()?.playlist?.browseId
                if (browseId != null) {
                    removeSongFromRemotePlaylist(browseId, map).getOrThrow()
                }
                database.withTransaction {
                    move(map.playlistId, map.position, Int.MAX_VALUE)
                    delete(map.copy(position = Int.MAX_VALUE))
                }
            }

        /**
         * Raw songs in the CURRENT render order — drag rows and swipe deletes
         * arrive as rendered indexes, so every positional operation resolves
         * through the same sort pipeline observe() renders.
         */
        internal suspend fun currentOrderSongs(): List<PlaylistSong> {
            val playlistRow = database.playlist(playlistId).first()
            val sortType = playlistRow?.playlist?.songSortType.toEnum(PlaylistSongSortType.CUSTOM)
            val sortDescending = playlistRow?.playlist?.songSortDescending ?: true
            val rawSongs = database.playlistSongs(playlistId).first()
            val visible = rawSongs.filter { ps -> ps.song.artists.none { it.blockedAt != null } }
            return sortInto(sortType, sortDescending, visible)
        }

        override suspend fun setSort(
            sortType: PlaylistSongSortType,
            descending: Boolean,
        ): Result<Unit> =
            runCatching {
                // Mirrors LocalPlaylistViewModel.updateSortPreference: persist and
                // let observe()'s live query re-emit the reordered list.
                database.updatePlaylistSortPreference(
                    playlistId = playlistId,
                    sortType = sortType.name,
                    descending = descending,
                )
            }

        /**
         * LOCAL PLAYLIST SLOT — open-time hygiene from LocalPlaylistViewModel.init
         * (verbatim): compact song positions to 0..n (reorder correctness) and
         * eagerly sync synced-but-empty playlists (VL browseId, no songs). New
         * local-only open-time behavior belongs here, NOT in the screen.
         */
        override suspend fun ensureLoaded(): Result<Unit> =
            runCatching {
                val sortedSongs =
                    database
                        .playlistSongs(playlistId)
                        .first()
                        .sortedWith(compareBy({ it.map.position }, { it.map.id }))
                database.withTransaction {
                    sortedSongs.forEachIndexed { index, playlistSong ->
                        if (playlistSong.map.position != index) {
                            update(playlistSong.map.copy(position = index))
                        }
                    }
                }
            }.also {
                // Eager sync of empty synced playlists; a sync failure must
                // not fail the open (verbatim init behavior).
                runCatching {
                    val playlistRow =
                        database
                            .playlist(playlistId)
                            .first() ?: return@runCatching
                    val browseId = playlistRow.playlist.browseId ?: return@runCatching
                    if (browseId.startsWith("VL") && playlistRow.songCount == 0) {
                        syncUtils.syncPlaylistNow(browseId = browseId, playlistId = playlistRow.id)
                    }
                }.onFailure { reportException(it) }
            }
    }
