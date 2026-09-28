package moe.rukamori.archivetune.audiodsp

data class AutomixPlan(
    val triggerOffsetMs: Long,
    val durationMs: Long,
    val incomingStartMs: Long,
    val enableBassSwap: Boolean,
    val triggerAtMs: Long? = null,
    val prepareAheadMs: Long? = null,
    val tier: TransitionTier = TransitionTier.PLAIN_CROSSFADE,
    val incomingTempoRatio: Float = 1.0f,
)

enum class TransitionTier { SMART_BEATMATCH, PLAIN_CROSSFADE, GAPLESS, HARD_CUT }

enum class TransitionStyle {
    PLAIN_CROSSFADE,
    DJ_ASSISTED,
    BEATMATCHED,
}
