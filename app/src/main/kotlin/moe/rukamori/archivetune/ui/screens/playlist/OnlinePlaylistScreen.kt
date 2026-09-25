/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.OnlinePlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val viewCounts by viewModel.viewCounts.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchState = rememberOnlinePlaylistSearchState(songs)
    val downloadUiState = rememberOnlinePlaylistDownloadUiState()
    val (gradientColors, gradientAlpha) =
        rememberOnlinePlaylistGradientState(
            thumbnailUrl = playlist?.thumbnail,
            context = context,
            fallbackColor = MaterialTheme.colorScheme.surface.toArgb(),
            lazyListState = lazyListState,
        )

    OnlinePlaylistEffects(
        searchState = searchState,
        downloadUiState = downloadUiState,
        songs = songs,
        downloadUtil = downloadUtil,
        lazyListState = lazyListState,
        onLoadMore = viewModel::loadMoreSongs,
    )

    val uiState =
        rememberOnlinePlaylistUiState(
            playlist = playlist,
            songs = songs,
            viewCounts = viewCounts,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            isLoadingMore = isLoadingMore,
            error = error,
            dbPlaylist = dbPlaylist,
            hasContinuation = viewModel.continuation != null,
        )

    val actions =
        rememberOnlinePlaylistActions(
            playlist = playlist,
            songs = songs,
            dbPlaylist = dbPlaylist,
            downloadUiState = downloadUiState,
            searchState = searchState,
            mediaMetadataId = mediaMetadata?.id,
            playerConnection = playerConnection,
            database = database,
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            navController = navController,
            menuState = menuState,
            context = context,
            haptics = haptics,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMoreSongs,
        )

    val headerItems by remember {
        derivedStateOf {
            val current = uiState.playlist
            if (!uiState.isLoading && current != null && !searchState.isSearching) 1 else 0
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
    ) {
        OnlinePlaylistGradientBackground(
            gradientColors = gradientColors,
            gradientAlpha = gradientAlpha,
            surfaceColor = MaterialTheme.colorScheme.surface,
            disableBlur = disableBlur,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            if (uiState.isLoading) {
                onlinePlaylistShimmerItem(systemBarsTopPadding = systemBarsTopPadding)
            } else if (uiState.playlist != null) {
                if (!searchState.isSearching) {
                    onlinePlaylistHeaderItem(
                        playlist = uiState.playlist,
                        isBookmarked = uiState.isBookmarked,
                        downloadState = downloadUiState.downloadState,
                        gradientColors = gradientColors,
                        systemBarsTopPadding = systemBarsTopPadding,
                        actions = actions,
                    )
                }

                if (uiState.songs.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    onlinePlaylistEmptyItem()
                }

                onlinePlaylistSongItems(
                    wrappedSongs = searchState.wrappedSongs,
                    viewCounts = uiState.viewCounts,
                    activeMediaId = mediaMetadata?.id,
                    isPlaying = isPlaying,
                    selection = searchState.selection,
                    hideExplicit = hideExplicit,
                    actions = actions,
                )

                if (uiState.hasContinuation && uiState.songs.isNotEmpty() && uiState.isLoadingMore) {
                    onlinePlaylistLoadingMoreItem()
                }
            } else {
                onlinePlaylistErrorItem(
                    error = uiState.error,
                    onRetry = actions.onRetry,
                )
            }
        }

        OnlinePlaylistScrollbar(
            scrollState = lazyListState,
            headerItems = headerItems,
            modifier =
                Modifier
                    .padding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues())
                    .align(Alignment.CenterEnd),
        )

        OnlinePlaylistTopBar(
            searchState = searchState,
            playlist = uiState.playlist,
            showTopBarTitle = remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }.value,
            menuState = menuState,
            navController = navController,
            onMenu = actions.onMenu,
        )

        OnlinePlaylistDownloadProgressToolbar(
            downloadUiState = downloadUiState,
            songs = uiState.songs,
            context = context,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(bottom = 16.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}
