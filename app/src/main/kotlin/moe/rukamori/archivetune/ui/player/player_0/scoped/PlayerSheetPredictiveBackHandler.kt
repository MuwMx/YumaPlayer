package moe.rukamori.archivetune.ui.player.player_0.scoped

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import kotlin.coroutines.cancellation.CancellationException

/**
 * Обработчик системного жеста "Назад".
 * Поддерживает предиктивную анимацию (Predictive Back) для Android 14+.
 */
@Composable
internal fun PlayerSheetPredictiveBackHandler(
    enabled: Boolean,
    currentSheetState: PlayerSheetState,
    predictiveBackFractionValue: Float,
    onPredictiveBackFractionChanged: (Float) -> Unit,
    sheetCollapsedTargetY: Float,
    sheetExpandedTargetY: Float,
    sheetMotionController: SheetMotionController,
    animationDurationMs: Int,
    onSwipeEdgeChanged: (Int?) -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    registrationKey: Any?
) {
    val scope = rememberCoroutineScope()

    key(registrationKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PredictiveBackHandler(enabled = enabled) { progressFlow ->

                var actualProgress = 0f

                try {
                    progressFlow.collect { backEvent ->
                        actualProgress = backEvent.progress
                        onSwipeEdgeChanged(backEvent.swipeEdge)
                        onPredictiveBackFractionChanged(actualProgress)
                    }

                    // Жест успешно завершен. Используем локальный actualProgress!
                    val currentVisualY = lerp(sheetExpandedTargetY, sheetCollapsedTargetY, actualProgress)
                    val currentVisualExpansionFraction = (1f - actualProgress).coerceIn(0f, 1f)

                    // 1. Фиксируем точку отрыва пальца в реальных координатах
                    sheetMotionController.snapTo(
                        translationYValue = currentVisualY,
                        expansionFractionValue = currentVisualExpansionFraction
                    )

                    // 2. Сбрасываем флаги для калькулятора геометрии
                    onPredictiveBackFractionChanged(0f)
                    onSwipeEdgeChanged(null)

                    // 3. Плавно роняем шторку вниз прямо в этом suspend-блоке
                    sheetMotionController.animateTo(
                        targetExpanded = false,
                        canExpand = true,
                        collapsedY = sheetCollapsedTargetY
                    )

                    onCollapse()

                } catch (_: CancellationException) {
                    // Жест отменен (вернули палец к краю экрана)
                    scope.launch {
                        // ИСПРАВЛЕНО: Плавный возврат от актуальной позиции, а не от нуля
                        Animatable(actualProgress).animateTo(
                            targetValue = 0f,
                            animationSpec = tween(animationDurationMs)
                        ) {
                            onPredictiveBackFractionChanged(this.value)
                        }

                        if (currentSheetState == PlayerSheetState.EXPANDED) {
                            onExpand()
                        } else {
                            onCollapse()
                        }

                        onSwipeEdgeChanged(null)
                    }
                }
            }
        } else {
            BackHandler(enabled = enabled) {
                onCollapse()
            }
        }
    }
}