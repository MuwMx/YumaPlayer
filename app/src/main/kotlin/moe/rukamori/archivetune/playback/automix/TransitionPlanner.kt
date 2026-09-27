package moe.rukamori.archivetune.playback.automix

import kotlin.math.abs
import kotlin.math.roundToLong

object TransitionPlanner {
    const val AUTO_MIN_SECONDS = 4.0
    const val AUTO_FAST_TRACK_MIN_SECONDS = 6.0
    const val AUTO_MAX_SECONDS = 12.0
    const val AUTO_FALLBACK_SECONDS = 8.0

    const val FAST_TRACK_BPM_THRESHOLD = 140.0
    const val OCTAVE_UPPER_BOUND = 1.5
    const val OCTAVE_LOWER_BOUND = 0.67
    const val TEMPO_ALIGNMENT_TOLERANCE = 0.07

    const val BEATS_MATCHED = 8
    const val BEATS_UNMATCHED = 16
    const val SECONDS_PER_MINUTE = 60.0
    const val MS_PER_SECOND = 1000L

    const val BEATMATCHED_THRESHOLD = 0.05
    const val DJ_ASSISTED_THRESHOLD = 0.15

    fun normalizedTempoRatio(currentBpm: Double, nextBpm: Double): Double {
        if (currentBpm <= 0.0 || nextBpm <= 0.0) return 1.0
        var ratio = nextBpm / currentBpm
        while (ratio > OCTAVE_UPPER_BOUND) {
            ratio /= 2.0
        }
        while (ratio < OCTAVE_LOWER_BOUND) {
            ratio *= 2.0
        }
        return ratio
    }

    fun calculateAdaptiveDurationSeconds(
        currentBpm: Double?,
        nextBpm: Double?,
        fallbackSeconds: Double? = null,
    ): Double {
        val cur = currentBpm ?: 0.0
        val next = nextBpm ?: 0.0
        if (cur <= 0.0 || next <= 0.0) {
            val fallback = fallbackSeconds ?: AUTO_FALLBACK_SECONDS
            return fallback.coerceIn(AUTO_MIN_SECONDS, AUTO_MAX_SECONDS)
        }

        val ratio = normalizedTempoRatio(cur, next)
        val transitionBeats = if (abs(1.0 - ratio) > TEMPO_ALIGNMENT_TOLERANCE) {
            BEATS_UNMATCHED
        } else {
            BEATS_MATCHED
        }
        val beatSeconds = SECONDS_PER_MINUTE / cur
        val minimumOverlap = if (cur >= FAST_TRACK_BPM_THRESHOLD) {
            AUTO_FAST_TRACK_MIN_SECONDS
        } else {
            AUTO_MIN_SECONDS
        }

        return (transitionBeats * beatSeconds).coerceIn(minimumOverlap, AUTO_MAX_SECONDS)
    }

    fun calculateAdaptiveDurationMs(
        currentBpm: Double?,
        nextBpm: Double?,
        fallbackDurationMs: Long? = null,
    ): Long {
        val fallbackSec = fallbackDurationMs?.let { it.toDouble() / MS_PER_SECOND }
        val seconds = calculateAdaptiveDurationSeconds(currentBpm, nextBpm, fallbackSec)
        return (seconds * MS_PER_SECOND).roundToLong()
    }

    fun resolveTransitionStyle(
        currentBpm: Double?,
        nextBpm: Double?,
    ): TransitionStyle {
        val cur = currentBpm ?: 0.0
        val next = nextBpm ?: 0.0
        if (cur <= 0.0 || next <= 0.0) {
            return TransitionStyle.PLAIN_CROSSFADE
        }
        val ratio = normalizedTempoRatio(cur, next)
        val deviation = abs(1.0 - ratio)
        return when {
            deviation <= BEATMATCHED_THRESHOLD -> TransitionStyle.BEATMATCHED
            deviation <= DJ_ASSISTED_THRESHOLD -> TransitionStyle.DJ_ASSISTED
            else -> TransitionStyle.PLAIN_CROSSFADE
        }
    }

    fun planTransition(
        currentDurationMs: Long,
        currentBpm: Double? = null,
        nextBpm: Double? = null,
        preferredDurationMs: Long? = null,
        incomingStartMs: Long = 0L,
    ): AutomixPlan {
        val durationMs = calculateAdaptiveDurationMs(currentBpm, nextBpm, preferredDurationMs)
            .coerceAtMost(maxOf(0L, currentDurationMs))
        val style = resolveTransitionStyle(currentBpm, nextBpm)
        val enableBassSwap = style != TransitionStyle.PLAIN_CROSSFADE

        return AutomixPlan(
            triggerOffsetMs = durationMs,
            durationMs = durationMs,
            incomingStartMs = incomingStartMs,
            enableBassSwap = enableBassSwap,
        )
    }
}
