/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortDescendingKey
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortType
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortTypeKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Source for auto playlists (liked / downloaded via the "auto_playlist/{name}"
 * route). Mirrors AutoPlaylistViewModel: sort prefs come from DataStore,
 * "liked" from Room, "downloaded" from DownloadUtil's completed set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlaylistSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playlistName: String,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        private val syncUtils: SyncUtils,
    ) : PlaylistTracksSource {
    override val id: String = "auto:$playlistName"
    override val capabilities: PlaylistCapabilities = PlaylistCapabilities.Auto

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: Flow<Boolean> = _isRefreshing

    private fun AutoPlaylistSongSortType.toSongSortType(): SongSortType =
        when (this) {
            AutoPlaylistSongSortType.CREATE_DATE -> SongSortType.CREATE_DATE
            AutoPlaylistSongSortType.NAME -> SongSortType.NAME
            AutoPlaylistSongSortType.ARTIST -> SongSortType.ARTIST
            AutoPlaylistSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
        }

    override fun observe(): Flow<PlaylistTracksResult> =
        context.dataStore.data
            .map {
                Triple(
                    it[AutoPlaylistSongSortTypeKey].toEnum(AutoPlaylistSongSortType.CREATE_DATE) to (
                        it[AutoPlaylistSongSortDescendingKey] ?: true
                    ),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoKey] ?: false,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit, hideVideo) ->
                val (sortType, descending) = sortDesc
                val songSortType = sortType.toSongSortType()
                when (playlistName) {
                    "liked" ->
                        database
                            .likedSongs(songSortType, descending, hideVideo, LikeSource.YTM)
                            .map { it.filterExplicit(hideExplicit) }

                    "downloaded" ->
                        downloadUtil.downloads.flatMapLatest { downloads: Map<String, androidx.media3.exoplayer.offline.Download> ->
                            database
                                .allSongs()
                                .flowOn(Dispatchers.IO)
                                .map { songs ->
                                    songs.filter {
                                        downloads[it.id]?.state == androidDownloadedState()
                                    }
                                }.map { songs ->
                                    sortDownloaded(songs, songSortType, descending) { songId ->
                                        downloads[songId]?.updateTimeMs ?: 0L
                                    }.filterExplicit(hideExplicit)
                                }
                        }

                    else -> MutableStateFlow(emptyList())
                }
            }.map { songs ->
                PlaylistTracksResult(
                    header = null,
                    tracks = songs.map { it.toPlaylistTrack() },
                )
            }

    private fun androidDownloadedState() = androidx.media3.exoplayer.offline.Download.STATE_COMPLETED

    private fun sortDownloaded(
        songs: List<moe.rukamori.archivetune.db.entities.Song>,
        sortType: SongSortType,
        descending: Boolean,
        updateTimeOf: (String) -> Long,
    ): List<moe.rukamori.archivetune.db.entities.Song> =
        when (sortType) {
            SongSortType.CREATE_DATE -> songs.sortedBy { updateTimeOf(it.id) }
            SongSortType.NAME -> {
                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                songs.sortedWith(compareBy(collator) { it.song.title })
            }
            SongSortType.ARTIST -> {
                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                songs.sortedWith(compareBy(collator) { s -> s.artists.joinToString(", ") { it.name } })
            }
            SongSortType.PLAY_TIME -> songs.sortedBy { it.song.totalPlayTime }
        }.reversed(descending)

    override suspend fun refresh(): Result<Unit> =
        runCatching {
            if (playlistName == "liked") {
                _isRefreshing.value = true
                try {
                    syncUtils.syncLikedSongs()
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
}
