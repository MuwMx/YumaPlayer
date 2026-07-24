package moe.rukamori.archivetune.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow

/**
 * Кастомная мягкая радиальная тень для прозрачных иконок и кнопок управления.
 * Предотвращает появление прямоугольных/многоугольных артефактов нативных теней Android.
 */
fun Modifier.transparentIconShadow(
    alpha: Float = 0.35f,
    shadowRadius: Dp = 24.dp
): Modifier = this.drawBehind {
    val radiusPx = shadowRadius.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = alpha),
                Color.Transparent
            ),
            center = center,
            radius = radiusPx
        ),
        radius = radiusPx,
        center = center
    )
}

/**
 * Кастомная линейная тень (Drop Shadow) для длинных горизонтальных элементов (сикбар, разделители).
 * Размывается строго вниз по всей ширине компонента.
 */
fun Modifier.transparentLineShadow(
    alpha: Float = 0.25f,       // Плотность тени под самой линией
    shadowHeight: Dp = 12.dp    // Высота рассеивания тени вниз
): Modifier = this.drawBehind {
    val shadowHeightPx = shadowHeight.toPx()

    // Рисуем теневой прямоугольник, который начинается от центра сикбара и уходит вниз
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = alpha),
                Color.Transparent
            ),
            startY = size.height / 2,
            endY = size.height / 2 + shadowHeightPx
        ),
        topLeft = Offset(0f, size.height / 2),
        size = Size(size.width, shadowHeightPx)
    )
}

/**
 * Аккуратная и глубокая тень для текста.
 * Спасает белый текст от растворения на светлых обложках.
 */
val SoftTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.3f), // Плотность темноты за буквами
    offset = Offset(0f, 2f),               // Смещение тени чуть вниз по оси Y
    blurRadius = 5f                        // Мягкость размытия контура букв
)
