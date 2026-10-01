package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.playerSheetVerticalDragGesture
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import kotlin.math.roundToInt

@Composable
internal fun PlayerSheetScaffold(
    sheetVisualState: SheetVisualState,
    currentSheetState: PlayerSheetState,
    expansionFraction: Animatable<Float, *>,
    offsetAnimatable: Animatable<Float, *>,
    visualOvershootScaleY: Animatable<Float, *>,
    miniDismissGestureHandler: MiniPlayerDismissGestureHandler,
    dragHandler: SheetVerticalDragGestureHandler,
    screenHeightPx: Float,
    screenWidthPx: Float,
    density: Density,
    pillShape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, sheetVisualState.visualSheetTranslationYProvider().roundToInt()) }
            .graphicsLayer {
                translationX = if (currentSheetState == PlayerSheetState.COLLAPSED || expansionFraction.value < 0.01f) offsetAnimatable.value else 0f
                scaleY = visualOvershootScaleY.value
            }
            .miniPlayerDismissHorizontalGesture(
                enabled = currentSheetState == PlayerSheetState.COLLAPSED,
                handler = miniDismissGestureHandler
            )
            .playerSheetVerticalDragGesture(
                enabled = true,
                handler = dragHandler
            )
    ) {
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val targetHeightPx = sheetVisualState.playerContentAreaHeightPxProvider().toInt().coerceAtLeast(0)
                    val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider().toInt().coerceAtLeast(0)
                    val endPaddingPx = sheetVisualState.currentHorizontalPaddingEndPxProvider().toInt().coerceAtLeast(0)
                    val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx).coerceAtLeast(0)

                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = innerWidth, maxWidth = innerWidth,
                            minHeight = targetHeightPx, maxHeight = targetHeightPx
                        )
                    )
                    layout(constraints.maxWidth, targetHeightPx) { placeable.placeRelative(startPaddingPx, 0) }
                }
                .graphicsLayer {
                    shape = pillShape
                    clip = expansionFraction.value < 0.99f
                }
                .layout { measurable, constraints ->
                    val targetContentHeightPx = screenHeightPx.roundToInt()
                    val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider().toInt()
                    val stableScreenWidthPx = screenWidthPx.roundToInt()

                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = stableScreenWidthPx, maxWidth = stableScreenWidthPx,
                            minHeight = targetContentHeightPx, maxHeight = targetContentHeightPx
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(-startPaddingPx, 0) }
                }
        ) {
            content()
        }
    }
}
