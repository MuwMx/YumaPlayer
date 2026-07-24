package moe.rukamori.archivetune.ui.player.player_0.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Централизует обновления движения шторки, чтобы логика анимации и привязки (snap)
 * находилась в одном месте. Обеспечивает стабильное поведение при перехвате жестов.
 */
internal class SheetMotionController(
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultAnimationSpec: AnimationSpec<Float>,
    private val expandedY: Float = 0f
) {
    suspend fun animateTo(
        targetExpanded: Boolean,
        canExpand: Boolean,
        collapsedY: Float,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (canExpand && targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) expandedY else collapsedY
        val velocityScale = (collapsedY - expandedY).coerceAtLeast(1f)

        // Если мы уже в целевой точке и анимация не идет — ничего не делаем
        if (
            translationY.value == targetY &&
            expansionFraction.value == targetFraction &&
            !translationY.isRunning &&
            !expansionFraction.isRunning
        ) {
            return
        }

        // MutatorMutex отменяет предыдущую анимацию, если запускается новая
        mutex.mutate {
            coroutineScope {
                launch {
                    translationY.animateTo(
                        targetValue = targetY,
                        initialVelocity = initialVelocity,
                        animationSpec = animationSpec
                    )
                }
                launch {
                    expansionFraction.animateTo(
                        targetValue = targetFraction,
                        initialVelocity = initialVelocity / velocityScale,
                        animationSpec = animationSpec
                    )
                }
            }
        }
    }

    suspend fun stop() {
        translationY.stop()
        expansionFraction.stop()
    }

    suspend fun snapTo(translationYValue: Float, expansionFractionValue: Float) {
        mutex.mutate {
            translationY.snapTo(translationYValue)
            expansionFraction.snapTo(expansionFractionValue)
        }
    }

    suspend fun snapCollapsed(collapsedY: Float) {
        snapTo(
            translationYValue = collapsedY,
            expansionFractionValue = 0f
        )
    }

    suspend fun syncToExpansion(collapsedY: Float) {
        val adjustedY = collapsedY + (expandedY - collapsedY) * expansionFraction.value
        if (translationY.value == adjustedY && !translationY.isRunning) return
        mutex.mutate {
            translationY.snapTo(adjustedY)
        }
    }
}