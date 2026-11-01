/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.ProvideArtworkSurface
import moe.rukamori.archivetune.ui.component.artworkBackground
import moe.rukamori.archivetune.ui.component.rememberArtworkBackground
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.ui.screens.album.AlbumDownloadProgressToolbar
import moe.rukamori.archivetune.ui.screens.album.AlbumEffects
import moe.rukamori.archivetune.ui.screens.album.AlbumSelectionTopBarActions
import moe.rukamori.archivetune.ui.screens.album.AlbumTopBarNavigationIcon
import moe.rukamori.archivetune.ui.screens.album.AlbumTopBarTitle
import moe.rukamori.archivetune.ui.screens.album.albumHeaderSection
import moe.rukamori.archivetune.ui.screens.album.albumOtherVersionsSection
import moe.rukamori.archivetune.ui.screens.album.albumSongsSection
import moe.rukamori.archivetune.ui.screens.album.albumStatePlaceholders
import moe.rukamori.archivetune.ui.screens.album.rememberAlbumActions
import moe.rukamori.archivetune.ui.screens.album.rememberAlbumDownloadUiState
import moe.rukamori.archivetune.ui.screens.album.rememberAlbumSelectionState
import moe.rukamori.archivetune.ui.screens.album.rememberAlbumUiState
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.screens.album.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val albumWithSongs by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val otherVersions by viewModel.otherVersions.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val lazyListState = rememberLazyListState()

    val screenUiState = rememberAlbumUiState(albumWithSongs, uiState, otherVersions)
    val selectionState = rememberAlbumSelectionState(screenUiState.albumWithSongs, hideExplicit)
    val downloadUiState = rememberAlbumDownloadUiState()
    val hazeState = remember { HazeState() }

    AlbumEffects(
        albumWithSongs = screenUiState.albumWithSongs,
        downloadUtil = downloadUtil,
        downloadUiState = downloadUiState,
    )

    val actions =
        rememberAlbumActions(
            albumWithSongs = screenUiState.albumWithSongs,
            downloadUiState = downloadUiState,
            playerConnection = playerConnection,
            database = database,
            menuState = menuState,
            navController = navController,
            context = context,
        )

    val currentAlbumWithSongs = screenUiState.albumWithSongs
    val hasHeader = currentAlbumWithSongs != null && currentAlbumWithSongs.songs.isNotEmpty()
    val hasArtworkBackdrop = hasHeader && !currentAlbumWithSongs.album.thumbnailUrl.isNullOrBlank()

    val headerProgress = rememberHeaderScrollProgress(
        lazyListState = lazyListState,
        hasCollapsibleHeader = hasArtworkBackdrop,
        collapseDistance = ImmersiveArtworkTokens.BackdropHeight,
        standardThreshold = 48.dp,
    )

    val playerAwareInsets = LocalPlayerAwareWindowInsets.current
    val contentInsets = if (hasArtworkBackdrop) {
        playerAwareInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    } else {
        playerAwareInsets
    }
    val contentPadding = contentInsets.asPaddingValues()

    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }
    val artworkBackground = rememberArtworkBackground(currentAlbumWithSongs?.album?.thumbnailUrl)

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
                if (currentAlbumWithSongs != null && currentAlbumWithSongs.songs.isNotEmpty()) {
                    albumHeaderSection(
                        albumWithSongs = currentAlbumWithSongs,
                        songCount = selectionState.wrappedSongs.size,
                        downloadState = downloadUiState.downloadState,
                        systemBarsTopPadding = systemBarsTopPadding,
                        actions = actions,
                        headerProgress = headerProgress,
                        disableBlur = disableBlur,
                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                    )

                    albumSongsSection(
                        wrappedSongs = selectionState.wrappedSongs,
                        selection = selectionState.selection,
                        onSelectionChange = { selectionState.selection = it },
                        activeMediaId = mediaMetadata?.id,
                        isPlaying = isPlaying,
                        onSongClick = actions.onSongClick,
                        onTogglePlayPause = { playerConnection.player.togglePlayPause() },
                        onSongMenu = { song ->
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )

                    albumOtherVersionsSection(
                        otherVersions = screenUiState.otherVersions,
                        activeAlbumId = mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        coroutineScope = scope,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onAlbumLongClick = { albumItem ->
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = albumItem,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                } else {
                    albumStatePlaceholders(
                        uiState = screenUiState.uiState,
                        topPadding = 16.dp,
                        onRetry = { viewModel.retry() },
                    )
                }
            }

            HeaderHazeBackdrop(
                progress = headerProgress,
                hasCollapsibleHeader = hasArtworkBackdrop,
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            TopAppBar(
                modifier = Modifier.align(Alignment.TopCenter),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                scrollBehavior = scrollBehavior,
                title = {
                    AlbumTopBarTitle(
                        selection = selectionState.selection,
                        selectedCount = selectionState.selectedCount,
                        title = screenUiState.albumWithSongs?.album?.title.orEmpty(),
                        showTitle = showTopBarTitle,
                    )
                },
                navigationIcon = {
                    AlbumTopBarNavigationIcon(
                        selectionState = selectionState,
                        navController = navController,
                    )
                },
                actions = {
                    if (selectionState.selection) {
                        AlbumSelectionTopBarActions(
                            selectionState = selectionState,
                            menuState = menuState,
                        )
                    }
                },
            )

            AlbumDownloadProgressToolbar(
                downloadUiState = downloadUiState,
                albumWithSongs = screenUiState.albumWithSongs,
                context = context,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                        .padding(bottom = 16.dp),
            )
        }
    }
}
