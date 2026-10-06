/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.queue_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight

@Composable
internal fun QueueItemDismissReveal(
    dismissHandler: QueueItemDismissGestureHandler?,
    dismissOffsetAnimatable: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val isSwipeTargeted = dismissHandler?.isInDismissZone == true
    val density = LocalDensity.current

    val dismissBackgroundColor by animateColorAsState(
        targetValue =
            if (isSwipeTargeted) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
            },
        animationSpec = tween(durationMillis = 150),
        label = "dismissBackgroundColor",
    )
    val dismissIconScale by animateFloatAsState(
        targetValue = if (isSwipeTargeted) 1.08f else 0.95f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconScale",
    )

    Box(
        modifier =
            modifier
                .padding(end = 12.dp)
                .height(ListItemHeight)
                .fillMaxWidth()
                .drawBehind {
                    val offset = dismissOffsetAnimatable.value
                    val revealWidthPx = (-offset).coerceAtLeast(0f)
                    if (revealWidthPx <= 0f) return@drawBehind
                    val pillHeight = size.height
                    val pillWidth = revealWidthPx.coerceAtMost(size.width)
                    val left = size.width - pillWidth
                    drawRoundRect(
                        color = dismissBackgroundColor,
                        topLeft = Offset(left, 0f),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius =
                            CornerRadius(pillHeight / 2f, pillHeight / 2f),
                    )
                },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = stringResource(R.string.remove_from_queue),
            modifier =
                Modifier
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        val offset = dismissOffsetAnimatable.value
                        val revealWidthPx = (-offset).coerceAtLeast(0f)
                        val progress =
                            if (density.density > 0f) {
                                (revealWidthPx / (56.dp.toPx())).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        alpha = progress * if (isSwipeTargeted) 1f else 0.88f
                        scaleX = dismissIconScale
                        scaleY = dismissIconScale
                    },
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
