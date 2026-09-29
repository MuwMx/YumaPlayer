package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.audiodsp.AudioDeck
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.FALL
import moe.rukamori.archivetune.audiodsp.RISE
import moe.rukamori.archivetune.audiodsp.incomingBassGainDb
import moe.rukamori.archivetune.audiodsp.outgoingBassGainDb
import moe.rukamori.archivetune.audiodsp.outgoingLowPassHz
import moe.rukamori.archivetune.audiodsp.outgoingMidDuckGain
import timber.log.Timber

class ExoDeck(
    override val player: ExoPlayer,
    override val djFilter: DjFilterAudioProcessor,
    var isIncoming: Boolean = false,
    var baseVolume: Float = 1f,
    var maxGainFactor: Float = 1f,
    var gainCompensation: Float = 1f,
) : AudioDeck {
    private var lastLoggedQuarter = -1

    override fun applyAutomation(progress: Float, plan: AutomixPlan) {
        val clamped = progress.coerceIn(0f, 1f)
        if (isIncoming) {
            player.volume = (baseVolume * gainCompensation * RISE(clamped)).coerceIn(0f, maxGainFactor)
            if (plan.enableBassSwap) {
                // No entry high-pass: a corner sweeping down to 20 Hz phase-smears the kick.
                djFilter.highPassHz = DjFilterAudioProcessor.BYPASS_HIGH_PASS_HZ
                djFilter.bassGainDb = incomingBassGainDb(clamped)
            } else {
                djFilter.clearAutomation()
            }
        } else {
            player.volume = (
                baseVolume * gainCompensation * FALL(clamped) * outgoingMidDuckGain(clamped).toFloat()
                ).coerceIn(0f, maxGainFactor)
            if (plan.enableBassSwap) {
                djFilter.lowPassCutoffHz = outgoingLowPassHz(clamped)
                djFilter.bassGainDb = outgoingBassGainDb(clamped)
            } else {
                djFilter.clearAutomation()
            }
        }
        val quarter = (clamped * 4f).toInt().coerceIn(0, 4)
        if (plan.enableBassSwap && quarter != lastLoggedQuarter) {
            lastLoggedQuarter = quarter
            Timber.tag("DjFilter").d(
                "automation incoming=$isIncoming p=${"%.2f".format(clamped)}" +
                    " lp=${"%.0f".format(djFilter.lowPassCutoffHz)}" +
                    " bass=${"%.1f".format(djFilter.bassGainDb)}",
            )
        }
    }

    override fun clearAutomation() {
        djFilter.clearAutomation()
        lastLoggedQuarter = -1
    }
}
