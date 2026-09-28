package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.audiodsp.AudioDeck
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.FALL
import moe.rukamori.archivetune.audiodsp.RISE
import kotlin.math.pow

class ExoDeck(
    override val player: ExoPlayer,
    override val djFilter: DjFilterAudioProcessor,
    var isIncoming: Boolean = false,
    var baseVolume: Float = 1f,
    var maxGainFactor: Float = 1f,
) : AudioDeck {

    override fun applyAutomation(progress: Float, plan: AutomixPlan) {
        val clamped = progress.coerceIn(0f, 1f)
        if (isIncoming) {
            player.volume = (baseVolume * RISE(clamped)).coerceIn(0f, maxGainFactor)
            if (plan.enableBassSwap) {
                djFilter.lowPassCutoffHz = DjFilterAudioProcessor.BYPASS_CUTOFF_HZ
                djFilter.bassGainDb = DjFilterAudioProcessor.FULL_CUT_DB +
                    (-DjFilterAudioProcessor.FULL_CUT_DB) * ((clamped - 0.5f) / 0.5f).coerceIn(0f, 1f).toDouble()
            } else {
                djFilter.clearAutomation()
            }
        } else {
            player.volume = (baseVolume * FALL(clamped)).coerceIn(0f, maxGainFactor)
            if (plan.enableBassSwap) {
                djFilter.lowPassCutoffHz = DjFilterAudioProcessor.BYPASS_CUTOFF_HZ *
                    (DjFilterAudioProcessor.SWEEP_TARGET_HZ / DjFilterAudioProcessor.BYPASS_CUTOFF_HZ)
                        .pow(clamped.toDouble())
                djFilter.bassGainDb = DjFilterAudioProcessor.FULL_CUT_DB * (clamped * 2f).coerceIn(0f, 1f).toDouble()
            } else {
                djFilter.clearAutomation()
            }
        }
    }

    override fun clearAutomation() {
        djFilter.clearAutomation()
    }
}
