/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.utils.isScrollingUp

val LocalFabHazeState = compositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalFabHazeState.current,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
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
            blurRadius = blurRadius,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    label: String,
    hazeState: HazeState? = LocalFabHazeState.current,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onClick: () -> Unit,
) {
    HideOnScrollFAB(
        visible = visible,
        lazyListState = lazyListState,
        icon = icon,
        label = label,
        modifier = Modifier.align(Alignment.BottomEnd),
        hazeState = hazeState,
        blurRadius = blurRadius,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyGridState,
    @DrawableRes icon: Int,
    label: String,
    hazeState: HazeState? = LocalFabHazeState.current,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
        enter = slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        exit = slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ),
    ) {
        HideOnScrollFabButton(
            icon = icon,
            label = label,
            hazeState = hazeState,
            blurRadius = blurRadius,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    scrollState: ScrollState,
    @DrawableRes icon: Int,
    label: String,
    hazeState: HazeState? = LocalFabHazeState.current,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible && scrollState.isScrollingUp(),
        enter = slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        exit = slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ),
    ) {
        HideOnScrollFabButton(
            icon = icon,
            label = label,
            hazeState = hazeState,
            blurRadius = blurRadius,
            onClick = onClick,
        )
    }
}

@Composable
private fun HideOnScrollFabButton(
    @DrawableRes icon: Int,
    label: String,
    hazeState: HazeState?,
    blurRadius: Float,
    onClick: () -> Unit,
) {
    val haptics = LocalYumaHaptics.current
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val hazeStyle =
        remember(containerColor, blurRadius) {
            HazeDefaults.style(
                backgroundColor = containerColor,
                tint = HazeTint(containerColor.copy(alpha = SettingsDimensions.HazeDefaultTintAlpha)),
                blurRadius = blurRadius.dp,
                noiseFactor = SettingsDimensions.HazeNoiseFactor,
            )
        }

    ExtendedFloatingActionButton(
        modifier =
            Modifier
                .padding(16.dp)
                .clip(FloatingActionButtonDefaults.extendedFabShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = hazeStyle,
                        ) {
                            inputScale = HazeInputScale.Fixed(SettingsDimensions.HazeInputScaleValue)
                        }
                    } else {
                        Modifier
                    },
                ),
        onClick = {
            haptics.click()
            onClick()
        },
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        },
        text = { Text(label) },
        containerColor = if (hazeState != null) Color.Transparent else containerColor,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
