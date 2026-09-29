package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.audiodsp.AudioDeck
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.FALL
import moe.rukamori.archivetune.audiodsp.RISE
import moe.rukamori.archivetune.audiodsp.incomingBassGainDb
import moe.rukamori.archivetune.audiodsp.incomingHighPassHz
import moe.rukamori.archivetune.audiodsp.outgoingLowPassHz
import timber.log.Timber

class ExoDeck(
    override val player: ExoPlayer,
    override val djFilter: DjFilterAudioProcessor,
    var isIncoming: Boolean = false,
    var baseVolume: Float = 1f,
    var maxGainFactor: Float = 1f,
) : AudioDeck {
    private var lastLoggedQuarter = -1

    override fun applyAutomation(progress: Float, plan: AutomixPlan) {
        val clamped = progress.coerceIn(0f, 1f)
        val quarter = (clamped * 4f).toInt().coerceIn(0, 4)
        if (plan.enableBassSwap && quarter != lastLoggedQuarter) {
            lastLoggedQuarter = quarter
            Timber.tag("DjFilter").d("automation incoming=$isIncoming p=${"%.2f".format(clamped)} cutoff=${"%.0f".format(djFilter.lowPassCutoffHz)} bass=${"%.1f".format(djFilter.bassGainDb)}")
        }
        if (isIncoming) {
            player.volume = (baseVolume * RISE(clamped)).coerceIn(0f, maxGainFactor)
            if (plan.enableBassSwap) {
                djFilter.lowPassCutoffHz = DjFilterAudioProcessor.BYPASS_CUTOFF_HZ
                djFilter.highPassHz = incomingHighPassHz(clamped)
                djFilter.bassGainDb = incomingBassGainDb(clamped)
            } else {
                djFilter.clearAutomation()
            }
        } else {
            player.volume = (baseVolume * FALL(clamped)).coerceIn(0f, maxGainFactor)
            if (plan.enableBassSwap) {
                djFilter.lowPassCutoffHz = outgoingLowPassHz(clamped)
            } else {
                djFilter.clearAutomation()
            }
        }
    }

    override fun clearAutomation() {
        djFilter.clearAutomation()
        lastLoggedQuarter = -1
    }
}
