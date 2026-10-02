package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.platform.LocalDensity
import moe.rukamori.archivetune.constants.NavigationBarShadowAlpha
import moe.rukamori.archivetune.constants.NavigationBarShadowBlur
import moe.rukamori.archivetune.constants.NavigationBarShadowDy
import moe.rukamori.archivetune.constants.NavigationBarShadowLayerAlpha

@Composable
internal fun rememberNavigationShadowPaint(): Paint {
    val density = LocalDensity.current
    val shadowDyPx = remember(density) { with(density) { NavigationBarShadowDy.toPx() } }
    val shadowBlurPx = remember(density) { with(density) { NavigationBarShadowBlur.toPx() } }
    return remember(shadowDyPx, shadowBlurPx) {
        Paint().apply {
            nativePaint.apply {
                isAntiAlias = true
                color = android.graphics.Color.argb((NavigationBarShadowAlpha * 255).toInt(), 0, 0, 0)
                setShadowLayer(
                    shadowBlurPx,
                    0f,
                    shadowDyPx,
                    android.graphics.Color.argb((NavigationBarShadowLayerAlpha * 255).toInt(), 0, 0, 0),
                )
            }
        }
    }
}

internal fun Modifier.navigationShapeShadow(
    shape: Shape,
    shadowPaint: Paint,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val shadowPath = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
    drawIntoCanvas { canvas ->
        canvas.drawPath(shadowPath, shadowPaint)
    }
}
