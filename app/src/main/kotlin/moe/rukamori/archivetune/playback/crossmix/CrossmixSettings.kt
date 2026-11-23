/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.crossmix

import moe.rukamori.archivetune.constants.CrossmixLength
import moe.rukamori.archivetune.constants.CrossmixTempoMatch

/** the listener's choices for how crossmix mixes, read by the planner; the defaults are the planner left to itself */
data class CrossmixSettings(
    val length: CrossmixLength = CrossmixLength.MEDIUM,
    val tempoMatch: CrossmixTempoMatch = CrossmixTempoMatch.FULL,
    /** one style for every mix, or null for the one that fits the pair */
    val style: CrossmixStyle? = null,
    /** whether backspins, tape stops, risers and the like may be picked when the energy jumps */
    val stunts: Boolean = true,
    /** every hand-over a real blend of the two songs: no drop right at the end, no plain fade, even without a beat to lock to */
    val alwaysMix: Boolean = true,
) {
    companion object {
        val Default = CrossmixSettings()
    }
}
