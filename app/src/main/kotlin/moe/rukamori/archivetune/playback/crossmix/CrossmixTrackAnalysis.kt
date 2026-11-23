/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.crossmix

import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CrossmixTrackAnalysis(
    val analyzedMs: Long,
    val complete: Boolean,
    val bpm: Float?,
    /** how sure the tempo is, 0..1. below ~0.35 nothing is beat-matched to it */
    val bpmConfidence: Float,
    val beatsMs: LongArray,
    /** the beats that start a bar, as indices into beatsMs */
    val barBeats: IntArray,
    /** the bars a new section starts on, as indices into barStartsMs */
    val sectionBars: IntArray,
    val key: String?,
    val keyConfidence: Float,
    val leadingSilenceEndMs: Long,
    val trailingSilenceStartMs: Long,
    val energy: FloatArray,
    val bass: FloatArray,
    val brightness: FloatArray,
    val vibe: Vibe,
    val vocal: FloatArray = FloatArray(0),
) {
    val beatMs: Float? get() = bpm?.let { 60_000f / it }

    val isBeatReliable: Boolean get() = bpm != null && bpmConfidence >= 0.35f && beatsMs.size >= 16

    /** the beats that start a bar, in order */
    fun barStartsMs(): LongArray = LongArray(barBeats.size) { beatsMs[barBeats[it]] }

    /** how big a phrase bar index starts (8, 4, 2 or 1 bars), counted from the section starts either side of it */
    fun phraseLevel(index: Int): Int {
        var previous = 0
        var next = -1
        for (bar in sectionBars) {
            if (bar <= index) previous = bar else { next = bar; break }
        }
        val fromPrevious = phraseLevelOf(index - previous)
        return if (next < 0) fromPrevious else max(fromPrevious, phraseLevelOf(next - index))
    }

    private fun phraseLevelOf(bars: Int): Int = when {
        bars % 8 == 0 -> 8
        bars % 4 == 0 -> 4
        bars % 2 == 0 -> 2
        else -> 1
    }

    /** the 0..1 loudness around ms, averaged over windowMs */
    fun energyAround(ms: Long, windowMs: Long = 4_000L): Float = averageOver(energy, ms, windowMs)

    fun bassAround(ms: Long, windowMs: Long = 4_000L): Float = averageOver(bass, ms, windowMs)

    /** seconds of real music between two points: a fading tail or silence costs nothing when a mix leaves it unplayed */
    fun audibleSeconds(fromMs: Long, toMs: Long): Float {
        if (toMs <= fromMs || energy.isEmpty()) return 0f
        val from = (fromMs / 1_000L).toInt().coerceIn(0, energy.size)
        val to = (toMs / 1_000L).toInt().coerceIn(from, energy.size)
        var seconds = 0
        for (s in from until to) if (energy[s] >= AudibleEnergy) seconds++
        return seconds.toFloat()
    }

    /** beat grid near ms, least squares over nearby beats: far steadier than any one detected beat, so two tracks lock without flamming */
    fun localGrid(ms: Long, span: Int = 12): LocalGrid? {
        if (beatsMs.size < 4) return null
        val nearest = beatsMs.indices.minByOrNull { abs(beatsMs[it] - ms) } ?: return null
        val from = (nearest - span).coerceAtLeast(0)
        val to = (nearest + span).coerceAtMost(beatsMs.lastIndex)
        val count = to - from + 1
        if (count < 4) return null
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (i in from..to) {
            val x = (i - from).toDouble(); val y = beatsMs[i].toDouble()
            sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        val denominator = count * sxx - sx * sx
        if (denominator == 0.0) return null
        val period = (count * sxy - sx * sy) / denominator
        val intercept = (sy - period * sx) / count
        if (period <= 200.0 || period >= 1_500.0) return null
        return LocalGrid(firstBeatMs = intercept, periodMs = period, firstIndex = from)
    }

    private fun averageOver(values: FloatArray, ms: Long, windowMs: Long): Float {
        if (values.isEmpty()) return 0.5f
        val from = ((ms - windowMs / 2) / 1_000L).toInt().coerceIn(0, values.lastIndex)
        val to = ((ms + windowMs / 2) / 1_000L).toInt().coerceIn(from, values.lastIndex)
        var sum = 0f
        for (i in from..to) sum += values[i]
        return sum / (to - from + 1)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CrossmixTrackAnalysis

        if (analyzedMs != other.analyzedMs) return false
        if (complete != other.complete) return false
        if (bpm != other.bpm) return false
        if (bpmConfidence != other.bpmConfidence) return false
        if (!beatsMs.contentEquals(other.beatsMs)) return false
        if (!barBeats.contentEquals(other.barBeats)) return false
        if (!sectionBars.contentEquals(other.sectionBars)) return false
        if (key != other.key) return false
        if (keyConfidence != other.keyConfidence) return false
        if (leadingSilenceEndMs != other.leadingSilenceEndMs) return false
        if (trailingSilenceStartMs != other.trailingSilenceStartMs) return false
        if (!energy.contentEquals(other.energy)) return false
        if (!bass.contentEquals(other.bass)) return false
        if (!brightness.contentEquals(other.brightness)) return false
        if (vibe != other.vibe) return false
        if (!vocal.contentEquals(other.vocal)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = analyzedMs.hashCode()
        result = 31 * result + complete.hashCode()
        result = 31 * result + (bpm?.hashCode() ?: 0)
        result = 31 * result + bpmConfidence.hashCode()
        result = 31 * result + beatsMs.contentHashCode()
        result = 31 * result + barBeats.contentHashCode()
        result = 31 * result + sectionBars.contentHashCode()
        result = 31 * result + (key?.hashCode() ?: 0)
        result = 31 * result + keyConfidence.hashCode()
        result = 31 * result + leadingSilenceEndMs.hashCode()
        result = 31 * result + trailingSilenceStartMs.hashCode()
        result = 31 * result + energy.contentHashCode()
        result = 31 * result + bass.contentHashCode()
        result = 31 * result + brightness.contentHashCode()
        result = 31 * result + vibe.hashCode()
        result = 31 * result + vocal.contentHashCode()
        return result
    }
}

typealias TrackAnalysis = CrossmixTrackAnalysis

private const val AudibleEnergy = 0.35f

/** a stretch of steady beats: beat n of the stretch falls at firstBeatMs + n * periodMs */
data class LocalGrid(val firstBeatMs: Double, val periodMs: Double, val firstIndex: Int) {
    /** the beat time nearest ms on this grid */
    fun snap(ms: Long): Long {
        val n = ((ms - firstBeatMs) / periodMs).roundToInt()
        return (firstBeatMs + n * periodMs).roundToLong()
    }

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}

data class Vibe(
    val energy: Float,
    val danceability: Float,
    val brightness: Float,
    val bassWeight: Float,
    val minor: Boolean,
)

/** key names to pitch classes and the distances djs mix by */
object Harmony {
    private val Names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun pitchClass(key: String?): Int? = key?.substringBefore(' ')?.let(Names::indexOf)?.takeIf { it >= 0 }

    /** distance on the camelot wheel: 0 same key, 1 a neighbour or relative major/minor, more means they clash */
    fun camelotDistance(a: String?, b: String?): Int? {
        val ca = camelot(a) ?: return null
        val cb = camelot(b) ?: return null
        val ring = abs(ca.first - cb.first).let { min(it, 12 - it) }
        return if (ca.second == cb.second) ring else if (ring == 0) 1 else ring + 1
    }

    private fun camelot(key: String?): Pair<Int, Boolean>? {
        val pc = pitchClass(key) ?: return null
        val minor = key!!.endsWith("minor", ignoreCase = true)
        // the relative major shares a number: A minor (8A) with C major (8B)
        val majorRoot = if (minor) (pc + 3) % 12 else pc
        val number = (majorRoot * 7) % 12
        return number to minor
    }
}

fun TrackAnalysisResult.toCrossmixAnalysis(complete: Boolean = true): CrossmixTrackAnalysis? {
    if (rawJson.isBlank()) return null
    return runCatching {
        val json = JSONObject(rawJson)
        val durationSec = json.optDouble("duration", 0.0)
        val analyzedMs = (durationSec * 1000.0).roundToLong()
        val bpmVal = json.optDouble("bpm", this.bpm).takeIf { it > 0.0 }?.toFloat()
        val bpmConfidence = json.optDouble("beatConfidence", 0.0).toFloat().coerceIn(0f, 1f)

        val beatsArr = json.optJSONArray("beats")
        val beatsMs = if (beatsArr != null && beatsArr.length() > 0) {
            LongArray(beatsArr.length()) { (beatsArr.getDouble(it) * 1000.0).roundToLong() }
        } else {
            val intervalMs = json.optDouble("beatInterval", if (bpmVal != null && bpmVal > 0f) 60.0 / bpmVal else 0.0) * 1000.0
            val firstBeatMs = json.optDouble("firstBeat", this.firstBeat) * 1000.0
            if (intervalMs > 200.0 && analyzedMs > 0L) {
                val count = ((analyzedMs - firstBeatMs) / intervalMs).toInt().coerceAtLeast(0)
                LongArray(count) { (firstBeatMs + it * intervalMs).roundToLong() }
            } else {
                LongArray(0)
            }
        }

        val downbeatsArr = json.optJSONArray("downbeats")
        val downbeatsMs = if (downbeatsArr != null && downbeatsArr.length() > 0) {
            LongArray(downbeatsArr.length()) { (downbeatsArr.getDouble(it) * 1000.0).roundToLong() }
        } else {
            LongArray(0)
        }

        val barBeats = if (beatsMs.isNotEmpty() && downbeatsMs.isNotEmpty()) {
            downbeatsMs.map { db ->
                beatsMs.indices.minByOrNull { abs(beatsMs[it] - db) } ?: 0
            }.distinct().sorted().toIntArray()
        } else if (beatsMs.size >= 4) {
            IntArray(beatsMs.size / 4) { it * 4 }
        } else {
            IntArray(0)
        }

        val barStartsMs = LongArray(barBeats.size) { beatsMs[barBeats[it]] }

        val phrasesArr = json.optJSONArray("phraseBoundaries")
        val sectionBars = if (phrasesArr != null && phrasesArr.length() > 0 && barStartsMs.isNotEmpty()) {
            val pbMs = LongArray(phrasesArr.length()) { (phrasesArr.getDouble(it) * 1000.0).roundToLong() }
            pbMs.map { pb ->
                barStartsMs.indices.minByOrNull { abs(barStartsMs[it] - pb) } ?: 0
            }.distinct().sorted().toIntArray()
        } else {
            IntArray(0)
        }

        val key = json.optString("key").takeIf { it.isNotBlank() && it != "null" }
        val keyConfidence = json.optDouble("keyConfidence", 0.0).toFloat().coerceIn(0f, 1f)

        val audibleStartSec = json.optDouble("audibleStartTime", json.optDouble("firstBeat", 0.0))
        val leadingSilenceEndMs = (audibleStartSec * 1000.0).roundToLong().coerceAtLeast(0L)

        val outroSec = json.optDouble("outroStartTime", json.optDouble("contentEndTime", durationSec))
        val trailingSilenceStartMs = (outroSec * 1000.0).roundToLong().coerceIn(0L, analyzedMs)

        val totalSeconds = (analyzedMs / 1000L).toInt().coerceAtLeast(1)

        val energyPoints = parsePoints(json.optJSONArray("energyCurve"))
        val lowEnergyPoints = parsePoints(json.optJSONArray("lowEnergyCurve"))

        val energy = sampleCurve(energyPoints, totalSeconds)
        val bass = sampleCurve(lowEnergyPoints, totalSeconds)
        val brightness = FloatArray(totalSeconds) { 0.5f }

        val vocalArr = json.optJSONArray("vocalActivityMask")
        val vocal = if (vocalArr != null && vocalArr.length() > 0) {
            val vPoints = ArrayList<Pair<Double, Float>>(vocalArr.length())
            val step = if (energyPoints.isNotEmpty()) {
                energyPoints.last().first / vocalArr.length().toDouble()
            } else 1.0
            for (i in 0 until vocalArr.length()) {
                vPoints.add((i * step) to vocalArr.getDouble(i).toFloat())
            }
            sampleCurve(vPoints, totalSeconds, clampTo1 = true)
        } else {
            FloatArray(0)
        }

        val vibeEnergy = if (energy.isNotEmpty()) {
            val sorted = energy.sorted()
            val loud = sorted.drop((sorted.size * 0.4).toInt())
            loud.average().toFloat().coerceIn(0f, 1f)
        } else 0.5f

        val vibeBass = if (bass.isNotEmpty()) {
            var sum = 0f
            var count = 0
            for (i in energy.indices) {
                if (energy[i] > 0.5f && i < bass.size) {
                    sum += bass[i]
                    count++
                }
            }
            if (count > 0) (sum / count).coerceIn(0f, 1f) else bass.average().toFloat().coerceIn(0f, 1f)
        } else 0.5f

        val vibe = Vibe(
            energy = vibeEnergy,
            danceability = bpmConfidence,
            brightness = 0.5f,
            bassWeight = vibeBass,
            minor = key?.endsWith("minor", ignoreCase = true) == true,
        )

        CrossmixTrackAnalysis(
            analyzedMs = analyzedMs,
            complete = complete,
            bpm = bpmVal,
            bpmConfidence = bpmConfidence,
            beatsMs = beatsMs,
            barBeats = barBeats,
            sectionBars = sectionBars,
            key = key,
            keyConfidence = keyConfidence,
            leadingSilenceEndMs = leadingSilenceEndMs,
            trailingSilenceStartMs = trailingSilenceStartMs,
            energy = energy,
            bass = bass,
            brightness = brightness,
            vibe = vibe,
            vocal = vocal,
        )
    }.getOrNull()
}

private fun parsePoints(arr: JSONArray?): List<Pair<Double, Float>> {
    if (arr == null) return emptyList()
    val list = ArrayList<Pair<Double, Float>>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val t = obj.optDouble("t", 0.0)
        val e = obj.optDouble("e", 0.0).toFloat()
        list.add(t to e)
    }
    return list
}

private fun sampleCurve(points: List<Pair<Double, Float>>, targetSeconds: Int, clampTo1: Boolean = false): FloatArray {
    if (points.isEmpty()) return FloatArray(targetSeconds) { 0.5f }
    val result = FloatArray(targetSeconds)
    var cursor = 0
    for (s in 0 until targetSeconds) {
        val sec = s.toDouble()
        while (cursor + 1 < points.size && points[cursor + 1].first <= sec) {
            cursor++
        }
        val current = points[cursor]
        val next = if (cursor + 1 < points.size) points[cursor + 1] else null
        val raw = if (next != null && next.first > current.first) {
            val fraction = ((sec - current.first) / (next.first - current.first)).toFloat().coerceIn(0f, 1f)
            current.second + fraction * (next.second - current.second)
        } else {
            current.second
        }
        result[s] = if (clampTo1) raw.coerceIn(0f, 1f) else (raw / 1.5f).coerceIn(0f, 1f)
    }
    return result
}

private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
