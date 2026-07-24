package moe.rukamori.archivetune.ui.player.player_0.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import moe.rukamori.archivetune.ui.state.PlayerSheetState

internal data class SheetVisualState(
    val currentBottomPadding: Dp,
    val baseBottomPadding: Dp,
    /** Провайдер для фазы отрисовки: читать внутри graphicsLayer, чтобы избежать рекомпозиции. */
    val playerContentAreaHeightPxProvider: () -> Float,
    /** Провайдер для фазы компоновки: читать внутри .offset { }, чтобы избежать рекомпозиции. */
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    /** Провайдеры для фазы отрисовки: читать внутри graphicsLayer. */
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
internal fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    currentSheetContentState: PlayerSheetState,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetY: Float,
    isPlaying: Boolean,
    hasCurrentSong: Boolean
): SheetVisualState {
    val density = LocalDensity.current

    // Константа высоты мини-плеера (в PixelPlayer это 64.dp)
    val miniPlayerHeightDp = 64.dp

    val baseBottomPadding = remember(containerHeight, sheetCollapsedTargetY, density) {
        val targetYDp = with(density) { sheetCollapsedTargetY.toDp() }
        (containerHeight - miniPlayerHeightDp - targetYDp).coerceAtLeast(0.dp)
    }

    val currentBottomPadding = 0.dp

    val miniHeightPx = remember(density) { with(density) { miniPlayerHeightDp.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)

    val visualSheetTranslationYProvider: () -> Float = remember(
        currentSheetTranslationY,
        sheetCollapsedTargetY
    ) {
        {
            val progress = predictiveBackCollapseProgressState.value
            currentSheetTranslationY.value * (1f - progress) + (sheetCollapsedTargetY * progress)
        }
    }

    val playerContentAreaHeightPxProvider: () -> Float = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        miniHeightPx,
        containerHeightPx,
        visualSheetTranslationYProvider,
        sheetCollapsedTargetY
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                val translationY = visualSheetTranslationYProvider()

                if (translationY <= sheetCollapsedTargetY) {
                    val targetBottom = androidx.compose.ui.util.lerp(
                        sheetCollapsedTargetY + miniHeightPx,
                        containerHeightPx,
                        safeFraction
                    )
                    (targetBottom - translationY).coerceAtLeast(0f)
                } else {
                    androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, safeFraction)
                }
            } else {
                0f
            }
        }
    }

    // Радиус верхних углов: от 32.dp (свернут) до 0.dp (развернут)
    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            val collapsedCornerTarget = 32.dp
            val expandedTarget = 0.dp
            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)

            if (showPlayerContentArea) {
                lerp(collapsedCornerTarget, expandedTarget, safeFraction)
            } else {
                collapsedCornerTarget
            }
        }
    }

    val isPlayingState = rememberUpdatedState(isPlaying)
    val hasCurrentSongState = rememberUpdatedState(hasCurrentSong)

    // Радиус нижних углов: от 32.dp (свернут) до 0.dp (развернут)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            val collapsedRadius = 32.dp
            val expandedTarget = 0.dp
            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)

            if (showPlayerContentArea) {
                lerp(collapsedRadius, expandedTarget, safeFraction)
            } else {
                if (!isPlayingState.value || !hasCurrentSongState.value) {
                    32.dp
                } else {
                    collapsedRadius
                }
            }
        }
    }

    val collapsedStateHorizontalPaddingPx = remember(collapsedStateHorizontalPadding, density) {
        with(density) { collapsedStateHorizontalPadding.toPx() }
    }

    // Провайдеры горизонтальных отступов (читаются в graphicsLayer)
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, safeFraction)
            } else {
                collapsedStateHorizontalPaddingPx
            }
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = currentHorizontalPaddingStartPxProvider

    return SheetVisualState(
        currentBottomPadding = currentBottomPadding,
        baseBottomPadding = baseBottomPadding,
        playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider = visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
    )
}