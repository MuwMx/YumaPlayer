/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.crossmix

import moe.rukamori.archivetune.constants.CrossmixLength
import moe.rukamori.archivetune.lyrics.parsing.LrcParser
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** one planned hand-over on a clock counted in the outgoing song's beats: beat 0 is outStartMs, the bass changes hands and the next song's landing (inCueMs) is heard at swapBeat, and the outgoing song is gone by lengthBeats */
data class CrossmixPlan(
    val style: CrossmixStyle,
    val variation: Int,
    val lengthBeats: Int,
    val swapBeat: Int,
    val outStartMs: Long,
    val outBeatMs: Double,
    /** where the next song lands, its drop or its first beat, heard at swapBeat */
    val inCueMs: Long,
    /** incoming media ms per outgoing beat: its own beat length when the tempos are matched */
    val inMediaPerBeat: Double,
    /** the incoming song's speed during the mix, against the listener's own (1 when it isn't the slower one) */
    val inSpeed: Float,
    /** the outgoing song's speed from just before the mix, against the listener's own: above 1 only when the next song is the faster one */
    val outSpeed: Float = 1f,
    val beatMatched: Boolean,
    /** the transition beat the incoming player starts on (silently, if before it's heard) */
    val inStartBeat: Float,
    /** 0..1 how much the two voices would overlap, and so how far they're given bands of their own */
    val yieldAmount: Float = 0f,
) {
    /** incoming media time heard at a transition beat */
    fun inMediaAt(beat: Float): Double = inCueMs + (beat - swapBeat) * inMediaPerBeat

    /** where the incoming player starts */
    val inStartMs: Long get() = inMediaAt(inStartBeat).toLong().coerceAtLeast(0L)

    /** outgoing media time of a transition beat */
    fun outMediaAt(beat: Float): Double = outStartMs + beat * outBeatMs

    /** the outgoing player can be let go after this (tails included) */
    val outReleaseMs: Long get() = outMediaAt(lengthBeats + style.tailBeats + 1f).toLong()

    /** after this beat the incoming song is untouched */
    val inSettledBeat: Float get() = lengthBeats + 8f
}

/** what the planner sees of each song; durationMs 0 when it isn't known yet */
data class CrossmixTrackInput(
    val mediaId: String,
    val durationMs: Long,
    val analysis: CrossmixTrackAnalysis?,
    val lyrics: String?,
)

object CrossmixPlanner {
    /** the hand-over may start this long before the outgoing song's end, and no earlier */
    const val MaxLeadMs = 30_000L
    /** the next song may be entered this far in, and no further */
    const val MaxEntryMs = 120_000L

    /** score lost per beat of two voices clearly heard at once: enough to outweigh any style's fit for a few beats of it */
    private const val ClashPerBeat = 0.35f
    // giving the voices bands of their own changes how a blend sounds: a shorter blend or another spot should win first
    private const val YieldCost = 0.3f
    /** an overlap runs at most this long, two calm beat-matched songs a little longer, two that can't lock together shorter */
    private const val MaxOverlapMs = 16_000.0
    private const val MaxCalmOverlapMs = 24_000.0
    private const val MaxUnmatchedOverlapMs = 8_500.0
    // and at least this long, longer for a fast count: a slow song is often counted in double time, and eight of those beats are over in a blink
    private const val MinOverlapMs = 3_500.0
    private const val MinFastOverlapMs = 6_000.0
    /** seconds of real music the outgoing song may leave unplayed by handing over early */
    private const val MaxSkippedMusicS = 12f
    /** a change in energy at least this big between the two songs is what the stunt styles are kept for */
    private const val SharpEnergyChange = 0.3f
    private const val CalmEnergy = 0.45f
    // the nudge a pair's own move gets among the unmatched ones, so close calls differ from pair to pair but never between plays
    private const val GestureBonus = 0.3f
    // with always mix on, a blend is never over in a blink, so it's heard as two songs mixing and not as a quick fade
    private const val AlwaysMixMinOverlapMs = 6_000.0

    private class PlanningContext(
        val settings: CrossmixSettings,
        val out: CrossmixTrackAnalysis,
        val inc: CrossmixTrackAnalysis?,
        val outGrid: LocalGrid,
        val tempo: Tempo?,
        val keyDistance: Int?,
        val keyClash: Boolean,
        val outLines: List<Line>,
        val bars: LongArray,
        val exits: List<Exit>,
        val outVoice: Voice,
        val inVoice: Voice,
        val vocalPair: Float,
        val calmMatch: Boolean,
        val naturalEnd: Long,
        val windowStart: Long,
        val latestLanding: Long,
        val gesture: CrossmixStyle,
        val variation: Int,
        val scratchOut: FxParams = FxParams(),
        val scratchIn: FxParams = FxParams(),
    )

    private data class Candidate(val plan: CrossmixPlan, val score: Float)

