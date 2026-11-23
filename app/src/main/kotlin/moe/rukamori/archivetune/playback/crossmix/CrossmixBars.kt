/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.crossmix

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** works out which beat starts each bar and where a song's sections change, from its chords, drums and sound rather than its loudest kick */
internal object CrossmixBars {

    /** what the analyser kept for every frame; chroma holds 12 values per frame */
    class Frames(
        val onset: FloatArray,
        val kick: FloatArray,
        val snap: FloatArray,
        val chroma: FloatArray,
        val rms: FloatArray,
        val lowShare: FloatArray,
        val centroidHz: FloatArray,
    )

    /** bars: the beat each bar starts on. sections: the bar each section starts on */
    class Found(val bars: IntArray, val sections: IntArray)

    val None = Found(IntArray(0), IntArray(0))

    fun find(beatFrames: IntArray, frames: Frames): Found {
        if (beatFrames.size < 8 || frames.rms.isEmpty()) return None
        val period = medianGap(beatFrames)
        val beats = BeatSound(beatFrames, period, frames)
        val position = barPositions(beatFrames, period, beats)
        val bars = position.indices.filter { position[it] == 0 }.toIntArray()
        return Found(bars, sectionStarts(bars, beats))
    }

    /** each beat's place in its bar (0 is the one), the best run through the evidence where the count only jumps once bars of it disagree */
    private fun barPositions(beatFrames: IntArray, period: Float, beats: BeatSound): IntArray {
        val n = beats.size
        val harmony = harmonyChange(beats)
        val texture = textureChange(beats)
        // kick and snare on one shared scale, so a song with no snare doesn't get its hiss blown up into a backbeat
        val drumScale = 2f / (beats.kick.average() + beats.snap.average()).toFloat().coerceAtLeast(1e-6f)
        val accentScale = 1f / beats.accent.average().toFloat().coerceAtLeast(1e-9f)
        val fit = FloatArray(n * 4)
        for (i in 0 until n) {
            // kick beats (the one and three) against snare and clap beats (two and four)
            val backbeat = ((beats.kick[i] - beats.snap[i]) * drumScale).coerceIn(-2f, 2f) * BackbeatWeight
            val one = HarmonyWeight * harmony[i] + TextureWeight * texture[i] + AccentWeight * beats.accent[i] * accentScale
            fit[i * 4] = one + backbeat
            fit[i * 4 + 1] = -backbeat
            fit[i * 4 + 2] = HarmonyWeight * ThreeShare * harmony[i] + backbeat
            fit[i * 4 + 3] = -backbeat
        }

        val score = FloatArray(4) { fit[it] }
        val next = FloatArray(4)
        val back = IntArray(n * 4)
        for (i in 1 until n) {
            // a beat the tracker skipped still moves the count on by the beats its gap spans
            val step = ((beatFrames[i] - beatFrames[i - 1]) / period).roundToInt().coerceAtLeast(1)
            var leader = 0
            for (k in 1 until 4) if (score[k] > score[leader]) leader = k
            val jump = score[leader] - JumpCost
            for (k in 0 until 4) {
                val from = Math.floorMod(k - step, 4)
                val kept = score[from] >= jump
                next[k] = (if (kept) score[from] else jump) + fit[i * 4 + k]
                back[i * 4 + k] = if (kept) from else leader
            }
            next.copyInto(score)
        }
        val position = IntArray(n)
        var state = (0 until 4).maxBy { score[it] }
        for (i in n - 1 downTo 0) {
            position[i] = state
            if (i > 0) state = back[i * 4 + state]
        }
        return position
    }

    /** how far the chords move at each beat, the two beats before against the two after; a song's usual move is 1 and a steady drone stays near 0 */
    private fun harmonyChange(beats: BeatSound): FloatArray {
        val n = beats.size
        // square roots so one loud note can't outweigh the rest of a chord
        val rooted = FloatArray(n * 12) { sqrt(beats.chroma[it]) }
        val change = FloatArray(n)
        val before = FloatArray(12)
        val after = FloatArray(12)
        for (i in 2 until n - 1) {
            for (k in 0 until 12) {
                before[k] = rooted[(i - 2) * 12 + k] + rooted[(i - 1) * 12 + k]
                after[k] = rooted[i * 12 + k] + rooted[(i + 1) * 12 + k]
            }
            change[i] = 1f - cosine(before, after)
        }
        val usual = change.average().toFloat().coerceAtLeast(0.03f)
        for (i in change.indices) change[i] /= usual
        return change
    }

    /** a clear change in the sound right at a beat, four beats before against four after: sections start on the one, so only the peak of a big change counts */
    private fun textureChange(beats: BeatSound): FloatArray {
        val change = soundChange(arrayOf(beats.decibels(), beats.low, beats.bright, beats.density), window = 4)
        return FloatArray(change.size) { i ->
            if (change[i] >= MinSectionChange && isPeak(change, i, 2)) (change[i] - 1.5f).coerceAtMost(6f) else 0f
        }
    }

