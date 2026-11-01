/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortDescendingKey
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortType
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortTypeKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.ProvideArtworkSurface
import moe.rukamori.archivetune.ui.component.artworkBackground
import moe.rukamori.archivetune.ui.component.rememberArtworkBackground
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.ui.screens.playlist.auto.AutoPlaylistHeader
import moe.rukamori.archivetune.ui.screens.playlist.auto.AutoPlaylistRemoveDownloadDialog
import moe.rukamori.archivetune.ui.screens.playlist.auto.autoPlaylistEmptyState
import moe.rukamori.archivetune.ui.screens.playlist.auto.autoPlaylistRuleCards
import moe.rukamori.archivetune.ui.screens.playlist.auto.autoPlaylistTrackRows
import moe.rukamori.archivetune.ui.screens.playlist.auto.playlistScrollbar
import moe.rukamori.archivetune.ui.screens.playlist.auto.rememberAutoPlaylistDownloadState
import moe.rukamori.archivetune.ui.screens.playlist.auto.rememberAutoPlaylistSearchState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.screens.playlist.AutoPlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playlist =
        if (viewModel.playlist == "liked") stringResource(R.string.liked) else stringResource(R.string.offline)

    val songs by viewModel.likedSongs.collectAsStateWithLifecycle()

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val likeLength = remember(songs) { songs.fastSumBy { it.song.duration } }

    val playlistId = viewModel.playlist
    val playlistType =
        when (playlistId) {
            "liked" -> PlaylistType.LIKE
            "downloaded" -> PlaylistType.DOWNLOAD
            else -> PlaylistType.OTHER
        }

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            AutoPlaylistSongSortTypeKey,
            AutoPlaylistSongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(AutoPlaylistSongSortDescendingKey, true)

    val searchState = rememberAutoPlaylistSearchState(songs)
    val downloadUiState = rememberAutoPlaylistDownloadState(songs)

    LaunchedEffect(ytmSync, playlistType) {
        if (ytmSync && playlistType == PlaylistType.LIKE) {
            viewModel.syncLikedSongs()
        }
    }

    if (downloadUiState.showRemoveDownloadDialog) {
        AutoPlaylistRemoveDownloadDialog(
            playlist = playlist,
            songs = songs,
            onDismiss = { downloadUiState.showRemoveDownloadDialog = false },
            context = context,
        )
    }

    val mediaItems =
        remember(searchState.filteredSongs) {
            searchState.filteredSongs.map { it.item.toMediaItem() }
        }

    val onSongClick =
        remember(mediaItems, searchState.filteredSongs, playlist, playerConnection) {
            { songId: String ->
                val index = searchState.filteredSongs.indexOfFirst { it.item.id == songId }
                if (index != -1) {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist,
                            items = mediaItems,
                            startIndex = index,
                        ),
                    )
                }
            }
        }

    val lazyListState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val headerItems by remember {
        derivedStateOf {
            if (songs.isNotEmpty() && !searchState.isSearching) 2 else 0
        }
    }
    val hasHeader = headerItems > 0
    val firstSongThumbnail = songs.firstOrNull()?.song?.thumbnailUrl
    val hasArtworkBackdrop = hasHeader && !firstSongThumbnail.isNullOrBlank()
    val headerProgress = rememberHeaderScrollProgress(
        lazyListState = lazyListState,
        hasCollapsibleHeader = hasArtworkBackdrop,
        collapseDistance = ImmersiveArtworkTokens.BackdropHeight,
        standardThreshold = 48.dp,
    )
    val playerAwareInsets = LocalPlayerAwareWindowInsets.current
    val imeInsets = WindowInsets.ime
    val contentInsets = if (hasArtworkBackdrop) {
        playerAwareInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    } else {
        playerAwareInsets
    }
    val contentPadding = contentInsets.union(imeInsets).asPaddingValues()

    val artworkBackground = rememberArtworkBackground(firstSongThumbnail)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .artworkBackground(artworkBackground),
    ) {
        ProvideArtworkSurface(artworkBackground) {
            LazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        ) {
            if (!searchState.isSearching) {
                item(
                    key = "header",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    AutoPlaylistHeader(
                        playlist = playlist,
                        songs = songs,
                        downloadState = downloadUiState.downloadState,
                        globalProgress = (downloadUiState.globalDownloadState as? HeaderDownloadState.Partial)?.progress,
                        onDownloadToggle = {
                            sendAddMissingDownloads(
                                context = context,
                                songs =
                                    songs.map {
                                        HeaderDownloadItem(
                                            id = it.song.id,
                                            title = it.song.title,
                                        )
                                    },
                                downloads = downloadUiState.downloads,
                            )
                        },
                        onRemoveConfirm = {
                            downloadUiState.showRemoveDownloadDialog = true
                        },
                        onProgressNavigate = {
                            navController.navigate("auto_playlist/downloaded?tab=progress")
                        },
                        playerConnection = playerConnection,
                        headerProgress = headerProgress,
                        disableBlur = disableBlur,
                        systemBarsTopPadding = systemBarsTopPadding,
                        likeLength = likeLength,
                    )
                }
            }

            if (songs.isEmpty()) {
                autoPlaylistEmptyState()
            } else {
                autoPlaylistRuleCards(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                )

                autoPlaylistTrackRows(
                    filteredSongs = searchState.filteredSongs,
                    wrappedSongs = searchState.wrappedSongs,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    selection = searchState.selection,
                    selectedCount = searchState.selectedCount,
                    onSelectionChange = { searchState.selection = it },
                    playerConnection = playerConnection,
                    navController = navController,
                    menuState = menuState,
                    haptics = haptics,
                    downloads = downloadUiState.downloads,
                    onSongClick = onSongClick,
                )
            }
        }

        playlistScrollbar(
            scrollState = lazyListState,
            headerItems = headerItems,
        )

        HeaderHazeBackdrop(
            progress = headerProgress,
            hasCollapsibleHeader = hasArtworkBackdrop,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AutoPlaylistSelectionTopBar(
            state =
                SelectionTopBarUiState(
                    selection = searchState.selection,
                    selectedCount = searchState.selectedCount,
                    totalCount = searchState.wrappedSongs.size,
                    isSearching = searchState.isSearching,
                    query = searchState.query,
                    showTopBarTitle = showTopBarTitle,
                    title = playlist,
                    hasSongs = songs.isNotEmpty(),
                ),
            callbacks =
                SelectionTopBarCallbacks(
                    onQueryChange = { searchState.query = it },
                    onSearchOpen = searchState::openSearch,
                    onSearchClose = { searchState.closeSearch(focusManager) },
                    onSelectionClear = searchState::clearSelection,
                    onSelectAll = searchState::toggleSelectAll,
                    onShowSelectionMenu = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection =
                                    searchState.wrappedSongs
                                        .filter { it.isSelected }
                                        .map { it.item },
                                onDismiss = menuState::dismiss,
                                clearAction = searchState::clearSelection,
                            )
                        }
                    },
                    onShowMoreMenu = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = songs,
                                onDismiss = menuState::dismiss,
                                clearAction = {},
                            )
                        }
                    },
                    onNavigateUp = { navController.navigateUp() },
                    onBackToMain = { navController.backToMain() },
                ),
            focusRequester = searchState.focusRequester,
            scrollBehavior = scrollBehavior,
        )
        }
    }
}
