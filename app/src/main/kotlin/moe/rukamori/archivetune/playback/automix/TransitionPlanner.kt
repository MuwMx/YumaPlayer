package moe.rukamori.archivetune.playback.automix

import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.audiodsp.TransitionStyle

@Deprecated("Use moe.rukamori.archivetune.audiodsp.TransitionPlanner directly")
object TransitionPlanner {
    const val AUTO_MIN_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.AUTO_MIN_SECONDS
    const val AUTO_FAST_TRACK_MIN_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.AUTO_FAST_TRACK_MIN_SECONDS
    const val AUTO_MAX_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.AUTO_MAX_SECONDS
    const val AUTO_FALLBACK_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.AUTO_FALLBACK_SECONDS

    const val SOFT_MIN_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.SOFT_MIN_SECONDS
    const val SOFT_MAX_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.SOFT_MAX_SECONDS
    const val CLUB_MIN_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.CLUB_MIN_SECONDS
    const val CLUB_MAX_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.CLUB_MAX_SECONDS

    const val FAST_TRACK_BPM_THRESHOLD = moe.rukamori.archivetune.audiodsp.TransitionPlanner.FAST_TRACK_BPM_THRESHOLD
    const val OCTAVE_UPPER_BOUND = moe.rukamori.archivetune.audiodsp.TransitionPlanner.OCTAVE_UPPER_BOUND
    const val OCTAVE_LOWER_BOUND = moe.rukamori.archivetune.audiodsp.TransitionPlanner.OCTAVE_LOWER_BOUND
    const val TEMPO_ALIGNMENT_TOLERANCE = moe.rukamori.archivetune.audiodsp.TransitionPlanner.TEMPO_ALIGNMENT_TOLERANCE

    const val BEATS_MATCHED = moe.rukamori.archivetune.audiodsp.TransitionPlanner.BEATS_MATCHED
    const val BEATS_UNMATCHED = moe.rukamori.archivetune.audiodsp.TransitionPlanner.BEATS_UNMATCHED
    const val SECONDS_PER_MINUTE = moe.rukamori.archivetune.audiodsp.TransitionPlanner.SECONDS_PER_MINUTE
    const val MS_PER_SECOND = moe.rukamori.archivetune.audiodsp.TransitionPlanner.MS_PER_SECOND

    const val BEATMATCHED_THRESHOLD = moe.rukamori.archivetune.audiodsp.TransitionPlanner.BEATMATCHED_THRESHOLD
    const val DJ_ASSISTED_THRESHOLD = moe.rukamori.archivetune.audiodsp.TransitionPlanner.DJ_ASSISTED_THRESHOLD
    const val OUTRO_MIN_LEAD_SECONDS = moe.rukamori.archivetune.audiodsp.TransitionPlanner.OUTRO_MIN_LEAD_SECONDS

    fun minSecondsFor(aggressiveness: String): Double =
        moe.rukamori.archivetune.audiodsp.TransitionPlanner.minSecondsFor(aggressiveness)

    fun maxSecondsFor(aggressiveness: String): Double =
        moe.rukamori.archivetune.audiodsp.TransitionPlanner.maxSecondsFor(aggressiveness)

    fun resolveBassSwap(style: TransitionStyle, aggressiveness: String): Boolean =
        moe.rukamori.archivetune.audiodsp.TransitionPlanner.resolveBassSwap(style, aggressiveness)

    fun resolveForcedDurationMs(preset: String): Long? =
        moe.rukamori.archivetune.audiodsp.TransitionPlanner.resolveForcedDurationMs(preset)

    fun resolvePreferredDurationMs(preset: String, fallbackMs: Long?): Long? =
        moe.rukamori.archivetune.audiodsp.TransitionPlanner.resolvePreferredDurationMs(preset, fallbackMs)

    fun normalizedTempoRatio(currentBpm: Double, nextBpm: Double): Double =
        moe.rukamori.archivetune.audiodsp.TransitionPlanner.normalizedTempoRatio(currentBpm, nextBpm)

    fun calculateAdaptiveDurationSeconds(
        currentBpm: Double?,
        nextBpm: Double?,
        fallbackSeconds: Double? = null,
        aggressiveness: String = "standard",
    ): Double = moe.rukamori.archivetune.audiodsp.TransitionPlanner.calculateAdaptiveDurationSeconds(
        currentBpm, nextBpm, fallbackSeconds, aggressiveness,
    )

    fun calculateAdaptiveDurationMs(
        currentBpm: Double?,
        nextBpm: Double?,
        fallbackDurationMs: Long? = null,
        aggressiveness: String = "standard",
    ): Long = moe.rukamori.archivetune.audiodsp.TransitionPlanner.calculateAdaptiveDurationMs(
        currentBpm, nextBpm, fallbackDurationMs, aggressiveness,
    )

    fun resolveTransitionStyle(
        currentBpm: Double?,
        nextBpm: Double?,
    ): TransitionStyle = moe.rukamori.archivetune.audiodsp.TransitionPlanner.resolveTransitionStyle(
        currentBpm, nextBpm,
    )

    fun planTransition(
        currentDurationMs: Long,
        currentBpm: Double? = null,
        nextBpm: Double? = null,
        preferredDurationMs: Long? = null,
        incomingStartMs: Long = 0L,
        aggressiveness: String = "standard",
    ): AutomixPlan = moe.rukamori.archivetune.audiodsp.TransitionPlanner.planTransition(
        currentDurationMs, currentBpm, nextBpm, preferredDurationMs, incomingStartMs, aggressiveness,
    )

    fun planSmartTransition(
        outgoingAnalysis: TrackAnalysisResult,
        incomingAnalysis: TrackAnalysisResult?,
        currentDurationMs: Long,
        preferredDurationMs: Long? = null,
        aggressiveness: String = "standard",
        isGaplessAlbumTransition: Boolean = false,
    ): AutomixPlan = moe.rukamori.archivetune.audiodsp.TransitionPlanner.planSmartTransition(
        outgoingAnalysis, incomingAnalysis, currentDurationMs, preferredDurationMs, aggressiveness, isGaplessAlbumTransition,
    )
}
