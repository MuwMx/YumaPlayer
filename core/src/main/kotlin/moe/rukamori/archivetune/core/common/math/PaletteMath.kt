package moe.rukamori.archivetune.core.common.math

const val FALLBACK_COLOR_ARGB = 0xFF1DB954.toInt()

const val MIN_VISIBLE_RGB_SUM = 36
const val MIN_VISIBLE_PIXEL_ALPHA = 28
const val ACCURATE_CUTOFF_CHROMA = 3.5
const val ACCURATE_MIN_HUE_DIFFERENCE = 10
const val ACCURATE_MAX_HUE_DIFFERENCE = 64
const val ACCURATE_REPRESENTATIVE_PIXEL_CHROMA_THRESHOLD = 6.0
const val ACCURATE_CHROMA_ABOVE_WEIGHT = 0.14
const val ACCURATE_CHROMA_BELOW_WEIGHT = 0.04
const val ACCURATE_FIDELITY_HUE_WINDOW = 52.0
const val ACCURATE_FIDELITY_CHROMA_WINDOW = 18.0
const val ACCURATE_FIDELITY_TONE_WINDOW = 18.0
const val ACCURATE_FIDELITY_HUE_WEIGHT = 28.0
const val ACCURATE_FIDELITY_CHROMA_WEIGHT = 14.0
const val ACCURATE_FIDELITY_TONE_WEIGHT = 6.0
const val ACCURATE_EXCESS_CHROMA_PENALTY_START = 8.0
const val ACCURATE_EXCESS_CHROMA_PENALTY_WEIGHT = 0.38
const val ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW = 18.0
const val ACCURATE_LOCAL_REFINEMENT_BLEND_RATIO = 0.72f
const val ACCURATE_REPRESENTATIVE_BLEND_RATIO = 0.42f
const val MIN_REPRESENTATIVE_PIXEL_RATIO = 0.04
const val MIN_REFINEMENT_PIXEL_RATIO = 0.08

const val GREYSCALE_SATURATION_THRESHOLD = 0.22f

val MESH_HUE_TARGETS = floatArrayOf(25f, -25f, 55f, -55f, 120f, -120f, 180f, 150f, -150f)
val MESH_VALUE_TARGETS = floatArrayOf(0.82f, 0.74f, 0.68f, 0.6f, 0.86f, 0.7f)

fun hueShift(hue: Float, degrees: Float): Float {
    return ((hue + degrees) % 360f + 360f) % 360f
}

fun hueShift(hsv: FloatArray, degrees: Float) {
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
}

fun tuneColorForMesh(
    hsv: FloatArray,
    saturationMin: Float,
    saturationBoost: Float,
    valueTarget: Float,
    valueMin: Float,
    valueMax: Float,
) {
    hsv[1] = (kotlin.math.max(hsv[1], saturationMin) * saturationBoost).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * 0.85f + valueTarget * 0.15f).coerceIn(valueMin, valueMax)
}

fun tuneColorForMeshSaturation(
    saturation: Float,
    saturationMin: Float,
    saturationBoost: Float,
): Float = (kotlin.math.max(saturation, saturationMin) * saturationBoost).coerceIn(0f, 1f)

fun tuneColorForMeshValue(
    value: Float,
    valueTarget: Float,
    valueMin: Float,
    valueMax: Float,
): Float = (value * 0.85f + valueTarget * 0.15f).coerceIn(valueMin, valueMax)

fun enhanceColorVividness(
    hsv: FloatArray,
    saturationFactor: Float = 1.4f,
) {
    hsv[1] = (hsv[1] * saturationFactor).coerceAtMost(1.0f)
    hsv[2] = (hsv[2] * 1.02f).coerceIn(0.32f, 0.88f)
}

fun enhanceSaturation(saturation: Float, saturationFactor: Float = 1.4f): Float =
    (saturation * saturationFactor).coerceAtMost(1.0f)

