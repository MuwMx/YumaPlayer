@file:SuppressLint("RestrictedApi")

package moe.rukamori.archivetune.ui.theme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.MathUtils
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeFidelity
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// ============================================================================
// КОНФИГУРАЦИИ И КОНСТАНТЫ
// ============================================================================

data class ColorScoringConfig(
    val targetChroma: Double = 48.0,
    val weightProportion: Double = 0.7,
    val weightChromaAbove: Double = 0.3,
    val weightChromaBelow: Double = 0.1,
    val cutoffChroma: Double = 5.0,
    val cutoffExcitedProportion: Double = 0.01,
    val maxColorCount: Int = 4,
    val maxHueDifference: Int = 90,
    val minHueDifference: Int = 15
)

data class ColorExtractionConfig(
    val downscaleMaxDimension: Int = 128,
    val quantizerMaxColors: Int = 128,
    val scoring: ColorScoringConfig = ColorScoringConfig(),
    val normalizedAccuracy: Double = 1.0 // Максимальная точность по умолчанию
)

private data class ScoredHct(val hct: Hct, val score: Double)
private data class RepresentativeArtworkColor(val argb: Int, val hct: Hct)

private val extractedColorCache = LruCache<Int, Color>(32)

// Внутренние константы алгоритма Monet
private const val MIN_VISIBLE_RGB_SUM = 36
private const val MIN_VISIBLE_PIXEL_ALPHA = 28
private const val ACCURATE_CUTOFF_CHROMA = 3.5
private const val ACCURATE_MIN_HUE_DIFFERENCE = 10
private const val ACCURATE_MAX_HUE_DIFFERENCE = 64
private const val ACCURATE_REPRESENTATIVE_PIXEL_CHROMA_THRESHOLD = 6.0
private const val ACCURATE_CHROMA_ABOVE_WEIGHT = 0.14
private const val ACCURATE_CHROMA_BELOW_WEIGHT = 0.04
private const val ACCURATE_FIDELITY_HUE_WINDOW = 52.0
private const val ACCURATE_FIDELITY_CHROMA_WINDOW = 18.0
private const val ACCURATE_FIDELITY_TONE_WINDOW = 18.0
private const val ACCURATE_FIDELITY_HUE_WEIGHT = 28.0
private const val ACCURATE_FIDELITY_CHROMA_WEIGHT = 14.0
private const val ACCURATE_FIDELITY_TONE_WEIGHT = 6.0
private const val ACCURATE_EXCESS_CHROMA_PENALTY_START = 8.0
private const val ACCURATE_EXCESS_CHROMA_PENALTY_WEIGHT = 0.38
private const val ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW = 18.0
private const val ACCURATE_LOCAL_REFINEMENT_BLEND_RATIO = 0.72f
private const val ACCURATE_REPRESENTATIVE_BLEND_RATIO = 0.42f
private const val MIN_REPRESENTATIVE_PIXEL_RATIO = 0.04
private const val MIN_REFINEMENT_PIXEL_RATIO = 0.08

// ============================================================================
// ПУБЛИЧНЫЕ МЕТОДЫ
// ============================================================================

/**
 * Извлекает доминантный, сбалансированный цвет из обложки трека.
 */
fun extractSeedColor(bitmap: Bitmap, config: ColorExtractionConfig = ColorExtractionConfig()): Color {
    val cacheKey = 31 * bitmap.hashCode() + config.hashCode()
    extractedColorCache.get(cacheKey)?.let { return it }

    val workingBitmap = resizeForExtraction(bitmap, config.downscaleMaxDimension)

    val seedColor = runCatching {
        val pixels = IntArray(workingBitmap.width * workingBitmap.height)
        workingBitmap.getPixels(pixels, 0, workingBitmap.width, 0, 0, workingBitmap.width, workingBitmap.height)
        Color(selectSeedColorArgbFromPixels(pixels, config))
    }.getOrElse { Color(0xFF1DB954) } // Spotify Green Fallback

    extractedColorCache.put(cacheKey, seedColor)
    if (workingBitmap !== bitmap) workingBitmap.recycle()

    return seedColor
}

