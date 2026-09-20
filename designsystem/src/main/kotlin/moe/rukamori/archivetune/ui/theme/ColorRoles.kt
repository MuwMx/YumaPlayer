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
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeFidelity
import moe.rukamori.archivetune.core.common.math.ACCURATE_CHROMA_ABOVE_WEIGHT
import moe.rukamori.archivetune.core.common.math.ACCURATE_CHROMA_BELOW_WEIGHT
import moe.rukamori.archivetune.core.common.math.ACCURATE_CUTOFF_CHROMA
import moe.rukamori.archivetune.core.common.math.ACCURATE_FIDELITY_HUE_WINDOW
import moe.rukamori.archivetune.core.common.math.ACCURATE_LOCAL_REFINEMENT_BLEND_RATIO
import moe.rukamori.archivetune.core.common.math.ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW
import moe.rukamori.archivetune.core.common.math.ACCURATE_REPRESENTATIVE_BLEND_RATIO
import moe.rukamori.archivetune.core.common.math.ACCURATE_REPRESENTATIVE_PIXEL_CHROMA_THRESHOLD
import moe.rukamori.archivetune.core.common.math.MIN_REFINEMENT_PIXEL_RATIO
import moe.rukamori.archivetune.core.common.math.MIN_REPRESENTATIVE_PIXEL_RATIO
import moe.rukamori.archivetune.core.common.math.calculateExcessChromaPenalty
import moe.rukamori.archivetune.core.common.math.calculateHueExcitedProportions
import moe.rukamori.archivetune.core.common.math.calculateMonetScore
import moe.rukamori.archivetune.core.common.math.calculateRefinementPixelWeight
import moe.rukamori.archivetune.core.common.math.calculateRepresentativeFidelityScore
import moe.rukamori.archivetune.core.common.math.calculateRepresentativePixelWeight
import moe.rukamori.archivetune.core.common.math.differenceDegrees
import moe.rukamori.archivetune.core.common.math.isVisiblePixel
import moe.rukamori.archivetune.core.common.math.packArgb
import moe.rukamori.archivetune.core.common.math.sanitizeDegreesInt
import kotlin.collections.iterator
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

    val seedColor = try {
        val pixels = IntArray(workingBitmap.width * workingBitmap.height)
        workingBitmap.getPixels(pixels, 0, workingBitmap.width, 0, 0, workingBitmap.width, workingBitmap.height)
        Color(selectSeedColorArgbFromPixels(pixels, config))
    } catch (_: Throwable) {
        Color(0xFF1DB954)
    } finally {
        if (workingBitmap !== bitmap && !workingBitmap.isRecycled) {
            workingBitmap.recycle()
        }
    }

    extractedColorCache.put(cacheKey, seedColor)
    return seedColor
}

/**
 * Генерирует готовую темную схему Material 3 на основе базового цвета.
 * Использует SchemeExpressive для сочных и выразительных цветов обложки.
 */

fun generateDarkColorSchemeFromSeed(seedColor: Color): ColorScheme {
    return runCatching {
        val sourceHct = Hct.fromInt(seedColor.toArgb())
        SchemeFidelity(sourceHct, true, 0.0).toComposeColorScheme()
    }.getOrElse {
        SchemeFidelity(Hct.fromInt(0xFF1DB954.toInt()), true, 0.0).toComposeColorScheme()
    }
}

