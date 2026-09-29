package moe.rukamori.archivetune.audiodsp

import kotlin.math.abs
import kotlin.math.roundToLong
import org.json.JSONObject

object TransitionPlanner {
    private const val MAX_STRETCH_DEVIATION = 0.04f

    const val AUTO_MIN_SECONDS = CrossfadeConstants.STANDARD_MIN_S
    const val AUTO_FAST_TRACK_MIN_SECONDS = 6.0
    const val AUTO_MAX_SECONDS = CrossfadeConstants.STANDARD_MAX_S
    const val AUTO_FALLBACK_SECONDS = 8.0

    const val SOFT_MIN_SECONDS = CrossfadeConstants.SOFT_MIN_S
    const val SOFT_MAX_SECONDS = CrossfadeConstants.SOFT_MAX_S
    const val CLUB_MIN_SECONDS = CrossfadeConstants.CLUB_MIN_S
    const val CLUB_MAX_SECONDS = CrossfadeConstants.CLUB_MAX_S

    const val FAST_TRACK_BPM_THRESHOLD = 140.0
    const val OCTAVE_UPPER_BOUND = 1.5
    const val OCTAVE_LOWER_BOUND = 0.67
    const val TEMPO_ALIGNMENT_TOLERANCE = 0.07

    const val BEATS_MATCHED = 8
    const val BEATS_UNMATCHED = 16
    const val SECONDS_PER_MINUTE = 60.0
    const val MS_PER_SECOND = CrossfadeConstants.MS_PER_SECOND

    const val BEATMATCHED_THRESHOLD = 0.05
    const val DJ_ASSISTED_THRESHOLD = 0.15
    const val OUTRO_MIN_LEAD_SECONDS = 8.0

    // The energy detector only sees a waveform, so a quiet passage can read as an exit point.
    // Anything this far from the end is that false positive, and the guard belongs here because
    // the planner is the only layer that knows the fade and the seekbar marker must agree.
    const val OUTRO_MIN_TRACK_FRACTION = 0.65
    const val OUTRO_MAX_LEAD_MS = 30_000L

    // The analyzer infers duration from the decoded head window when the real one is unknown, so
    // it can report that slice as the whole track. A content end this much below the real duration
    // is that artefact, not a genuine outro.
    const val ANALYSIS_DURATION_TRUST_RATIO = 0.8

    private const val FALLBACK_LEAD_BEAT_MULTIPLIER = 2

    fun minSecondsFor(aggressiveness: String): Double =
        CrossfadeConstants.Aggressiveness.fromString(aggressiveness).minSeconds

    fun maxSecondsFor(aggressiveness: String): Double =
        CrossfadeConstants.Aggressiveness.fromString(aggressiveness).maxSeconds

    fun resolveBassSwap(aggressiveness: String): Boolean = aggressiveness.lowercase() != "soft"

    fun resolveForcedDurationMs(preset: String): Long? = when (preset.lowercase()) {
        "4" -> 4L * MS_PER_SECOND
        "8" -> 8L * MS_PER_SECOND
        "12" -> 12L * MS_PER_SECOND
        else -> null
    }

