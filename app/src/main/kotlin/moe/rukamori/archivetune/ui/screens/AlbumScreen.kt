/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.rememberCollapseFraction
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.AlbumViewModel

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

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val screenWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val lazyListState = rememberLazyListState()

    val screenUiState = rememberAlbumUiState(albumWithSongs, uiState, otherVersions)
    val selectionState = rememberAlbumSelectionState(screenUiState.albumWithSongs, hideExplicit)
    val downloadUiState = rememberAlbumDownloadUiState()
    val gradientColors = rememberAlbumGradientColors(screenUiState.albumWithSongs?.album?.thumbnailUrl, context)
    val gradientAlpha by rememberAlbumGradientAlpha(lazyListState)
    val collapseFraction by rememberCollapseFraction(lazyListState)

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

    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
    ) {
        AlbumGradientBackground(
            gradientColors = gradientColors,
            gradientAlpha = gradientAlpha,
            surfaceColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AlbumMorphingHeader(
            thumbnailUrl = screenUiState.albumWithSongs?.album?.thumbnailUrl,
            collapseFraction = collapseFraction,
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            val currentAlbumWithSongs = screenUiState.albumWithSongs
            if (currentAlbumWithSongs != null && currentAlbumWithSongs.songs.isNotEmpty()) {
                albumHeaderSection(
                    albumWithSongs = currentAlbumWithSongs,
                    songCount = selectionState.wrappedSongs.size,
                    downloadState = downloadUiState.downloadState,
                    actions = actions,
                    topPadding = systemBarsTopPadding + AppBarHeight,
                    heroSpacerHeight = screenWidthDp * 0.55f,
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
                    topPadding = systemBarsTopPadding + AppBarHeight,
                    onRetry = { viewModel.retry() },
                )
            }
        }

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