fun generateLightColorSchemeFromSeed(seedColor: Color): ColorScheme {
    return runCatching {
        val sourceHct = Hct.fromInt(seedColor.toArgb())
        SchemeFidelity(sourceHct, false, 0.0).toComposeColorScheme()
    }.getOrElse {
        SchemeFidelity(Hct.fromInt(0xFF1DB954.toInt()), false, 0.0).toComposeColorScheme()
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
        val hue = sanitizeDegreesInt(floor(hct.hue).toInt())
        huePopulation[hue] += population
        populationSum += population.toDouble()
    }

    if (populationSum <= 0.0) return listOf(fallbackColorArgb)

    val effectiveCutoffChroma = lerpDouble(scoring.cutoffChroma, ACCURATE_CUTOFF_CHROMA, accuracy)
    val effectiveTargetChroma = representativeColor?.let {
        lerpDouble(scoring.targetChroma, it.hct.chroma.coerceIn(12.0, 72.0), accuracy * 0.92)
    } ?: scoring.targetChroma

    val hueExcitedProportions = calculateHueExcitedProportions(huePopulation, populationSum)

    val scoredColors = ArrayList<ScoredHct>(colorsHct.size)
    for (hct in colorsHct) {
        val hue = sanitizeDegreesInt(hct.hue.roundToInt())
        val excitedProportion = hueExcitedProportions[hue]
        if (hct.chroma < effectiveCutoffChroma || excitedProportion <= scoring.cutoffExcitedProportion) continue

        val fidelityScore = representativeColor?.let { calculateRepresentativeFidelityScore(hct, it.hct, accuracy) } ?: 0.0
        val excessChromaPenalty = representativeColor?.let { calculateExcessChromaPenalty(hct, it.hct, accuracy) } ?: 0.0

        val score = calculateMonetScore(
            excitedProportion = excitedProportion,
            weightProportion = scoring.weightProportion,
            chroma = hct.chroma,
            effectiveTargetChroma = effectiveTargetChroma,
            fidelityScore = fidelityScore,
            excessChromaPenalty = excessChromaPenalty
        )

        scoredColors.add(ScoredHct(hct, score))
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
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF
        if (!isVisiblePixel(alpha, red, green, blue)) continue

        val hct = Hct.fromInt(argb)
        if (hct.chroma < ACCURATE_REPRESENTATIVE_PIXEL_CHROMA_THRESHOLD) continue

        val weight = calculateRepresentativePixelWeight(hct.chroma, hct.tone)
        totalRed += red * weight; totalGreen += green * weight; totalBlue += blue * weight
        totalWeight += weight; representativePixelCount++
    }

    if (totalWeight <= 0.0 || representativePixelCount.toDouble() / pixels.size < MIN_REPRESENTATIVE_PIXEL_RATIO) return null

    val argb = packArgb(
        0xFF,
        (totalRed / totalWeight).roundToInt().coerceIn(0, 255),
        (totalGreen / totalWeight).roundToInt().coerceIn(0, 255),
        (totalBlue / totalWeight).roundToInt().coerceIn(0, 255)
    )
    return RepresentativeArtworkColor(argb, Hct.fromInt(argb))
}

private fun calculateRepresentativeFidelityScore(candidate: Hct, representative: Hct, accuracy: Double): Double {
    return calculateRepresentativeFidelityScore(
        candidate.hue, candidate.chroma, candidate.tone,
        representative.hue, representative.chroma, representative.tone
    )
}

private fun calculateExcessChromaPenalty(candidate: Hct, representative: Hct, accuracy: Double): Double {
    return calculateExcessChromaPenalty(candidate.chroma, representative.chroma)
}

private fun refineSeedColorArgb(candidateArgb: Int, pixels: IntArray, representativeColor: RepresentativeArtworkColor?, cutoffChroma: Double, accuracy: Double): Int {
    if (pixels.isEmpty()) return candidateArgb
    val candidateHct = Hct.fromInt(candidateArgb)
    var totalRed = 0.0; var totalGreen = 0.0; var totalBlue = 0.0; var totalWeight = 0.0
    var matchingPixelCount = 0

    for (argb in pixels) {
        val alpha = (argb ushr 24) and 0xFF
        val red = (argb ushr 16) and 0xFF; val green = (argb ushr 8) and 0xFF; val blue = argb and 0xFF
        if (!isVisiblePixel(alpha, red, green, blue)) continue

        val hct = Hct.fromInt(argb)
        if (hct.chroma < ACCURATE_CUTOFF_CHROMA) continue

        val hueDistance = differenceDegrees(candidateHct.hue, hct.hue)
        if (hueDistance > ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW) continue

        val weight = calculateRefinementPixelWeight(hueDistance, hct.chroma)
        totalRed += red * weight; totalGreen += green * weight; totalBlue += blue * weight
        totalWeight += weight; matchingPixelCount++
    }

    if (totalWeight <= 0.0 || matchingPixelCount.toDouble() / pixels.size < MIN_REFINEMENT_PIXEL_RATIO) return candidateArgb

    val localAverageArgb = packArgb(
        0xFF,
        (totalRed / totalWeight).roundToInt().coerceIn(0, 255),
        (totalGreen / totalWeight).roundToInt().coerceIn(0, 255),
        (totalBlue / totalWeight).roundToInt().coerceIn(0, 255)
    )

    val refinedArgb = blendArgb(candidateArgb, localAverageArgb, ACCURATE_LOCAL_REFINEMENT_BLEND_RATIO)
    if (representativeColor == null) return refinedArgb

    return if (differenceDegrees(Hct.fromInt(localAverageArgb).hue, representativeColor.hct.hue) <= ACCURATE_FIDELITY_HUE_WINDOW) {
        blendArgb(refinedArgb, representativeColor.argb, ACCURATE_REPRESENTATIVE_BLEND_RATIO)
    } else refinedArgb
}

private fun blendArgb(firstArgb: Int, secondArgb: Int, ratio: Float): Int =
    moe.rukamori.archivetune.core.common.math.blendArgb(firstArgb, secondArgb, ratio)

private fun lerpDouble(start: Double, stop: Double, fraction: Double): Double =
    moe.rukamori.archivetune.core.common.math.lerpDouble(start, stop, fraction)

private fun averageColorArgb(pixels: IntArray): Int =
    moe.rukamori.archivetune.core.common.math.averageColorArgb(pixels)

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