    /** bars where a new section begins: the sound clearly changes between the four bars before and the four after (chords loop over 8 bars too often to say) */
    private fun sectionStarts(bars: IntArray, beats: BeatSound): IntArray {
        val count = bars.size
        if (count < 8) return IntArray(0)
        val power = FloatArray(count)
        val low = FloatArray(count)
        val bright = FloatArray(count)
        val density = FloatArray(count)
        for (b in 0 until count) {
            val from = bars[b]
            val to = if (b + 1 < count) bars[b + 1] else min(beats.size, from + 4)
            for (i in from until to) {
                power[b] += beats.power[i]; low[b] += beats.low[i]; bright[b] += beats.bright[i]; density[b] += beats.density[i]
            }
            val beatsInBar = (to - from).coerceAtLeast(1).toFloat()
            power[b] /= beatsInBar; low[b] /= beatsInBar; bright[b] /= beatsInBar; density[b] /= beatsInBar
        }
        val decibels = FloatArray(count) { (10.0 * log10(power[it] + 1e-12)).toFloat() }
        val change = soundChange(arrayOf(decibels, low, bright, density), window = 4)

        // sections a few bars long keep the whole song changing, so the bar has to clear a fixed level and top its neighbours
        val picked = ArrayList<Int>()
        for (b in (2..count - 2).sortedByDescending { change[it] }) {
            if (change[b] < MinSectionChange) break
            if (isPeak(change, b, 2) && picked.none { abs(it - b) < 4 }) picked += b
        }
        return picked.sorted().toIntArray()
    }

    /** at each point, how far the average of each series moves between the window before and the window after, in that series' own spread */
    private fun soundChange(series: Array<FloatArray>, window: Int): FloatArray {
        val n = series[0].size
        val change = FloatArray(n)
        for ((index, values) in series.withIndex()) {
            val spread = standardDeviation(values).coerceAtLeast(SpreadFloors[index])
            val prefix = DoubleArray(n + 1)
            for (i in 0 until n) prefix[i + 1] = prefix[i] + values[i]
            for (i in 0 until n) {
                val w = min(window, min(i, n - i))
                if (w < 2) continue
                val before = (prefix[i] - prefix[i - w]) / w
                val after = (prefix[i + w] - prefix[i]) / w
                change[i] += (abs(after - before) / spread).toFloat()
            }
        }
        return change
    }

    /** each beat's sound, gathered over the frames from it to the next beat */
    private class BeatSound(beatFrames: IntArray, period: Float, frames: Frames) {
        val size = beatFrames.size
        val kick = FloatArray(size)
        val snap = FloatArray(size)
        val accent = FloatArray(size)
        val power = FloatArray(size)
        val low = FloatArray(size)
        val bright = FloatArray(size)
        val density = FloatArray(size)
        val chroma = FloatArray(size * 12)

        init {
            val last = frames.rms.lastIndex
            for (i in 0 until size) {
                val at = beatFrames[i].coerceIn(0, last)
                kick[i] = peakNear(frames.kick, at)
                snap[i] = peakNear(frames.snap, at)
                // how hard the beat lands in the bass, on a straight scale: the attack curves are log-squashed and hide a louder one
                for (f in max(0, at - 1)..min(last, at + 4)) accent[i] = max(accent[i], frames.rms[f] * sqrt(frames.lowShare[f]))
                val until = (if (i + 1 < size) beatFrames[i + 1] else (at + period).roundToInt()).coerceIn(at + 1, last + 1)
                for (f in at until until) {
                    power[i] += frames.rms[f] * frames.rms[f]
                    low[i] += frames.lowShare[f]
                    bright[i] += frames.centroidHz[f]
                    density[i] += frames.onset[f]
                    for (k in 0 until 12) chroma[i * 12 + k] += frames.chroma[f * 12 + k]
                }
                val count = (until - at).toFloat()
                power[i] /= count; low[i] /= count; bright[i] /= count; density[i] /= count
            }
        }

        fun decibels() = FloatArray(size) { (10.0 * log10(power[it] + 1e-12)).toFloat() }

        // the strongest attack within two frames of the beat, since a beat is placed on its onset peak give or take a frame
        private fun peakNear(values: FloatArray, at: Int): Float {
            var best = 0f
            for (f in max(0, at - 2)..min(values.lastIndex, at + 2)) best = max(best, values[f])
            return best
        }
    }

    private fun medianGap(beatFrames: IntArray): Float {
        val gaps = IntArray(beatFrames.size - 1) { beatFrames[it + 1] - beatFrames[it] }.sorted()
        return gaps[gaps.size / 2].coerceAtLeast(1).toFloat()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var aa = 0f
        var bb = 0f
        for (k in a.indices) { dot += a[k] * b[k]; aa += a[k] * a[k]; bb += b[k] * b[k] }
        return if (aa <= 0f || bb <= 0f) 1f else dot / sqrt(aa * bb)
    }

    private fun isPeak(values: FloatArray, at: Int, reach: Int): Boolean =
        (max(0, at - reach)..min(values.lastIndex, at + reach)).all { values[it] <= values[at] }

    private fun standardDeviation(values: FloatArray): Float {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size.coerceAtLeast(1)).toFloat()
    }

    private const val HarmonyWeight = 1f
    // chords change on the three now and then, never as often as on the one
    private const val ThreeShare = 0.35f
    private const val BackbeatWeight = 0.6f
    private const val TextureWeight = 0.5f
    private const val AccentWeight = 0.3f
    // roughly two clear bars of contrary evidence before the count is allowed to jump
    private const val JumpCost = 8f
    // a section start has to change at least this much, summed over the series in their own spreads
    private const val MinSectionChange = 2.5f
    // the smallest spread each series is measured in (db, bass share, brightness in hz, busyness), so a flat song's noise doesn't read as change
    private val SpreadFloors = floatArrayOf(1f, 0.02f, 150f, 0.02f)
}
