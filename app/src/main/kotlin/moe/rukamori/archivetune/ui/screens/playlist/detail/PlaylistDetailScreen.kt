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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.playlist.source.RoomPlaylistSource
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.ProvideArtworkSurface
import moe.rukamori.archivetune.ui.component.artworkBackground
import moe.rukamori.archivetune.ui.component.rememberArtworkBackground
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalDownloadUtil
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistBadgeStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistDownloadStateHolder

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
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(navBackStackEntry),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    // ONLINE rows gate explicit songs' taps behind this preference
    // (legacy OnlinePlaylistScreen.kt:86 + ListSection.kt:259).
    val hideExplicit by rememberPreference(moe.rukamori.archivetune.constants.HideExplicitKey, false)

    val capabilities = uiState.capabilities
    val tracks = uiState.tracks
    val mediaMetadataId = mediaMetadata?.id

    val searchState = rememberPlaylistDetailSearchState(tracks)
    val downloadHolder = rememberPlaylistDownloadStateHolder()
    // BADGE HOIST — liked/in-library/spotify-liked id sets, one screen-level
    // subscription instead of a per-row DB lookup (the 0412b6f7 pattern the
    // online playlist call site never received).
    val badgeHolder = rememberPlaylistBadgeStateHolder(LocalDatabase.current)
    val downloadUtil = LocalDownloadUtil.current
    val haptics = rememberYumaHaptics()
    // LOCAL PLAYLIST SLOT — lock + swipe-to-song gate reorder/swipe-delete.
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
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
    val emptyTexts = rememberPlaylistDetailEmptyTexts(viewModel)
    val hasArtworkBackdrop = header != null && !header.thumbnailUrl.isNullOrBlank()

    // LOCAL PLAYLIST SLOT — pick-cover launcher, dialog flags, live liked/
    // name state. Null on every other route.
    val context = LocalContext.current
    val localGlue = rememberLocalPlaylistGlue(
        viewModel.source as? RoomPlaylistSource, uiState.editable, context,
        onCoverPicked = { uri -> viewModel.updateCover(context, uri) },
        onDeletePlaylist = { viewModel.deletePlaylist(onDeleted = navController::popBackStack) })

    // Hero + header-slot items above the track list (drag/scrollbar offsets).
    val headerItems = if (header != null) 1 + playlistDetailHeaderSlotCount(uiState, viewModel) else 0
    val reorderableState = rememberPlaylistDetailReorderableState(
        lazyListState, headerItems, mutableTracks, dragInfo,
        onDragInfoChange = { dragInfo = it })

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
    val artworkBackground = rememberArtworkBackground(header?.thumbnailUrl)
    // Player-aware + IME content padding — see
    // rememberPlaylistDetailContentPadding in Wiring.
    val contentPadding = rememberPlaylistDetailContentPadding(hasArtworkBackdrop)

    ExpressivePullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().artworkBackground(artworkBackground),
    ) {
        ProvideArtworkSurface(artworkBackground) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = contentPadding,
            ) {
                when {
                    uiState.isLoading -> playlistDetailShimmerItem()
                    uiState.error != null ->
                        playlistDetailErrorItem(error = uiState.error, onRetry = viewModel::refresh)
                    else -> {
                        // LOCAL PLAYLIST SLOT — leading hero button, pick-cover
                        // overlay and the editable-aware remove-downloads
                        // confirm; every non-local route passes nulls through.
                        playlistDetailHeroItem(
                            header, systemBarsTopPadding, headerProgress, disableBlur,
                            actions = {
                                localPlaylistHeroActionsOrUnified(
                                    viewModel, localGlue, capabilities, playerConnection,
                                    downloadHolder, header?.title.orEmpty(), visibleTracks, context,
                                )
                            },
                            artworkOverlay = localPlaylistPickCoverOverlayOrNull(
                                localGlue, systemBarsTopPadding, hasArtworkBackdrop),
                            // ONLINE PLAYLIST SLOT — the author line links into
                            // the artist page (OnlinePlaylistActions.kt:285-287).
                            onAuthorClick = { authorId -> navController.navigate("artist/$authorId") },
                        )

                        renderPlaylistDetailHeaderSlot(uiState, viewModel, locked, { locked = !locked })

                        playlistDetailListBody(
                            viewModel = viewModel,
                            uiState = uiState,
                            searchState = searchState,
                            header = header,
                            emptyTexts = emptyTexts,
                            visibleTracks = visibleTracks,
                            selectedTracks = selectedTracks,
                            lazyListState = lazyListState,
                            reorderableState = reorderableState,
                            playerConnection = playerConnection,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            locked = locked,
                            swipeToSongEnabled = swipeToSongEnabled,
                            hideExplicit = hideExplicit,
                            downloadHolder = downloadHolder,
                            badgeHolder = badgeHolder,
                            onShowMenu = { track ->
                                menuState.show {
                                    PlaylistDetailMenuContent(
                                        track, searchState, uiState, navController,
                                        menuState::dismiss,
                                    )
                                }
                            },
                            haptics = haptics,
                            showLocalSuggestions = localGlue != null,
                        )
                    }
                }
            }

            // Overlays (scrollbar, haze, download toolbar, snackbar) + top bar
            // — see PlaylistDetailScaffoldTail.
            PlaylistDetailScaffoldTail(
                viewModel = viewModel,
                searchState = searchState,
                menuState = menuState,
                navController = navController,
                lazyListState = lazyListState,
                headerItems = headerItems,
                headerProgress = headerProgress,
                hasArtworkBackdrop = hasArtworkBackdrop,
                hazeState = hazeState,
                downloadHolder = downloadHolder,
                tracks = tracks,
                title = header?.title.orEmpty(),
                scrollBehavior = scrollBehavior,
                snackbarHostState = snackbarHostState,
                localGlue = localGlue,
                editable = uiState.editable,
                likeSourceHint = localPlaylistLikeSourceHint(uiState.browseId),
            )
        }
    }
}
