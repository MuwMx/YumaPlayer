/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.audiodsp.AudioDeck
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.FALL
import moe.rukamori.archivetune.audiodsp.RISE
import moe.rukamori.archivetune.audiodsp.TransitionTier
import moe.rukamori.archivetune.audiodsp.incomingBassGainDb
import moe.rukamori.archivetune.audiodsp.incomingStageGain
import moe.rukamori.archivetune.audiodsp.outgoingBassGainDb
import moe.rukamori.archivetune.audiodsp.outgoingLowPassHz
import moe.rukamori.archivetune.audiodsp.outgoingStageGain
import timber.log.Timber

class ExoDeck(
    override val player: ExoPlayer,
    override val djFilter: DjFilterAudioProcessor,
    var partner: ExoDeck? = null,
    var isIncoming: Boolean = false,
    var baseVolume: Float = 1f,
    var maxGainFactor: Float = 1f,
) : AudioDeck {
    private var lastLoggedQuarter = -1

    override fun applyAutomation(progress: Float, plan: AutomixPlan) {
        val clamped = progress.coerceIn(0f, 1f)
        val isAutomix = plan.tier != TransitionTier.PLAIN_CROSSFADE || plan.enableBassSwap

        if (isIncoming) {
            val gain = if (isAutomix) incomingStageGain(clamped) else RISE(clamped)
            player.volume = (baseVolume * gain).coerceIn(0f, maxGainFactor)
            if (plan.enableBassSwap) {
                // No entry high-pass: a corner sweeping down to 20 Hz phase-smears the kick.
                djFilter.highPassHz = DjFilterAudioProcessor.BYPASS_HIGH_PASS_HZ
                djFilter.bassGainDb = incomingBassGainDb(clamped)
            } else {
                djFilter.clearAutomation()
            }
        } else {
            if (isAutomix) {
                // Both decks are audible during the overlap, so their gains must sum to at most unity or
                // the mix clips on the loudest transients. The reference bounds this with a fixed -6 dB
                // mid duck, but that is sized for its equal-power curves, where the sum peaks at 1.414.
                // Our staged curves overlap more broadly and reach 1.33, so the outgoing deck is capped by
                // whatever the incoming deck has already taken.
                val partnerGain = (partner?.baseVolume ?: 0f) * incomingStageGain(clamped)
                val sumCap = (1f - partnerGain).coerceIn(0f, 1f)
                player.volume = (baseVolume * outgoingStageGain(clamped))
                    .coerceIn(0f, maxGainFactor)
                    .coerceAtMost(sumCap)
            } else {
                player.volume = (baseVolume * FALL(clamped)).coerceIn(0f, maxGainFactor)
            }

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
                "automation incoming=%b p=%.2f lp=%.0f bass=%.1f",
                isIncoming,
                clamped,
                djFilter.lowPassCutoffHz,
                djFilter.bassGainDb
            )
        }
    }
    override fun clearAutomation() {
        djFilter.clearAutomation()
        lastLoggedQuarter = -1
    }
}
