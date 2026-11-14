/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0.full

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State

@Immutable
internal data class CoverKinematics(
    val scale: Float,
    val translationY: Float,
    val alpha: Float,
)

@Composable
internal fun rememberCoverPlayPauseScale(
    isPlaying: Boolean,
    disabled: Boolean = false,
): State<Float> {
    return animateFloatAsState(
        targetValue = if (disabled || isPlaying) 1.0f else 0.84f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 350f,
        ),
        label = "CoverPlayPauseScale",
    )
}

internal fun calculateCoverKinematics(
    q: Float,
    playPauseScale: Float,
    isCoverVisible: Boolean,
    safeWidthPx: Float,
    cardWidthPx: Float,
    cardTopInRoot: Float,
    toolbarTopInRoot: Float,
    rootHeightPx: Float,
    capsuleHeaderHeightPx: Float,
    extraLiftPx: Float,
    gapPx: Float,
): CoverKinematics {
    if (!isCoverVisible) {
        return CoverKinematics(
            scale = 1f,
            translationY = 0f,
            alpha = 0f,
        )
    }

    val targetScale = if (cardWidthPx > 0f && safeWidthPx > 0f) {
        (safeWidthPx / cardWidthPx) * 1.04f
    } else {
        1f
    }

    val peekProgress = (q / 0.45f).coerceIn(0f, 1f)
    val coverHeight = cardWidthPx * targetScale

    val queueTravel = (rootHeightPx - capsuleHeaderHeightPx).coerceAtLeast(0f)
    val queuePeekTop = rootHeightPx - 0.45f * queueTravel

    val peekTop = minOf(
        toolbarTopInRoot - extraLiftPx,
        queuePeekTop - coverHeight - gapPx,
    )

    return if (q <= 0.45f) {
        val scale = playPauseScale + peekProgress * (targetScale - playPauseScale)
        val translationY = (peekTop - cardTopInRoot) * peekProgress
        CoverKinematics(
            scale = scale,
            translationY = translationY,
            alpha = 1f,
        )
    } else {
        val queueTop = rootHeightPx - q * queueTravel
        val coverTop = minOf(
            peekTop,
            queueTop - coverHeight - gapPx,
        )
        val translationY = coverTop - cardTopInRoot

        val coverBottom = coverTop + coverHeight
        val exitBottom = capsuleHeaderHeightPx - gapPx
        val fadeDistance = capsuleHeaderHeightPx.coerceAtLeast(1f)
        val alpha = ((coverBottom - exitBottom) / fadeDistance).coerceIn(0f, 1f)

        CoverKinematics(
            scale = targetScale,
            translationY = translationY,
            alpha = alpha,
        )
    }
}