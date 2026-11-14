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
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed source for local playlists (the "local_playlist/{playlistId}"
 * route). The sorting pipeline mirrors LocalPlaylistViewModel verbatim;
 * reorder/remove use the DAO's position-shift helpers the legacy wiring used
 * (database.move + delete-at-MAX_VALUE), which keep remote setVideoId
 * bookkeeping intact.
 */
@Singleton
class RoomPlaylistSource
    @Inject
    constructor(
        private val playlistId: String,
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
    ) : PlaylistTracksSource {
        override val id: String = "local:$playlistId"
        override val capabilities: PlaylistCapabilities = PlaylistCapabilities.Room

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
                            PlaylistHeaderInfo(
                                id = it.id,
                                title = it.playlist.name,
                                author = null,
                                thumbnailUrl = it.thumbnails.firstOrNull(),
                                songCount = it.songCount,
                            )
                        },
                    tracks = sorted.map { it.toPlaylistTrack() },
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
            val playlist =
                database
                    .playlist(playlistId)
                    .first() ?: return Result.success(Unit)
            val browseId = playlist.playlist.browseId ?: return Result.success(Unit)
            syncUtils.syncPlaylistNow(browseId = browseId, playlistId = playlistId)
        }

    override suspend fun reorder(
        from: Int,
        to: Int,
    ): Result<Unit> =
        runCatching {
            database.withTransaction {
                move(playlistId, from, to)
            }
        }

    override suspend fun removeSong(position: Int): Result<Unit> =
        runCatching {
            val songs = database.playlistSongs(playlistId).first()
            val map = songs.getOrNull(position)?.map ?: return@runCatching
            database.withTransaction {
                move(map.playlistId, map.position, Int.MAX_VALUE)
                delete(map.copy(position = Int.MAX_VALUE))
            }
        }
}
