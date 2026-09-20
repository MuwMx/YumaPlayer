package moe.rukamori.archivetune.core.common.math

fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

fun lerp3(start: Float, mid: Float, end: Float, fraction: Float): Float =
    if (fraction <= 0.5f) lerp(start, mid, fraction * 2f) else lerp(mid, end, (fraction - 0.5f) * 2f)