    /** the one transition that suits these two songs best, within the listener's settings; the same two songs always get the same mix */
    fun plan(
        outgoing: CrossmixTrackInput,
        incoming: CrossmixTrackInput,
        settings: CrossmixSettings = CrossmixSettings.Default,
    ): CrossmixPlan {
        val out = outgoing.analysis
        val inc = incoming.analysis
        val naturalEnd = naturalEnd(outgoing)
        val windowStart = (outgoing.durationMs - MaxLeadMs).coerceAtLeast(0L)

        // nothing to plan around: a plain fade, or with always mix on, a real blend counted in seconds
        fun noPlan() = if (settings.alwaysMix) {
            forcedBlend(outgoing, incoming, naturalEnd, out, settings.style)
        } else {
            smoothFade(outgoing, incoming, naturalEnd, settings.style)
        }
        if (out == null || !out.isBeatReliable) return noPlan()
        val outGrid = out.localGrid((naturalEnd - 12_000L).coerceAtLeast(0L)) ?: return noPlan()

        val tempo = resolveTempo(out, inc, settings)
        val keyDistance = Harmony.camelotDistance(out.key, inc?.key)?.takeIf {
            out.keyConfidence > 0.3f && (inc?.keyConfidence ?: 0f) > 0.3f
        }
        val outLines = parseLines(outgoing.lyrics)
        val inLines = parseLines(incoming.lyrics)
        val bars = out.barStartsMs()
        val outVoice = Voice(outLines, out.vocal)
        val inVoice = Voice(inLines, inc?.vocal)
        val ctx = PlanningContext(
            settings = settings,
            out = out,
            inc = inc,
            outGrid = outGrid,
            tempo = tempo,
            keyDistance = keyDistance,
            keyClash = keyDistance != null && keyDistance >= 3,
            outLines = outLines,
            bars = bars,
            exits = exitPoints(out, bars, outGrid, windowStart, naturalEnd),
            outVoice = outVoice,
            inVoice = inVoice,
            // how much singing there is where these two would meet: the end of this one, the opening of the next
            vocalPair = outVoice.average(windowStart.toDouble(), naturalEnd.toDouble()) *
                inVoice.average(0.0, 60_000.0),
            calmMatch = tempo != null && max(out.vibe.energy, inc?.vibe?.energy ?: 0.5f) < CalmEnergy,
            naturalEnd = naturalEnd,
            windowStart = windowStart,
            // the next song lands no deeper than two minutes in, nor past a quarter of it
            latestLanding = if (incoming.durationMs > 0L) min(MaxEntryMs, incoming.durationMs / 4) else MaxEntryMs,
            gesture = pairGesture(out, inc),
            // variants differ in how many beats a spin, stop or echo takes: quick tempos use the longer count so both last about as long in seconds
            variation = if (60_000.0 / outGrid.periodMs >= 118.0) 1 else 0,
        )

        return resolveStyle(ctx) ?: noPlan()
    }

    private fun resolveTempo(
        out: CrossmixTrackAnalysis,
        inc: CrossmixTrackAnalysis?,
        settings: CrossmixSettings,
    ): Tempo? = tempoFactor(out, inc, settings.tempoMatch.maxSpeedUp)

    private fun resolveStyle(ctx: PlanningContext): CrossmixPlan? {
        val candidates = ArrayList<Candidate>()
        // the listener's own style first; when it can't play here (it needs a beat match these two don't have), the planner picks as usual
        ctx.settings.style?.let { evaluateStyle(ctx, it, picked = true, candidates = candidates) }
        if (candidates.isEmpty()) {
            for (style in CrossmixStyle.entries) {
                if (style.use == StyleUse.Flavour) continue
                if (!ctx.settings.stunts && style.use != StyleUse.Everyday) continue
                evaluateStyle(ctx, style, picked = false, candidates = candidates)
            }
        }
        if (candidates.isEmpty()) return null
        // best fit wins, a dead heat goes to the longer then earlier-listed style so the pick never depends on search order
        return candidates.maxWith(
            compareBy<Candidate> { it.score }
                .thenBy { it.plan.lengthBeats }
                .thenByDescending { it.plan.style.ordinal }
                .thenBy { it.plan.outStartMs }
        ).plan
    }

