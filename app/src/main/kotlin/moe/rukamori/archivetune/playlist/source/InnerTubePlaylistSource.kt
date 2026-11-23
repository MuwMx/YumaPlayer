/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

/**
 * InnerTube (YouTube Music) source for the "online_playlist/{playlistId}"
 * route. Mirrors OnlinePlaylistViewModel: one-shot load + continuation-based
 * pagination + pull-to-refresh; blocked artists filtered at ingest.
 *
 * Not a singleton — one instance per playlist screen (holds continuation
 * state); provided by [PlaylistSourceFactory].
 */
class InnerTubePlaylistSource
    @Inject
    constructor(
        internal val browseId: String,
        private val database: MusicDatabase,
    ) : PlaylistTracksSource {
    override val id: String = "yt:$browseId"
    override val capabilities: PlaylistCapabilities = PlaylistCapabilities.InnerTube

    private val state = MutableStateFlow(PlaylistTracksResult())
    override fun observe(): Flow<PlaylistTracksResult> = state.asStateFlow()

    internal val currentTracks: List<PlaylistTrack> get() = state.value.tracks

    private var continuation: String? = null
    private val loadMutex = Mutex()
    private var initialLoaded = false

    /**
     * ONLINE PLAYLIST SLOT — playback endpoints from the loaded
     * [PlaylistItem] that the LCD header cannot carry. The unified screen's
     * song-click and hero play/shuffle actions read these exactly like the
     * legacy OnlinePlaylistActions did; null until the first load succeeds.
     *
     * Internally stored only — the UI consumes domain-safe functions
     * ([createPlayQueue], etc.) instead of DTO references.
     */
    private var _playEndpoint: WatchEndpoint? = null
    private var _shuffleEndpoint: WatchEndpoint? = null
    private var _radioEndpoint: WatchEndpoint? = null

    /** Computes a playable YouTube queue, or null before initial load. */
    internal fun createPlayQueue(): YouTubeQueue? =
        _playEndpoint?.let { YouTubeQueue.playlist(it) }

    /** Computes a shuffle YouTube queue, or null before initial load. */
    internal fun createShuffleQueue(): YouTubeQueue? =
        _shuffleEndpoint?.let { YouTubeQueue.playlist(it) }

    /** Computes a radio YouTube queue, or null before initial load. */
    internal fun createRadioQueue(): YouTubeQueue? =
        _radioEndpoint?.let { YouTubeQueue(it) }

    /**
     * The loaded playlist's model — null until the first load succeeds.
     * Only exposed through domain-safe projections so DTOs never cross into
     * UI composables.
     */
    private var _playlistItem: PlaylistItem? = null

    /** True when the browseId is "LM" (liked mix — not bookmarkable). */
    internal val isLikedPlaylist: Boolean get() = _playlistItem?.id == "LM"

    /** Playlist params for constructing per-song streaming endpoints. */
    internal val playlistPlayParams: String? get() = _playEndpoint?.params

    /**
     * Bridge accessor for [InnerTubePlaylistMutations.togglePlaylistBookmark]
     * — a captive domain method that needs the full DTO to upsert Room rows
     * (title, thumbnail, isEditable, endpoint params). When refactoring the
     * bookmark logic to accept primitives, replace this with a data holder.
     */
    internal val playlistMenuItem: PlaylistItem? get() = _playlistItem

    override suspend fun refresh(): Result<Unit> =
        load(initial = false)

    override suspend fun ensureLoaded(): Result<Unit> =
        load(initial = true)

    override suspend fun loadMore(): Result<Unit> =
        continuation?.let { cont ->
            loadMutex.withLock {
                state.value = state.value.copy(isLoadingMore = true)
                val result =
                    YouTube
                        .playlistContinuation(cont, browseId)
                        .onSuccess { page ->
                            val visible =
                                page.songs.filterBlockedArtists(database.getBlockedArtistIds().toSet())
                            val currentTracks = state.value.tracks
                            state.value =
                                state.value.copy(
                                    tracks = currentTracks + visible.map { it.toPlaylistTrack() },
                                    isLoadingMore = false,
                                    hasContinuation = page.continuation != null,
                                    error = null,
                                )
                            continuation = page.continuation
                        }.onFailure { throwable ->
                            reportException(throwable)
                            state.value =
                                state.value.copy(
                                    isLoadingMore = false,
                                    error = throwable.message ?: "Failed to load more",
                                    hasContinuation = continuation != null,
                                )
                        }
                result.map { }
            }
        } ?: Result.success(Unit)

    suspend fun load(initial: Boolean): Result<Unit> =
        loadMutex.withLock {
            if (initial && initialLoaded) return Result.success(Unit)
            val wasRefresh = !initial
            if (initial) initialLoaded = true
            state.value =
                state.value.copy(
                    isLoading = initial,
                    isRefreshing = wasRefresh,
                    error = null,
                )
            YouTube
                .playlist(browseId)
                .onSuccess { page ->
                    val playlist: PlaylistItem = page.playlist
                    val visible =
                        page.songs.filterBlockedArtists(database.getBlockedArtistIds().toSet())
                    continuation = page.songsContinuation ?: page.continuation
                    _playEndpoint = playlist.playEndpoint
                    _shuffleEndpoint = playlist.shuffleEndpoint
                    _radioEndpoint = playlist.radioEndpoint
                    _playlistItem = playlist
                    state.value =
                        PlaylistTracksResult(
                            header =
                                PlaylistHeaderInfo(
                                    id = playlist.id,
                                    title = playlist.title,
                                    author = playlist.author?.name,
                                    authorId = playlist.author?.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    songCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                                    description = playlist.description,
                                ),
                            tracks = visible.map { it.toPlaylistTrack() },
                            hasContinuation = continuation != null,
                        )
                }.onFailure { throwable ->
                    reportException(throwable)
                    state.value =
                        state.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = throwable.message ?: "Failed to load playlist",
                        )
                }
            Result.success(Unit)
        }

}
