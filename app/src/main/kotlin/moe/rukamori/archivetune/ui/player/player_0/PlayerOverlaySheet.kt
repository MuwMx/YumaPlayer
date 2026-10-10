package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.scoped.playerSheetVerticalDragGesture
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassBorder

@Composable
internal fun PlayerOverlaySheet(
    fractionProvider: () -> Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    cardShape: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    showDragHandle: Boolean = false,
    dragHandler: SheetVerticalDragGestureHandler? = null,
    headerContent: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = CapsuleDefaults.totalHeaderHeight())
                .graphicsLayer {
                    val fraction = fractionProvider().coerceIn(0f, 1f)

                    alpha = 1f
                    translationY = (1f - fraction) * size.height

                    shape = cardShape
                    clip = true
                }
                .background(backgroundColor, cardShape)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val inset = 1.dp.roundToPx()
                        val width = constraints.maxWidth + inset * 2

                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = width,
                                maxWidth = width
                            )
                        )

                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(-inset, 0)
                        }
                    }
                    .glassBorder(
                        shape = cardShape,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                        topAlpha = SettingsDimensions.GlassBorderTopAlpha,
                        bottomAlpha = SettingsDimensions.GlassBorderBottomAlpha,
                    )
            )

            content()

            if (showDragHandle) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .align(Alignment.TopCenter)
                        .then(
                            if (dragHandler != null) {
                                Modifier.playerSheetVerticalDragGesture(
                                    enabled = true,
                                    handler = dragHandler,
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.40f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        if (headerContent != null) {
            headerContent()
        }
    }
}
