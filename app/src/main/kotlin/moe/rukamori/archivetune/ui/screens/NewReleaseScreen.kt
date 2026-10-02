/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.NewReleaseContent
import moe.rukamori.archivetune.viewmodels.NewReleaseUiState
import moe.rukamori.archivetune.viewmodels.NewReleaseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(NewReleaseTab.All) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.new_releases)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
    ) { paddingValues ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(150))
            },
            modifier = Modifier.fillMaxSize(),
            label = "NewReleaseContent",
        ) { state ->
            when (state) {
                NewReleaseUiState.Loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                        contentPadding = paddingValues,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(12) {
                            ShimmerHost {
                                GridItemPlaceHolder(fillMaxWidth = true)
                            }
                        }
                    }
                }

                is NewReleaseUiState.Success -> {
                    NewReleaseGridContent(
                        content = state.content,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        paddingValues = paddingValues,
                        activeAlbumId = mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        coroutineScope = coroutineScope,
                        onReleaseClick = { album -> navController.navigate("album/${album.id}") },
                        onReleaseLongClick = { album ->
                            haptics.longPress()
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = album,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onRefresh = viewModel::retry,
                    )
                }

                NewReleaseUiState.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 24.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "New releases are temporarily unavailable",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "ArchiveTune could not load this YouTube Music section. Try again later.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = viewModel::retry,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                NewReleaseUiState.Empty -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 24.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.no_results_found),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = viewModel::retry,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.refresh))
                            }
                        }
                    }
                }
            }
        }
    }
}
