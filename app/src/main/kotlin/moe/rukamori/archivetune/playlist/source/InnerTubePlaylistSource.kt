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
        private val browseId: String,
        private val database: MusicDatabase,
    ) : PlaylistTracksSource {
    override val id: String = "yt:$browseId"
    override val capabilities: PlaylistCapabilities = PlaylistCapabilities.InnerTube

    private val state = MutableStateFlow(PlaylistTracksResult())
    override fun observe(): StateFlow<PlaylistTracksResult> = state.asStateFlow()

    private var continuation: String? = null
    private val loadMutex = Mutex()
    private var initialLoaded = false

    override suspend fun refresh(): Result<Unit> =
        load(initial = false)

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
                    state.value =
                        PlaylistTracksResult(
                            header =
                                PlaylistHeaderInfo(
                                    id = playlist.id,
                                    title = playlist.title,
                                    author = playlist.author?.name,
                                    thumbnailUrl = playlist.thumbnail,
                                    songCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
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

    private fun currentSongItems(): List<SongItem> = state.value.tracks.mapNotNull { it.origin as? SongItem }
}
