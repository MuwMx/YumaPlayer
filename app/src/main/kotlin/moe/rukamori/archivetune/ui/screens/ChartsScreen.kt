/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ChartsUiState
import moe.rukamori.archivetune.viewmodels.ChartsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    navController: NavController,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (uiState !is ChartsUiState.Success) {
            viewModel.loadCharts()
        }
    }

    val onSongClick =
        remember(playerConnection, mediaMetadata?.id) {
            { song: SongItem ->
                if (song.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        YouTubeQueue(
                            endpoint = WatchEndpoint(videoId = song.id),
                            preloadItem = song.toMediaMetadata(),
                        ),
                    )
                }
            }
        }

    val onSongLongClick =
        remember(menuState, navController, haptics) {
            { song: SongItem ->
                haptics.longPress()
                menuState.show {
                    YouTubeSongMenu(
                        song = song,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }

    val onMoreClick =
        remember(menuState, navController) {
            { song: SongItem ->
                menuState.show {
                    YouTubeSongMenu(
                        song = song,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.charts)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                ChartsUiState.Loading -> {
                    ChartsShimmer(modifier = Modifier.fillMaxSize())
                }

                ChartsUiState.Empty -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_results_found),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                is ChartsUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(state.messageResId),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                is ChartsUiState.Success -> {
                    val page = state.page
                    LazyColumn(
                        state = lazyListState,
                        contentPadding =
                            LocalPlayerAwareWindowInsets.current
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                .asPaddingValues(),
                    ) {
                        chartsSongSections(
                            sections = page.sections.filter { it.title != TOP_MUSIC_VIDEOS_SECTION_TITLE },
                            activeSongId = mediaMetadata?.id,
                            isPlaying = isPlaying,
                            onSongClick = onSongClick,
                            onSongLongClick = onSongLongClick,
                            onMoreClick = onMoreClick,
                        )

                        page.sections.find { it.title == TOP_MUSIC_VIDEOS_SECTION_TITLE }?.let { topVideosSection ->
                            chartsTopVideosSection(
                                section = topVideosSection,
                                activeSongId = mediaMetadata?.id,
                                isPlaying = isPlaying,
                                coroutineScope = coroutineScope,
                                onVideoClick = onSongClick,
                                onVideoLongClick = onSongLongClick,
                            )
                        }

                        page.sections.filter { it.title != TOP_MUSIC_VIDEOS_SECTION_TITLE }.forEach { section ->
                            val nonSongItems = section.items.filter { it !is SongItem }
                            if (nonSongItems.isNotEmpty()) {
                                item(contentType = CONTENT_TYPE_CHARTS_HEADER) {
                                    NavigationTitle(
                                        title = section.title.ifEmpty { stringResource(R.string.charts) },
                                    )
                                }
                                item(contentType = CONTENT_TYPE_CHARTS_ROW) {
                                    LazyRow(
                                        contentPadding =
                                            LocalPlayerAwareWindowInsets.current
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                    ) {
                                        items(
                                            items = nonSongItems,
                                            key = { item -> item.id },
                                        ) { item ->
                                            YouTubeGridItem(
                                                item = item,
                                                isActive = false,
                                                isPlaying = false,
                                                coroutineScope = coroutineScope,
                                                modifier =
                                                    Modifier.combinedClickable(
                                                        onClick = {
                                                            when (item) {
                                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                                else -> Unit
                                                            }
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