    /** every way this style could play between these two songs; a style the listener picked by hand is never scored out */
    private fun evaluateStyle(
        ctx: PlanningContext,
        style: CrossmixStyle,
        picked: Boolean,
        candidates: MutableList<Candidate>,
    ) {
        val tempo = ctx.tempo
        if (!picked && style.needsBeatMatch && tempo == null) return
        // always mix keeps to the styles where both songs really play together; a style picked by hand still wins
        if (ctx.settings.alwaysMix && !picked && !style.blendsBoth) return
        var styleFit = styleFit(style, ctx.out, ctx.inc, tempo != null, ctx.keyDistance, ctx.outLines, ctx.naturalEnd, ctx.vocalPair)
        if (picked) styleFit = max(styleFit, 0f) else if (styleFit <= -5f) return
        val bonus = if (tempo == null && style == ctx.gesture) GestureBonus else 0f
        // overlapping styles lock the beat when tempos allow, hand-over styles only need the next downbeat on the outgoing one's
        val locked = tempo != null && (style.needsBeatMatch || !style.entersAtEnd)
        val maxSpeedUp = ctx.settings.tempoMatch.maxSpeedUp
        for (length in style.lengths) {
            // overlaps stay short: long ones only for two calm songs that lock together, or when the listener asked for long
            if (!style.entersAtEnd && !overlapFits(length, length * ctx.outGrid.periodMs, tempo != null, ctx.calmMatch, ctx.settings)) continue
            val swap = style.swapAt(length)
            for (exit in ctx.exits) {
                // the hand-over sits on one of this song's bars, counted on the beats fitted around it, and the whole move fits in its last 30 s
                val period = exit.grid.periodMs
                val start = (exit.swapMs - swap * period).toLong()
                val end = (start + length * period).toLong()
                if (start < ctx.windowStart || end > ctx.naturalEnd - 150L) continue
                val exitScore = exitScore(ctx.out, ctx.bars, exit.grid, exit.swapMs, end, ctx.naturalEnd, ctx.outLines, style) ?: continue
                val landing = pickLanding(
                    style, ctx.out, ctx.inc, exit.swapMs, swap, period, tempo, locked,
                    ctx.outVoice, ctx.inVoice, ctx.latestLanding,
                ) ?: continue
                val match = if (locked) beatMatch(ctx.inc!!, landing.timeMs, tempo!!, period, maxSpeedUp) else null
                if (!picked && style.needsBeatMatch && match == null) continue
                val inBeat = match?.inBeatMs ?: period
                // heard from the start of a blend, or from the hand-over for a drop; up to a bar of silent run-up before that, so the beat is locked before anyone hears it
                val heardFrom = if (style.entersAtEnd) length.toFloat() else 0f
                val heardMs = landing.timeMs + (heardFrom - swap) * inBeat
                if (heardMs < -1.0) continue
                val preRoll = min(4f, (heardMs / inBeat).toFloat()).coerceAtLeast(0f)
                val plan = CrossmixPlan(
                    style = style,
                    variation = ctx.variation,
                    lengthBeats = length,
                    swapBeat = swap,
                    outStartMs = start,
                    outBeatMs = period,
                    inCueMs = landing.timeMs,
                    inMediaPerBeat = inBeat,
                    inSpeed = match?.inSpeed ?: 1f,
                    outSpeed = match?.outSpeed ?: 1f,
                    beatMatched = match != null,
                    inStartBeat = heardFrom - preRoll,
                )
                val lengthFit = lengthFit(style, length, ctx.out, ctx.inc, ctx.keyClash)
                // two voices at once is the one thing a mix must never do: a clash scores a blend down so a shorter one or another spot wins, and failing that the voices get bands of their own
                var chosen = plan
                val found = vocalClash(plan, ctx.outVoice, ctx.inVoice, ctx.scratchOut, ctx.scratchIn)
                var clash = found.beats * ClashPerBeat
                if (clash > 0.08f && !style.entersAtEnd && style != CrossmixStyle.FarAway && style != CrossmixStyle.Dampen) {
                    val yielding = plan.copy(yieldAmount = (found.sharedShare / 0.5f).coerceIn(0.35f, 1f))
                    val yieldClash = vocalClash(yielding, ctx.outVoice, ctx.inVoice, ctx.scratchOut, ctx.scratchIn).beats * ClashPerBeat + YieldCost
                    if (yieldClash < clash) {
                        chosen = yielding
                        clash = yieldClash
                    }
                }
                candidates += Candidate(chosen, styleFit + bonus + exitScore + landing.score + lengthFit - clash)
            }
        }
    }

    /** where a song's voice is, 0..1: from synced lyrics if it has them, else the analyser's lead curve (a good guess), else a middling guess */
    private class Voice(private val lines: List<Line>, private val curve: FloatArray?) {
        fun at(ms: Double): Float {
            if (lines.isNotEmpty()) return if (lines.any { ms >= it.start - 200 && ms <= it.end }) 1f else 0f
            val values = curve?.takeIf { it.isNotEmpty() } ?: return 0.35f
            val second = (ms / 1_000.0).toInt()
            return if (second in values.indices) values[second] * 0.85f else 0f
        }

        fun average(fromMs: Double, toMs: Double): Float {
            if (toMs <= fromMs) return at(fromMs)
            var sum = 0f
            var count = 0
            var t = fromMs
            while (t <= toMs) { sum += at(t); count++; t += 500.0 }
            return sum / max(1, count)
        }
    }

