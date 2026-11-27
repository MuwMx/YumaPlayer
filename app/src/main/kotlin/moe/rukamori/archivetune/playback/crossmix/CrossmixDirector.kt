/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.crossmix

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Runs Crossmix transitions: analyses the song and the next one, plans the hand-over, then plays
 * it on two players.
 *
 * The rule throughout is that a transition either happens properly or not at all. The next song
 * is prepared silently well ahead; only once it's actually ready is the outgoing side touched, and
 * if it isn't ready in time the song simply ends and the next one starts as it always did. The
 * next player starts slightly early by a measured amount and its beat is then locked to the
 * outgoing one by a small delay inside its renderer, mostly while it's still silent. Tempo is set
 * once before it starts and eased back after the overlap, never moved while both are playing,
 * because Media3 drains and resets the whole audio chain on every speed change.
 */
@UnstableApi
class CrossmixDirector(
    private val scope: CoroutineScope,
    private val host: Host,
    private val trackAnalyzer: TrackAnalyzer = TrackAnalyzer,
) {
    interface Host {
        val currentPlayer: ExoPlayer
        fun processorFor(player: Player): CrossmixTransitionAudioProcessor?
        /** a new player holding the queue, at [index] / [positionMs], paused, at [parameters], not yet prepared */
        fun createIncoming(index: Int, positionMs: Long, parameters: PlaybackParameters): ExoPlayer
        /** [incoming] becomes the session's player; [outgoing] plays on as the fading side */
        fun promote(incoming: ExoPlayer, outgoing: ExoPlayer)
        /** the outgoing side has finished: let its player go */
        fun release(outgoing: ExoPlayer)
        /** a prepared next player that won't be used after all */
        fun discard(incoming: ExoPlayer)
        /** why Crossmix can't run right now (it's off, casting, a song-end sleep timer...), or null */
        fun crossmixBlocker(): String?
        fun nextIndex(): Int
        /** the song's lyrics, fetched if they haven't been yet; synced ones say where its voice is */
        suspend fun lyrics(item: MediaItem): String?
        /** the listener's own speed and pitch, which the transition returns to */
        fun baseParameters(): PlaybackParameters
        /** two songs are playing together (true) or the outgoing one has gone (false) */
        fun onMixingChanged(mixing: Boolean)
        /** what Crossmix is doing now, in a line, for the settings screen */
        fun onStatus(status: String)
        /** the album of the song at [index], for telling continuous albums apart */
        fun albumOf(index: Int): String?
        /** the listener's choices: overlap length, tempo matching, a style of their own, stunts */
        fun settings(): CrossmixSettings
        /** how long play() takes to be heard on this phone, remembered between launches (null until the first mix taught it) */
        fun learnedStartLatencyMs(): Double?
        fun onStartLatencyLearned(ms: Double)
        fun kickOffAnalysis(item: MediaItem) {}
    }

    private var planningJob: Job? = null
    private var planningKey: String? = null
    private var armed: ExoPlayer? = null
    private var running: Running? = null
    private var promoting = false
    private var lastStatus = ""
    // the test tool's jump to just before the next planned hand-over
    private var testJump = false
    private var selfSeeking = false

    // how long play() takes to be heard, learnt from each transition's first beat-lock reading and kept across launches
    private var startLatencyMs = host.learnedStartLatencyMs() ?: DefaultStartLatencyMs

    /** the outgoing song brought up to a faster next song's tempo, and the speed it goes back to if the mix is called off */
    private class SpeedRamp(val player: ExoPlayer, val base: PlaybackParameters) {
        var lastSet = base.speed
    }

    private var speedRamp: SpeedRamp? = null

    val isTransitioning: Boolean get() = running != null

    /**
     * every decision, as a line for the settings screen and for logcat. android.util.Log at info
     * level is kept in release builds (Timber is debug-only), so it can be read off a real phone.
     */
    private fun report(status: String) {
        if (status == lastStatus) return
        lastStatus = status
        host.onStatus(status)
        android.util.Log.i(Tag, status)
    }

    private fun voiceSource(lyrics: String?, analysis: CrossmixTrackAnalysis?): String = when {
        lyrics != null && Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(lyrics) -> "synced lyrics"
        analysis?.vocal?.isNotEmpty() == true -> "the sound"
        else -> "a guess"
    }

    private fun clock(ms: Double): String {
        val total = (ms / 1_000).toLong().coerceAtLeast(0L)
        return "%d:%02d".format(total / 60, total % 60)
    }

    private class Running(
        val plan: CrossmixPlan,
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        val outProcessor: CrossmixTransitionAudioProcessor,
        val inProcessor: CrossmixTransitionAudioProcessor,
        val base: PlaybackParameters,
    ) {
        val jobs = ArrayList<Job>()
        var released = false
        var tempoSettled = false
        var lastSetSpeed = 0f
    }

    /** called whenever the playing song, its position or its state may have changed */
    fun schedule() {
        if (promoting) return
        if (running != null) return
        val player = host.currentPlayer
        val item = player.currentMediaItem ?: return cancelPending()
        host.crossmixBlocker()?.let { reason ->
            report(reason)
            return cancelPending()
        }
        val duration = player.duration
        if (duration == C.TIME_UNSET) return cancelPending()
        if (duration < MinTrackMs) {
            report("This song is too short to mix")
            return cancelPending()
        }
        val nextIndex = host.nextIndex()
        if (nextIndex == C.INDEX_UNSET) {
            report("Nothing queued after this song")
            return cancelPending()
        }
        val next = player.getMediaItemAt(nextIndex)
        val key = "${item.mediaId}→${next.mediaId}@$nextIndex"
        // decided already (planned, armed, or found it can't be mixed): only a seek or a new song
        // asks again, not every pause or buffering blip
        if (key == planningKey) return
        cancelPending()
        planningKey = key
        val jump = testJump
        testJump = false
        planningJob = scope.launch { prepareTransition(player, item, next, nextIndex, duration, jump) }
    }

    /** the listener moved the playhead: a planned transition is re-timed, a running one ends cleanly */
    fun onUserSeek() {
        if (selfSeeking) return
        if (running != null) abortRunning("seek") else { cancelPending(); schedule() }
    }

    /**
     * for trying a mix out without sitting through the song: plans this song's hand-over as
     * usual, then jumps to a few seconds before the next song gets ready for it
     */
    fun testMix() {
        if (running != null) abortRunning("test")
        cancelPending()
        testJump = true
        schedule()
        testJump = false
    }

    /** the song changed under us (a skip, a queue edit) */
    fun onItemChanged() {
        if (promoting) return
        if (running != null) abortRunning("item changed") else cancelPending()
    }

    /** the listener changed how mixes are made: a planned one is planned again their way, a running one finishes as it is */
    fun onSettingsChanged() {
        if (running != null) return
        cancelPending()
        schedule()
    }

    fun release() {
        cancelPending()
        running?.let { abortRunning("service released") }
    }

    private fun cancelPending() {
        planningJob?.cancel()
        planningJob = null
        planningKey = null
        restoreSpeed()
        armed?.let { incoming ->
            host.processorFor(incoming)?.clear()
            host.discard(incoming)
        }
        armed = null
        // the outgoing side is only ever armed right before the hand-over starts; if a transition
        // was called off after that, the song must play on untouched
        host.processorFor(host.currentPlayer)?.let { if (running == null) it.clear() }
    }

    private suspend fun getAnalysis(item: MediaItem, wholeTrack: Boolean): CrossmixTrackAnalysis? {
        val mediaId = item.mediaId.ifBlank { item.localConfiguration?.uri?.toString() } ?: return null
        var cached = trackAnalyzer.getCached(mediaId) ?: trackAnalyzer.getOrFetchCached(mediaId)
        if (cached == null) {
            host.kickOffAnalysis(item)
            val startWait = System.currentTimeMillis()
            while (cached == null && System.currentTimeMillis() - startWait < 1500L && scope.isActive) {
                delay(150)
                cached = trackAnalyzer.getCached(mediaId) ?: trackAnalyzer.getOrFetchCached(mediaId)
            }
        }
        return cached?.toCrossmixAnalysis(complete = wholeTrack)
    }

    private suspend fun prepareTransition(
        outgoing: ExoPlayer,
        item: MediaItem,
        next: MediaItem,
        nextIndex: Int,
        durationMs: Long,
        testJump: Boolean,
    ) {
        report("Listening to this song and the next one…")
        val startedAt = System.currentTimeMillis()
        // lyrics come over the network while the audio is analysed
        val outLyrics = scope.async { host.lyrics(item) }
        val inLyrics = scope.async { host.lyrics(next) }
        val outAnalysis = getAnalysis(item, wholeTrack = true)
        val inAnalysis = getAnalysis(next, wholeTrack = false)
        val took = (System.currentTimeMillis() - startedAt) / 1_000f
        android.util.Log.i(
            Tag,
            "analysed in %.1f s: this %s bpm (%.2f), next %s bpm (%.2f)".format(
                took, outAnalysis?.bpm?.let { "%.1f".format(it) } ?: "?", outAnalysis?.bpmConfidence ?: 0f,
                inAnalysis?.bpm?.let { "%.1f".format(it) } ?: "?", inAnalysis?.bpmConfidence ?: 0f,
            ),
        )

        // an album that runs straight from one track into the next is already continuous: any
        // mix would only break it, so it plays gapless as recorded
        val album = host.albumOf(outgoing.currentMediaItemIndex)
        if (album != null && album == host.albumOf(nextIndex) && outAnalysis != null && inAnalysis != null &&
            outAnalysis.complete && outAnalysis.trailingSilenceStartMs >= durationMs - SeamlessMs &&
            inAnalysis.leadingSilenceEndMs <= SeamlessMs
        ) {
            report("Continuous album: playing straight through")
            return
        }

        val outText = outLyrics.await()
        val inText = inLyrics.await()
        val settings = host.settings()
        // a few thousand candidate mixes are scored, so off the main thread
        val plan = withContext(Dispatchers.Default) {
            CrossmixPlanner.plan(
                outgoing = CrossmixTrackInput(item.mediaId, durationMs, outAnalysis, outText),
                incoming = CrossmixTrackInput(next.mediaId, 0L, inAnalysis, inText),
                settings = settings,
            )
        }
        // the listener's own speed and pitch, read before this song is touched
        val base = host.baseParameters()
        android.util.Log.i(
            Tag,
            "voices from: this ${voiceSource(outText, outAnalysis)}, next ${voiceSource(inText, inAnalysis)}; settings $settings",
        )
        report(
            "${plan.style.label} at ${clock(plan.outStartMs.toDouble())}" +
                (if (settings.style != null && plan.style == settings.style) ", your pick" else "") +
                (if (plan.beatMatched) ", beat-matched" else "") +
                (if (plan.yieldAmount > 0f) ", voices kept apart" else "") +
                (if (plan.inCueMs > 1_000) ", landing on ${clock(plan.inCueMs.toDouble())} of the next" else "")
        )
        android.util.Log.i(
            Tag,
            "plan: %s %d beats from %d ms, swap on beat %d, beat %.1f ms, lands at %d ms, start beat %.1f, speed in %.4f out %.4f, yield %.2f".format(
                plan.style.name, plan.lengthBeats, plan.outStartMs, plan.swapBeat, plan.outBeatMs, plan.inCueMs,
                plan.inStartBeat, plan.inSpeed, plan.outSpeed, plan.yieldAmount,
            ),
        )

        // the earliest moment either side does anything: the outgoing effects start at beat 0, the
        // next player may start a little before that on a silent run-up
        val firstMoveMs = plan.outMediaAt(min(plan.inStartBeat, 0f))
        // a faster next song: this one comes up to its tempo over a few beats, before the next one is even prepared
        val rampMs = if (plan.outSpeed > 1.0005f) ceil((plan.outSpeed - 1f) / RampStep) * plan.outBeatMs else 0.0
        if (testJump) {
            if (!stillCurrent(outgoing, item)) {
                android.util.Log.i(Tag, "prepareTransition: bailed out, outgoing not current during test jump")
                return
            }
            val target = (firstMoveMs - ArmLeadMs - rampMs - TestRunUpMs).toLong().coerceAtLeast(0L)
            selfSeeking = true
            try {
                outgoing.seekTo(target)
            } finally {
                selfSeeking = false
            }
            if (!outgoing.playWhenReady) outgoing.play()
            android.util.Log.i(Tag, "test: jumped to ${clock(target.toDouble())}, the mix starts at ${clock(firstMoveMs)}")
        }
        if (outgoing.currentPosition > firstMoveMs - MinArmLeadMs) {
            report("Too close to the end to mix this one")
            return
        }
        try {
            if (rampMs > 0.0) {
                if (!waitForOutgoing(outgoing, item, firstMoveMs - ArmLeadMs - rampMs)) {
                    android.util.Log.i(Tag, "prepareTransition: bailed out, waitForOutgoing before ramp failed")
                    return
                }
                host.crossmixBlocker()?.let { report(it); return }
                report("Bringing this song up to the next one's tempo…")
                if (!rampOutgoing(outgoing, item, plan, base, firstMoveMs - ArmLeadMs)) {
                    android.util.Log.i(Tag, "prepareTransition: bailed out, rampOutgoing failed")
                    return
                }
            }
            prepareAndStart(outgoing, item, nextIndex, plan, base, firstMoveMs)
        } finally {
            // called off at any point before the mix took this song over: it plays on at the listener's own speed
            if (running?.outgoing !== outgoing) restoreSpeed()
        }
    }

    /** brings the outgoing song up to the faster next one's tempo in steps too small to hear, a beat apart, finishing by untilMs */
    private suspend fun rampOutgoing(outgoing: ExoPlayer, item: MediaItem, plan: CrossmixPlan, base: PlaybackParameters, untilMs: Double): Boolean {
        val ramp = SpeedRamp(outgoing, base).also { speedRamp = it }
        val target = base.speed * plan.outSpeed
        val beatsLeft = ((untilMs - outgoing.currentPosition) / plan.outBeatMs).toInt()
        val steps = min(ceil((plan.outSpeed - 1f) / RampStep).toInt(), beatsLeft).coerceAtLeast(1)
        for (index in 0 until steps) {
            if (!stillCurrent(outgoing, item)) return false
            // the listener changed the speed themselves: theirs wins and this mix is off
            if (abs(outgoing.playbackParameters.speed - ramp.lastSet) > 0.0005f) {
                speedRamp = null
                return false
            }
            val speed = if (index == steps - 1) target else base.speed + (target - base.speed) * (index + 1) / steps
            outgoing.playbackParameters = PlaybackParameters(speed, base.pitch)
            ramp.lastSet = speed
            if (index < steps - 1 && !waitForOutgoing(outgoing, item, outgoing.currentPosition + plan.outBeatMs)) return false
        }
        return true
    }

    /** a sped-up outgoing song back to the listener's own speed, unless they've chosen another since */
    private fun restoreSpeed() {
        val ramp = speedRamp ?: return
        speedRamp = null
        runCatching {
            if (abs(ramp.player.playbackParameters.speed - ramp.lastSet) < 0.0005f) ramp.player.playbackParameters = ramp.base
        }
    }

    private suspend fun prepareAndStart(
        outgoing: ExoPlayer,
        item: MediaItem,
        nextIndex: Int,
        plan: CrossmixPlan,
        base: PlaybackParameters,
        firstMoveMs: Double,
    ) {
        if (!waitForOutgoing(outgoing, item, firstMoveMs - ArmLeadMs)) {
            android.util.Log.i(Tag, "prepareAndStart: bailed out, waitForOutgoing before arm lead failed")
            return
        }
        host.crossmixBlocker()?.let { report(it); return }
        if (outgoing.currentPosition > firstMoveMs - MinArmLeadMs) {
            report("Too close to the end to mix this one")
            return
        }
        report("Getting the next song ready for ${plan.style.label}…")

        val outProcessor = host.processorFor(outgoing)
        if (outProcessor == null || !outProcessor.canRender) {
            report("This song's audio (${outProcessor?.format ?: "no renderer"}) can't be mixed: played through")
            return
        }

        // the incoming side starts on a delay with room to move both ways, plus room for any drift
        // the delay will have to absorb when the tempos are a hair apart but not stretched
        val overlapMediaMs = (plan.lengthBeats + 8) * plan.outBeatMs
        val inRate = plan.inMediaPerBeat / plan.outBeatMs
        val drift = (inRate - plan.inSpeed / plan.outSpeed) * overlapMediaMs
        val initialDelayMs = (BaseDelayMs + max(0.0, drift)).coerceAtMost(400.0)

        val parameters = PlaybackParameters((base.speed * plan.inSpeed).coerceIn(0.25f, 4f), base.pitch)
        val incoming = runCatching { host.createIncoming(nextIndex, plan.inStartMs, parameters) }.getOrNull()
        if (incoming == null) {
            report("Couldn't open a player for the next song: played through")
            return
        }
        armed = incoming
        val inProcessor = host.processorFor(incoming)
        if (inProcessor == null) {
            android.util.Log.i(Tag, "prepareAndStart: bailed out, processorFor(incoming) is null")
            return giveUp(incoming)
        }
        // armed before it has decoded a thing: a paused player still fills its audio buffer, and
        // that audio is the first heard on play(), so it must already be the transition's (silent
        // before the next song's entry), never a blip of the next song at full volume
        inProcessor.arm(script(plan, CrossmixRole.Incoming, plan.inStartMs.toDouble(), 0L))
        inProcessor.setBeatLockDelay(initialDelayMs)
        var failed = false
        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        incoming.addListener(errorListener)
        incoming.prepare()

        // go or no-go, decided before the outgoing side is touched. READY isn't enough: a paused
        // player can be READY before its renderer has decoded anything and been set up for it
        val goDeadline = firstMoveMs - GoMarginMs
        while (incoming.playbackState != Player.STATE_READY || !inProcessor.configured) {
            if (failed || !stillCurrent(outgoing, item) || outgoing.currentPosition > goDeadline) {
                incoming.removeListener(errorListener)
                if (stillCurrent(outgoing, item)) {
                    report(
                        when {
                            failed -> "The next song failed to load: played through"
                            incoming.playbackState == Player.STATE_READY -> "The next song's audio didn't start in time: played through"
                            else -> "The next song didn't load in time: played through"
                        }
                    )
                } else {
                    android.util.Log.i(Tag, "prepareAndStart: bailed out, outgoing song no longer current")
                }
                return giveUp(incoming)
            }
            delay(40)
        }
        incoming.removeListener(errorListener)
        // both sides must be able to render the transition, or neither is touched
        if (inProcessor.unsupported) {
            report("The next song's audio (${inProcessor.format}) can't be mixed: played through")
            return giveUp(incoming)
        }
        android.util.Log.i(Tag, "next song ready: ${inProcessor.format}, this song ${outProcessor.format}")

        outProcessor.arm(script(plan, CrossmixRole.Outgoing, outgoing.currentPosition.toDouble(), System.nanoTime()))

        // start the next player early by its start latency and by the delay it begins with, both counted on this song's clock
        val playAt = plan.outMediaAt(plan.inStartBeat) - initialDelayMs * plan.outSpeed / plan.inSpeed -
            startLatencyMs * base.speed * plan.outSpeed
        if (!waitForOutgoing(outgoing, item, playAt, precise = true)) {
            android.util.Log.i(Tag, "prepareAndStart: bailed out, waitForOutgoing at playAt failed")
            // the song changed in the last moment: put everything back
            outProcessor.clear()
            return giveUp(incoming)
        }

        armed = null
        val session = Running(plan, outgoing, incoming, outProcessor, inProcessor, base)
        running = session
        // the mix owns the outgoing song now: it goes when the mix is over, at whatever speed it had
        speedRamp = null
        planningJob = null
        planningKey = null

        incoming.play()
        try {
            promoting = true
            host.promote(incoming, outgoing)
        } finally {
            promoting = false
        }
        host.onMixingChanged(true)
        report("Mixing · ${plan.style.label}")
        session.lastSetSpeed = incoming.playbackParameters.speed

        session.jobs += scope.launch { lockBeat(session) }
        session.jobs += scope.launch { releaseOutgoingWhenDone(session) }
    }

    private fun giveUp(incoming: ExoPlayer) {
        armed = null
        host.processorFor(incoming)?.clear()
        host.discard(incoming)
    }

    /**
     * keeps the next song's beat on the outgoing one for as long as they overlap: reads both
     * positions a few times a second and moves the incoming side's delay by the median error.
     * the first readings come while the next song is still silent, so the lock is in before it's heard.
     */
    private suspend fun lockBeat(session: Running) {
        val plan = session.plan
        val errors = ArrayList<Double>()
        var firstCorrection = true
        // the first reading should land inside the silent run-up, where the renderer may still jump the delay into place
        delay(250)
        while (scope.isActive && running === session) {
            val outgoing = session.outgoing
            val incoming = session.incoming
            if (session.released) break
            if (outgoing.isPlaying && incoming.isPlaying) {
                val outPos = outgoing.currentPosition
                val inPos = incoming.currentPosition
                val beat = ((outPos - plan.outStartMs) / plan.outBeatMs).toFloat()
                if (beat > plan.lengthBeats + 2f) break
                val expected = plan.inMediaAt(beat)
                val heard = inPos - session.inProcessor.currentDelayMs
                errors += heard - expected
                if (errors.size >= 5) {
                    val median = errors.sorted()[errors.size / 2]
                    errors.clear()
                    if (abs(median) < 600.0) {
                        if (firstCorrection) {
                            // a positive error means it was heard early: next time, start it later
                            startLatencyMs = (startLatencyMs - median * 0.5).coerceIn(20.0, 250.0)
                            host.onStartLatencyLearned(startLatencyMs)
                            firstCorrection = false
                        }
                        if (abs(median) > 2.0) {
                            session.inProcessor.setBeatLockDelay(session.inProcessor.currentDelayMs + median)
                        }
                    }
                }
            } else {
                errors.clear()
            }
            delay(LockIntervalMs)
        }
        // the overlap is over: ease the delay away (slowly enough to be inaudible), then the tempo
        session.inProcessor.setBeatLockDelay(0.0)
        relaxTempo(session)
    }

    /** back to the listener's own tempo in steps too small to hear, once the renderer is idle */
    private suspend fun relaxTempo(session: Running) {
        val incoming = session.incoming
        val base = session.base
        // the renderer goes idle once its delay has eased back to nothing; it can't take longer
        // than this, but if it somehow does, step it out rather than hold up every later transition
        val deadline = System.currentTimeMillis() + 90_000L
        while (scope.isActive && !session.inProcessor.idle) {
            if (incoming.currentMediaItemIndex == C.INDEX_UNSET) return
            if (System.currentTimeMillis() > deadline) {
                session.inProcessor.finish()
                delay(1_000)
                break
            }
            delay(500)
        }
        while (scope.isActive) {
            val current = incoming.playbackParameters
            // the listener changed the speed themselves: theirs wins
            if (abs(current.speed - session.lastSetSpeed) > 0.0005f) break
            val gap = base.speed - current.speed
            if (abs(gap) < 0.0005f) {
                if (current.speed != base.speed || current.pitch != base.pitch) incoming.playbackParameters = base
                break
            }
            val step = gap.coerceIn(-SpeedStep, SpeedStep)
            val nextSpeed = current.speed + step
            incoming.playbackParameters = PlaybackParameters(nextSpeed, base.pitch)
            session.lastSetSpeed = nextSpeed
            // two bars between steps
            delay((8 * session.plan.inMediaPerBeat / max(0.25f, nextSpeed)).toLong().coerceIn(1_000L, 8_000L))
        }
        session.tempoSettled = true
        finishSession(session)
    }

    private suspend fun releaseOutgoingWhenDone(session: Running) {
        val plan = session.plan
        val outgoing = session.outgoing
        while (scope.isActive && running === session && !session.released) {
            val ended = outgoing.playbackState == Player.STATE_ENDED ||
                outgoing.playbackState == Player.STATE_IDLE ||
                (!outgoing.playWhenReady && outgoing.currentPosition >= plan.outStartMs + plan.lengthBeats * plan.outBeatMs)
            if (ended || outgoing.currentPosition >= plan.outReleaseMs) break
            delay(200)
        }
        if (session.released) return
        session.outProcessor.fadeOutNow()
        delay(90)
        session.released = true
        host.release(outgoing)
        host.onMixingChanged(false)
        letIncomingThrough(session)
        report("Mixed with ${plan.style.label}")
        finishSession(session)
    }

    // the old song is gone now, so a next song still held silent by its renderer (a clock that never lined up) plays on plainly instead of staying mute until a seek
    private fun letIncomingThrough(session: Running) {
        val processor = session.inProcessor
        if (processor.idle) return
        val beat = processor.currentBeat
        if (beat.isNaN() || beat < session.plan.swapBeat) {
            android.util.Log.w(Tag, "the next song was still held silent at beat $beat when the old one ended, letting it through")
        }
        processor.finish()
    }

    /** a session is over once the outgoing player is gone and the tempo is the listener's again */
    private fun finishSession(session: Running) {
        if (running !== session) return
        if (!session.released || !session.tempoSettled) return
        running = null
        // the incoming song is the current one now; plan its own hand-over
        schedule()
    }

    private fun abortRunning(reason: String) {
        val session = running ?: return
        report("Mix stopped early ($reason)")
        running = null
        session.jobs.forEach { it.cancel() }
        host.onMixingChanged(false)
        session.inProcessor.finish()
        if (!session.released) {
            session.outProcessor.fadeOutNow()
            session.released = true
            scope.launch {
                delay(90)
                host.release(session.outgoing)
            }
        }
        // whatever plays next plays at the listener's own tempo
        val current = session.incoming.playbackParameters
        if (current.speed != session.base.speed) session.incoming.playbackParameters = session.base
        scope.launch { delay(150); schedule() }
    }

    private fun script(plan: CrossmixPlan, role: CrossmixRole, calibrationMs: Double, calibrationNanos: Long) =
        CrossmixTransitionAudioProcessor.Script(
            role = role,
            style = plan.style,
            variation = plan.variation,
            lengthBeats = plan.lengthBeats.toFloat(),
            anchorMs = if (role == CrossmixRole.Outgoing) plan.outStartMs.toDouble() else plan.inMediaAt(0f),
            mediaPerBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
            musicalBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
            endBeat = if (role == CrossmixRole.Outgoing) plan.lengthBeats + plan.style.tailBeats + 2f else plan.inSettledBeat,
            calibrationMs = calibrationMs,
            calibrationNanos = calibrationNanos,
            yieldAmount = plan.yieldAmount,
            swapBeat = plan.swapBeat.toFloat(),
        )

    private fun stillCurrent(player: ExoPlayer, item: MediaItem): Boolean =
        host.currentPlayer === player && player.currentMediaItem?.mediaId == item.mediaId

    /**
     * waits until the outgoing song reaches [targetMs] (its own clock, so pauses just wait longer).
     * false if the song changed or moved away meanwhile. [precise]: the last stretch is polled
     * every couple of ms, for the moment the next player starts.
     */
    private suspend fun waitForOutgoing(player: ExoPlayer, item: MediaItem, targetMs: Double, precise: Boolean = false): Boolean {
        while (scope.isActive) {
            if (!stillCurrent(player, item)) return false
            val remaining = targetMs - player.currentPosition
            if (remaining <= 0) return true
            val speed = player.playbackParameters.speed.coerceAtLeast(0.25f)
            val wall = remaining / speed
            delay(
                when {
                    !player.isPlaying -> 250L
                    wall > 3_000 -> min(1_000L, (wall - 2_000).toLong())
                    wall > 120 -> (wall / 3).toLong().coerceAtLeast(10L)
                    precise -> 2L
                    else -> wall.toLong().coerceAtLeast(1L)
                }
            )
        }
        return false
    }

    private companion object {
        const val Tag = "Crossmix"
        const val MinTrackMs = 45_000L
        /** the next song is prepared this far ahead of its first move */
        const val ArmLeadMs = 9_000.0
        /** too close to plan properly: let this one play through */
        const val MinArmLeadMs = 2_500.0
        /** the next song must be ready by this long before anything happens, or it's called off */
        const val GoMarginMs = 1_200.0
        /** the next song starts this far early and is delayed back: the room the lock has to pull it later or earlier */
        const val BaseDelayMs = 60.0
        const val DefaultStartLatencyMs = 70.0
        const val LockIntervalMs = 100L
        const val SpeedStep = 0.004f
        /** how much the outgoing song speeds up per beat on its way to a faster next song */
        const val RampStep = 0.005f
        /** this little silence at a track boundary or less: the two run into each other */
        const val SeamlessMs = 400L
        /** the test jump lands this long before the next song is prepared */
        const val TestRunUpMs = 4_000.0
    }
}
