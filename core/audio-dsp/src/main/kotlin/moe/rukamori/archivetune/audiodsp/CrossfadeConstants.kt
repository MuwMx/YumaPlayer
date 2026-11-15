package moe.rukamori.archivetune.audiodsp

object CrossfadeConstants {
    const val MIN_FADE_MS = 500L
    const val END_GUARD_MS = 150L
    const val DEFAULT_MS = 2000L

    // Must exceed the standby load control's min-buffer-before-start, otherwise the deck is still
    // buffering when the fade opens and the incoming track arrives a beat late.
    const val PRIME_LEAD_MS = 3000L
    const val PRIME_MAX_DRIFT_MS = 40L
    const val CLAMP_MIN_S = 1
    const val CLAMP_MAX_S = 20
    const val MS_PER_SECOND = 1000L
    const val CLAMP_MIN_MS = CLAMP_MIN_S * MS_PER_SECOND
    const val CLAMP_MAX_MS = CLAMP_MAX_S * MS_PER_SECOND

    // The BPM-less fallback lead before the analyzer has measured anything. Kept at the historical
    // auto ceiling so widening the user-facing clamp (CLAMP_MAX_S) does not silently move the
    // trigger point of every unanalysed transition; a wider lead here would start fades 8s earlier
    // on tracks where the analyzer never produces a BPM.
    const val AUTO_FALLBACK_LEAD_S = 12.0

    const val SOFT_MIN_S = 6.0
    const val SOFT_MAX_S = 14.0
    const val STANDARD_MIN_S = 4.0

    // Must stay equal to CLAMP_MAX_S: STANDARD is the aggressiveness every runtime caller
    // (scheduler, seekbar marker) plans with, so it tracks the user-facing ceiling.
    const val STANDARD_MAX_S = 20.0
    const val CLUB_MIN_S = 3.0
    const val CLUB_MAX_S = 8.0

    enum class Aggressiveness(
        val minSeconds: Double,
        val maxSeconds: Double,
    ) {
        SOFT(SOFT_MIN_S, SOFT_MAX_S),
        STANDARD(STANDARD_MIN_S, STANDARD_MAX_S),
        CLUB(CLUB_MIN_S, CLUB_MAX_S);

        companion object {
            fun fromString(value: String): Aggressiveness = when (value.lowercase()) {
                "soft" -> SOFT
                "club" -> CLUB
                else -> STANDARD
            }
        }
    }
}

typealias Aggressiveness = CrossfadeConstants.Aggressiveness
