package moe.rukamori.archivetune.playback.automix

data class AutomixPlan(
    val triggerOffsetMs: Long,
    val durationMs: Long,
    val incomingStartMs: Long,
    val enableBassSwap: Boolean,
    val triggerAtMs: Long? = null,
    val prepareAheadMs: Long? = null,
)

enum class TransitionStyle {
    PLAIN_CROSSFADE,
    DJ_ASSISTED,
    BEATMATCHED,
}
