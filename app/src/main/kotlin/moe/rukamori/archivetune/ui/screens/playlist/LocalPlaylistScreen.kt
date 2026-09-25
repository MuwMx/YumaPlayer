/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LocalPlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
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
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val sortDescending by viewModel.sortDescending.collectAsStateWithLifecycle()

    val uiState = rememberLocalPlaylistUiState(playlist, songs, sortType, sortDescending, viewCounts, isRefreshing)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadUtil = LocalDownloadUtil.current
    val dialogState = rememberLocalPlaylistDialogState()
    val searchState = rememberLocalPlaylistSearchState(songs)
    val downloadUiState = rememberLocalPlaylistDownloadUiState()

    val editable: Boolean = playlist?.playlist?.isEditable == true
    val playlistLength = remember(songs) { songs.fastSumBy { it.song.song.duration } }
    val pickCoverLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            updateLocalPlaylistCover(context, database, playlist, uri)
        }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            val hasContent = current != null && (current.songCount > 0 || current.playlist.remoteSongCount != 0)
            if (hasContent && !searchState.isSearching) 2 else 0
        }
    }
    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState =
        rememberLocalPlaylistReorderableState(
            lazyListState = lazyListState,
            headerItems = headerItems,
            mutableSongs = downloadUiState.mutableSongs,
            dragInfo = dragInfo,
            onDragInfoChange = { dragInfo = it },
        )

    val actions =
        rememberLocalPlaylistActions(
            playlist = playlist,
            songs = songs,
            playlistId = viewModel.playlistId,
            playerConnection = playerConnection,
            mediaMetadataId = mediaMetadata?.id,
            database = database,
            downloadState = downloadUiState.downloadState,
            downloads = downloadUiState.downloads,
            editable = editable,
            context = context,
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            dialogState = dialogState,
            onPickCover = { pickCoverLauncher.launch(arrayOf("image/*")) },
            onStartDownload = {
                downloadUiState.dismissed = false
                startLocalPlaylistDownloads(context, songs, downloadUiState.downloads)
            },
            onSortTypeChange = { viewModel.updateSortPreference(it, sortDescending) },
            onSortDescendingChange = { viewModel.updateSortPreference(sortType, it) },
            onRefresh = viewModel::refresh,
            onLockToggle = { locked = !locked },
        )

    LocalPlaylistEffects(
        searchState = searchState,
        downloadState = downloadUiState,
        songs = songs,
        downloadUtil = downloadUtil,
        isDragging = reorderableState.isAnyItemDragging,
        dragInfo = dragInfo,
        onReorderCommit = actions.onReorderCommit,
        onResetDragInfo = { dragInfo = null },
    )

    val (gradientColors, gradientAlpha) =
        rememberLocalPlaylistGradientState(
            thumbnails = playlist?.thumbnails,
            context = context,
            fallbackColor = MaterialTheme.colorScheme.surface.toArgb(),
            lazyListState = lazyListState,
        )
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = actions.onRefresh,
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        if (!searchState.isSearching) {
            LocalPlaylistMeshGradient(
                disableBlur = disableBlur,
                gradientColors = gradientColors,
                gradientAlpha = gradientAlpha,
                surfaceColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            uiState.playlist?.let { currentPlaylist ->
                if (currentPlaylist.songCount == 0 && currentPlaylist.playlist.remoteSongCount == 0) {
                    localPlaylistEmptyItem()
                } else if (!searchState.isSearching) {
                    localPlaylistHeroItem(
                        playlist = currentPlaylist,
                        playlistLength = playlistLength,
                        gradientColors = gradientColors,
                        systemBarsTopPadding = systemBarsTopPadding,
                        downloadState = downloadUiState.downloadState,
                        actions = actions,
                    )

                    localPlaylistSortHeaderItem(
                        sortType = uiState.sortType,
                        sortDescending = uiState.sortDescending,
                        onSortTypeChange = actions.onSortTypeChange,
                        onSortDescendingChange = actions.onSortDescendingChange,
                        editable = editable,
                        locked = locked,
                        onToggleLock = actions.onLockToggle,
                    )
                }
            }

            localPlaylistSongs(
                searchState = searchState,
                downloadUiState = downloadUiState,
                reorderableState = reorderableState,
                lazyListState = lazyListState,
                mediaMetadataId = mediaMetadata?.id,
                isPlaying = isPlaying,
                uiState = uiState,
                locked = locked,
                editable = editable,
                swipeToSongEnabled = swipeToSongEnabled,
                navController = navController,
                menuState = menuState,
                coroutineScope = coroutineScope,
                database = database,
                snackbarHostState = snackbarHostState,
                context = context,
                playerConnection = playerConnection,
                haptics = haptics,
            )

            if (!searchState.selection && !searchState.isSearching) {
                item(key = LOCAL_PLAYLIST_KEY_SUGGESTIONS, contentType = CONTENT_TYPE_LOCAL_PLAYLIST_SUGGESTIONS) {
                    PlaylistSuggestionsSection(
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = headerItems,
        )

        LocalPlaylistTopBar(
            searchState = searchState,
            playlistTitle = playlist?.playlist?.name.orEmpty(),
            browseId = playlist?.playlist?.browseId,
            showTopBarTitle = remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }.value,
            menuState = menuState,
            navController = navController,
            onEdit = {
                if (playlist?.playlist?.isEditable == true) {
                    actions.onEdit()
                } else {
                    actions.onSync()
                }
            },
            isEditable = playlist?.playlist?.isEditable == true,
        )

        LocalPlaylistDownloadProgressToolbar(
            downloadUiState = downloadUiState,
            songs = songs,
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

    LocalPlaylistDialogsHost(
        dialogState = dialogState,
        playlist = playlist,
        songs = songs,
        editable = editable,
        database = database,
        coroutineScope = viewModel.viewModelScope,
        context = context,
        onDeleteSuccess = { navController.popBackStack() },
    )
}
