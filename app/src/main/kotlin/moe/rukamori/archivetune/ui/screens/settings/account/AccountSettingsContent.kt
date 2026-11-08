/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.ui.screens.settings.AccountSettingsUiActions
import moe.rukamori.archivetune.ui.screens.settings.AccountSettingsUiState
import moe.rukamori.archivetune.ui.screens.settings.SettingsScreenBackground
import moe.rukamori.archivetune.ui.screens.settings.account.header.ProfileHeroHeader
import moe.rukamori.archivetune.ui.screens.settings.account.sections.AccountGeneralSection
import moe.rukamori.archivetune.ui.screens.settings.account.sections.AccountIntegrationSection
import moe.rukamori.archivetune.ui.screens.settings.account.sections.AccountMiscSection
import moe.rukamori.archivetune.ui.screens.settings.account.sections.UpdateBannerStrip
import moe.rukamori.archivetune.ui.screens.settings.account.sections.VersionStamp
import moe.rukamori.archivetune.ui.screens.settings.playerAwareBottomInset
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun AccountSettingsContent(
    state: AccountSettingsUiState,
    actions: AccountSettingsUiActions,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    SettingsScreenBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            val hazeState = remember { HazeState() }
            val lazyListState = rememberLazyListState()
            val headerProgress = rememberHeaderScrollProgress(
                lazyListState = lazyListState,
                hasCollapsibleHeader = true,
            )
            val isHeaderInViewport by remember {
                derivedStateOf {
                    val visible = lazyListState.layoutInfo.visibleItemsInfo
                    visible.isEmpty() || visible.any { it.index == 0 }
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
                    ),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = SettingsDimensions.ScreenBottomPadding + playerAwareBottomInset(),
                ),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
            ) {
                item {
                    val extractedColor = remember(state.extractedColorHex) {
                        state.extractedColorHex?.let {
                            try {
                                Color(it.toColorInt())
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }

                    ProfileHeroHeader(
                        avatarUrl = state.accountImageUrl,
                        displayName = state.displayName,
                        handle = state.accountHandle.ifBlank { state.accountEmail },
                        accentColor = extractedColor ?: MaterialTheme.colorScheme.primary,
                        isLoggedIn = state.isLoggedIn,
                        isViewportVisible = isHeaderInViewport,
                        onAccountActionClick = actions.onOpenAccountMenu,
                    )
                }

                if (state.hasUpdate) {
                    item {
                        UpdateBannerStrip(
                            modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                            latestVersion = state.latestVersionName,
                            onClick = actions.onOpenUpdateDownload,
                        )
                    }
                }

                item {
                    AccountGeneralSection(
                        isLoggedIn = state.isLoggedIn,
                        useLoginForBrowse = state.useLoginForBrowse,
                        onUseLoginForBrowseChange = actions.onUseLoginForBrowseChange,
                        ytmSync = state.ytmSync,
                        onYtmSyncChange = actions.onYtmSyncChange,
                        forceSyncOnAccountSwitch = state.forceSyncOnAccountSwitch,
                        onForceSyncOnAccountSwitchChange = actions.onForceSyncOnAccountSwitchChange,
                    )
                }

                item {
                    AccountIntegrationSection(
                        spotifyState = state.spotifyState,
                        useSpotifyHome = state.useSpotifyHome,
                        spotifySyncLikes = state.spotifySyncLikes,
                        hideYtmLikedSongs = state.hideYtmLikedSongs,
                        onSpotifyEntryClick = actions.onSpotifyClick,
                        onUseSpotifyHomeChange = actions.onUseSpotifyHomeChange,
                        onSpotifySyncLikesChange = actions.onSpotifySyncLikesChange,
                        onHideYtmLikedSongsChange = actions.onHideYtmLikedSongsChange,
                        onNavigateIntegration = actions.onNavigateIntegration,
                        onNavigateMusicTogether = actions.onNavigateMusicTogether,
                    )
                }

                item {
                    AccountMiscSection(
                        tokenActionTitle = state.tokenActionTitle,
                        tokenDescription = state.tokenDescription,
                        tokenPreview = state.tokenPreview,
                        showToken = state.showToken,
                        onNavigateHiddenPlaylists = actions.onNavigateHiddenPlaylists,
                        onTokenEntryClick = actions.onTokenRowClick,
                        onRevealToken = actions.onRevealToken,
                    )
                }

                item {
                    VersionStamp(modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding))
                }
            }

            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                HeaderHazeBackdrop(
                    progress = headerProgress,
                    hasCollapsibleHeader = true,
                    hazeState = hazeState,
                    alwaysShowFallback = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(
                            onClick = actions.onNavigateUp,
                            onLongClick = actions.onNavigateHome,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (state.hasUpdate) {
                            BadgedBox(
                                badge = { Badge(containerColor = MaterialTheme.colorScheme.error) },
                            ) {
                                OutlinedIconButton(
                                    onClick = actions.onOpenUpdateDownload,
                                    colors = IconButtonDefaults.outlinedIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    ),
                                    border = null,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.update),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    }
}