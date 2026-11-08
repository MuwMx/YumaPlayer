/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.GlassScaffold
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun SettingsScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        content()
    }
}

/** Bottom player-aware inset; apply inside the scroll container so content can pass behind the mini player. */
@Composable
fun Modifier.playerAwareBottomPadding(): Modifier =
    windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))

/** Bottom player-aware inset for a lazy list's content padding. */
@Composable
fun playerAwareBottomInset(): Dp =
    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

@Composable
fun settingsHeaderHeight(): Dp {
    val statusBarTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    return AppBarHeight + statusBarTop
}

@Composable
fun YumaSettingsScaffold(
    title: @Composable () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBackLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    scrollProgress: (() -> Float)? = null,
    hazeState: HazeState? = null,
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    val internalScrollState = rememberScrollState()
    val effectiveHazeState = hazeState ?: remember { HazeState() }
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 48.dp.toPx() } }

    val effectiveProgress: () -> Float = scrollProgress ?: {
        if (scrollBehavior != null) {
            val limit = scrollBehavior.state.heightOffsetLimit
            if (limit != 0f) {
                (-scrollBehavior.state.heightOffset / -limit).coerceIn(0f, 1f)
            } else {
                scrollBehavior.state.collapsedFraction
            }
        } else if (scrollable) {
            (internalScrollState.value / thresholdPx).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    val statusBarTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerHeight = AppBarHeight + statusBarTop
    val headerPadding = remember(headerHeight) { PaddingValues(top = headerHeight) }

    SettingsScreenBackground(modifier = modifier) {
        GlassScaffold(
            modifier = if (scrollBehavior != null) {
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                Modifier
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = snackbarHost,
            topBar = {
                Box {
                    HeaderHazeBackdrop(
                        progress = effectiveProgress,
                        hazeState = effectiveHazeState,
                        height = headerHeight,
                        alwaysShowFallback = true,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    TopAppBar(
                        title = title,
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onBackClick,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer(scaleX = -1f),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        actions = actions,
                        colors = GlassDefaults.topAppBarColors(),
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { _ ->
            val baseModifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal),
                )

            if (scrollable) {
                Column(
                    modifier = baseModifier
                        .verticalScroll(internalScrollState)
                        .hazeSource(effectiveHazeState)
                        .playerAwareBottomPadding()
                        .padding(bottom = SettingsDimensions.ScreenBottomPadding),
                ) {
                    Spacer(Modifier.height(headerHeight))
                    content(headerPadding)
                }
            } else {
                Column(
                    modifier = baseModifier.hazeSource(effectiveHazeState),
                ) {
                    content(headerPadding)
                }
            }
        }
    }
}
