/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassStroke
import moe.rukamori.archivetune.ui.theme.yumaClickable

@Stable
sealed interface ScrollContract {
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int

    @Immutable
    data class LazyList(val state: LazyListState) : ScrollContract {
        override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
        override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    }

    @Immutable
    data class LazyGrid(val state: LazyGridState) : ScrollContract {
        override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
        override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    }

    @Immutable
    data class Scroll(val state: ScrollState) : ScrollContract {
        override val firstVisibleItemIndex: Int get() = 0
        override val firstVisibleItemScrollOffset: Int get() = state.value
    }
}

fun ScrollContract(state: LazyListState): ScrollContract = ScrollContract.LazyList(state)
fun ScrollContract(state: LazyGridState): ScrollContract = ScrollContract.LazyGrid(state)
fun ScrollContract(state: ScrollState): ScrollContract = ScrollContract.Scroll(state)

@Composable
fun LazyListState.asScrollContract(): ScrollContract = remember(this) { ScrollContract.LazyList(this) }

@Composable
fun LazyGridState.asScrollContract(): ScrollContract = remember(this) { ScrollContract.LazyGrid(this) }

@Composable
fun ScrollState.asScrollContract(): ScrollContract = remember(this) { ScrollContract.Scroll(this) }

@Composable
fun ScrollContract.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }

    LaunchedEffect(this) {
        var previousIndex = firstVisibleItemIndex
        var previousScrollOffset = firstVisibleItemScrollOffset
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (currentIndex, currentScrollOffset) ->
                if (currentIndex == 0 && currentScrollOffset == 0) {
                    isScrollingUp = true
                } else if (previousIndex != currentIndex) {
                    isScrollingUp = previousIndex > currentIndex
                } else if (previousScrollOffset != currentScrollOffset) {
                    isScrollingUp = previousScrollOffset > currentScrollOffset
                }
                previousIndex = currentIndex
                previousScrollOffset = currentScrollOffset
            }
    }

    return isScrollingUp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HideOnScrollFAB(
    scrollContract: ScrollContract,
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible && scrollContract.isScrollingUp(),
        enter = slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        exit = slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        modifier =
            modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        HideOnScrollFabButton(
            icon = icon,
            label = label,
            hazeState = hazeState,
            pureBlack = pureBlack,
            blurRadius = blurRadius,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.HideOnScrollFAB(
    scrollContract: ScrollContract,
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onClick: () -> Unit,
) {
    HideOnScrollFAB(
        scrollContract = scrollContract,
        icon = icon,
        label = label,
        modifier = modifier.align(Alignment.BottomEnd),
        visible = visible,
        hazeState = hazeState,
        pureBlack = pureBlack,
        blurRadius = blurRadius,
        onClick = onClick,
    )
}

@Composable
private fun HideOnScrollFabButton(
    @DrawableRes icon: Int,
    label: String,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onClick: () -> Unit,
) {
    val haptics = LocalYumaHaptics.current
    val fabShape = CircleShape
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
    val fixedTintAlpha = if (pureBlack) SettingsDimensions.HazePureBlackTintAlpha else SettingsDimensions.HazeDefaultTintAlpha
    val hazeStyle =
        remember(containerColor, blurRadius, pureBlack) {
            HazeDefaults.style(
                backgroundColor = containerColor,
                tint = HazeTint(containerColor.copy(alpha = fixedTintAlpha)),
                blurRadius = blurRadius.dp,
                noiseFactor = SettingsDimensions.HazeNoiseFactor,
            )
        }

    Box(
        modifier =
            Modifier
                .padding(16.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = {
                        haptics.click()
                        onClick()
                    },
                )
                .clip(fabShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = hazeStyle,
                        ) {
                            inputScale = HazeInputScale.Fixed(SettingsDimensions.HazeInputScaleValue)
                        }
                    } else {
                        Modifier.background(containerColor)
                    },
                )
                .glassStroke(
                    shape = fabShape,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                    topAlpha = SettingsDimensions.GlassBorderTopAlpha,
                    bottomAlpha = SettingsDimensions.GlassBorderBottomAlpha,
                    topColor = Color.White,
                    bottomColor = Color.Black,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    role = Role.Button
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
