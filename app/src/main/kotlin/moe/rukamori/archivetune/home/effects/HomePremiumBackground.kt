package moe.rukamori.archivetune.home.effects

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint as NativePaint
import android.graphics.RadialGradient as NativeRadialGradient
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clipToBounds

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

    var noiseBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        noiseBitmap = HomeBackgroundEffects.generateNoiseBitmapAsync()
    }

    val blobPaint = remember {
        NativePaint().apply {
            isDither = true
            isAntiAlias = true
        }
    }

    val noisePaint = remember(noiseBitmap) {
        noiseBitmap?.let { bitmap ->
            NativePaint().apply {
                isAntiAlias = true
                shader = BitmapShader(
                    bitmap,
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT
                )
                alpha = (0.04f * 255).toInt()
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
    val centerX: Float = size.width * 0.5f
        val centerY: Float = size.height * 0.15f
        val radius: Float = size.width * 1.15f

        val colorArray = intArrayOf(
            animatedColor.copy(alpha = 0.60f).toArgb(),
            animatedColor.copy(alpha = 0.35f).toArgb(),
            animatedColor.copy(alpha = 0.10f).toArgb(),
            android.graphics.Color.TRANSPARENT
        )

        val colorStops: FloatArray? = floatArrayOf(0f, 0.35f, 0.75f, 1f)

        blobPaint.shader = NativeRadialGradient(
            centerX,
            centerY,
            radius,
            colorArray,
            colorStops,
            Shader.TileMode.CLAMP
        )

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(centerX, centerY, radius, blobPaint)
        }

        noisePaint?.let { paint ->
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    paint
                )
            }
        }
    }
}