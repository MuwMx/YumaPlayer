/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.ProvideArtworkSurface
import moe.rukamori.archivetune.ui.component.artworkBackground
import moe.rukamori.archivetune.ui.component.rememberArtworkBackground
import moe.rukamori.archivetune.ui.main.LocalDownloadUtil
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistDownloadStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistSearchStateHolder

/**
 * The unified playlist detail screen for all six playlist routes. Each
 * route declares PLAYLIST_DETAIL_ROUTE_KEY as a nav ARGUMENT with a
 * defaultValue (never part of the route pattern itself), so hiltViewModel()
 * resolves the VM with the route key already inside its SavedStateHandle —
 * the same mechanism every other nav-arg (playlistId et al.) uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    navBackStackEntry: NavBackStackEntry,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: PlaylistDetailViewModel = hiltViewModel(navBackStackEntry),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val capabilities = uiState.capabilities
    val tracks = uiState.tracks
    val mediaMetadataId = mediaMetadata?.id

    val searchState =
        rememberPlaylistSearchStateHolder(
            items = tracks,
            filter = { track, query ->
                track.title.contains(query, ignoreCase = true) ||
                    track.artists.any { it.contains(query, ignoreCase = true) }
            },
        )
    val downloadHolder = rememberPlaylistDownloadStateHolder()
    val downloadUtil = LocalDownloadUtil.current
    val haptics = rememberYumaHaptics()
    // LOCAL PLAYLIST SLOT — lock + swipe-to-song gate reorder/swipe-delete
    // (LocalPlaylistScreen.kt:95).
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)

    val systemBarsTopPadding: Dp = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val lazyListState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val snackbarHostState = remember { SnackbarHostState() }

    val mutableTracks = rememberPlaylistDetailTracks(tracks = tracks)
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Render model + hero header: see rememberPlaylistDetailRenderState /
    // rememberPlaylistDetailHeader (search-gated, origin-aware).
    val renderState = rememberPlaylistDetailRenderState(tracks, mutableTracks, searchState)
    val visibleTracks = renderState.visibleTracks
    val selectedTracks = renderState.selectedTracks
    val header = rememberPlaylistDetailHeader(uiState, viewModel, searchState.isSearching)
    val hasArtworkBackdrop = header != null && !header?.thumbnailUrl.isNullOrBlank()

    // Hero + header-slot items above the track list (drag/scrollbar offsets).
    val headerItems = if (header != null) 1 + playlistDetailHeaderSlotCount(uiState, viewModel) else 0
    val reorderableState =
        rememberPlaylistDetailReorderableState(
            lazyListState, headerItems, mutableTracks,
            dragInfo, onDragInfoChange = { dragInfo = it },
        )

    PlaylistDetailEffects(
        downloadHolder = downloadHolder, downloadUtil = downloadUtil, tracks = tracks,
        capabilities = capabilities, lazyListState = lazyListState,
        reorderableState = reorderableState, dragInfo = dragInfo,
        onResetDragInfo = { dragInfo = null }, viewModel = viewModel,
    )
    val headerProgress =
        rememberHeaderScrollProgress(
            lazyListState = lazyListState,
            hasCollapsibleHeader = hasArtworkBackdrop,
            collapseDistance = ImmersiveArtworkTokens.BackdropHeight,
            standardThreshold = 48.dp,
        )
    val artworkBackground =
        rememberArtworkBackground(header?.thumbnailUrl)

    ExpressivePullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().artworkBackground(artworkBackground),
    ) {
        ProvideArtworkSurface(artworkBackground) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            ) {
                when {
                    uiState.isLoading -> playlistDetailShimmerItem()
                    uiState.error != null ->
                        playlistDetailErrorItem(error = uiState.error, onRetry = viewModel::refresh)
                    else -> {
                        playlistDetailHeroItem(
                            header = header,
                            systemBarsTopPadding = systemBarsTopPadding,
                            headerProgress = headerProgress,
                            disableBlur = disableBlur,
                            actions = {
                                PlaylistDetailHeroActionsHost(
                                    capabilities = capabilities,
                                    source = viewModel.source,
                                    playerConnection = playerConnection,
                                    downloadHolder = downloadHolder,
                                    title = header?.title.orEmpty(),
                                    tracks = visibleTracks,
                                )
                            },
                        )

                        renderPlaylistDetailHeaderSlot(
                            uiState = uiState,
                            viewModel = viewModel,
                        )

                        if (visibleTracks.isEmpty() && uiState.error == null) {
                            playlistDetailEmptyItem(searchState.isSearching)
                        } else {
                            playlistDetailSongs(
                                tracks = visibleTracks,
                                selectedTracks = selectedTracks,
                                capabilities = capabilities,
                                reorderableState = reorderableState,
                                isScrollInProgress = lazyListState.isScrollInProgress,
                                mediaMetadataId = mediaMetadataId,
                                isPlaying = isPlaying,
                                viewCounts = uiState.viewCounts,
                                sortType = uiState.sortInfo?.sortType ?: PlaylistSongSortType.CUSTOM,
                                selection = searchState.selection, isSearching = searchState.isSearching,
                                locked = locked, swipeToSongEnabled = swipeToSongEnabled,
                                showTrackNumbers = capabilities.showsTrackNumbers,
                                downloads = downloadHolder.downloads,
                                navController = navController,
                                playlistBrowseId = null,
                                menuState = menuState,
                                onSongClick = { index, track ->
                                    onPlaylistDetailRowClick(
                                        viewModel = viewModel,
                                        searchState = searchState,
                                        playerConnection = playerConnection,
                                        mediaMetadataId = mediaMetadataId,
                                        tracks = tracks,
                                        visibleTracks = visibleTracks,
                                        playlistTitle = header?.title.orEmpty(),
                                        track = track,
                                        index = index,
                                    )
                                },
                                onSongLongClick = { _, track ->
                                    onPlaylistDetailRowLongClick(
                                        searchState = searchState,
                                        track = track,
                                        onHaptic = haptics::longPress,
                                    )
                                },
                                onSwipeDelete = viewModel::removeSong,
                            )
                        }

                        if (capabilities.paginated && uiState.hasContinuation && uiState.isLoadingMore) {
                            playlistDetailLoadingMoreItem()
                        }
                    }
                }
            }

            PlaylistDetailOverlays(
                lazyListState = lazyListState,
                headerItems = headerItems,
                headerProgress = headerProgress,
                hasArtworkBackdrop = hasArtworkBackdrop,
                hazeState = hazeState,
                downloadHolder = downloadHolder,
                songIds = tracks.map { it.id },
                snackbarHostState = snackbarHostState,
            )

            PlaylistDetailTopBarHost(
                searchState = searchState,
                menuState = menuState,
                navController = navController,
                lazyListState = lazyListState,
                title = header?.title.orEmpty(),
                scrollBehavior = scrollBehavior,
                onRemoveSongFromCache = onPlaylistDetailRemoveSongFromCache(viewModel),
                trailingAction =
                    renderPlaylistDetailTrailingAction(
                        viewModel = viewModel,
                        menuState = menuState,
                        tracks = tracks,
                        onClearSelection = { searchState.clearSelection() },
                    ),
            )
        }
    }
}
