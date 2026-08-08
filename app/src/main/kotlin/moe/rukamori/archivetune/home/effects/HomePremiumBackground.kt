package moe.rukamori.archivetune.home.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun HomePremiumBackground(
    blobColor: Color,
    surfaceColor: Color = MaterialTheme.colorScheme.background,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = blobColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "blobColorAnimation"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX: Float = size.width * 0.5f
        val centerY: Float = size.height * 0.15f
        val radius: Float = size.width * 1.15f

        val brush = Brush.radialGradient(
            colors = listOf(
                animatedColor.copy(alpha = 0.40f),
                animatedColor.copy(alpha = 0.0f)
            ),
            center = Offset(centerX, centerY),
            radius = radius
        )

        drawCircle(
            brush = brush,
            radius = radius,
            center = Offset(centerX, centerY)
        )
    }
}