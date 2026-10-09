package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical

@Composable
internal fun NavigationDragSelectorOverlay(
    selectorState: NavigationTabSelectorState,
    modifier: Modifier = Modifier,
) {
    val selectorColor = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha)
    val density = LocalDensity.current
    val verticalPaddingPx = with(density) { NavigationTabSlotPaddingVertical.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                if (selectorState.tabWidthPx > 0f && selectorState.selectorWidthAnimatable.value > 0f) {
                    val currentWidth = selectorState.selectorWidthAnimatable.value
                    val currentX = selectorState.selectorXAnimatable.value
                    val height = size.height - (verticalPaddingPx * 2)
                    val top = verticalPaddingPx
                    val left = currentX - currentWidth / 2f
                    val cornerRadius = CornerRadius(height / 2f, height / 2f)

                    drawRoundRect(
                        color = selectorColor,
                        topLeft = Offset(left, top),
                        size = Size(currentWidth, height),
                        cornerRadius = cornerRadius,
                    )
                }
            },
    )
}

@Composable
internal fun NavigationDragSelectorOverlay(
    dragState: NavigationBarGestureState,
    modifier: Modifier = Modifier,
) {
    NavigationDragSelectorOverlay(
        selectorState = dragState.selectorState,
        modifier = modifier,
    )
}