/**
 * Генерирует готовую темную схему Material 3 на основе базового цвета.
 * Использует SchemeExpressive для сочных и выразительных цветов обложки.
 */
//fun generateDarkColorSchemeFromSeed(seedColor: Color): ColorScheme {
//    return runCatching {
//        val sourceHct = Hct.fromInt(seedColor.toArgb())
//        // Генерируем выразительную (Expressive) темную тему (isDark = true)
//        SchemeExpressive(sourceHct, true, 0.0).toComposeColorScheme()
//    }.getOrElse {
//        // Фолбэк на случай ошибки математики
//        SchemeExpressive(Hct.fromInt(0xFF1DB954.toInt()), true, 0.0).toComposeColorScheme()
//    }
//}

fun generateDarkColorSchemeFromSeed(seedColor: Color): ColorScheme {
    return runCatching {
        val sourceHct = Hct.fromInt(seedColor.toArgb())
        // Меняем SchemeExpressive на SchemeFidelity.
        // Теперь никаких левых оттенков — строго родной пигмент трека.
        SchemeFidelity(sourceHct, true, 0.0).toComposeColorScheme()
    }.getOrElse {
        // Фолбэк на случай ошибки
        SchemeFidelity(Hct.fromInt(0xFF1DB954.toInt()), true, 0.0).toComposeColorScheme()
    }
}

// ============================================================================
// ВНУТРЕННЯЯ МАТЕМАТИКА КВАНТИЗАЦИИ (MONET ENGINE)
// ============================================================================

private fun selectSeedColorArgbFromPixels(pixels: IntArray, config: ColorExtractionConfig): Int {
    val fallbackArgb = averageColorArgb(pixels)
    val quantized = QuantizerCelebi.quantize(pixels, config.quantizerMaxColors)

    val representativeColor = calculateRepresentativeArtworkColor(pixels, config.normalizedAccuracy)
    val rankedSeeds = scoreQuantizedColors(quantized, config.scoring, fallbackArgb, representativeColor, config.normalizedAccuracy)
    val selectedSeed = rankedSeeds.firstOrNull() ?: fallbackArgb

    return refineSeedColorArgb(selectedSeed, pixels, representativeColor, config.scoring.cutoffChroma, config.normalizedAccuracy)
}

private fun resizeForExtraction(bitmap: Bitmap, maxDimension: Int): Bitmap {
    if (maxDimension <= 0 || (bitmap.width <= maxDimension && bitmap.height <= maxDimension)) return bitmap
    val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height).toFloat()
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

private fun scoreQuantizedColors(
    colorsToPopulation: Map<Int, Int>,
    scoring: ColorScoringConfig,
    fallbackColorArgb: Int,
    representativeColor: RepresentativeArtworkColor?,
    accuracy: Double
): List<Int> {
    if (colorsToPopulation.isEmpty()) return listOf(fallbackColorArgb)

    val colorsHct = ArrayList<Hct>(colorsToPopulation.size)
    val huePopulation = IntArray(360)
    var populationSum = 0.0

    for ((argb, population) in colorsToPopulation) {
        if (population <= 0) continue
        val hct = Hct.fromInt(argb)
        colorsHct.add(hct)
        val hue = MathUtils.sanitizeDegreesInt(floor(hct.hue).toInt())
        huePopulation[hue] += population
        populationSum += population.toDouble()
    }

    if (populationSum <= 0.0) return listOf(fallbackColorArgb)

    val effectiveCutoffChroma = lerpDouble(scoring.cutoffChroma, ACCURATE_CUTOFF_CHROMA, accuracy)
    val effectiveTargetChroma = representativeColor?.let {
        lerpDouble(scoring.targetChroma, it.hct.chroma.coerceIn(12.0, 72.0), accuracy * 0.92)
    } ?: scoring.targetChroma

    val hueExcitedProportions = DoubleArray(360)
    for (hue in 0 until 360) {
        val proportion = huePopulation[hue] / populationSum
        for (neighbor in hue - 14..hue + 15) {
            val wrappedHue = MathUtils.sanitizeDegreesInt(neighbor)
            hueExcitedProportions[wrappedHue] += proportion
        }
    }

    val scoredColors = ArrayList<ScoredHct>(colorsHct.size)
    for (hct in colorsHct) {
        val hue = MathUtils.sanitizeDegreesInt(hct.hue.roundToInt())
        val excitedProportion = hueExcitedProportions[hue]
        if (hct.chroma < effectiveCutoffChroma || excitedProportion <= scoring.cutoffExcitedProportion) continue

        val proportionScore = excitedProportion * 100.0 * scoring.weightProportion
        val chromaWeight = if (hct.chroma < effectiveTargetChroma) ACCURATE_CHROMA_BELOW_WEIGHT else ACCURATE_CHROMA_ABOVE_WEIGHT
        val chromaScore = (hct.chroma - effectiveTargetChroma) * chromaWeight

        val fidelityScore = representativeColor?.let { calculateRepresentativeFidelityScore(hct, it.hct, accuracy) } ?: 0.0
        val excessChromaPenalty = representativeColor?.let { calculateExcessChromaPenalty(hct, it.hct, accuracy) } ?: 0.0

        scoredColors.add(ScoredHct(hct, proportionScore + chromaScore + fidelityScore - excessChromaPenalty))
    }

    scoredColors.sortByDescending { it.score }
    return scoredColors.map { it.hct.toInt() }.takeIf { it.isNotEmpty() } ?: listOf(fallbackColorArgb)
}