    /** how clearly a voice comes through these settings: level, band and words */
    private fun clarity(p: FxParams): Float {
        // the consonants that make words live above ~2 khz; a low-pass under 400 hz leaves a hum
        val words = (ln(p.highCutHz / 400f) / ln(3_500f / 400f)).coerceIn(0f, 1f)
        // a high-pass leaves a voice alone until it starts cutting into its body
        val body = 1f - (ln(max(p.lowCutHz, 20f) / 900f) / ln(3_200f / 900f)).coerceIn(0f, 1f)
        return (p.gain * p.midBand.coerceIn(0f, 1.2f) * words * body * (1f - 0.5f * p.gateDepth)).coerceAtLeast(0f)
    }

    /** beats: two voices both clearly heard at once, through the transition as it would play. sharedShare: how much of the overlap both songs sing, whatever the style does */
    private class Clash(val beats: Float, val sharedShare: Float)

    private fun vocalClash(plan: CrossmixPlan, outVoice: Voice, inVoice: Voice, a: FxParams, b: FxParams): Clash {
        val style = plan.style
        val length = plan.lengthBeats.toFloat()
        val swap = plan.swapBeat.toFloat()
        val last = length + style.tailBeats
        var clash = 0f
        var shared = 0
        var beat = 0f
        while (beat <= last) {
            style.evaluate(CrossmixRole.Outgoing, beat, length, swap, plan.variation, a)
            style.evaluate(CrossmixRole.Incoming, beat, length, swap, plan.variation, b)
            if (plan.yieldAmount > 0f) {
                style.yieldVocals(CrossmixRole.Outgoing, beat, length, swap, plan.yieldAmount, a)
                style.yieldVocals(CrossmixRole.Incoming, beat, length, swap, plan.yieldAmount, b)
            }
            val outSings = outVoice.at(plan.outMediaAt(beat))
            val inSings = inVoice.at(plan.inMediaAt(beat))
            if (beat < length && outSings > 0.5f && inSings > 0.5f) shared++
            val both = clarity(a) * clarity(b)
            if (both > 0.02f) clash += both * outSings * inSings
            beat += 1f
        }
        return Clash(clash, shared / length.coerceAtLeast(1f))
    }

    /** how much two songs could pass for one piece (tempo, keys, energy, groove): 0 nothing alike, 1 seamless, a high score asks for a long beat-matched blend */
    internal fun continuity(out: CrossmixTrackAnalysis, inc: CrossmixTrackAnalysis?, beatMatchable: Boolean, keyDistance: Int?): Float {
        if (inc == null || !beatMatchable) return 0f
        val energy = 1f - (abs(inc.vibe.energy - out.vibe.energy) / 0.35f).coerceIn(0f, 1f)
        val groove = min(out.vibe.danceability, inc.vibe.danceability).coerceIn(0f, 1f)
        val key = when {
            keyDistance == null -> 0.6f
            keyDistance <= 1 -> 1f
            keyDistance == 2 -> 0.5f
            else -> 0f
        }
        val bass = 1f - abs(inc.vibe.bassWeight - out.vibe.bassWeight).coerceIn(0f, 1f)
        return (energy * 0.35f + groove * 0.3f + key * 0.25f + bass * 0.1f).coerceIn(0f, 1f)
    }

    /** how the next song's beats count against this one's: 1 same, 2 its beats are twice as fast */
    private class Tempo(val factor: Float, val inBeatMs: Double)

    private data class Match(val inBeatMs: Double, val inSpeed: Float, val outSpeed: Float)

    /** whether the tempos can be laid on each other: the same, or close enough that speeding the slower one up by maxSpeedUp or less meets them, allowing half and double time */
    private fun tempoFactor(out: CrossmixTrackAnalysis, inc: CrossmixTrackAnalysis?, maxSpeedUp: Float): Tempo? {
        if (inc == null || !inc.isBeatReliable) return null
        val outBpm = out.bpm ?: return null
        val inBpm = inc.bpm ?: return null
        for (factor in floatArrayOf(1f, 2f, 0.5f)) {
            val ratio = outBpm / (inBpm * factor)
            if (ratio in 1f / (1f + maxSpeedUp)..(1f + maxSpeedUp)) return Tempo(factor, 60_000.0 / (inBpm * factor))
        }
        return null
    }

    /** beat length at the landing from the locally fitted beats (the song-wide tempo drifts a long blend), and the speeds that lay it on the outgoing beat: the slower song is sped up, nothing is slowed */
    private fun beatMatch(inc: CrossmixTrackAnalysis, landingMs: Long, tempo: Tempo, outBeatMs: Double, maxSpeedUp: Float): Match? {
        val local = inc.localGrid(landingMs)?.periodMs ?: return null
        // the grid counts the song's own beats; half or double time counts them in pairs or halves
        val inBeat = local / tempo.factor
        // incoming media per outgoing media: above 1 the next song is the slower one
        val ratio = (inBeat / outBeatMs).toFloat()
        // within 0.3% nothing is stretched, the delay line keeps the two locked and that sounds cleaner than a time-stretch
        if (abs(ratio - 1f) < 0.003f) return Match(inBeat, 1f, 1f)
        if (ratio > 1f) return if (ratio - 1f > maxSpeedUp) null else Match(inBeat, ratio, 1f)
        val outUp = 1f / ratio
        return if (outUp - 1f > maxSpeedUp) null else Match(inBeat, 1f, outUp)
    }

