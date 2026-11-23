/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.crossmix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** every crossmix renderer knob at one moment (defaults are the untouched track), a style fills it from the beat position alone so it always sounds the same */
class FxParams {
    /** level of the track itself; echo and reverb returns have their own */
    var gain = 1f
    var lowCutHz = 0f
    var highCutHz = OpenHz
    var resonance = 0.707f
    var lowBand = 1f
    var midBand = 1f
    var highBand = 1f
    /** 0 the bass is there, 1 it's gone behind a steep cut at 200 hz: the side that doesn't own the low end */
    var bassCut = 0f
    var echoSend = 0f
    var echoBeats = 0.75f
    var echoFeedback = 0.45f
    /** 0 dark (dub), 1 bright repeats */
    var echoTone = 0.6f
    var echoPingPong = false
    var reverbSend = 0f
    var reverbSize = 0.84f
    /** 1 holds the reverb tail forever (a freeze) */
    var reverbFreeze = 0f
    var gateDepth = 0f
    /** gate steps per beat: 2 eighths, 4 sixteenths */
    var gateDivision = 4f
    var gateDuty = 0.5f
    var flangerMix = 0f
    var flangerRateHz = 0.3f
    var crush = 0f
    var pan = 0f
    var width = 1f
    /** 0..1 how far the sound circles the listener; one turn per orbitBeats */
    var orbit = 0f
    var orbitBeats = 8f
    var noiseLevel = 0f
    var noiseHz = 1_000f
    var deck = Deck.Live
    /** spin-back and tape-stop: 0..1 through the move. loop roll: the loop length in beats */
    var deckAmount = 0f
    /** loop roll: the beat (in transition beats) the current loop started on */
    var deckMark = 0f

    fun reset() {
        gain = 1f; lowCutHz = 0f; highCutHz = OpenHz; resonance = 0.707f
        lowBand = 1f; midBand = 1f; highBand = 1f; bassCut = 0f
        echoSend = 0f; echoBeats = 0.75f; echoFeedback = 0.45f; echoTone = 0.6f; echoPingPong = false
        reverbSend = 0f; reverbSize = 0.84f; reverbFreeze = 0f
        gateDepth = 0f; gateDivision = 4f; gateDuty = 0.5f
        flangerMix = 0f; flangerRateHz = 0.3f; crush = 0f
        pan = 0f; width = 1f; orbit = 0f; orbitBeats = 8f
        noiseLevel = 0f; noiseHz = 1_000f
        deck = Deck.Live; deckAmount = 0f; deckMark = 0f
    }

    enum class Deck { Live, Loop, SpinBack, TapeStop }

    companion object {
        const val OpenHz = 20_000f
    }
}

/** which side of the transition a renderer is playing */
enum class CrossmixRole { Outgoing, Incoming }

/** when the planner may pick a style by itself: every day, only into a sharp rise or fall in energy, or never (they stay for testing by hand) */
enum class StyleUse { Everyday, Lift, Fall, Flavour }

