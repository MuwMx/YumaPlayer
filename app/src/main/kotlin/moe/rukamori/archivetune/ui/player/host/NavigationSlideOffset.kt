package moe.rukamori.archivetune.ui.player.host

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun rememberNavigationSlideOffset(
    navSlideDistance: Dp,
    bottomNavigationBarHeight: Dp,
    navVisibleHeight: Dp,
    playerExpansionAnimatable: Animatable<Float, AnimationVector1D>,
): State<IntOffset> {
    val density = LocalDensity.current
    return remember(density, navSlideDistance, bottomNavigationBarHeight, navVisibleHeight, playerExpansionAnimatable) {
        derivedStateOf {
            with(density) {
                if (bottomNavigationBarHeight == 0.dp) {
                    IntOffset(
                        x = 0,
                        y = navSlideDistance.roundToPx(),
                    )
                } else {
                    val slideOffset =
                        navSlideDistance.toPx() *
                            playerExpansionAnimatable.value.coerceIn(
                                0f,
                                1f,
                            )
                    val hideOffset =
                        navSlideDistance.toPx() *
                            (
                                1f -
                                    bottomNavigationBarHeight.coerceAtMost(navVisibleHeight) /
                                        navVisibleHeight
                            )
                    IntOffset(
                        x = 0,
                        y = (slideOffset + hideOffset).roundToInt(),
                    )
                }
            }
        }
    }
}