    /** the unmatched move this pair leans to, the same every time the two meet: picked from their tempos and keys, never their ids */
    private fun pairGesture(out: CrossmixTrackAnalysis, inc: CrossmixTrackAnalysis?): CrossmixStyle {
        var hash = 17
        hash = hash * 31 + ((out.bpm ?: 0f) * 10f).roundToInt()
        hash = hash * 31 + ((inc?.bpm ?: 0f) * 10f).roundToInt()
        hash = hash * 31 + (out.key?.hashCode() ?: 0)
        hash = hash * 31 + (inc?.key?.hashCode() ?: 0)
        return UnmatchedGestures[Math.floorMod(hash, UnmatchedGestures.size)]
    }

    private val UnmatchedGestures = listOf(CrossmixStyle.FilterSweep, CrossmixStyle.SilkBlend, CrossmixStyle.EchoOut)

    /** how well a style suits these two songs; mostly beat and energy, a little lyrics */
    private fun styleFit(
        style: CrossmixStyle,
        out: CrossmixTrackAnalysis,
        inc: CrossmixTrackAnalysis?,
        beatMatchable: Boolean,
        keyDistance: Int?,
        outLines: List<Line>,
        naturalEnd: Long,
        vocalPair: Float,
    ): Float {
        val outEnergy = out.energyAround(naturalEnd - 10_000L, 16_000L)
        val inEnergy = inc?.vibe?.energy ?: 0.5f
        val delta = inEnergy - out.vibe.energy
        // the stunts only ever mark a sharp change in energy, never an everyday hand-over
        if (style.use == StyleUse.Lift && delta < SharpEnergyChange) return -10f
        if (style.use == StyleUse.Fall && -delta < SharpEnergyChange) return -10f
        val dance = min(out.vibe.danceability, inc?.vibe?.danceability ?: 0.4f)
        val calm = 1f - max(out.vibe.energy, inEnergy)
        val clash = keyDistance != null && keyDistance >= 3
        val harmonic = keyDistance != null && keyDistance <= 1
        val vocalsNearEnd = outLines.any { it.end > naturalEnd - 8_000L }
        val outro = outEnergy < 0.45f
        val overlaps = !style.entersAtEnd
        val continuous = continuity(out, inc, beatMatchable, keyDistance)

        var score = when (style) {
            // the bass hand-off: the everyday move for two songs that don't lock together
            CrossmixStyle.SilkBlend -> 0.55f + dance * 0.3f + calm * 0.2f + (if (!beatMatchable) 0.25f else 0f)
            CrossmixStyle.BassSwap -> 0.4f + dance * 1.4f + out.vibe.bassWeight * 0.4f
            // the filter ride: also what clashing keys get, the filters keep them apart
            CrossmixStyle.FilterSweep -> 0.5f + dance * 0.2f + (if (!beatMatchable) 0.3f else 0f) + (if (clash) 0.3f else 0f)
            CrossmixStyle.EchoOut -> 0.38f + (if (!beatMatchable) 0.3f else 0f) + max(0f, -delta) * 0.4f + (if (vocalsNearEnd) 0.15f else 0f)
            CrossmixStyle.VocalHandoff -> 0.1f + (if (vocalsNearEnd) 0.9f else -0.6f) + (if (!beatMatchable) 0.3f else 0f)
            CrossmixStyle.DropCut -> 0.1f + dance * 0.9f + inEnergy * 0.5f + max(0f, delta) * 0.4f
            CrossmixStyle.HarmonicGlide -> 0.2f + (if (harmonic) 1.1f else -0.8f) + dance * 0.3f
            // the distance styles are for two voices that would really meet, the further the more dreamy and slow
            CrossmixStyle.FarAway -> 0.05f + vocalPair * 1.5f + calm * 0.5f + (1f - dance) * 0.2f
            CrossmixStyle.Dampen -> 0.05f + vocalPair * 1.4f + dance * 0.1f
            CrossmixStyle.HighPassLift -> 0.3f + delta * 1.4f + dance * 0.3f
            CrossmixStyle.Backspin -> 0.2f + out.vibe.energy * 0.5f + delta * 0.6f
            CrossmixStyle.LoopRoll -> 0.2f + dance * 0.9f + delta * 0.6f
            CrossmixStyle.RiserBuild -> 0.1f + delta * 1.6f + dance * 0.4f
            CrossmixStyle.ThunderDrop -> 0.1f + delta * 1.3f + out.vibe.bassWeight * 0.4f
            CrossmixStyle.TapeStop -> 0.2f - delta * 0.6f + out.vibe.energy * 0.3f
            CrossmixStyle.PowerDown -> 0.15f - delta * 0.9f + (if (out.vibe.minor) 0.2f else 0f)
            CrossmixStyle.Downlifter -> 0.15f - delta * 1.3f
            // flavour styles are never picked by the planner
            else -> -10f
        }
        // two songs in clashing keys shouldn't sit on top of each other for long
        if (overlaps && clash && style != CrossmixStyle.FilterSweep) score -= 0.6f
        // a quiet outro wants a gentle hand-over, not a stunt
        if (outro && style.entersAtEnd && style != CrossmixStyle.VocalHandoff && style != CrossmixStyle.EchoOut) score -= 0.3f
        // songs that belong together run into each other with the beat never stopping, the further apart they are the more a mix needs an effect
        score += when (style) {
            CrossmixStyle.BassSwap, CrossmixStyle.HarmonicGlide -> continuous * 1.6f
            CrossmixStyle.SilkBlend -> continuous * 1.0f
            CrossmixStyle.FilterSweep -> continuous * 0.5f
            CrossmixStyle.Backspin, CrossmixStyle.TapeStop, CrossmixStyle.PowerDown, CrossmixStyle.DropCut,
            CrossmixStyle.ThunderDrop, CrossmixStyle.LoopRoll, CrossmixStyle.RiserBuild, CrossmixStyle.HighPassLift,
            CrossmixStyle.Downlifter -> -continuous * 1.3f
            else -> -continuous * 0.4f
        }
        return score
    }

