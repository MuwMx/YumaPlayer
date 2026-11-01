/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.carousel.LocalHeroScrollProgress
import moe.rukamori.archivetune.ui.scaffold.BindHeaderScroll
import moe.rukamori.archivetune.ui.utils.SnapLayoutInfoProvider

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    lazyListState: LazyListState,
    forgottenFavoritesGridState: LazyGridState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    headerScrollConnection: NestedScrollConnection? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        ExpressivePullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val forgottenItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                val forgottenItemWidth = maxWidth.coerceAtMost(HomeFeedMaxWidth) * forgottenItemWidthFactor
                val forgottenSnapLayoutInfoProvider =
                    remember(forgottenFavoritesGridState, forgottenItemWidthFactor) {
                        SnapLayoutInfoProvider(
                            lazyGridState = forgottenFavoritesGridState,
                            positionInLayout = { layoutSize, itemSize ->
                                layoutSize * forgottenItemWidthFactor / 2f - itemSize / 2f
                            },
                        )
                    }

                val mediaContext =
                    remember(mediaMetadata, isPlaying, navController, playerConnection, menuState, scope) {
                        HomeFeedMediaContext(
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            scope = scope,
                        )
                    }

                val isFilterActive = uiState.selectedChip != null
                val insetsPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                val isHeroMode = uiState.quickPicksDisplayMode == moe.rukamori.archivetune.constants.QuickPicksDisplayMode.HERO
                val isHeroPresent = isHeroMode && !isFilterActive && uiState.quickPicks.isNotEmpty()

                val headerScrollProgress = BindHeaderScroll(
                    connection = headerScrollConnection,
                    lazyListState = lazyListState,
                    hasCollapsibleHeader = isHeroPresent,
                    collapseDistance = 360.dp,
                    standardThreshold = 48.dp,
                )

                LazyColumn(
                    state = lazyListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = insetsPadding.calculateLeftPadding(LocalLayoutDirection.current),
                        top = if (isHeroMode) 0.dp else insetsPadding.calculateTopPadding(),
                        end = insetsPadding.calculateRightPadding(LocalLayoutDirection.current),
                        bottom = insetsPadding.calculateBottomPadding(),
                    ),
                    modifier = Modifier
                        .widthIn(max = HomeFeedMaxWidth)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                ) {
                    if (uiState.quickPicksDisplayMode != moe.rukamori.archivetune.constants.QuickPicksDisplayMode.HERO) {
                        homeCategoryChipsSection(
                            uiState = uiState,
                            onAction = onAction,
                        )
                    }

                    if (!isFilterActive && uiState.quickPicks.isNotEmpty()) {
                        item(
                            key = "home_quick_picks",
                            contentType = "quick_picks",
                        ) {
                            CompositionLocalProvider(LocalHeroScrollProgress provides headerScrollProgress) {
                                QuickPicksSection(
                                    quickPicks = uiState.quickPicks,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    displayMode = uiState.quickPicksDisplayMode,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }

                    homeCommunitySection(
                        uiState = uiState,
                        mediaContext = mediaContext,
                    )

                    if (!isFilterActive) {
                        if (uiState.speedDialItems.isNotEmpty()) {
                            sectionSpacer("speed_dial")
                            item(
                                key = "home_speed_dial_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.speed_dial),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "home_speed_dial",
                                contentType = "speed_dial",
                            ) {
                                SpeedDialSection(
                                    speedDialItems = uiState.speedDialItems,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    scope = scope,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        if (uiState.keepListening.isNotEmpty()) {
                            sectionSpacer("keep_listening")
                            item(
                                key = "home_keep_listening_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.keep_listening),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "home_keep_listening",
                                contentType = "media_shelf",
                            ) {
                                KeepListeningSection(
                                    keepListening = uiState.keepListening,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    scope = scope,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        if (uiState.accountPlaylists.isNotEmpty()) {
                            sectionSpacer("account_playlists")
                            item(
                                key = "home_account_playlists",
                                contentType = "media_shelf",
                            ) {
                                Column(modifier = Modifier.animateItem()) {
                                    AccountPlaylistsTitle(
                                        accountName = uiState.accountName,
                                        accountImageUrl = uiState.accountImageUrl,
                                        onClick = { navController.navigate("account") },
                                    )
                                    AccountPlaylistsSection(
                                        accountPlaylists = uiState.accountPlaylists,
                                        mediaMetadata = mediaMetadata,
                                        isPlaying = isPlaying,
                                        navController = navController,
                                        playerConnection = playerConnection,
                                        menuState = menuState,
                                        scope = scope,
                                    )
                                }
                            }
                        }

                        homeForgottenFavoritesSection(
                            uiState = uiState,
                            mediaContext = mediaContext,
                            forgottenItemWidth = forgottenItemWidth,
                            forgottenFavoritesGridState = forgottenFavoritesGridState,
                            forgottenSnapLayoutInfoProvider = forgottenSnapLayoutInfoProvider,
                        )

                        homeSimilarRecommendationsSection(
                            uiState = uiState,
                            mediaContext = mediaContext,
                        )
                    }

                    homeRemoteSections(
                        uiState = uiState,
                        mediaContext = mediaContext,
                    )

                    homeLoadingMoreItem(
                        isLoadingMore = uiState.isLoadingMore,
                    )
                }
            }
        }
    }
}
