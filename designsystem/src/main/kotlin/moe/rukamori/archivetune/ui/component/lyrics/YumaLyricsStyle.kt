/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

@Immutable
data class YumaLyricsStyle(
    val textScale: Float = 1f,
    val alignment: YumaLyricsAlignment = YumaLyricsAlignment.Start,
    val blurEnabled: Boolean = true,
    val maxBlurRadiusDp: Float = 4f,
    val dimAlpha: Float = 0.35f,
    val activeScale: Float = 1.0f,
    val inactiveScale: Float = 0.94f,
    val showTranslation: Boolean = true,
    val showRomanization: Boolean = true,
    val textColor: Color = Color.White,
    val fontFamily: FontFamily? = null,
    val lineSpacingMultiplier: Float = 1.3f,
)
