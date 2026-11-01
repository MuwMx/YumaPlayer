/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.ui.component.ChipsRow
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.screens.settings.account.header.ProfileHeroHeader
import moe.rukamori.archivetune.ui.screens.account.AccountContentType
import moe.rukamori.archivetune.ui.screens.account.AccountScreenUiState
import moe.rukamori.archivetune.ui.screens.account.AccountViewModel

@Composable
fun AccountScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.accountProfile.collectAsStateWithLifecycle()

    val lazyGridState = rememberLazyGridState()
    val isHeaderInViewport by remember {
        derivedStateOf {
            val visible = lazyGridState.layoutInfo.visibleItemsInfo
            visible.isEmpty() || visible.any { it.index == 0 }
        }
    }

    val playerInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = PaddingValues(
            start = playerInsets.calculateStartPadding(layoutDirection),
            end = playerInsets.calculateEndPadding(layoutDirection),
            top = 0.dp,
            bottom = playerInsets.calculateBottomPadding(),
        ),
        modifier = Modifier
            .fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeroHeader(
                avatarUrl = profile.avatarUrl,
                displayName = profile.name.ifBlank { stringResource(R.string.account) },
                handle = profile.handle,
                isLoggedIn = profile.name.isNotBlank(),
                isViewportVisible = isHeaderInViewport,
                onAccountActionClick = { navController.navigate("settings/account") },
            )
        }

        when (val state = uiState) {
            AccountScreenUiState.Loading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.padding(vertical = 12.dp)) {
                        ChipsRow(
                            chips = listOf(
                                AccountContentType.PLAYLISTS to stringResource(R.string.filter_playlists),
                                AccountContentType.ALBUMS to stringResource(R.string.filter_albums),
                                AccountContentType.ARTISTS to stringResource(R.string.filter_artists),
                            ),
                            currentValue = AccountContentType.PLAYLISTS,
                            onValueUpdate = {},
                        )
                    }
                }

                items(8, key = { "shimmer_$it" }) {
                    ShimmerHost {
                        GridItemPlaceHolder(fillMaxWidth = true)
                    }
                }
            }

            AccountScreenUiState.Empty -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyPlaceholder(
                        icon = R.drawable.account,
                        text = stringResource(R.string.no_title),
                    )
                }
            }

            is AccountScreenUiState.Error -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyPlaceholder(
                        icon = R.drawable.error,
                        text = state.message ?: stringResource(R.string.error_unknown),
                    )
                }
            }

            is AccountScreenUiState.Success -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.padding(vertical = 12.dp)) {
                        ChipsRow(
                            chips = listOf(
                                AccountContentType.PLAYLISTS to stringResource(R.string.filter_playlists),
                                AccountContentType.ALBUMS to stringResource(R.string.filter_albums),
                                AccountContentType.ARTISTS to stringResource(R.string.filter_artists),
                            ),
                            currentValue = state.selectedContentType,
                            onValueUpdate = { viewModel.setSelectedContentType(it) },
                        )
                    }
                }

                when (state.selectedContentType) {
                    AccountContentType.PLAYLISTS -> {
                        items(
                            items = state.playlists.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                fillMaxWidth = true,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        navController.navigate("online_playlist/${item.id}")
                                    },
                                    onLongClick = {
                                        haptics.longPress()
                                        menuState.show {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ),
                            )
                        }
                    }

                    AccountContentType.ALBUMS -> {
                        items(
                            items = state.albums.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                fillMaxWidth = true,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        navController.navigate("album/${item.id}")
                                    },
                                    onLongClick = {
                                        haptics.longPress()
                                        menuState.show {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ),
                            )
                        }
                    }

                    AccountContentType.ARTISTS -> {
                        items(
                            items = state.artists.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                fillMaxWidth = true,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        navController.navigate("artist/${item.id}")
                                    },
                                    onLongClick = {
                                        haptics.longPress()
                                        menuState.show {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = menuState::dismiss,
                                            )
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