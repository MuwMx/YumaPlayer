/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import android.content.Context
import androidx.datastore.preferences.core.edit
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
import moe.rukamori.archivetune.constants.HideYtmLikedSongsKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.YtmSyncKey
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
        /** Route playlist arg ("liked"/"downloaded"); drives the hero title. */
        val playlistName: String,
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

    /**
     * Auto playlists have no CUSTOM order; the unified sort header's CUSTOM
     * selection maps to CREATE_DATE (the legacy default) instead of failing.
     */
    private fun PlaylistSongSortType.toAutoPlaylistSongSortType(): AutoPlaylistSongSortType =
        when (this) {
            PlaylistSongSortType.CUSTOM -> AutoPlaylistSongSortType.CREATE_DATE
            PlaylistSongSortType.CREATE_DATE -> AutoPlaylistSongSortType.CREATE_DATE
            PlaylistSongSortType.NAME -> AutoPlaylistSongSortType.NAME
            PlaylistSongSortType.ARTIST -> AutoPlaylistSongSortType.ARTIST
            PlaylistSongSortType.PLAY_TIME -> AutoPlaylistSongSortType.PLAY_TIME
        }

    /** Reports the applied sort back in the unified enum space. */
    private fun AutoPlaylistSongSortType.toPlaylistSongSortType(): PlaylistSongSortType =
        when (this) {
            AutoPlaylistSongSortType.CREATE_DATE -> PlaylistSongSortType.CREATE_DATE
            AutoPlaylistSongSortType.NAME -> PlaylistSongSortType.NAME
            AutoPlaylistSongSortType.ARTIST -> PlaylistSongSortType.ARTIST
            AutoPlaylistSongSortType.PLAY_TIME -> PlaylistSongSortType.PLAY_TIME
        }

    override fun observe(): Flow<PlaylistTracksResult> =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        it[AutoPlaylistSongSortTypeKey].toEnum(AutoPlaylistSongSortType.CREATE_DATE),
                        it[AutoPlaylistSongSortDescendingKey] ?: true,
                        it[HideYtmLikedSongsKey] ?: false,
                    ),
                    Pair(
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    ),
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortAndHideYtm, hideFlagsAndVideo) ->
                val (sortType, descending, hideYtmLikedSongs) = sortAndHideYtm
                val (hideExplicit, hideVideo) = hideFlagsAndVideo
                val songSortType = sortType.toSongSortType()
                when (playlistName) {
                    "liked" -> {
                        // Upstream 99bd75f0: with HideYtmLikedSongsKey set,
                        // the Liked playlist shows Spotify-side likes only.
                        val source = if (hideYtmLikedSongs) LikeSource.SPOTIFY else LikeSource.YTM
                        database
                            .likedSongs(songSortType, descending, hideVideo, source)
                            .map { it.filterExplicit(hideExplicit) }
                    }

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
                }.map { songs -> songs to (sortType to descending) }
            }.map { (songs, sortAndDescending) ->
                val (autoSortType, descending) = sortAndDescending
                val tracks = songs.map { it.toPlaylistTrack() }
                PlaylistTracksResult(
                    // AUTO HERO — legacy AutoPlaylistHeader renders first-song
                    // artwork, the localized "Liked"/"Offline" title (resolved
                    // UI-side, see rememberPlaylistDetailHeader), plural count
                    // and duration chips (AutoPlaylistScreen.kt:91-99, 207+).
                    header =
                        PlaylistHeaderInfo(
                            id = id,
                            title = "", // resolved UI-side
                            thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl,
                            songCount = tracks.size,
                            totalDurationMs = tracks.sumOf { it.durationMs ?: 0L },
                        ),
                    tracks = tracks,
                    sortInfo = PlaylistSortInfo(autoSortType.toPlaylistSongSortType(), descending),
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

    /**
     * Open-time sync (legacy AutoPlaylistScreen.kt:119-123 LaunchedEffect):
     * when YTM sync is enabled and this is the liked playlist, sync liked
     * songs on open. Pull-to-refresh then re-syncs via [refresh].
     */
    override suspend fun ensureLoaded(): Result<Unit> =
        runCatching {
            if (playlistName == "liked") {
                val ytmSync =
                    context.dataStore.data
                        .first()
                        .get(YtmSyncKey) ?: true
                if (ytmSync) {
                    syncUtils.syncLikedSongs()
                }
            }
        }

    override suspend fun setSort(
        sortType: PlaylistSongSortType,
        descending: Boolean,
    ): Result<Unit> =
        runCatching {
            // Mirrors rememberEnumPreference's write path (PreferenceStore key +
            // DataStore); observe() re-emits with the new sort automatically.
            context.dataStore.edit { preferences ->
                preferences[AutoPlaylistSongSortTypeKey] = sortType.toAutoPlaylistSongSortType().name
                preferences[AutoPlaylistSongSortDescendingKey] = descending
            }
        }
}
