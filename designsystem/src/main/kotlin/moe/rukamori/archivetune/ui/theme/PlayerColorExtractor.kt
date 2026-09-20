/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.core.common.math.calculateColorWeight
import moe.rukamori.archivetune.core.common.math.calculateGreyStops
import moe.rukamori.archivetune.core.common.math.calculateSaturationFactor
import moe.rukamori.archivetune.core.common.math.enhanceColorVividness
import moe.rukamori.archivetune.core.common.math.hueShift
import moe.rukamori.archivetune.core.common.math.isGreyscaleImage
import moe.rukamori.archivetune.core.common.math.isNearGray
import moe.rukamori.archivetune.core.common.math.isSimilarColor
import moe.rukamori.archivetune.core.common.math.tuneColorForMesh

data class ExtractedColors(
    val vibrant: Int,
    val darkMuted: Int,
    val gradient: Int,
)

/**
 * Player color extraction system for generating gradients from album artwork
 *
 * This system analyzes album artwork and extracts vibrant, dominant colors
 * to create visually appealing gradients for the music player interface.
 */
object PlayerColorExtractor {
    private val hsv = FloatArray(3)
    private val hsv1 = FloatArray(3)
    private val hsv2 = FloatArray(3)
    private val greyStops = FloatArray(6)
    private val targets = listOf(25f, -25f, 55f, -55f, 120f, -120f, 180f, 150f, -150f)
    private val valueTargets = floatArrayOf(0.82f, 0.74f, 0.68f, 0.6f, 0.86f, 0.7f)

    fun extractColors(bitmap: Bitmap): ExtractedColors {
        val seedColor = extractSeedColor(bitmap)
        val scheme = generateDarkColorSchemeFromSeed(seedColor)
        return ExtractedColors(
            vibrant = scheme.primary.toArgb(),
            darkMuted = scheme.secondaryContainer.toArgb(),
            gradient = scheme.primaryContainer.toArgb(),
        )
    }

    /**
     * Extracts colors from a palette and creates a gradient
     *
     * @param palette The color palette extracted from album artwork
     * @param fallbackColor Fallback color to use if extraction fails
     * @return List of colors for gradient (primary, darker variant, black)
     */
    suspend fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> =
        withContext(Dispatchers.Default) {
            synchronized(this@PlayerColorExtractor) {
                val allSwatches =
                    listOfNotNull(
                        palette.vibrantSwatch,
                        palette.lightVibrantSwatch,
                        palette.darkVibrantSwatch,
                        palette.dominantSwatch,
                        palette.mutedSwatch,
                        palette.darkMutedSwatch,
                        palette.lightMutedSwatch,
                    ).distinctBy { it.rgb }

                val rankedSwatches = allSwatches.sortedByDescending { calculateColorWeight(it) }

                val availableColors = mutableListOf<Color>()

                fun addIfUnique(
                    color: Color,
                    saturationFactor: Float,
                ) {
                    if (!isSimilarToAny(color, availableColors)) {
                        availableColors.add(enhanceColorVividness(color, saturationFactor))
                    }
                }

                for (swatch in rankedSwatches) {
                    android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                    val satFactor = calculateSaturationFactor(hsv[1])
                    addIfUnique(Color(swatch.rgb), satFactor)
                    if (availableColors.size >= 6) break
                }

                val totalPopulation = allSwatches.sumOf { it.population }.coerceAtLeast(1)
                val weightedExtractedSaturation =
                    allSwatches
                        .sumOf { swatch ->
                            android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                            (hsv[1] * swatch.population).toDouble()
                        }.toFloat() / totalPopulation.toFloat()

                val dominantColor = availableColors.firstOrNull() ?: Color(fallbackColor)
                val isGreyscaleImage = isGreyscaleImage(weightedExtractedSaturation, isNearGray(dominantColor))

                if (isGreyscaleImage) {
                    availableColors.clear()
                    val baseBrightness =
                        allSwatches.maxByOrNull { it.population }?.let { swatch ->
                            android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                            hsv[2]
                        } ?: 0.10f
                    calculateGreyStops(baseBrightness, greyStops)
                    hsv[0] = 0f
                    hsv[1] = 0f
                    while (availableColors.size < 6) {
                        val v = greyStops[availableColors.size % greyStops.size]
                        hsv[2] = v
                        availableColors.add(Color(android.graphics.Color.HSVToColor(hsv)))
                    }
                    return@withContext availableColors
                }

                val fallbackSeed =
                    Color(fallbackColor).takeUnless { isNearGray(it) }
                        ?: palette.dominantSwatch?.let { Color(it.rgb) }?.takeUnless { isNearGray(it) }
                        ?: Color.DarkGray

                val seed = availableColors.firstOrNull() ?: fallbackSeed

                run {
                    val baseCandidates = (availableColors.toList() + seed).distinct()
                    var baseIndex = 0
                    var targetIndex = 0
                    while (availableColors.size < 6) {
                        val baseColor = baseCandidates[baseIndex % baseCandidates.size]
                        val hueShiftDegrees = targets[targetIndex % targets.size]
                        val valueTarget = valueTargets[availableColors.size % valueTargets.size]
                        val derived =
                            tuneColorForMesh(
                                hueShift(baseColor, hueShiftDegrees),
                                saturationMin = 0.62f,
                                saturationBoost = 1.08f,
                                valueTarget = valueTarget,
                                valueMin = 0.38f,
                                valueMax = 0.9f,
                            )
                        if (!isSimilarToAny(derived, availableColors)) {
                            availableColors.add(derived)
                        }
                        baseIndex++
                        targetIndex++
                        if (baseIndex > 40) break
                    }
                }

                if (availableColors.isEmpty()) {
                    availableColors.add(tuneColorForMesh(fallbackSeed, 0.62f, 1.08f, 0.75f, 0.38f, 0.9f))
                }

                availableColors
            }
        }

    private fun enhanceColorVividness(
        color: Color,
        saturationFactor: Float = 1.4f,
    ): Color {
        val argb = color.toArgb()
        android.graphics.Color.colorToHSV(argb, hsv)
        enhanceColorVividness(hsv, saturationFactor)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun calculateColorWeight(swatch: Palette.Swatch?): Float {
        if (swatch == null) return 0f
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        return calculateColorWeight(hsv, swatch.population)
    }

    /**
     * Checks if two colors are similar (to avoid using nearly identical colors)
     */
    private fun isSimilarColor(
        color1: Color?,
        color2: Color?,
    ): Boolean {
        if (color1 == null || color2 == null) return false
        android.graphics.Color.colorToHSV(color1.toArgb(), hsv1)
        android.graphics.Color.colorToHSV(color2.toArgb(), hsv2)
        return isSimilarColor(hsv1, hsv2, color1.toArgb(), color2.toArgb())
    }

    private fun isSimilarToAny(
        color: Color,
        colors: List<Color>,
    ): Boolean = colors.any { isSimilarColor(color, it) }

    private fun hueShift(
        color: Color,
        degrees: Float,
    ): Color {
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hueShift(hsv, degrees)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun tuneColorForMesh(
        color: Color,
        saturationMin: Float,
        saturationBoost: Float,
        valueTarget: Float,
        valueMin: Float,
        valueMax: Float,
    ): Color {
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        tuneColorForMesh(hsv, saturationMin, saturationBoost, valueTarget, valueMin, valueMax)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun isNearGray(color: Color): Boolean {
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return isNearGray(hsv)
    }

    /**
     * Configuration constants for color extraction
     */
    object Config {
        const val MAX_COLOR_COUNT = 32
        const val BITMAP_AREA = 8000
        const val IMAGE_SIZE = 200
    }
}
