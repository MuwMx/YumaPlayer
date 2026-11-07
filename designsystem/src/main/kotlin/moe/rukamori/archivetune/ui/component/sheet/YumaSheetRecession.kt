/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.sheet

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.theme.SheetMotionTokens

fun Modifier.yumaSheetBackdropRecession(
    progressProvider: () -> Float,
    enabled: Boolean = true,
): Modifier = composed {
    val animationsDisabled = LocalAnimationsDisabled.current
    if (!enabled || animationsDisabled) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            val p = progressProvider().coerceIn(0f, 1f)
            val scale = 1f - SheetMotionTokens.BackdropRecessionDelta * p
            scaleX = scale
            scaleY = scale
            shape = RoundedCornerShape(SheetMotionTokens.BackdropMaxCornerRadius * p)
            clip = p > 0f
            compositingStrategy = if (p > 0f && p < 1f) {
                CompositingStrategy.Offscreen
            } else {
                CompositingStrategy.Auto
            }
        }
    }
}