private fun calculateRepresentativeArtworkColor(pixels: IntArray, accuracy: Double): RepresentativeArtworkColor? {
    if (pixels.isEmpty()) return null
    var totalRed = 0.0; var totalGreen = 0.0; var totalBlue = 0.0; var totalWeight = 0.0
    var representativePixelCount = 0

    for (argb in pixels) {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha < MIN_VISIBLE_PIXEL_ALPHA) continue
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF
        if (red + green + blue <= MIN_VISIBLE_RGB_SUM) continue

        val hct = Hct.fromInt(argb)
        if (hct.chroma < ACCURATE_REPRESENTATIVE_PIXEL_CHROMA_THRESHOLD) continue

        val weight = 1.0 + (((hct.chroma - ACCURATE_REPRESENTATIVE_PIXEL_CHROMA_THRESHOLD) / 24.0).coerceAtLeast(0.0) * 0.42) + (hct.tone / 100.0)
        totalRed += red * weight; totalGreen += green * weight; totalBlue += blue * weight
        totalWeight += weight; representativePixelCount++
    }

    if (totalWeight <= 0.0 || representativePixelCount.toDouble() / pixels.size < MIN_REPRESENTATIVE_PIXEL_RATIO) return null

    val argb = (0xFF shl 24) or ((totalRed / totalWeight).roundToInt().coerceIn(0, 255) shl 16) or
            ((totalGreen / totalWeight).roundToInt().coerceIn(0, 255) shl 8) or (totalBlue / totalWeight).roundToInt().coerceIn(0, 255)
    return RepresentativeArtworkColor(argb, Hct.fromInt(argb))
}

private fun calculateRepresentativeFidelityScore(candidate: Hct, representative: Hct, accuracy: Double): Double {
    val hueDistance = MathUtils.differenceDegrees(candidate.hue, representative.hue)
    val chromaDistance = abs(candidate.chroma - representative.chroma)
    val toneDistance = abs(candidate.tone - representative.tone)

    val hueScore = ((ACCURATE_FIDELITY_HUE_WINDOW - hueDistance).coerceAtLeast(0.0) / ACCURATE_FIDELITY_HUE_WINDOW) * ACCURATE_FIDELITY_HUE_WEIGHT
    val chromaScore = ((ACCURATE_FIDELITY_CHROMA_WINDOW - chromaDistance).coerceAtLeast(0.0) / ACCURATE_FIDELITY_CHROMA_WINDOW) * ACCURATE_FIDELITY_CHROMA_WEIGHT
    val toneScore = ((ACCURATE_FIDELITY_TONE_WINDOW - toneDistance).coerceAtLeast(0.0) / ACCURATE_FIDELITY_TONE_WINDOW) * ACCURATE_FIDELITY_TONE_WEIGHT
    return hueScore + chromaScore + toneScore
}

