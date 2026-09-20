package moe.rukamori.archivetune.core.common.math

fun calculateColorWeight(hsv: FloatArray, population: Int): Float {
    val saturation = hsv[1]
    val brightness = hsv[2]
    val vibrancyBonus = if (saturation > 0.3f && brightness in 0.2f..0.9f) 1.3f else 1.0f
    return population * vibrancyBonus
}

fun isSimilarColor(hsv1: FloatArray, hsv2: FloatArray, rgb1: Int, rgb2: Int): Boolean {
    val hueDiffRaw = kotlin.math.abs(hsv1[0] - hsv2[0])
    val hueDiff = kotlin.math.min(hueDiffRaw, 360f - hueDiffRaw)
    val satDiff = kotlin.math.abs(hsv1[1] - hsv2[1])
    val valueDiff = kotlin.math.abs(hsv1[2] - hsv2[2])
    if (hueDiff < 12f && satDiff < 0.12f && valueDiff < 0.12f) return true

    val threshold = 28
    val r1 = (rgb1 shr 16) and 0xFF
    val g1 = (rgb1 shr 8) and 0xFF
    val b1 = rgb1 and 0xFF
    val r2 = (rgb2 shr 16) and 0xFF
    val g2 = (rgb2 shr 8) and 0xFF
    val b2 = rgb2 and 0xFF

    return kotlin.math.abs(r1 - r2) < threshold &&
        kotlin.math.abs(g1 - g2) < threshold &&
        kotlin.math.abs(b1 - b2) < threshold
}

fun isNearGray(hsv: FloatArray): Boolean = hsv[1] < 0.15f || hsv[2] < 0.08f