fun enhanceValue(value: Float): Float =
    (value * 1.02f).coerceIn(0.32f, 0.88f)

fun calculateSaturationFactor(saturation: Float): Float =
    if (saturation > 0.3f) 1.25f else 1.05f

fun calculateGreyStops(
    baseBrightness: Float,
    stops: FloatArray = FloatArray(6),
): FloatArray {
    stops[0] = (baseBrightness * 1.2f).coerceIn(0.06f, 0.40f)
    stops[1] = (baseBrightness * 0.9f).coerceIn(0.04f, 0.28f)
    stops[2] = (baseBrightness * 0.6f).coerceIn(0.02f, 0.16f)
    stops[3] = (baseBrightness * 1.4f).coerceIn(0.08f, 0.44f)
    stops[4] = (baseBrightness * 0.7f).coerceIn(0.03f, 0.20f)
    stops[5] = (baseBrightness * 0.5f).coerceIn(0.01f, 0.12f)
    return stops
}

fun isGreyscaleImage(
    weightedSaturation: Float,
    isDominantNearGray: Boolean,
): Boolean = weightedSaturation < GREYSCALE_SATURATION_THRESHOLD || isDominantNearGray

fun blendArgb(firstArgb: Int, secondArgb: Int, ratio: Float): Int {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val inverseRatio = 1f - clampedRatio
    val alpha = java.lang.Math.round(((firstArgb ushr 24) and 0xFF) * inverseRatio + ((secondArgb ushr 24) and 0xFF) * clampedRatio).coerceIn(0, 255)
    val red = java.lang.Math.round(((firstArgb ushr 16) and 0xFF) * inverseRatio + ((secondArgb ushr 16) and 0xFF) * clampedRatio).coerceIn(0, 255)
    val green = java.lang.Math.round(((firstArgb ushr 8) and 0xFF) * inverseRatio + ((secondArgb ushr 8) and 0xFF) * clampedRatio).coerceIn(0, 255)
    val blue = java.lang.Math.round((firstArgb and 0xFF) * inverseRatio + (secondArgb and 0xFF) * clampedRatio).coerceIn(0, 255)
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

fun averageColorArgb(pixels: IntArray, fallbackArgb: Int = FALLBACK_COLOR_ARGB): Int {
    if (pixels.isEmpty()) return fallbackArgb
    var r = 0L
    var g = 0L
    var b = 0L
    for (argb in pixels) {
        r += (argb ushr 16) and 0xFF
        g += (argb ushr 8) and 0xFF
        b += argb and 0xFF
    }
    val size = pixels.size.toLong()
    return (0xFF shl 24) or ((r / size).toInt() shl 16) or ((g / size).toInt() shl 8) or (b / size).toInt()
}

fun lerpDouble(start: Double, stop: Double, fraction: Double): Double =
    start + ((stop - start) * fraction.coerceIn(0.0, 1.0))

fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

fun sanitizeDegreesInt(degrees: Int): Int {
    val mod = degrees % 360
    return if (mod < 0) mod + 360 else mod
}

fun differenceDegrees(a: Double, b: Double): Double =
    180.0 - kotlin.math.abs(kotlin.math.abs(a - b) - 180.0)

fun differenceDegrees(a: Float, b: Float): Float {
    val diffRaw = kotlin.math.abs(a - b)
    return kotlin.math.min(diffRaw, 360f - diffRaw)
}

fun isVisiblePixel(alpha: Int, red: Int, green: Int, blue: Int): Boolean =
    alpha >= MIN_VISIBLE_PIXEL_ALPHA && (red + green + blue) > MIN_VISIBLE_RGB_SUM

fun calculateRepresentativePixelWeight(chroma: Double, tone: Double): Double =
    1.0 + (((chroma - ACCURATE_REPRESENTATIVE_PIXEL_CHROMA_THRESHOLD) / 24.0).coerceAtLeast(0.0) * 0.42) + (tone / 100.0)

fun calculateRefinementPixelWeight(hueDistance: Double, chroma: Double): Double =
    1.0 + ((ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW - hueDistance) / ACCURATE_LOCAL_REFINEMENT_HUE_WINDOW) + ((chroma - ACCURATE_CUTOFF_CHROMA) / 32.0).coerceAtLeast(0.0)

fun calculateRepresentativeFidelityScore(
    candidateHue: Double,
    candidateChroma: Double,
    candidateTone: Double,
    representativeHue: Double,
    representativeChroma: Double,
    representativeTone: Double,
): Double {
    val hueDistance = differenceDegrees(candidateHue, representativeHue)
    val chromaDistance = kotlin.math.abs(candidateChroma - representativeChroma)
    val toneDistance = kotlin.math.abs(candidateTone - representativeTone)

    val hueScore = ((ACCURATE_FIDELITY_HUE_WINDOW - hueDistance).coerceAtLeast(0.0) / ACCURATE_FIDELITY_HUE_WINDOW) * ACCURATE_FIDELITY_HUE_WEIGHT
    val chromaScore = ((ACCURATE_FIDELITY_CHROMA_WINDOW - chromaDistance).coerceAtLeast(0.0) / ACCURATE_FIDELITY_CHROMA_WINDOW) * ACCURATE_FIDELITY_CHROMA_WEIGHT
    val toneScore = ((ACCURATE_FIDELITY_TONE_WINDOW - toneDistance).coerceAtLeast(0.0) / ACCURATE_FIDELITY_TONE_WINDOW) * ACCURATE_FIDELITY_TONE_WEIGHT
    return hueScore + chromaScore + toneScore
}

fun calculateExcessChromaPenalty(
    candidateChroma: Double,
    representativeChroma: Double,
): Double {
    val excessChroma = candidateChroma - representativeChroma - ACCURATE_EXCESS_CHROMA_PENALTY_START
    return if (excessChroma <= 0.0) 0.0 else excessChroma * ACCURATE_EXCESS_CHROMA_PENALTY_WEIGHT
}

fun calculateHueExcitedProportions(
    huePopulation: IntArray,
    populationSum: Double,
): DoubleArray {
    val hueExcitedProportions = DoubleArray(360)
    for (hue in 0 until 360) {
        val proportion = huePopulation[hue] / populationSum
        for (neighbor in hue - 14..hue + 15) {
            val wrappedHue = sanitizeDegreesInt(neighbor)
            hueExcitedProportions[wrappedHue] += proportion
        }
    }
    return hueExcitedProportions
}

fun calculateMonetScore(
    excitedProportion: Double,
    weightProportion: Double,
    chroma: Double,
    effectiveTargetChroma: Double,
    fidelityScore: Double,
    excessChromaPenalty: Double,
): Double {
    val proportionScore = excitedProportion * 100.0 * weightProportion
    val chromaWeight = if (chroma < effectiveTargetChroma) ACCURATE_CHROMA_BELOW_WEIGHT else ACCURATE_CHROMA_ABOVE_WEIGHT
    val chromaScore = (chroma - effectiveTargetChroma) * chromaWeight
    return proportionScore + chromaScore + fidelityScore - excessChromaPenalty
}

fun calculateGradientDistanceScore(
    h1: Float, s1: Float, v1: Float,
    h2: Float, s2: Float, v2: Float,
): Float {
    val hueDiffRaw = kotlin.math.abs(h2 - h1)
    val hueDiff = kotlin.math.min(hueDiffRaw, 360f - hueDiffRaw) / 180f
    val satDiff = kotlin.math.abs(s2 - s1)
    val valueDiff = kotlin.math.abs(v2 - v1)
    return hueDiff * 0.65f + satDiff * 0.2f + valueDiff * 0.15f
}

fun calculateGradientDistanceScore(hsv1: FloatArray, hsv2: FloatArray): Float =
    calculateGradientDistanceScore(hsv1[0], hsv1[1], hsv1[2], hsv2[0], hsv2[1], hsv2[2])