    /** whether a blend this long suits the pair: long only for calm songs that lock together, short for two that can't, never over in a blink; the listener's length setting stretches or shrinks the caps */
    private fun overlapFits(length: Int, lengthMs: Double, matched: Boolean, calmMatch: Boolean, settings: CrossmixSettings): Boolean {
        val setting = settings.length
        val longest = when {
            !matched -> MaxUnmatchedOverlapMs
            calmMatch -> MaxCalmOverlapMs
            else -> MaxOverlapMs
        } * setting.scale
        val counted = if (60_000.0 * length / lengthMs >= 140.0) MinFastOverlapMs else MinOverlapMs
        val shortest = if (settings.alwaysMix) max(counted, AlwaysMixMinOverlapMs) else counted
        // a short setting can cap below always mix's floor: it still gets a little room above it
        return lengthMs in shortest..max(shortest * 1.35, longest) && (length < 32 || calmMatch || setting == CrossmixLength.LONG)
    }

    /** a place the outgoing song can hand over, on the beats fitted around it */
    private class Exit(val swapMs: Long, val grid: LocalGrid)

    /** where the outgoing song can hand over: its bars near the end, each snapped to the grid fitted around it (steadier than the one detected beat), or every bar of the end grid where the tracker lost the beat */
    private fun exitPoints(out: CrossmixTrackAnalysis, bars: LongArray, grid: LocalGrid, fromMs: Long, toMs: Long): List<Exit> {
        val inWindow = bars.filter { it in fromMs..toMs }
        if (inWindow.isNotEmpty()) {
            return inWindow.map { bar ->
                val local = out.localGrid(bar) ?: grid
                Exit(local.snap(bar), local)
            }
        }
        val points = ArrayList<Exit>()
        var t = grid.snap(fromMs).toDouble()
        while (t <= toMs) {
            if (t >= fromMs) points += Exit(t.toLong(), grid)
            t += 4 * grid.periodMs
        }
        return points
    }

    /** how good a hand-over this is: on a phrase, where this song's music lets go, not mid-line, and not cutting more than a short tail; null when it would cut the song short */
    private fun exitScore(
        out: CrossmixTrackAnalysis,
        bars: LongArray,
        grid: LocalGrid,
        swapMs: Long,
        endMs: Long,
        naturalEnd: Long,
        lines: List<Line>,
        style: CrossmixStyle,
    ): Float? {
        // the music left unplayed after the outgoing song is gone: a short tail is natural, more is cutting the song short
        val skipped = out.audibleSeconds(endMs, naturalEnd)
        if (skipped > MaxSkippedMusicS) return null
        val lateness = -max(0f, skipped - 4f) * 0.06f
        val barIndex = bars.indexOfFirst { abs(it - swapMs) < grid.periodMs / 3 }
        val phrase = if (barIndex < 0) 0f else when (out.phraseLevel(barIndex)) {
            8 -> 1f
            4 -> 0.6f
            2 -> 0.25f
            else -> 0f
        }
        // the music dropping away right at the swap (an outro starting) is a natural seam
        val drop = (out.energyAround(swapMs - 3_000L, 4_000L) - out.energyAround(swapMs + 3_000L, 4_000L)).coerceIn(-0.3f, 0.5f)
        // lyrics weigh in lightly: don't hand over in the middle of a sung line
        val midLine = lines.any { swapMs > it.start + 300L && swapMs < it.end - 200L }
        val afterLine = lines.any { swapMs - it.end in 0L..2_500L }
        var lyric = (if (midLine) -0.35f else 0f) + (if (afterLine) 0.15f else 0f)
        if (style == CrossmixStyle.VocalHandoff) lyric *= 2f
        return phrase * 0.9f + lateness + drop * 0.6f + lyric
    }