    fun resolvePreferredDurationMs(preset: String, fallbackMs: Long?): Long? =
        resolveForcedDurationMs(preset) ?: fallbackMs

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
        aggressiveness: String = "standard",
    ): Double {
        val minSeconds = minSecondsFor(aggressiveness)
        val maxSeconds = maxSecondsFor(aggressiveness)
        val cur = currentBpm ?: 0.0
        val next = nextBpm ?: 0.0
        if (cur <= 0.0 || next <= 0.0) {
            val fallback = fallbackSeconds ?: AUTO_FALLBACK_SECONDS
            return fallback.coerceIn(minSeconds, maxSeconds)
        }

        val ratio = normalizedTempoRatio(cur, next)
        val transitionBeats = if (abs(1.0 - ratio) > TEMPO_ALIGNMENT_TOLERANCE) {
            BEATS_UNMATCHED
        } else {
            BEATS_MATCHED
        }
        val beatSeconds = SECONDS_PER_MINUTE / cur
        val minimumOverlap = if (cur >= FAST_TRACK_BPM_THRESHOLD) {
            maxOf(AUTO_FAST_TRACK_MIN_SECONDS, minSeconds)
        } else {
            minSeconds
        }

        return (transitionBeats * beatSeconds).coerceIn(minimumOverlap, maxSeconds)
    }

    fun calculateAdaptiveDurationMs(
        currentBpm: Double?,
        nextBpm: Double?,
        fallbackDurationMs: Long? = null,
        aggressiveness: String = "standard",
    ): Long {
        val fallbackSec = fallbackDurationMs?.let { it.toDouble() / MS_PER_SECOND }
        val seconds = calculateAdaptiveDurationSeconds(currentBpm, nextBpm, fallbackSec, aggressiveness)
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

    private fun fallbackLeadBeats(outgoingBpm: Double?, nextBpm: Double?): Int {
        val cur = outgoingBpm ?: 0.0
        val next = nextBpm ?: 0.0
        if (cur <= 0.0 || next <= 0.0) return BEATS_UNMATCHED * FALLBACK_LEAD_BEAT_MULTIPLIER
        val ratio = normalizedTempoRatio(cur, next)
        val transitionBeats = if (abs(1.0 - ratio) > TEMPO_ALIGNMENT_TOLERANCE) {
            BEATS_UNMATCHED
        } else {
            BEATS_MATCHED
        }
        return transitionBeats * FALLBACK_LEAD_BEAT_MULTIPLIER
    }

    private fun fallbackLeadMsBeforeContentEnd(outgoingBpm: Double?, nextBpm: Double?): Long {
        val cur = outgoingBpm ?: 0.0
        if (cur <= 0.0) return (AUTO_MAX_SECONDS * MS_PER_SECOND).roundToLong()
        return ((fallbackLeadBeats(outgoingBpm, nextBpm) * SECONDS_PER_MINUTE / cur) * MS_PER_SECOND).roundToLong()
    }

    fun sanitizeOutroMs(candidateMs: Long, contentEndMs: Long): Long? {
        if (candidateMs <= 0L || contentEndMs <= 0L) return null
        if (candidateMs >= contentEndMs) return candidateMs
        val earliestByFraction = (contentEndMs * OUTRO_MIN_TRACK_FRACTION).toLong()
        val earliestByTail = contentEndMs - OUTRO_MAX_LEAD_MS
        val earliest = maxOf(earliestByFraction, earliestByTail)
        return if (candidateMs >= earliest) candidateMs else null
    }

    fun resolveTrustedContentEndMs(reportedContentEndMs: Long, currentDurationMs: Long): Long {
        if (currentDurationMs <= 0L) return maxOf(0L, reportedContentEndMs)
        if (reportedContentEndMs <= 0L) return currentDurationMs
        if (reportedContentEndMs < currentDurationMs * ANALYSIS_DURATION_TRUST_RATIO) {
            return currentDurationMs
        }
        return reportedContentEndMs.coerceAtMost(currentDurationMs)
    }

    fun planTransition(
        currentDurationMs: Long,
        currentBpm: Double? = null,
        nextBpm: Double? = null,
        preferredDurationMs: Long? = null,
        incomingStartMs: Long = 0L,
        aggressiveness: String = "standard",
    ): AutomixPlan {
        val durationMs = calculateAdaptiveDurationMs(currentBpm, nextBpm, preferredDurationMs, aggressiveness)
            .coerceAtMost(maxOf(0L, currentDurationMs))
        val enableBassSwap = resolveBassSwap(aggressiveness)

        return AutomixPlan(
            triggerOffsetMs = durationMs,
            durationMs = durationMs,
            incomingStartMs = incomingStartMs,
            enableBassSwap = enableBassSwap,
        )
    }

    fun planSmartTransition(
        outgoingAnalysis: TrackAnalysisResult,
        incomingAnalysis: TrackAnalysisResult?,
        currentDurationMs: Long,
        preferredDurationMs: Long? = null,
        aggressiveness: String = "standard",
        isGaplessAlbumTransition: Boolean = false,
        currentPositionMs: Long = 0L,
    ): AutomixPlan {
        if (isGaplessAlbumTransition) {
            return AutomixPlan(0L, 0L, 0L, false, currentDurationMs, 0L, TransitionTier.GAPLESS, 1.0f)
        }
        val minMs = (minSecondsFor(aggressiveness) * MS_PER_SECOND).roundToLong()
        val maxMs = (maxSecondsFor(aggressiveness) * MS_PER_SECOND).roundToLong()
        val outgoingBpm = outgoingAnalysis.bpm.takeIf { it > 0.0 }
        val nextBpm = incomingAnalysis?.bpm?.takeIf { it > 0.0 }
        val bpmAdjustedDurationMs = calculateAdaptiveDurationMs(outgoingBpm, nextBpm, preferredDurationMs, aggressiveness)
            .coerceAtMost(maxOf(0L, currentDurationMs))

        val canBeatmatch = outgoingBpm != null && nextBpm != null &&
            ((abs(outgoingBpm - nextBpm) / outgoingBpm).toFloat() <= MAX_STRETCH_DEVIATION)
        val tier = if (canBeatmatch) TransitionTier.SMART_BEATMATCH else TransitionTier.PLAIN_CROSSFADE
        val incomingTempoRatio = if (canBeatmatch) (outgoingBpm / nextBpm).toFloat() else 1.0f

        val enableBassSwap = resolveBassSwap(aggressiveness)

        val incomingStartMs = if (canBeatmatch) {
            (incomingAnalysis?.mixInTime?.takeIf { it > 0.0 }
                ?.let { it * MS_PER_SECOND }?.roundToLong() ?: 0L)
                .coerceAtLeast(0L)
        } else {
            0L
        }

        val reportedContentEndMs = if (outgoingAnalysis.contentEndTime > 0.0) {
            (outgoingAnalysis.contentEndTime * MS_PER_SECOND).roundToLong()
        } else {
            currentDurationMs
        }
        val contentEndMs = resolveTrustedContentEndMs(reportedContentEndMs, currentDurationMs)

        val rawMixOutMs = if (outgoingAnalysis.mixOutTime > 0.0) {
            (outgoingAnalysis.mixOutTime * MS_PER_SECOND).roundToLong()
        } else {
            contentEndMs
        }.coerceIn(0L, contentEndMs)

        val outroStartTime = if (outgoingAnalysis.rawJson.isNotBlank()) {
            runCatching {
                JSONObject(outgoingAnalysis.rawJson).optDouble("outroStartTime", 0.0)
            }.getOrDefault(0.0)
        } else {
            0.0
        }
        val outroStartMs = if (outroStartTime.isFinite() && outroStartTime > 0.0) {
            (outroStartTime * MS_PER_SECOND).roundToLong()
        } else {
            null
        }

        val acceptedMixOutMs = sanitizeOutroMs(rawMixOutMs, contentEndMs)
        val acceptedOutroStartMs = outroStartMs?.let { sanitizeOutroMs(it, contentEndMs) }

        val isInteriorCliff = acceptedMixOutMs != null &&
            acceptedMixOutMs < contentEndMs - CrossfadeConstants.CLAMP_MIN_MS
        val outroLeadMs = (OUTRO_MIN_LEAD_SECONDS * MS_PER_SECOND).roundToLong()

        val (startAtMs, fadeDurationMs) = if (isInteriorCliff) {
            val naturalFade = (contentEndMs - acceptedMixOutMs).coerceAtLeast(CrossfadeConstants.CLAMP_MIN_MS)
            val fade = naturalFade.coerceIn(minMs, maxMs)
                .coerceAtMost(maxOf(0L, currentDurationMs - acceptedMixOutMs))
            Pair(acceptedMixOutMs, fade)
        } else if (acceptedOutroStartMs != null &&
            acceptedOutroStartMs <= contentEndMs - outroLeadMs
        ) {
            val fade = minOf(bpmAdjustedDurationMs, contentEndMs - acceptedOutroStartMs)
                .coerceIn(minMs, maxMs)
            Pair(acceptedOutroStartMs, fade)
        } else {
            val fade = bpmAdjustedDurationMs.coerceIn(minMs, maxMs).coerceAtMost(contentEndMs)
            val fallbackLeadMs = fallbackLeadMsBeforeContentEnd(outgoingBpm, nextBpm)
            val start = (contentEndMs - maxOf(fade, fallbackLeadMs)).coerceAtLeast(0L)
            Pair(start, fade)
        }

        val prepareAheadMs = maxOf(bpmAdjustedDurationMs, 7000L)

        val (anchoredStartAtMs, anchoredFadeMs) =
            anchorPlanToPlaybackPosition(startAtMs, fadeDurationMs, currentPositionMs, contentEndMs)

        return AutomixPlan(
            triggerOffsetMs = currentDurationMs - anchoredStartAtMs,
            durationMs = anchoredFadeMs,
            incomingStartMs = incomingStartMs,
            enableBassSwap = enableBassSwap,
            triggerAtMs = anchoredStartAtMs,
            prepareAheadMs = prepareAheadMs,
            tier = tier,
            incomingTempoRatio = incomingTempoRatio,
        )
    }

    // A trigger the listener already seeked past yields negative remaining time and starts the fade
    // where nobody is listening, so re-anchor to the live position and shorten to what is left.
    fun anchorPlanToPlaybackPosition(
        plannedStartAtMs: Long,
        plannedFadeMs: Long,
        currentPositionMs: Long,
        contentEndMs: Long,
    ): Pair<Long, Long> {
        if (currentPositionMs <= 0L || currentPositionMs < plannedStartAtMs) {
            return plannedStartAtMs to plannedFadeMs
        }
        val remainingMs = (contentEndMs - currentPositionMs).coerceAtLeast(0L)
        if (remainingMs <= 0L) return currentPositionMs to CrossfadeConstants.MIN_FADE_MS
        val compressedFade = plannedFadeMs.coerceAtMost(remainingMs)
            .coerceAtLeast(CrossfadeConstants.MIN_FADE_MS)
        return currentPositionMs to compressedFade
    }
}