/** the transitions crossmix can play; needsBeatMatch ones overlap on the beat, entersAtEnd ones bring the next song in after the outgoing one is gone (a drop) */
enum class CrossmixStyle(
    val label: String,
    val needsBeatMatch: Boolean,
    val entersAtEnd: Boolean,
    /** lengths that suit it, in beats */
    val lengths: IntArray,
    /** beats the outgoing side keeps ringing after the hand-over (echo and reverb tails) */
    val tailBeats: Int = 0,
    val use: StyleUse = StyleUse.Flavour,
) {
    SilkBlend("Silk blend", false, false, intArrayOf(8, 16), use = StyleUse.Everyday),
    BassSwap("Bass swap", true, false, intArrayOf(8, 16, 32), use = StyleUse.Everyday),
    FilterSweep("Filter sweep", false, false, intArrayOf(8, 16), use = StyleUse.Everyday),
    HighPassLift("High-pass lift", false, true, intArrayOf(8, 16), use = StyleUse.Lift),
    EchoOut("Echo out", false, true, intArrayOf(4, 8), tailBeats = 12, use = StyleUse.Everyday),
    DubThrow("Dub throw", false, true, intArrayOf(8), tailBeats = 16),
    ReverbWash("Reverb wash", false, false, intArrayOf(16, 24), tailBeats = 16),
    Backspin("Backspin", false, true, intArrayOf(4, 8), use = StyleUse.Lift),
    RewindWash("Rewind wash", false, true, intArrayOf(8), tailBeats = 12),
    TapeStop("Tape stop", false, true, intArrayOf(4, 8), use = StyleUse.Fall),
    PowerDown("Power down", false, true, intArrayOf(8), tailBeats = 8, use = StyleUse.Fall),
    LoopRoll("Loop roll", false, true, intArrayOf(8, 16), use = StyleUse.Lift),
    StutterGate("Stutter gate", true, false, intArrayOf(16, 32)),
    RiserBuild("Riser build", false, true, intArrayOf(16, 32), tailBeats = 4, use = StyleUse.Lift),
    Downlifter("Downlifter", false, false, intArrayOf(8, 16), tailBeats = 8, use = StyleUse.Fall),
    PhaserGlide("Phaser glide", false, false, intArrayOf(16, 32)),
    LofiMelt("Lo-fi melt", false, false, intArrayOf(16, 24)),
    SpatialOrbit("Spatial orbit", false, false, intArrayOf(16, 32), tailBeats = 8),
    DopplerPass("Doppler pass", false, false, intArrayOf(8, 16)),
    VocalHandoff("Vocal hand-off", false, true, intArrayOf(4, 8), tailBeats = 8, use = StyleUse.Everyday),
    DropCut("Drop cut", true, true, intArrayOf(8, 16), use = StyleUse.Everyday),
    GateFade("Gate fade", true, false, intArrayOf(16, 32)),
    HarmonicGlide("Harmonic glide", true, false, intArrayOf(16, 32), use = StyleUse.Everyday),
    BreakdownBridge("Breakdown bridge", false, false, intArrayOf(16, 24), tailBeats = 4),
    ThunderDrop("Thunder drop", false, true, intArrayOf(8, 16), tailBeats = 12, use = StyleUse.Lift),
    FarAway("Fade to far", false, false, intArrayOf(8, 16), tailBeats = 8, use = StyleUse.Everyday),
    Dampen("Dampen", false, false, intArrayOf(8, 16), tailBeats = 6, use = StyleUse.Everyday);

    /** the beat the low end changes hands and the next song's landing is heard: the end for a drop, otherwise up to two bars before it */
    fun swapAt(length: Int): Int = if (entersAtEnd) length else length - min(8, length / 2)

    /** both songs really play together in it: not a drop at the very end, not one fading away behind the other */
    val blendsBoth: Boolean get() = !entersAtEnd && this != FarAway && this != Dampen

    /** fills out for one side at a transition beat; length is where the outgoing song has handed over, swap where the bass changes hands, variation (0..3) picks close variants */
    fun evaluate(role: CrossmixRole, beat: Float, length: Float, swap: Float, variation: Int, out: FxParams) {
        out.reset()
        val p = (beat / length).coerceIn(0f, 1f)
        val swapShare = (swap / length).coerceIn(0.1f, 1f)
        val outgoing = role == CrossmixRole.Outgoing
        when (this) {
            SilkBlend -> if (outgoing) {
                // the bass hand-off: whole until the bass goes, then it darkens and falls away
                out.gain = if (beat <= swap) 1f else fadeOut(ramp(beat, swap, length))
                out.highCutHz = sweepDown(ramp(beat, swap, length).pow(0.8f), OpenHz, 1_600f)
            } else {
                out.gain = fadeIn(ramp(beat, 0f, swap))
                out.lowCutHz = entryHighPass(p, 900f, 0.8f * swapShare)
            }

            BassSwap -> if (outgoing) {
                // both faders up until the swap, then this one is pulled; only its air goes before that
                out.gain = if (beat <= swap) 1f else fadeOut(ramp(beat, swap, length))
                out.highCutHz = sweepDown(ramp(p, 0.3f, 1f), OpenHz, 2_200f)
            } else {
                out.gain = fadeIn(ramp(beat, 0f, swap))
                out.lowCutHz = entryHighPass(p, 520f, 0.9f * swapShare)
            }

            FilterSweep -> if (outgoing) {
                // front-loaded, so its top is gone early and it settles on a low bed rather than crawling down
                out.highCutHz = sweepDown(p.pow(0.6f), OpenHz, 300f)
                out.resonance = 0.707f + 0.15f * sin(PI.toFloat() * p)
                out.gain = fadeOut(ramp(p, 0.6f, 1f))
            } else {
                out.lowCutHz = entryHighPass(p, 1_200f, 0.6f)
                out.gain = fadeIn(ramp(p, 0f, 0.5f))
            }

            HighPassLift -> if (outgoing) {
                out.lowCutHz = sweepUp(ramp(p, 0f, 1f).pow(1.4f), 20f, 1_900f)
                out.resonance = 0.9f
                out.reverbSend = 0.25f * p
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.25f)
            }

            EchoOut -> if (outgoing) {
                // the last beat repeats on the beat, so the echoes sound like the song trailing off; it stays open, an echo of a muffled song is only mud
                out.echoBeats = 1f
                out.echoSend = ramp(beat, length - 1f, length - 0.05f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.echoFeedback = 0.45f
                out.echoTone = 0.55f
                out.highCutHz = sweepDown(ramp(beat, length - 1f, length), OpenHz, 9_000f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.5f)
                out.lowCutHz = sweepDown(ramp(beat, length, length + 2f), 420f, 0f)
            }

            DubThrow -> if (outgoing) {
                out.echoBeats = 0.5f
                out.echoPingPong = true
                out.echoSend = ramp(beat, length - 6f, length - 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.echoFeedback = 0.72f
                out.echoTone = 0.25f
                out.reverbSend = 0.18f * ramp(beat, length - 4f, length)
                out.highCutHz = sweepDown(ramp(beat, length - 4f, length), OpenHz, 1_800f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 1f)
                out.highCutHz = sweepUp(ramp(beat, length, length + 4f), 900f, OpenHz)
            }

            ReverbWash -> if (outgoing) {
                out.reverbSend = 0.95f * smooth(p)
                out.reverbSize = 0.9f
                out.reverbFreeze = ramp(p, 0.8f, 1f) * 0.6f
                out.highCutHz = sweepDown(ramp(p, 0.3f, 1f), OpenHz, 2_200f)
                out.gain = fadeOut(ramp(p, 0.15f, 0.9f))
                out.reverbSend *= cutAt(beat, length + 0.01f).coerceAtLeast(0f)
            } else {
                out.gain = fadeIn(ramp(p, 0.35f, 1f))
                out.highCutHz = sweepUp(ramp(p, 0.35f, 1f), 1_200f, OpenHz)
                out.reverbSend = 0.3f * (1f - ramp(p, 0.6f, 1f))
            }

            Backspin -> if (outgoing) {
                val spinBeats = if (variation % 2 == 0) 2f else 1.5f
                val t = ramp(beat, length - spinBeats, length)
                if (t > 0f) { out.deck = FxParams.Deck.SpinBack; out.deckAmount = t }
                out.highCutHz = sweepDown(t, OpenHz, 2_400f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.12f)
            }

            RewindWash -> if (outgoing) {
                val t = ramp(beat, length - 2f, length)
                if (t > 0f) { out.deck = FxParams.Deck.SpinBack; out.deckAmount = t }
                out.reverbSend = 0.75f * ramp(beat, length - 3f, length - 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.reverbSize = 0.92f
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.25f)
                out.highCutHz = sweepUp(ramp(beat, length, length + 4f), 700f, OpenHz)
            }

            TapeStop -> if (outgoing) {
                val stopBeats = if (variation % 2 == 0) 2f else 3f
                val t = ramp(beat, length - stopBeats, length)
                if (t > 0f) { out.deck = FxParams.Deck.TapeStop; out.deckAmount = t }
                out.highCutHz = sweepDown(t, OpenHz, 1_600f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.12f)
            }

            PowerDown -> if (outgoing) {
                val t = ramp(beat, length - 4f, length)
                if (t > 0f) { out.deck = FxParams.Deck.TapeStop; out.deckAmount = t }
                out.highCutHz = sweepDown(t, OpenHz, 900f)
                out.reverbSend = 0.5f * t * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 2f)
                out.lowCutHz = sweepDown(ramp(beat, length, length + 4f), 650f, 0f)
            }

            LoopRoll -> if (outgoing) {
                // a beat, then half, quarter and eighth beats: the roll speeds up into the drop
                val rollStart = length - 4f
                if (beat >= rollStart) {
                    val step = (beat - rollStart).toInt().coerceIn(0, 3)
                    out.deck = FxParams.Deck.Loop
                    out.deckAmount = 1f / (1 shl step)
                    out.deckMark = rollStart + step
                }
                out.lowCutHz = sweepUp(ramp(beat, rollStart - 4f, length), 20f, 1_300f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.06f)
            }

            StutterGate -> if (outgoing) {
                out.gateDivision = if (variation % 2 == 0) 4f else 2f
                out.gateDepth = 0.9f * smooth(ramp(p, 0.1f, 0.8f))
                out.gateDuty = 0.55f - 0.3f * p
                out.echoSend = 0.18f * ramp(p, 0.5f, 1f)
                out.echoBeats = 0.25f
                out.gain = fadeOut(ramp(p, 0.75f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0.35f, 0.85f))
            }

            RiserBuild -> if (outgoing) {
                out.noiseLevel = 0.3f * ramp(p, 0.2f, 1f).pow(1.6f) * cutAt(beat, length)
                out.noiseHz = sweepUp(ramp(p, 0.2f, 1f), 350f, 9_000f)
                out.lowCutHz = sweepUp(ramp(p, 0.3f, 1f), 20f, 850f)
                out.reverbSend = 0.35f * ramp(p, 0.4f, 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.03f)
                out.lowBand = 1f + 0.18f * (1f - ramp(beat, length, length + 1f))
            }

            Downlifter -> if (outgoing) {
                out.noiseLevel = 0.22f * (1f - ramp(p, 0.05f, 0.6f)) * ramp(p, 0f, 0.05f)
                out.noiseHz = sweepDown(ramp(p, 0f, 0.6f), 7_000f, 280f)
                out.highCutHz = sweepDown(ramp(p, 0f, 0.8f), OpenHz, 600f)
                out.reverbSend = 0.45f * ramp(p, 0.1f, 0.7f)
                out.gain = fadeOut(ramp(p, 0.2f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0.3f, 1f))
                out.highCutHz = sweepUp(ramp(p, 0.3f, 1f), 1_800f, OpenHz)
            }

            PhaserGlide -> if (outgoing) {
                out.flangerMix = 0.65f * smooth(ramp(p, 0f, 0.6f))
                out.flangerRateHz = 0.2f + 0.3f * p
                out.gain = fadeOut(ramp(p, 0.2f, 1f))
            } else {
                out.flangerMix = 0.5f * (1f - ramp(p, 0.4f, 1f))
                out.flangerRateHz = 0.35f
                out.gain = fadeIn(ramp(p, 0f, 0.8f))
            }

            LofiMelt -> if (outgoing) {
                out.crush = 0.85f * smooth(ramp(p, 0f, 0.8f))
                out.highCutHz = sweepDown(ramp(p, 0f, 1f), OpenHz, 2_800f)
                out.width = 1f - 0.6f * p
                out.gain = fadeOut(ramp(p, 0.25f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0.1f, 0.9f))
                out.highCutHz = sweepUp(ramp(p, 0.1f, 1f), 2_800f, OpenHz)
                out.crush = 0.35f * (1f - ramp(p, 0.2f, 0.7f))
            }

            SpatialOrbit -> if (outgoing) {
                out.orbit = smooth(ramp(p, 0f, 0.8f))
                out.orbitBeats = if (variation % 2 == 0) 8f else 16f
                out.width = 1f + 0.6f * p
                out.reverbSend = 0.55f * ramp(p, 0.2f, 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = fadeOut(ramp(p, 0.3f, 1f))
            } else {
                // arrives from the other side and settles in the middle, narrow to wide
                out.pan = -0.75f * (1f - smooth(ramp(p, 0.2f, 1f)))
                out.width = 0.3f + 0.7f * ramp(p, 0.3f, 1f)
                out.gain = fadeIn(ramp(p, 0.15f, 0.9f))
            }

            DopplerPass -> if (outgoing) {
                out.pan = 0.85f * smooth(p)
                out.highCutHz = sweepDown(ramp(p, 0.3f, 1f), OpenHz, 1_500f)
                out.gain = fadeOut(ramp(p, 0.2f, 1f))
            } else {
                out.pan = -0.85f * (1f - smooth(p))
                out.highCutHz = sweepUp(ramp(p, 0f, 0.7f), 1_500f, OpenHz)
                out.gain = fadeIn(ramp(p, 0f, 0.8f))
            }

            VocalHandoff -> if (outgoing) {
                // the last line rings out into a room while the song itself steps aside
                out.reverbSend = 0.7f * ramp(beat, length - 1f, length) * cutAt(beat, length + 1f).coerceAtLeast(0f)
                out.reverbSize = 0.88f
                out.gain = cutAt(beat, length + 0.5f)
                out.highCutHz = sweepDown(ramp(beat, length - 1f, length + 0.5f), OpenHz, 3_000f)
            } else {
                out.gain = entry(beat, length, 1f)
                out.lowCutHz = sweepDown(ramp(beat, length, length + 2f), 300f, 0f)
            }

            DropCut -> if (outgoing) {
                out.lowCutHz = sweepUp(ramp(beat, length - 2f, length), 20f, 650f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.03f)
            }

            GateFade -> if (outgoing) {
                out.gateDivision = 2f
                out.gateDepth = smooth(ramp(p, 0f, 0.4f))
                out.gateDuty = 0.9f - 0.75f * p
                out.gain = fadeOut(ramp(p, 0.6f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0f, 0.6f))
            }

            HarmonicGlide -> if (outgoing) {
                // two songs in keys that sit together: the mids cross over slowly, the bass on the swap
                out.midBand = 1f - 0.4f * ramp(p, 0.3f, 0.7f)
                out.highCutHz = sweepDown(ramp(p, 0.4f, 1f), OpenHz, 8_000f)
                out.gain = if (beat <= swap) 1f else fadeOut(ramp(beat, swap, length))
            } else {
                out.midBand = 0.5f + 0.5f * ramp(p, 0.3f, 0.7f)
                out.gain = fadeIn(ramp(beat, 0f, swap))
                out.lowCutHz = entryHighPass(p, 320f, 0.8f * swapShare)
            }

            BreakdownBridge -> if (outgoing) {
                out.highCutHz = sweepDown(ramp(p, 0f, 0.35f), OpenHz, 480f)
                out.resonance = 0.85f
                out.reverbSend = 0.3f * ramp(p, 0.2f, 0.7f)
                out.gain = fadeOut(ramp(p, 0.4f, 0.85f))
            } else {
                out.highCutHz = sweepUp(ramp(p, 0.6f, 1f), 480f, OpenHz)
                out.resonance = 0.85f - 0.15f * ramp(p, 0.6f, 1f)
                out.gain = fadeIn(ramp(p, 0.15f, 0.55f))
            }

            FarAway -> if (outgoing) {
                // drifts off to one side getting smaller and further away, while the next one arrives clear in front
                out.recede(smooth(ramp(p, 0f, 0.8f)), if (variation % 2 == 0) -1f else 1f)
                out.gain *= fadeOut(ramp(p, 0.55f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0f, 0.3f))
                out.lowCutHz = entryHighPass(p, 700f, 0.5f)
            }

            Dampen -> if (outgoing) {
                // the way apple music leaves a song: closed off behind a wall, muffled and small, while the next one comes in clear in front
                out.recede(0.85f * smooth(ramp(p, 0f, 0.35f)), 0f, room = 0.6f)
                out.gain *= fadeOut(ramp(p, 0.4f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0f, 0.3f))
                out.lowCutHz = entryHighPass(p, 500f, 0.4f)
            }

            ThunderDrop -> if (outgoing) {
                out.lowBand = 1f - ramp(beat, length - 2f, length - 1f)
                out.echoBeats = 0.75f
                out.echoSend = 0.7f * ramp(beat, length - 2f, length - 0.5f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.echoFeedback = 0.55f
                out.reverbSend = 0.45f * ramp(beat, length - 2f, length) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.03f)
                out.lowBand = 1f + 0.25f * (1f - ramp(beat, length, length + 1f))
            }
        }
        // only one kick at a time: the low end changes hands over half a beat that ends on the swap, where the next song lands
        if (!entersAtEnd) {
            val handed = ramp(beat, swap - 0.5f, swap)
            out.bassCut = if (outgoing) handed else 1f - handed
        }
        if (outgoing) {
            // a beat after the hand-over nothing new goes into the echoes and room, only the tails already there carry on and die away
            val feed = cutAt(beat, length + 1f)
            out.echoSend *= feed
            out.reverbSend *= feed
            out.noiseLevel *= feed
        } else if (beat < 0f) {
            // before the incoming side has begun it makes no sound at all, effects included
            out.gain = 0f
            out.echoSend = 0f
            out.reverbSend = 0f
            out.noiseLevel = 0f
        }
    }

    /** for a blend where two songs would sing over each other, as far as amount (0..1) says they do: the voices get bands of their own, the outgoing one darkening early while the next keeps its body out a little longer; call it after evaluate */
    fun yieldVocals(role: CrossmixRole, beat: Float, length: Float, swap: Float, amount: Float, out: FxParams) {
        val a = amount.coerceIn(0f, 1f)
        if (a <= 0f) return
        val p = (beat / length).coerceIn(0f, 1f)
        if (role == CrossmixRole.Outgoing) {
            out.highCutHz = minOf(out.highCutHz, sweepDown(a * smooth(ramp(p, 0.05f, 0.5f)), OpenHz, 1_600f))
            out.gain *= 1f - 0.25f * a * smooth(ramp(p, 0.1f, 0.6f))
            // after the swap its voice is gone in half the time the fader would take
            out.gain *= 1f - a * smooth(ramp(beat, swap, swap + (length - swap) * 0.5f))
        } else if (beat >= 0f) {
            // the next voice is kept to its air until close to the swap, then let in quickly
            val openBy = (swap / length).coerceIn(0.1f, 1f)
            val top = 700f + 1_700f * a
            out.lowCutHz = max(out.lowCutHz, sweepDown(ramp(p, openBy * (0.3f + 0.4f * a), openBy), top, 0f))
        }
    }
}

/** the next song's body lifted out while it comes in and gone by openBy: hats and air only at first, the song whole well before it's alone; held high, then let go quickly */
internal fun entryHighPass(p: Float, topHz: Float, openBy: Float): Float {
    val remaining = 1f - ramp(p, 0f, openBy)
    if (remaining <= 0f) return 0f
    return 20f * (topHz / 20f).pow(remaining.pow(0.35f))
}

/** what each song's level is multiplied by while both play: two loud songs at once would add up past full scale, so together they're brought down to the loudness of one */
fun headroom(own: Float, other: Float): Float {
    val power = own * own + other * other
    return if (power > 1f) 1f / sqrt(power) else 1f
}

fun FxParams.recede(distance: Float, side: Float, room: Float = 1f) {
    val d = distance.coerceIn(0f, 1f)
    if (d <= 0f) return
    gain *= 10f.pow(-12f * d.pow(1.25f) / 20f)
    // a soft slope, never a whistle: blended by distance so it can't jump where it takes over
    highCutHz = minOf(highCutHz, sweep(d.pow(0.85f), OpenHz, 950f))
    resonance += (0.62f - resonance) * d
    midBand *= 1f - 0.4f * d
    width *= 1f - 0.8f * d
    pan = (pan + side * 0.6f * d).coerceIn(-1f, 1f)
    reverbSend = max(reverbSend, 0.3f * room * d)
}

/** a cut sitting here is open: nothing filtered */
private const val OpenHz = FxParams.OpenHz

// a 0..1 ramp from a to b
internal fun ramp(x: Float, a: Float, b: Float): Float = if (b <= a) (if (x >= b) 1f else 0f) else ((x - a) / (b - a)).coerceIn(0f, 1f)

internal fun smooth(x: Float): Float = x * x * (3f - 2f * x)

/** equal-power fades: two uncorrelated songs at these levels add up to the same loudness */
internal fun fadeOut(x: Float): Float = cos(x.coerceIn(0f, 1f) * PI.toFloat() / 2f)
internal fun fadeIn(x: Float): Float = sin(x.coerceIn(0f, 1f) * PI.toFloat() / 2f)

/** full until at, gone 1/16 of a beat later: a cut that lands on the beat but never clicks */
internal fun cutAt(beat: Float, at: Float): Float = 1f - ramp(beat, at - 0.0625f, at)

/** silent until at, then in over overBeats (as short as a few ms for a drop) */
internal fun entry(beat: Float, at: Float, overBeats: Float): Float = fadeIn(ramp(beat, at, at + overBeats))

/** exponential sweeps, the way ears hear filter movement */
internal fun sweepDown(t: Float, from: Float, to: Float): Float = sweep(t, from, to)
internal fun sweepUp(t: Float, from: Float, to: Float): Float = sweep(t, from, to)

private fun sweep(t: Float, from: Float, to: Float): Float {
    if (t <= 0f) return from
    if (t >= 1f) return to
    val a = from.coerceAtLeast(20f)
    val b = to.coerceAtLeast(20f)
    val value = a * (b / a).pow(t)
    // a cut that sweeps to "off" (0 hz) ends exactly there instead of hovering at 20 hz
    return if (to <= 0f && t >= 0.98f) 0f else value
}