    private data class Landing(val timeMs: Long, val score: Float)

    /** where the next song lands, heard on the swap: a blend runs its intro under this one and lands on its drop, a drop enters straight on it; a locked landing sits on the beats fitted around it; null when nothing fits */
    private fun pickLanding(
        style: CrossmixStyle,
        out: CrossmixTrackAnalysis,
        inc: CrossmixTrackAnalysis?,
        swapMs: Long,
        swapBeat: Int,
        outBeatMs: Double,
        tempo: Tempo?,
        locked: Boolean,
        outVoice: Voice,
        inVoice: Voice,
        latestMs: Long,
    ): Landing? {
        val inBeat = tempo?.inBeatMs ?: outBeatMs
        // the stretch of the next song that plays under this one before it lands
        val runUpMs = if (style.entersAtEnd) 0.0 else swapBeat * inBeat
        // nothing known about it: it simply starts from the top
        if (inc == null) return Landing(runUpMs.toLong(), 0f)
        val start = inc.leadingSilenceEndMs
        val bars = inc.barStartsMs()
        val outEnergy = out.energyAround(swapMs - 4_000L)
        // a blend that rides on the next song's beat needs that beat at its landing
        val needsGrid = !style.entersAtEnd && tempo != null
        // how much the outgoing song sings while the two overlap
        val outSinging = if (style.entersAtEnd) 0f else outVoice.average(swapMs - swapBeat * outBeatMs, swapMs.toDouble())
        val fromTop = (start + runUpMs).toLong()
        val candidates = ArrayList<Long>()
        if (style.entersAtEnd) candidates += start
        // a blend can always start from the top and land wherever that falls, if nothing better lines up
        if (!style.entersAtEnd && fromTop <= latestMs) candidates += fromTop
        for (bar in bars) if (bar in start..latestMs && bar - runUpMs >= 0.0) candidates += bar
        var best: Landing? = null
        for (time in candidates.distinct()) {
            val barIndex = bars.indexOfFirst { abs(it - time) < 60L }
            val phrase = when {
                style.entersAtEnd && time == start -> 0.7f
                barIndex < 0 -> 0f
                else -> when (inc.phraseLevel(barIndex)) {
                    8 -> 1f
                    4 -> 0.5f
                    else -> 0.1f
                }
            }
            val section = if (barIndex >= 0 && barIndex in inc.sectionBars) 0.3f else 0f
            val before = inc.energyAround(time - 3_000L, 4_000L)
            val after = inc.energyAround(time + 3_000L, 4_000L)
            val energyFit = if (style.entersAtEnd) {
                // a drop: the next song should hit as it comes in, at least as hard as this one ends
                (after - before).coerceIn(-0.2f, 0.5f) + (1f - abs(after - max(outEnergy, 0.6f))) * 0.5f
            } else {
                // a blend lands on the drop: the next song's arrangement arriving, louder and with its bass, as this one lets go
                val bassRise = inc.bassAround(time + 3_000L, 4_000L) - inc.bassAround(time - 3_000L, 4_000L)
                ((after - before) + 0.5f * bassRise).coerceIn(-0.2f, 0.6f) * 1.2f + (1f - abs(after - max(outEnergy, 0.55f))) * 0.3f
            }
            val heardFrom = (time - runUpMs).coerceAtLeast(0.0)
            val skipPenalty = (heardFrom - start).coerceAtLeast(0.0).toFloat() / 60_000f * 0.45f
            // an instrumental run-up is what to bring in under one that sings
            val inSinging = if (style.entersAtEnd) 0f else inVoice.average(heardFrom, time.toDouble())
            val grid = inc.localGrid(time)
            val gridless = needsGrid && grid == null
            val score = phrase * 0.8f + section + energyFit - skipPenalty - inSinging * (0.25f + outSinging * 0.9f) - (if (gridless) 2f else 0f)
            // the one detected beat jitters by a few ms; a locked landing takes the time the beats around it agree on
            val landing = if (locked && grid != null) grid.snap(time) else time
            if (best == null || score > best.score) best = Landing(landing, score)
        }
        return best
    }

    private fun lengthFit(style: CrossmixStyle, length: Int, out: CrossmixTrackAnalysis, inc: CrossmixTrackAnalysis?, keyClash: Boolean): Float {
        val calm = 1f - max(out.vibe.energy, inc?.vibe?.energy ?: 0.5f)
        val longest = style.lengths.last().toFloat()
        // calm songs breathe in long blends, busy ones in short ones
        var fit = (length / longest - 0.5f) * (calm - 0.4f) * 0.6f
        // keys that clash shouldn't share more than a couple of bars
        if (keyClash && !style.entersAtEnd && length > 8) fit -= 0.3f
        return fit
    }

