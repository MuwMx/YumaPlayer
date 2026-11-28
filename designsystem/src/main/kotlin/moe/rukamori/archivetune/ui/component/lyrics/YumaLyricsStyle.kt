/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

@Immutable
data class YumaCubicBezier(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

@Immutable
data class TimingTokens(
    val richsyncOffsetMs: Long = 150L,
    val lineOffsetMs: Long = 115L,
    val scrollOffsetMs: Long = 500L,
    val earlyScrollMs: Long = 540L,
    val endingThresholdMs: Long = 500L,
    val timeJumpMs: Long = 500L,
    val swipeLeadRatio: Float = 0.1f,
    val swipeDurationRatio: Float = 1.6f,
    val swipeStart: Pair<Float, Float> = -0.2f to 1.4f,
    val swipeEnd: Pair<Float, Float> = -0.1f to 1.5f,
    val glowRatio: Float = 1.2f,
    val glowMinSec: Float = 1.2f,
    val exitFadeSec: Float = 0.5f,
    val lineFadeSec: Float = 0.33f,
    val lineStaggerSec: Float = 0.05f,
    val decoEntrySec: Float = 0.35f,
    val userScrollResumeSec: Float = 25f,
    val minInstrumentalGapMs: Long = 5000L,
    val instrumentalCollapseLeadSec: Float = 0.25f,
)

@Immutable
data class LetterWaveTokens(
    val enabled: Boolean = true,
    val crestEm: Float = 0.05f,
    val settleEm: Float = 0.02f,
    val secs: Float = 0.9f,
    val emphasis: Float = 1.11f,
    val longWordSecs: Float = 1.5f,
    val crestFraction: Float = 0.4f,
    val restScale: Float = 1.0f,
)

@Immutable
data class WobbleTokens(
    val enabled: Boolean = true,
    val secs: Float = 1.0f,
    val peakTranslateEm: Float = 0.05f,
    val peakScaleX: Float = 1.025f,
)

@Immutable
data class WordBounceTokens(
    val liftEm: Float = 0f,
    val scale: Float = 1f,
    val secs: Float = 0.35f,
    val settleEm: Float = 0f,
    val spring: Boolean = false,
    val durRatio: Float = 0f,
    val sideEm: Float = 0f,
    val fromAlpha: Float = 0f,
)

@Immutable
data class MotionTokens(
    val ease: YumaCubicBezier = YumaCubicBezier(0.25f, 0.1f, 0.25f, 1.0f),
    val easeIn: YumaCubicBezier = YumaCubicBezier(0.42f, 0.0f, 1.0f, 1.0f),
    val easeOut: YumaCubicBezier = YumaCubicBezier(0.0f, 0.0f, 0.58f, 1.0f),
    val easeInOut: YumaCubicBezier = YumaCubicBezier(0.42f, 0.0f, 0.58f, 1.0f),
    val scrollEase: YumaCubicBezier = YumaCubicBezier(0.86f, 0.0f, 0.2f, 1.0f),
    val decoEase: YumaCubicBezier = YumaCubicBezier(0.25f, 1.0f, 0.5f, 1.0f),
    val restScale: Float = 0.95f,
    val activeScale: Float = 1.0f,
    val scaleSecs: Float = 0.166f,
    val opacityTransitionSecs: Float = 0.2f,
    val blurTransitionSecs: Float = 0.2f,
    val scrollBaseSecs: Float = 0.75f,
    val letterWave: LetterWaveTokens = LetterWaveTokens(),
    val wobble: WobbleTokens = WobbleTokens(),
    val wordBounce: WordBounceTokens? = null,
)

@Immutable
data class LineDistanceStyle(
    val alpha: Float,
    val blurFraction: Float,
)

@Immutable
data class VisualTokens(
    val anchorRatio: Float = 0.37f,
    val dimAlpha: Float = 0.30f,
    val activeAlpha: Float = 1.0f,
    val glowAlpha: Float = 0.5f,
    val longGlowAlpha: Float = 0.5f,
    val glowRemRatio: Float = 0.8f / 3.0f,
    val glowInvisibleAlpha: Float = 0.5f / 255f,
    val lineHeightMultiplier: Float = 1.333f,
    val baseFontSizeSp: Float = 26f,
    val lineVerticalPaddingEm: Float = 2.0f / 3.0f,
    val longGroupMaxChars: Int = 20,
    val backgroundEm: Float = 0.75f,
    val backgroundAlpha: Float = 0.78f,
    val backgroundGapEm: Float = 0.1f,
    val romanizationScale: Float = 1.6f / 3.0f,
    val romanizationAlpha: Float = 0.6f,
    val romanizationPill: Boolean = true,
    val romanizationPillFillAlpha: Float = 0.05f,
    val romanizationPillStrokeAlpha: Float = 0.10f,
    val romanizationPillPadXEm: Float = 0.25f,
    val romanizationPillPadYEm: Float = 0.25f / 3.0f,
    val translationScale: Float = 2.0f / 3.0f,
    val translationAlpha: Float = 0.6f,
    val decoGapEm: Float = 1.0f / 6.0f,
    val creditsAlphaRest: Float = 0.2f,
    val creditsAlphaFocused: Float = 0.85f,
    val instrumentalDotFloatEm: Float = 0.06f,
    val instrumentalDotWavePeriodSec: Float = 1.8f,
    val instrumentalDotPhaseOffsetRad: Float = 1.0f,
    val instrumentalDotPulseScale: Float = 0.12f,
    val instrumentalCollapseDropEm: Float = 0.35f,
    val past: List<LineDistanceStyle> = listOf(
        LineDistanceStyle(alpha = 0.42f, blurFraction = 0.35f),
        LineDistanceStyle(alpha = 0.28f, blurFraction = 0.67f),
        LineDistanceStyle(alpha = 0.18f, blurFraction = 1.0f),
    ),
    val upcoming: List<LineDistanceStyle> = listOf(
        LineDistanceStyle(alpha = 0.28f, blurFraction = 0.35f),
        LineDistanceStyle(alpha = 0.18f, blurFraction = 0.67f),
        LineDistanceStyle(alpha = 0.12f, blurFraction = 1.0f),
    ),
)

@Immutable
data class YumaLyricsTokens(
    val timing: TimingTokens = TimingTokens(),
    val motion: MotionTokens = MotionTokens(),
    val visual: VisualTokens = VisualTokens(),
) {
    companion object {
        val Default = YumaLyricsTokens()
    }
}

@Immutable
data class YumaLyricsStyle(
    val textScale: Float = 1f,
    val alignment: YumaLyricsAlignment = YumaLyricsAlignment.Start,
    val blurEnabled: Boolean = true,
    val maxBlurRadiusDp: Float = 4f,
    val showTranslation: Boolean = true,
    val showRomanization: Boolean = true,
    val textColor: Color = Color.White,
    val fontFamily: FontFamily? = null,
    val lineSpacingMultiplier: Float = 1.333f,
    val tokens: YumaLyricsTokens = YumaLyricsTokens.Default,
)
