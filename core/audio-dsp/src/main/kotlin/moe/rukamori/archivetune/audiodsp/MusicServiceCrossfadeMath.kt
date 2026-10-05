/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

@Deprecated("Use CrossfadeConstants.DEFAULT_MS directly", ReplaceWith("CrossfadeConstants.DEFAULT_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val FADE_DEFAULT_MS = CrossfadeConstants.DEFAULT_MS

@Deprecated("Use CrossfadeConstants.MIN_FADE_MS directly", ReplaceWith("CrossfadeConstants.MIN_FADE_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val MIN_FADE_MS = CrossfadeConstants.MIN_FADE_MS

@Deprecated("Use CrossfadeConstants.END_GUARD_MS directly", ReplaceWith("CrossfadeConstants.END_GUARD_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val GUARD_WINDOW_MS = CrossfadeConstants.END_GUARD_MS

@Deprecated("Use CrossfadeConstants.CLAMP_MIN_S directly", ReplaceWith("CrossfadeConstants.CLAMP_MIN_S", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val FADE_CLAMP_MIN_S = CrossfadeConstants.CLAMP_MIN_S

@Deprecated("Use CrossfadeConstants.CLAMP_MAX_S directly", ReplaceWith("CrossfadeConstants.CLAMP_MAX_S", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
const val FADE_CLAMP_MAX_S = CrossfadeConstants.CLAMP_MAX_S

// --- НАСТРОЙКИ DJ-ФИЛЬТРА «ПОД ВОДОЙ» ---
// 1. Точки прогресса (от 0.0 до 1.0)
const val DIVE_START_P = 0.05f       // Начало закрытия фильтра (5%)
const val DIVE_SUBMERGED_P = 0.35f   // Трек уже полностью под водой (35%)
const val DIVE_HOLD_UNTIL_P = 0.45f  // До этого момента держим глухой звук и громкость (60%)

// 2. Частоты среза (Гц)
const val FILTER_OPEN_HZ = 20000.0   // Открытый звук
const val FILTER_UNDERWATER_HZ = 2200.0 // Глухой «подводный» звук
const val FILTER_FLOOR_HZ = 1000.0   // Полный срез перед выключением

// 3. Громкость входящего трека
const val INCOMING_START_P = 0.30f   // Входящий начинает плавно вступать с 30%

val RISE: (Float) -> Float = { p -> sin(p.coerceIn(0f, 1f) * PI.toFloat() / 2f) }
val FALL: (Float) -> Float = { p -> cos(p.coerceIn(0f, 1f) * PI.toFloat() / 2f) }

const val BASS_SWAP_WINDOW_START = 0.45f
const val BASS_SWAP_WINDOW_END = 0.55f
private const val BASS_SWAP_DOMINANCE = 0.55

const val MID_DUCK_MAX_DB = 6.0
const val SOLO_OUTGOING_PROGRESS = 0.55f

fun outgoingLowPassHz(progress: Float): Double {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p <= DIVE_START_P -> FILTER_OPEN_HZ
        p <= DIVE_SUBMERGED_P -> {
            val t = (p - DIVE_START_P) / (DIVE_SUBMERGED_P - DIVE_START_P)
            FILTER_OPEN_HZ + (FILTER_UNDERWATER_HZ - FILTER_OPEN_HZ) * smoothStep(t)
        }
        p <= DIVE_HOLD_UNTIL_P -> FILTER_UNDERWATER_HZ
        else -> {
            val t = (p - DIVE_HOLD_UNTIL_P) / (1f - DIVE_HOLD_UNTIL_P)
            FILTER_UNDERWATER_HZ + (FILTER_FLOOR_HZ - FILTER_UNDERWATER_HZ) * smoothStep(t)
        }
    }
}

// Shared depth curve keeps the outgoing close and the incoming open perceptually mirrored.
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

/**
 * Extra attenuation of the outgoing deck, applied on top of the equal-power fade.
 * RISE/FALL are equal-power, so their coherent sum reaches +3 dB in the middle of the fade;
 * this keeps the summed level at or below unity and leaves the low end to the bass-swap curves.
 */
fun outgoingMidDuckDb(progress: Float): Double {
    val clamped = progress.coerceIn(0f, 1f)
    val s = sin((clamped * PI / 2.0).toDouble())
    return -MID_DUCK_MAX_DB * s * s
}

fun outgoingMidDuckGain(progress: Float): Double = 10.0.pow(outgoingMidDuckDb(progress) / 20.0)

/**
 * Volume law of the outgoing deck, transcribed from Orchard's staged choreography:
 * (0.0, 1.0) -> (0.35, 0.95) -> (1.0, 0.0), smoothstep between the points. The outgoing barely
 * attenuates while its low-pass closes, so the filter is heard on a near-unity track with no
 * second track competing, which is what makes the effect read cleanly.
 */
fun outgoingStageGain(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p <= DIVE_HOLD_UNTIL_P) return 1.0f
    val t = (p - DIVE_HOLD_UNTIL_P) / (1f - DIVE_HOLD_UNTIL_P)
    return 1.0f - smoothStep(t)
}
/**
 * Incoming deck counterpart: (0.0, 0.0) -> (0.35, 0.38) -> (1.0, 1.0). The second track stays
 * near-silent through the first third and arrives only once the outgoing is already dark.
 */
fun incomingStageGain(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < INCOMING_START_P) return 0f
    val t = (p - INCOMING_START_P) / (1f - INCOMING_START_P)
    return smoothStep(t)
}
private fun smoothStep(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

/**
 * Lateness is folded back into the fade window, but never far enough to eat it: a fold equal to
 * the whole duration degenerates the crossfade into a hard cut, which is exactly what the fold
 * was meant to avoid. At most half the window is folded, and always enough room is left for
 * [CrossfadeConstants.MIN_FADE_MS].
 */
fun boundedFoldMs(latenessMs: Long, durationMs: Long): Long {
    if (latenessMs <= 0L || durationMs <= 0L) return 0L
    val maxFold = (durationMs / 2).coerceAtMost(durationMs - CrossfadeConstants.MIN_FADE_MS)
    return latenessMs.coerceIn(0L, maxFold.coerceAtLeast(0L))
}

/**
 * Progress is measured from the end of the fold rather than from the fade start, so the decks
 * start from the levels they already had. Progressing from the folded position instead would step
 * both gains to their mid-fade value on the very first tick.
 */
fun fadeProgress(elapsedMs: Long, foldMs: Long, durationMs: Long): Float {
    val span = durationMs - foldMs
    if (span <= 0L) return 1f
    return ((elapsedMs - foldMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
}

fun advanceCueForElapsed(cueInMs: Long, elapsedMs: Long, maxPositionMs: Long?): Long {
    if (cueInMs <= 0L || elapsedMs <= 0L) return cueInMs
    val advanced = cueInMs + elapsedMs
    val ceiling = maxPositionMs ?: return advanced
    return advanced.coerceAtMost(ceiling.coerceAtLeast(0L))
}

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

fun isGaplessAlbumTransition(
    currentAlbum: String?,
    targetAlbum: String?,
): Boolean = currentAlbum != null && currentAlbum == targetAlbum

