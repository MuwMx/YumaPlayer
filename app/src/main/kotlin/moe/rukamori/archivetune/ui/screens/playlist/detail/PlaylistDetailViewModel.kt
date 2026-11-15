/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.constants.MyTopFilter
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playlist.source.PlaylistSourceFactory
import moe.rukamori.archivetune.playlist.source.TopPlaylistSource
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

/**
 * SavedStateHandle key the route flip (Phase E) uses to hand the route
 * prefix to this ViewModel. The nav arg itself is always present under its
 * own name (playlistId / playlist / top) — three routes share "playlistId",
 * so only the explicit route key disambiguates local vs online vs spotify.
 */
internal const val PLAYLIST_DETAIL_ROUTE_KEY = "playlist_detail_route"

/**
 * The ONE ViewModel behind every playlist route. Each flipped route declares
 * [PLAYLIST_DETAIL_ROUTE_KEY] as a nav ARGUMENT with a defaultValue, so the
 * key arrives inside this SavedStateHandle the same way the playlist argument
 * does; the playlist argument keeps its own original nav-arg key.
 *
 * It injects only [PlaylistSourceFactory] — no MusicDatabase, no Context —
 * which fixes the rule debt the legacy LocalPlaylistViewModel and
 * TopPlaylistViewModel carry. Sorting, open-time hygiene and live queries
 * live inside the sources; view-count prefetch keeps the legacy shape
 * (mutex + semaphore(4) + in-flight set).
 */
@HiltViewModel
class PlaylistDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        sourceFactory: PlaylistSourceFactory,
    ) : ViewModel() {
    private val route: String =
        savedStateHandle.get<String>(PLAYLIST_DETAIL_ROUTE_KEY)
            ?: throw IllegalStateException("PlaylistDetailViewModel requires '$PLAYLIST_DETAIL_ROUTE_KEY' in SavedStateHandle")

    internal val playlistId: String =
        savedStateHandle.keys()
            .firstNotNullOfOrNull { key ->
                when (key) {
                    "playlistId", "playlist", "top" -> savedStateHandle.get<String>(key)
                    else -> null
                }
            } ?: throw IllegalStateException("PlaylistDetailViewModel requires a playlist id argument")

    val source = sourceFactory.sourceFor(route, playlistId)

    private val _uiState = MutableStateFlow(PlaylistDetailUiState(capabilities = source.capabilities))
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private val viewCountsMutex = Mutex()
    private val viewCountsInFlight = mutableSetOf<String>()
    private val viewCountsSemaphore = Semaphore(permits = VIEW_COUNTS_PERMITS)

    init {
        viewModelScope.launch {
            source.observe().collect { result ->
                _uiState.update { state ->
                    state.copy(
                        header = result.header,
                        tracks = result.tracks,
                        sortInfo = result.sortInfo,
                        viewCounts = state.viewCounts,
                        isLoading = result.isLoading,
                        isRefreshing = result.isRefreshing,
                        isLoadingMore = result.isLoadingMore,
                        error = result.error,
                        hasContinuation = result.hasContinuation,
                    )
                }
                prefetchViewCounts(result.tracks.map { it.id })
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            source
                .ensureLoaded()
                .onFailure { reportException(it) }
        }

        // LOCAL PLAYLIST SLOT — live per-playlist editable/browseId flags
        // (RoomPlaylistSource re-emits them on playlist-row changes).
        val roomSource = source as? moe.rukamori.archivetune.playlist.source.RoomPlaylistSource
        if (roomSource != null) {
            viewModelScope.launch {
                roomSource.editable.collect { editable ->
                    _uiState.update { it.copy(editable = editable) }
                }
            }
            viewModelScope.launch {
                roomSource.browseId.collect { browseId ->
                    _uiState.update { it.copy(browseId = browseId) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            source
                .refresh()
                .onFailure { reportException(it) }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch(Dispatchers.IO) {
            source
                .loadMore()
                .onFailure { reportException(it) }
        }
    }

    fun updateSortPreference(
        newSortType: PlaylistSongSortType,
        newSortDescending: Boolean,
    ) {
        if (!source.capabilities.sortable) return
        viewModelScope.launch(Dispatchers.IO) {
            source
                .setSort(newSortType, newSortDescending)
                .onFailure { reportException(it) }
        }
    }

    fun reorder(
        from: Int,
        to: Int,
    ) {
        if (!source.capabilities.reorderable) return
        viewModelScope.launch(Dispatchers.IO) {
            source
                .reorder(from, to)
                .onFailure { reportException(it) }
        }
    }

    fun removeSong(position: Int) {
        if (!source.capabilities.swipeDelete) return
        viewModelScope.launch(Dispatchers.IO) {
            source
                .removeSong(position)
                .onFailure { reportException(it) }
        }
    }

    /**
     * TOP PLAYLIST SLOT — the period chip calls this; a no-op for every
     * other source. Pinned so the top period selector survives unification.
     */
    fun setTopPeriod(period: MyTopFilter) {
        val topSource = source as? TopPlaylistSource ?: return
        topSource.setTopPeriod(period)
    }

    private fun prefetchViewCounts(videoIds: List<String>) {
        val uniqueIds = videoIds.distinct()
        if (uniqueIds.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            coroutineScope {
                uniqueIds
                    .map { videoId ->
                        async {
                            val shouldFetch =
                                viewCountsMutex.withLock {
                                    val alreadyKnown = _uiState.value.viewCounts.containsKey(videoId)
                                    if (alreadyKnown || viewCountsInFlight.contains(videoId)) {
                                        false
                                    } else {
                                        viewCountsInFlight.add(videoId)
                                        true
                                    }
                                }

                            if (!shouldFetch) return@async

                            try {
                                viewCountsSemaphore.withPermit {
                                    val count = YouTube.getMediaInfo(videoId).getOrNull()?.viewCount
                                    if (count != null && count >= 0) {
                                        _uiState.update { state ->
                                            state.copy(viewCounts = state.viewCounts + (videoId to count))
                                        }
                                    }
                                }
                            } finally {
                                viewCountsMutex.withLock { viewCountsInFlight.remove(videoId) }
                            }
                        }
                    }.awaitAll()
            }
        }
    }

    private companion object {
        const val VIEW_COUNTS_PERMITS = 4
    }
}