private fun calculateExcessChromaPenalty(candidate: Hct, representative: Hct, accuracy: Double): Double {
    val excessChroma = candidate.chroma - representative.chroma - ACCURATE_EXCESS_CHROMA_PENALTY_START
    return if (excessChroma <= 0.0) 0.0 else excessChroma * ACCURATE_EXCESS_CHROMA_PENALTY_WEIGHT
}

private fun refineSeedColorArgb(candidateArgb: Int, pixels: IntArray, representativeColor: RepresentativeArtworkColor?, cutoffChroma: Double, accuracy: Double): Int {
    if (pixels.isEmpty()) return candidateArgb
    val candidateHct = Hct.fromInt(candidateArgb)
    var totalRed = 0.0; var totalGreen = 0.0; var totalBlue = 0.0; var totalWeight = 0.0
    var matchingPixelCount = 0

    for (argb in pixels) {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha < MIN_VISIBLE_PIXEL_ALPHA) continue
        val red = (argb ushr 16) and 0xFF; val green = (argb ushr 8) and 0xFF; val blue = argb and 0xFF
        if (red + green + blue <= MIN_VISIBLE_RGB_SUM) continue

        val hct = Hct.fromInt(argb)
        if (hct.chroma < ACCURATE_CUTOFF_CHROMA) continue

        val hueDistance = MathUtils.differenceDegrees(candidateHct.hue, hct.hue)
        if (hueDistance > ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW) continue

        val weight = 1.0 + ((ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW - hueDistance) / ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW) + ((hct.chroma - ACCURATE_CUTOFF_CHROMA) / 32.0).coerceAtLeast(0.0)
        totalRed += red * weight; totalGreen += green * weight; totalBlue += blue * weight
        totalWeight += weight; matchingPixelCount++
    }

    if (totalWeight <= 0.0 || matchingPixelCount.toDouble() / pixels.size < MIN_REFINEMENT_PIXEL_RATIO) return candidateArgb

    val localAverageArgb = (0xFF shl 24) or ((totalRed / totalWeight).roundToInt().coerceIn(0, 255) shl 16) or
            ((totalGreen / totalWeight).roundToInt().coerceIn(0, 255) shl 8) or (totalBlue / totalWeight).roundToInt().coerceIn(0, 255)

    val refinedArgb = blendArgb(candidateArgb, localAverageArgb, ACCURATE_LOCAL_REFINEMENT_BLEND_RATIO)
    if (representativeColor == null) return refinedArgb

    return if (MathUtils.differenceDegrees(Hct.fromInt(localAverageArgb).hue, representativeColor.hct.hue) <= ACCURATE_FIDELITY_HUE_WINDOW) {
        blendArgb(refinedArgb, representativeColor.argb, ACCURATE_REPRESENTATIVE_BLEND_RATIO)
    } else refinedArgb
}

