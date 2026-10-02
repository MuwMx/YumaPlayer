package moe.rukamori.archivetune

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.NavigationBarAnimationSpec
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews

object PlayerDockDefaults {
    val MaxWidth: Dp = NavigationBarMaxWidth
    val HorizontalPadding: Dp = 16.dp
    val SlotGap: Dp = 2.dp
    val SoloCornerRadius: Dp = 28.dp
    val DockedCornerRadius: Dp = 10.dp
}

@Composable
fun PlayerDockContainer(
    modifier: Modifier = Modifier,
    isPillVisible: Boolean = false,
    isBarVisible: Boolean = true,
    barHeight: Dp = NavigationBarHeight,
    scrollVisibilityFactor: Float = 1f,
    miniPlayerSlot: @Composable (pillShape: RoundedCornerShape) -> Unit = {},
    barSlot: @Composable (barShape: RoundedCornerShape) -> Unit = {},
) {
    val junctionRadius by animateDpAsState(
        targetValue = if (isPillVisible && isBarVisible) {
            PlayerDockDefaults.DockedCornerRadius
        } else {
            PlayerDockDefaults.SoloCornerRadius
        },
        animationSpec = NavigationBarAnimationSpec,
        label = "DockJunctionRadius",
    )

    val animatedBarHeight = barHeight

    val pillShape = remember(junctionRadius) {
        RoundedCornerShape(
            topStart = PlayerDockDefaults.SoloCornerRadius,
            topEnd = PlayerDockDefaults.SoloCornerRadius,
            bottomStart = junctionRadius,
            bottomEnd = junctionRadius,
        )
    }

    val barShape = remember(junctionRadius) {
        RoundedCornerShape(
            topStart = junctionRadius,
            topEnd = junctionRadius,
            bottomStart = PlayerDockDefaults.SoloCornerRadius,
            bottomEnd = PlayerDockDefaults.SoloCornerRadius,
        )
    }

    Column(
        modifier = modifier
            .widthIn(max = PlayerDockDefaults.MaxWidth)
            .fillMaxWidth()
            .padding(horizontal = PlayerDockDefaults.HorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PlayerDockDefaults.SlotGap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = barHeight.toPx() * (1f - scrollVisibilityFactor)
                },
        ) {
            miniPlayerSlot(pillShape)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedBarHeight),
        ) {
            barSlot(barShape)
        }
    }
}

@ThemePreviews
@Composable
private fun PlayerDockContainerPreview() {
    TestThemeWrapper {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PlayerDockContainer(
                isPillVisible = true,
                isBarVisible = true,
                miniPlayerSlot = { pillShape ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(pillShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "MiniPlayer Pill Slot")
                    }
                },
                barSlot = { barShape ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(barShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Navigation Bar Slot")
                    }
                },
            )
        }
    }
}
