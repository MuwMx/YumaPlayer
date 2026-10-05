/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.DeckController
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.DualForwardingPlayer
import moe.rukamori.archivetune.audiodsp.DualPlayerRoleHolder
import moe.rukamori.archivetune.audiodsp.TransitionTier
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import timber.log.Timber

class ExoDeckController(
    private val service: MusicService,
    initialDeck: ExoDeck,
    val dualForwardingPlayer: DualForwardingPlayer,
) : DeckController {

    override var activeDeck: ExoDeck = initialDeck
        private set

    override var transitionDeck: ExoDeck? = null
        private set

    var reserveDeck: ExoDeck? = null
        private set

    val dualPlayerRoleHolder: DualPlayerRoleHolder = DualPlayerRoleHolder()

    var secondaryCrossfadeTarget: CrossfadeTarget? = null

    val secondaryCrossfadePlayer: ExoPlayer?
        get() = transitionDeck?.player

    val reserveCrossfadePlayer: ExoPlayer?
        get() = reserveDeck?.player

    override fun prepareNext(target: CrossfadeTarget): Boolean {
        val existingDeck = transitionDeck
        if (existingDeck != null && secondaryCrossfadeTarget == target) {
            existingDeck.player.playWhenReady = false
            val currentPos = existingDeck.player.currentPosition
            val currentIndex = existingDeck.player.currentMediaItemIndex
            if (currentIndex != target.index || currentPos > CrossfadeConstants.PRIME_MAX_DRIFT_MS) {
                existingDeck.player.seekTo(target.index, 0L)
            }
            return true
        }

        releaseTransitionDeck()

        val targetItem =
            runCatching { service.player.getMediaItemAt(target.index) }
                .getOrNull()
                ?.takeIf { it.mediaId == target.mediaId }
                ?: return false

        return runCatching {
            val (secondaryPlayer, secondaryFilter) =
                reserveDeck?.let {
                    val p = it.player
                    p.addListener(service.secondaryCrossfadeListener)
                    p.setOffloadEnabled(false)
                    p.skipSilenceEnabled = activeDeck.player.skipSilenceEnabled
                    reserveDeck = null
                    p to it.djFilter
                } ?: run {
                    val p = service.createSecondaryCrossfadePlayer()
                    val f = service.djFilterFor(p) ?: DjFilterAudioProcessor().also { filter -> service.djFilterByPlayer[p] = filter }
                    p to f
                }

            secondaryCrossfadeTarget = target
            val newTransitionDeck = ExoDeck(secondaryPlayer, secondaryFilter, isIncoming = true)
            transitionDeck = newTransitionDeck

            val queueItems =
                (0 until service.player.mediaItemCount).mapNotNull { index ->
                    runCatching { service.player.getMediaItemAt(index) }.getOrNull()
                }
            secondaryPlayer.setMediaItems(queueItems, target.index.coerceIn(0, (queueItems.size - 1).coerceAtLeast(0)), 0L)
            secondaryPlayer.playbackParameters = service.player.playbackParameters
            secondaryPlayer.volume = 0f
            secondaryPlayer.playWhenReady = false
            secondaryPlayer.pauseAtEndOfMediaItems = true
            secondaryPlayer.prepare()
            true
        }.onFailure { error ->
            Timber.tag(MusicService.TAG).w(error, "Failed to prepare crossfade player")
            releaseTransitionDeck()
        }.getOrDefault(false)
    }

    override fun primeIncoming(cueMs: Long) {
        val incomingPlayer = transitionDeck?.player ?: return
        if (cueMs > 0L && kotlin.math.abs(incomingPlayer.currentPosition - cueMs) > CrossfadeConstants.PRIME_MAX_DRIFT_MS) {
            incomingPlayer.seekTo(secondaryCrossfadeTarget?.index ?: 0, cueMs)
        }
        incomingPlayer.playWhenReady = false
    }

    override fun stopIncoming() {
        transitionDeck?.player?.playWhenReady = false
    }

    override fun startCrossfade(plan: AutomixPlan) {
        val incoming = transitionDeck ?: return
        val outgoing = activeDeck

        // Unity is the ceiling during a crossfade, not the normalisation bound: two decks sound at
        // once, so anything above unity on a single deck already overflows the pair. The reference
        // clamps to 1f here and keeps its normalisation off by default.
        outgoing.isIncoming = false
        outgoing.baseVolume = service.crossfadeBaseVolume
        outgoing.maxGainFactor = 1f

        incoming.isIncoming = true
        incoming.baseVolume = service.crossfadeIncomingBaseVolume
        incoming.maxGainFactor = 1f

        outgoing.partner = incoming
        incoming.partner = outgoing

        outgoing.player.pauseAtEndOfMediaItems = true

        outgoing.applyAutomation(0f, plan)
        incoming.applyAutomation(0f, plan)

        if (plan.tier == TransitionTier.SMART_BEATMATCH) {
            incoming.player.playbackParameters = PlaybackParameters(plan.incomingTempoRatio)
        } else {
            incoming.player.playbackParameters = service.player.playbackParameters
        }
        incoming.player.playWhenReady = service.crossfadePlaybackRequested
    }

    override fun completeHandoff() {
        val incomingDeck = transitionDeck ?: return
        val outgoingDeck = activeDeck
        val incomingPlayer = incomingDeck.player
        val outgoingPlayer = outgoingDeck.player

        outgoingPlayer.pauseAtEndOfMediaItems = false

        incomingPlayer.playWhenReady = true
        incomingPlayer.pauseAtEndOfMediaItems = false

        runCatching { incomingPlayer.removeListener(service.secondaryCrossfadeListener) }
        service.transferAudioEffects(incomingPlayer)
        incomingPlayer.setShuffleOrder(outgoingPlayer.shuffleOrder)

        dualForwardingPlayer.attachPlayer(incomingPlayer)

        activeDeck = incomingDeck
        transitionDeck = null
        secondaryCrossfadeTarget = null
        dualPlayerRoleHolder.reset()

        service.localPlayer = incomingPlayer

        outgoingPlayer.playWhenReady = false
        outgoingPlayer.volume = 0f
        outgoingPlayer.stop()
        outgoingPlayer.clearMediaItems()
        reserveDeck = outgoingDeck

        outgoingDeck.clearAutomation()
        incomingDeck.clearAutomation()
    }

    override fun cancel() {
        cancel(resetVolume = true, resetPauseAtEnd = true)
    }

    fun cancel(resetVolume: Boolean = true, resetPauseAtEnd: Boolean = true) {
        val secondary = transitionDeck
        secondary?.player?.apply {
            playWhenReady = false
            volume = 0f
            stop()
            clearMediaItems()
        }
        if (service.isPlayerInitialized()) {
            dualForwardingPlayer.attachPlayer(activeDeck.player)
        }
        activeDeck.clearAutomation()
        secondary?.clearAutomation()
        reserveDeck?.clearAutomation()
        dualPlayerRoleHolder.reset()
        if (service.isPlayerInitialized() && resetPauseAtEnd) {
            activeDeck.player.pauseAtEndOfMediaItems = false
        }
        releaseTransitionDeck()
        if (resetVolume && service.isPlayerInitialized()) {
            service.applyEffectiveVolumeImmediately()
        }
    }

    fun releaseTransitionDeck() {
        val deckToRelease = transitionDeck ?: return
        transitionDeck = null
        secondaryCrossfadeTarget = null
        deckToRelease.clearAutomation()
        val playerToRelease = deckToRelease.player
        runCatching { playerToRelease.removeListener(service.secondaryCrossfadeListener) }
        runCatching { playerToRelease.playWhenReady = false }
        runCatching { playerToRelease.volume = 0f }
        runCatching { playerToRelease.stop() }
        runCatching { playerToRelease.clearMediaItems() }
        runCatching { playerToRelease.release() }
        service.djFilterByPlayer.remove(playerToRelease)
    }

    fun releaseReserveDeck() {
        val deck = reserveDeck ?: return
        reserveDeck = null
        deck.clearAutomation()
        val player = deck.player
        runCatching { service.djFilterByPlayer.remove(player) }
        runCatching { player.release() }
    }
}