    /** no reliable beat: a smooth equal-power blend that finishes before the song runs out, starting after the last sung line where that fits */
    private fun smoothFade(
        outgoing: CrossmixTrackInput,
        incoming: CrossmixTrackInput,
        naturalEnd: Long,
        preferredStyle: CrossmixStyle? = null,
    ): CrossmixPlan {
        val lines = parseLines(outgoing.lyrics)
        val fadeMs = 9_000L
        val latestStart = (naturalEnd - fadeMs).coerceAtLeast(0L)
        val earliestStart = (outgoing.durationMs - MaxLeadMs).coerceAtLeast(0L)
        val afterLyrics = lines.lastOrNull { it.end in earliestStart..latestStart }?.end
        val start = (afterLyrics ?: latestStart).coerceIn(earliestStart, latestStart.coerceAtLeast(earliestStart))
        val beatMs = fadeMs / 16.0
        val style = preferredStyle ?: CrossmixStyle.Dampen
        val swap = style.swapAt(16)
        val cue = incoming.analysis?.leadingSilenceEndMs?.coerceAtMost(MaxEntryMs) ?: 0L
        return CrossmixPlan(
            // nothing is known about either song, so the safest hand-over: the outgoing one dampened behind the next, not even two voices can fight
            style = style,
            variation = 0,
            lengthBeats = 16,
            swapBeat = swap,
            outStartMs = start,
            outBeatMs = beatMs,
            // the next song starts from its first sound at beat 0, so it's that far in by the swap
            inCueMs = (cue + swap * beatMs).toLong(),
            inMediaPerBeat = beatMs,
            inSpeed = 1f,
            beatMatched = false,
            inStartBeat = 0f,
        )
    }

    /** always mix with no beat to plan around: still a real blend, this song sinking under a filter while the next comes up through it, after the last sung line where that fits */
    private fun forcedBlend(
        outgoing: CrossmixTrackInput,
        incoming: CrossmixTrackInput,
        naturalEnd: Long,
        out: CrossmixTrackAnalysis?,
        preferredStyle: CrossmixStyle? = null,
    ): CrossmixPlan {
        val style = preferredStyle ?: CrossmixStyle.FilterSweep
        val beats = 16
        // the song's own count when it has one, even a shaky one, so the sweep moves with it; else 10 s over the 16
        val beatMs = out?.bpm?.takeIf { it in 70f..180f }?.let { 60_000.0 / it } ?: (ForcedBlendMs / beats)
        val lengthMs = (beatMs * beats).toLong()
        val lines = parseLines(outgoing.lyrics)
        val latestStart = (naturalEnd - lengthMs).coerceAtLeast(0L)
        val earliestStart = (outgoing.durationMs - MaxLeadMs).coerceAtLeast(0L)
        val afterLyrics = lines.lastOrNull { it.end in earliestStart..latestStart }?.end
        val start = (afterLyrics ?: latestStart).coerceIn(earliestStart, latestStart.coerceAtLeast(earliestStart))
        val swap = style.swapAt(beats)
        val cue = incoming.analysis?.leadingSilenceEndMs?.coerceAtMost(MaxEntryMs) ?: 0L
        return CrossmixPlan(
            style = style,
            variation = 0,
            lengthBeats = beats,
            swapBeat = swap,
            outStartMs = start,
            outBeatMs = beatMs,
            // the next song starts from its first sound at beat 0, so it's that far in by the swap
            inCueMs = (cue + swap * beatMs).toLong(),
            inMediaPerBeat = beatMs,
            inSpeed = 1f,
            beatMatched = false,
            inStartBeat = 0f,
        )
    }

    private const val ForcedBlendMs = 10_000.0

    /** where the song really ends: before any trailing silence */
    private fun naturalEnd(track: CrossmixTrackInput): Long {
        val analysis = track.analysis
        val silence = analysis?.trailingSilenceStartMs?.takeIf { analysis.complete && it > track.durationMs / 2 }
        return min(track.durationMs, silence ?: track.durationMs)
    }

    private data class Line(val start: Long, val end: Long)

    private val SyncedStamp = Regex("""\[\d{1,2}:\d{2}""")

    private fun parseLines(lyrics: String?): List<Line> {
        // only lyrics timed to the line say where the voice is; plain text would put it all at 0:00
        if (lyrics.isNullOrBlank() || !SyncedStamp.containsMatchIn(lyrics)) return emptyList()
        val entries = runCatching { LrcParser.parseLyrics(lyrics) }.getOrNull().orEmpty()
            .filter { it.text.isNotBlank() }
        return entries.mapIndexed { index, entry ->
            // a line lasts until the next one, or ~4 s for the last
            val next = entries.getOrNull(index + 1)?.time ?: (entry.time + 4_000L)
            Line(entry.time, min(next, entry.time + 8_000L))
        }
    }
}
