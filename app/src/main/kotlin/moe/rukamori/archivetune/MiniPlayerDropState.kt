package moe.rukamori.archivetune

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing

@Stable
class MiniPlayerDropState(
    val slideOffset: Dp = 0.dp,
    val isDocked: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MiniPlayerDropState) return false
        return slideOffset == other.slideOffset && isDocked == other.isDocked
    }

    override fun hashCode(): Int {
        var result = slideOffset.hashCode()
        result = 31 * result + isDocked.hashCode()
        return result
    }

    override fun toString(): String {
        return "MiniPlayerDropState(slideOffset=$slideOffset, isDocked=$isDocked)"
    }

    companion object {
        val Default = MiniPlayerDropState(
            slideOffset = 0.dp,
            isDocked = true,
        )
    }
}

@Composable
fun rememberMiniPlayerDropState(
    navSlideDistance: Dp = 0.dp,
    scrollVisibilityFactor: Float = 1f,
    bottomNavigationBarHeight: Dp = 0.dp,
): MiniPlayerDropState {
    val animatedVisibilityFactor by animateFloatAsState(
        targetValue = scrollVisibilityFactor,
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing,
        ),
        label = "MiniPlayerDropFactor",
    )

    val isDocked = bottomNavigationBarHeight > 0.dp && scrollVisibilityFactor > 0.01f
    val dropDistance = if (navSlideDistance > 0.dp && bottomNavigationBarHeight > 0.dp) {
        bottomNavigationBarHeight + MiniPlayerBottomSpacing
    } else {
        0.dp
    }
    val slideOffset = dropDistance * (1f - animatedVisibilityFactor).coerceIn(0f, 1f)

    return remember(slideOffset, isDocked) {
        MiniPlayerDropState(
            slideOffset = slideOffset,
            isDocked = isDocked,
        )
    }
}
