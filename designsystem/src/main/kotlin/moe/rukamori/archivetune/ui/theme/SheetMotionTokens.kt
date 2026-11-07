/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SheetMotionTokens {
    const val BackdropRecessionScale: Float = 0.92f
    const val BackdropRecessionDelta: Float = 0.08f
    val BackdropMaxCornerRadius: Dp = 28.dp
    const val ScrimMaxAlpha: Float = 0.5f

    val MotionSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
        visibilityThreshold = 0.0005f,
    )
}

const val BackdropRecessionScale: Float = SheetMotionTokens.BackdropRecessionScale
const val BackdropRecessionDelta: Float = SheetMotionTokens.BackdropRecessionDelta
val BackdropMaxCornerRadius: Dp = SheetMotionTokens.BackdropMaxCornerRadius
const val ScrimMaxAlpha: Float = SheetMotionTokens.ScrimMaxAlpha
val SheetMotionSpring: SpringSpec<Float> = SheetMotionTokens.MotionSpring
