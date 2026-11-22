/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import moe.rukamori.archivetune.audiodsp.StageSource
import moe.rukamori.archivetune.ui.theme.LocalYumaColors

@Immutable
data class StageCanvasPalette(
    val grid: Color,
    val edgeFront: Color,
    val edgeBack: Color,
    val listener: Color,
    val facing: Color,
    val label: Color,
    val selectionRing: Color,
    val muted: Color,
    val headLight: Color,
    val headShadow: Color,
    val headCup: Color,
    val headBand: Color,
    val headNose: Color,
    val headEye: Color,
    val vocal: Color,
    val center: Color,
    val left: Color,
    val right: Color,
    val ambience: Color,
    val sub: Color,
) {
    fun colorFor(source: StageSource): Color = when (source) {
        StageSource.Vocal -> vocal
        StageSource.Center -> center
        StageSource.Left -> left
        StageSource.Right -> right
        StageSource.Ambience -> ambience
        StageSource.Sub -> sub
    }
}

@Composable
fun rememberStageCanvasPalette(): StageCanvasPalette {
    val scheme = MaterialTheme.colorScheme
    val yumaColors = LocalYumaColors.current
    val isDark = scheme.surface.luminance() < 0.5f
    return remember(scheme, yumaColors, isDark) {
        StageCanvasPalette(
            grid = scheme.onSurface.copy(alpha = if (isDark) 0.06f else 0.08f),
            edgeFront = scheme.onSurface.copy(alpha = if (isDark) 0.34f else 0.38f),
            edgeBack = scheme.onSurface.copy(alpha = if (isDark) 0.18f else 0.18f),
            listener = scheme.onSurface,
            facing = scheme.onSurface,
            label = yumaColors.textPrimary.copy(alpha = 0.85f),
            selectionRing = scheme.onSurface.copy(alpha = 0.9f),
            muted = yumaColors.textSecondary.copy(alpha = 0.45f),
            headLight = if (isDark) Color(0xFFF7F7F9) else Color(0xFFE6E6EC),
            headShadow = if (isDark) Color(0xFF8E8E96) else Color(0xFF6E6E78),
            headCup = if (isDark) Color(0xFFE5E5EA) else Color(0xFF48484F),
            headBand = if (isDark) Color.White else Color(0xFF3A3A40),
            headNose = Color(0xFFF2C6B4),
            headEye = Color(0xFF1C1C1E),
            vocal = if (isDark) Color(0xFFF2D46B) else Color(0xFFC49400),
            center = if (isDark) Color(0xFFF2A46B) else Color(0xFFD96B27),
            left = if (isDark) Color(0xFF6BBAF2) else Color(0xFF1E78C8),
            right = if (isDark) Color(0xFFF28BA8) else Color(0xFFC9446B),
            ambience = if (isDark) Color(0xFF6BD9C4) else Color(0xFF15967D),
            sub = if (isDark) Color(0xFFB98CF2) else Color(0xFF7B4AC9),
        )
    }
}
