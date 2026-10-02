package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Stable
internal class NavigationBarRepulsionState {
    var rowWidthPx by mutableFloatStateOf(0f)
    var rowHeightPx by mutableFloatStateOf(0f)

    // Лонгпресс-скейл бара из Telegram: 1.019f
    val barScaleAnimatable = Animatable(1.0f)

    // 2D антимагнитное смещение бара от пальца
    val barTranslationXAnimatable = Animatable(0f)
    val barTranslationYAnimatable = Animatable(0f)

    suspend fun onDragStart(
        offsetX: Float,
        offsetY: Float,
        maxRepulsionXPx: Float,
        maxRepulsionYPx: Float,
    ) {
        val normX = if (rowWidthPx > 0f) ((offsetX - rowWidthPx / 2f) / (rowWidthPx / 2f)).coerceIn(-1f, 1f) else 0f
        val normY = if (rowHeightPx > 0f) ((offsetY - rowHeightPx / 2f) / (rowHeightPx / 2f)).coerceIn(-1f, 1f) else 0f

        barTranslationXAnimatable.snapTo(-normX * maxRepulsionXPx)
        barTranslationYAnimatable.snapTo(-normY * maxRepulsionYPx)

        barScaleAnimatable.animateTo(
            targetValue = 1.019f,
            animationSpec = tween(
                durationMillis = 380,
                easing = EaseOutQuint,
            ),
        )
    }

    suspend fun onDrag(
        currentTouchX: Float,
        currentTouchY: Float,
        maxRepulsionXPx: Float,
        maxRepulsionYPx: Float,
    ) {
        // Антимагнитная репульсия (отталкивание в противоположную от пальца сторону)
        val normX = if (rowWidthPx > 0f) ((currentTouchX - rowWidthPx / 2f) / (rowWidthPx / 2f)).coerceIn(-1f, 1f) else 0f
        val normY = if (rowHeightPx > 0f) ((currentTouchY - rowHeightPx / 2f) / (rowHeightPx / 2f)).coerceIn(-1f, 1f) else 0f
        val targetRepulsionX = -normX * maxRepulsionXPx
        val targetRepulsionY = -normY * maxRepulsionYPx

        barTranslationXAnimatable.snapTo(targetRepulsionX)
        barTranslationYAnimatable.snapTo(targetRepulsionY)
    }

    suspend fun animateRelease() = coroutineScope {
        val animBarScale = launch {
            barScaleAnimatable.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = 0.25f, stiffness = 250f),
            )
        }
        val animBarX = launch {
            barTranslationXAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
            )
        }
        val animBarY = launch {
            barTranslationYAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
            )
        }

        animBarScale.join()
        animBarX.join()
        animBarY.join()
    }
}