private fun blendArgb(firstArgb: Int, secondArgb: Int, ratio: Float): Int {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val inverseRatio = 1f - clampedRatio
    val alpha = (((firstArgb ushr 24) and 0xFF) * inverseRatio + ((secondArgb ushr 24) and 0xFF) * clampedRatio).roundToInt().coerceIn(0, 255)
    val red = (((firstArgb ushr 16) and 0xFF) * inverseRatio + ((secondArgb ushr 16) and 0xFF) * clampedRatio).roundToInt().coerceIn(0, 255)
    val green = (((firstArgb ushr 8) and 0xFF) * inverseRatio + ((secondArgb ushr 8) and 0xFF) * clampedRatio).roundToInt().coerceIn(0, 255)
    val blue = ((firstArgb and 0xFF) * inverseRatio + (secondArgb and 0xFF) * clampedRatio).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun lerpDouble(start: Double, stop: Double, fraction: Double): Double = start + ((stop - start) * fraction.coerceIn(0.0, 1.0))
private fun averageColorArgb(pixels: IntArray): Int {
    if (pixels.isEmpty()) return 0xFF1DB954.toInt()
    var r = 0L; var g = 0L; var b = 0L
    for (argb in pixels) { r += (argb ushr 16) and 0xFF; g += (argb ushr 8) and 0xFF; b += argb and 0xFF }
    val size = pixels.size.toLong()
    return (0xFF shl 24) or ((r / size).toInt() shl 16) or ((g / size).toInt() shl 8) or (b / size).toInt()
}

// Конвертер DynamicScheme в Compose ColorScheme через токенизатор MaterialDynamicColors
private fun DynamicScheme.toComposeColorScheme(): ColorScheme {
    val colors = MaterialDynamicColors()
    return ColorScheme(
        primary = Color(colors.primary().getArgb(this)),
        onPrimary = Color(colors.onPrimary().getArgb(this)),
        primaryContainer = Color(colors.primaryContainer().getArgb(this)),
        onPrimaryContainer = Color(colors.onPrimaryContainer().getArgb(this)),
        inversePrimary = Color(colors.inversePrimary().getArgb(this)),
        secondary = Color(colors.secondary().getArgb(this)),
        onSecondary = Color(colors.onSecondary().getArgb(this)),
        secondaryContainer = Color(colors.secondaryContainer().getArgb(this)),
        onSecondaryContainer = Color(colors.onSecondaryContainer().getArgb(this)),
        tertiary = Color(colors.tertiary().getArgb(this)),
        onTertiary = Color(colors.onTertiary().getArgb(this)),
        tertiaryContainer = Color(colors.tertiaryContainer().getArgb(this)),
        onTertiaryContainer = Color(colors.onTertiaryContainer().getArgb(this)),
        background = Color(colors.background().getArgb(this)),
        onBackground = Color(colors.onBackground().getArgb(this)),
        surface = Color(colors.surface().getArgb(this)),
        onSurface = Color(colors.onSurface().getArgb(this)),
        surfaceVariant = Color(colors.surfaceVariant().getArgb(this)),
        onSurfaceVariant = Color(colors.onSurfaceVariant().getArgb(this)),
        surfaceTint = Color(colors.surfaceTint().getArgb(this)),
        inverseSurface = Color(colors.inverseSurface().getArgb(this)),
        inverseOnSurface = Color(colors.inverseOnSurface().getArgb(this)),
        error = Color(colors.error().getArgb(this)),
        onError = Color(colors.onError().getArgb(this)),
        errorContainer = Color(colors.errorContainer().getArgb(this)),
        onErrorContainer = Color(colors.onErrorContainer().getArgb(this)),
        outline = Color(colors.outline().getArgb(this)),
        outlineVariant = Color(colors.outlineVariant().getArgb(this)),
        scrim = Color(colors.scrim().getArgb(this)),
        surfaceBright = Color(colors.surfaceBright().getArgb(this)),
        surfaceDim = Color(colors.surfaceDim().getArgb(this)),
        surfaceContainer = Color(colors.surfaceContainer().getArgb(this)),
        surfaceContainerHigh = Color(colors.surfaceContainerHigh().getArgb(this)),
        surfaceContainerHighest = Color(colors.surfaceContainerHighest().getArgb(this)),
        surfaceContainerLow = Color(colors.surfaceContainerLow().getArgb(this)),
        surfaceContainerLowest = Color(colors.surfaceContainerLowest().getArgb(this)),
        primaryFixed = Color(colors.primaryFixed().getArgb(this)),
        primaryFixedDim = Color(colors.primaryFixedDim().getArgb(this)),
        onPrimaryFixed = Color(colors.onPrimaryFixed().getArgb(this)),
        onPrimaryFixedVariant = Color(colors.onPrimaryFixedVariant().getArgb(this)),
        secondaryFixed = Color(colors.secondaryFixed().getArgb(this)),
        secondaryFixedDim = Color(colors.secondaryFixedDim().getArgb(this)),
        onSecondaryFixed = Color(colors.onSecondaryFixed().getArgb(this)),
        onSecondaryFixedVariant = Color(colors.onSecondaryFixedVariant().getArgb(this)),
        tertiaryFixed = Color(colors.tertiaryFixed().getArgb(this)),
        tertiaryFixedDim = Color(colors.tertiaryFixedDim().getArgb(this)),
        onTertiaryFixed = Color(colors.onTertiaryFixed().getArgb(this)),
        onTertiaryFixedVariant = Color(colors.onTertiaryFixedVariant().getArgb(this))
    )
}
