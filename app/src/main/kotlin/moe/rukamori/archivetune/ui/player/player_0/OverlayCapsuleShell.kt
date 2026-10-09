package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object CapsuleDefaults {
    val Shape: Shape = RoundedCornerShape(24.dp)
    val Height: Dp = 64.dp
    val HorizontalPadding: Dp = 24.dp
    val Elevation: Dp = 12.dp
    val BottomSpacing: Dp = 8.dp

    @Composable
    fun topPadding(): Dp {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        return if (statusBarTop > 0.dp) statusBarTop + 8.dp else 44.dp
    }

    @Composable
    fun totalHeaderHeight(): Dp = topPadding() + Height + BottomSpacing
}

@Composable
internal fun OverlayCapsuleShell(
    isBlurBackgroundEnabled: Boolean,
    darkMutedColor: Int,
    modifier: Modifier = Modifier,
    progressProvider: (() -> Float)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val capsuleColor = if (isBlurBackgroundEnabled) Color.Black else Color(darkMutedColor)
    val topPadding = CapsuleDefaults.topPadding()

    Box(
        modifier = modifier
            .padding(top = topPadding, start = CapsuleDefaults.HorizontalPadding, end = CapsuleDefaults.HorizontalPadding)
            .fillMaxWidth()
            .height(CapsuleDefaults.Height)
            .shadow(elevation = CapsuleDefaults.Elevation, shape = CapsuleDefaults.Shape, clip = false)
            .then(
                if (progressProvider != null) {
                    Modifier.graphicsLayer {
                        val progress = progressProvider().coerceIn(0f, 1f)
                        alpha = progress
                        scaleX = 0.8f + (0.2f * progress)
                        scaleY = 0.8f + (0.2f * progress)
                    }
                } else {
                    Modifier
                }
            )
            .background(capsuleColor, CapsuleDefaults.Shape)
    ) {
        content()

        if (isBlurBackgroundEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(BorderStroke(1.dp, Color(0x22FFFFFF)), CapsuleDefaults.Shape)
            )
        }
    }
}
