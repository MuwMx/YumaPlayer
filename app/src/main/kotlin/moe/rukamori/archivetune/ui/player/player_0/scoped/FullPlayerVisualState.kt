package moe.rukamori.archivetune.ui.player.player_0.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp

/**
 * Хранит ссылки, необходимые для ленивого вычисления визуальных свойств большого плеера.
 *
 * **Ключевой дизайн**: [contentAlpha] и [translationY] вычисляются по требованию через геттеры,
 * которые читают [Animatable.value]. При вызове внутри `graphicsLayer { }` эти чтения
 * происходят во время фазы отрисовки (draw phase) и вызывают **только перерисовку**, а не рекомпозицию.
 * Это устраняет покадровую рекомпозицию родительского компонента во время жестов.
 */
internal class FullPlayerVisualState(
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val initialOffsetY: Float
) {
    /**
     * Плавное появление большого плеера:
     * полностью невидим до 25% раскрытия шторки, полностью непрозрачен при 100%.
     */
    val contentAlpha: Float
        get() {
            val f = expansionFraction.value
            return (f - 0.25f).coerceIn(0f, 0.75f) / 0.75f
        }

    /** Выплывание элементов снизу вверх, синхронизированное с [contentAlpha]. */
    val translationY: Float
        get() = lerp(initialOffsetY, 0f, contentAlpha)
}

@Composable
internal fun rememberFullPlayerVisualState(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    initialOffsetY: Float
): FullPlayerVisualState {
    return remember(expansionFraction, initialOffsetY) {
        FullPlayerVisualState(expansionFraction, initialOffsetY)
    }
}
