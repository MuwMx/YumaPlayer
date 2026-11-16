/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

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
    override fun observe(): StateFlow<PlaylistTracksResult> = state.asStateFlow()

    private var continuation: String? = null
    private val loadMutex = Mutex()
    private var initialLoaded = false

    /**
     * ONLINE PLAYLIST SLOT — playback endpoints from the loaded
     * [PlaylistItem] that the LCD header cannot carry. The unified screen's
     * song-click and hero play/shuffle actions read these exactly like the
     * legacy OnlinePlaylistActions did; null until the first load succeeds.
     */
    var playEndpoint: WatchEndpoint? = null
        private set

    var shuffleEndpoint: WatchEndpoint? = null
        private set

    var radioEndpoint: WatchEndpoint? = null
        private set

    /**
     * The loaded playlist's model — null until the first load succeeds.
     * The hero's ONLINE PLAYLIST SLOT (bookmark heart / LM-radio fallback)
     * and the top bar's YouTubePlaylistMenu read this exactly like the legacy
     * screen read its `playlist` state.
     */
    var playlistItem: PlaylistItem? = null
        private set

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
                            val current = currentSongItems()
                            state.value =
                                state.value.copy(
                                    tracks = (current + visible).map { it.toPlaylistTrack() },
                                    isLoadingMore = false,
                                    hasContinuation = page.continuation != null,
                                )
                            continuation = page.continuation
                        }.onFailure {
                            state.value = state.value.copy(isLoadingMore = false)
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
                    playEndpoint = playlist.playEndpoint
                    shuffleEndpoint = playlist.shuffleEndpoint
                    radioEndpoint = playlist.radioEndpoint
                    playlistItem = playlist
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

    fun currentSongItems(): List<SongItem> = state.value.tracks.mapNotNull { it.origin as? SongItem }
}
