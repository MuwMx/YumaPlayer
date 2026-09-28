package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

const val CURVE_IN_DEFAULT = "S_CURVE"
const val CURVE_OUT_DEFAULT = "S_CURVE"

@Deprecated("Use CrossfadeConstants.DEFAULT_MS directly", ReplaceWith("CrossfadeConstants.DEFAULT_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val FADE_DEFAULT_MS = CrossfadeConstants.DEFAULT_MS

@Deprecated("Use CrossfadeConstants.MIN_FADE_MS directly", ReplaceWith("CrossfadeConstants.MIN_FADE_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val MIN_FADE_MS = CrossfadeConstants.MIN_FADE_MS

@Deprecated("Use CrossfadeConstants.END_GUARD_MS directly", ReplaceWith("CrossfadeConstants.END_GUARD_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val GUARD_WINDOW_MS = CrossfadeConstants.END_GUARD_MS

const val ARM_LEAD_MS = 4000L
const val FADE_TIMEOUT_MS = 12000L

@Deprecated("Use CrossfadeConstants.CLAMP_MIN_S directly", ReplaceWith("CrossfadeConstants.CLAMP_MIN_S", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val FADE_CLAMP_MIN_S = CrossfadeConstants.CLAMP_MIN_S

@Deprecated("Use CrossfadeConstants.CLAMP_MAX_S directly", ReplaceWith("CrossfadeConstants.CLAMP_MAX_S", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val FADE_CLAMP_MAX_S = CrossfadeConstants.CLAMP_MAX_S

val RISE: (Float) -> Float = { p -> sin(p.coerceIn(0f, 1f) * PI.toFloat() / 2f) }
val FALL: (Float) -> Float = { p -> cos(p.coerceIn(0f, 1f) * PI.toFloat() / 2f) }

const val BASS_SWAP_WINDOW_START = 0.45f
const val BASS_SWAP_WINDOW_END = 0.55f
private const val BASS_SWAP_DOMINANCE = 0.55

private val bassFloorLinear = 10.0.pow(DjFilterAudioProcessor.FULL_CUT_DB / 20.0)

private fun bassSwapIncomingLinear(progress: Float): Double {
    val phase = ((progress.coerceIn(0f, 1f) - BASS_SWAP_WINDOW_START) /
        (BASS_SWAP_WINDOW_END - BASS_SWAP_WINDOW_START)).coerceIn(0f, 1f)
    return if (phase <= 0.5f) {
        phase / 0.5f * BASS_SWAP_DOMINANCE
    } else {
        BASS_SWAP_DOMINANCE + (phase - 0.5f) / 0.5f * (1.0 - BASS_SWAP_DOMINANCE)
    }.toDouble()
}

private fun linearToBassDb(linear: Double): Double =
    (20.0 * log10(linear.coerceAtLeast(bassFloorLinear))).coerceAtLeast(DjFilterAudioProcessor.FULL_CUT_DB)

fun incomingBassGainDb(progress: Float): Double = linearToBassDb(bassSwapIncomingLinear(progress))

fun outgoingBassGainDb(progress: Float): Double = linearToBassDb(1.0 - bassSwapIncomingLinear(progress))

fun effectiveCrossfadeDuration(durationMs: Long, configuredMs: Long): Long? {
    if (durationMs == C.TIME_UNSET || durationMs <= 0L) return null
    val maxDuration = durationMs - CrossfadeConstants.END_GUARD_MS
    if (maxDuration < CrossfadeConstants.MIN_FADE_MS) return null
    return configuredMs
        .coerceAtLeast(CrossfadeConstants.MIN_FADE_MS)
        .coerceAtMost(maxDuration)
}

fun requiredStartBufferMs(configuredMs: Long): Long =
    minOf(configuredMs, CrossfadeConstants.DEFAULT_MS)

fun requiredCrossfadeStartBufferMs(configuredMs: Long): Long =
    requiredStartBufferMs(configuredMs)

fun isGaplessAlbumTransition(
    currentAlbum: String?,
    targetAlbum: String?,
): Boolean = currentAlbum != null && currentAlbum == targetAlbum

fun isGaplessAlbumTransition(
    currentItem: MediaItem,
    targetItem: MediaItem,
    currentAlbum: String? = null,
    targetAlbum: String? = null,
): Boolean {
    val current = currentAlbum
        ?: currentItem.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
    val target = targetAlbum
        ?: targetItem.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
    return isGaplessAlbumTransition(current, target)
}
