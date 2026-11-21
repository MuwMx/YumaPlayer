/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object AudioTap {
    private const val RingSize = 1 shl 15
    private const val Mask = RingSize - 1
    private const val PlaybackLagSeconds = 0.2
    private val ring = FloatArray(RingSize)
    @Volatile private var writeIndex = 0L
    @Volatile var sampleRate = 44_100
        private set
    @Volatile private var owner = 0
    @Volatile private var lastWriteNanos = 0L
    @Volatile var listeners = 0
        private set

    fun listen() { synchronized(this) { listeners++ } }
    fun stopListening() { synchronized(this) { listeners = max(0, listeners - 1) } }

    val wanted: Boolean get() = listeners > 0
    val isLive: Boolean get() = writeIndex > 0L && System.nanoTime() - lastWriteNanos < 400_000_000L

    fun write(writer: Int, samples: DoubleArray, frames: Int, channels: Int, rate: Int) {
        val now = System.nanoTime()
        if (owner != writer) {
            if (now - lastWriteNanos < 300_000_000L) return
            owner = writer
        }
        lastWriteNanos = now
        sampleRate = rate
        var w = writeIndex
        var i = 0
        val inv = 1.0 / channels
        repeat(frames) {
            var sum = 0.0
            for (c in 0 until channels) sum += samples[i + c]
            ring[(w and Mask.toLong()).toInt()] = (sum * inv).toFloat()
            w++
            i += channels
        }
        writeIndex = w
    }

    fun read(into: FloatArray, count: Int = into.size): Boolean {
        if (!isLive) return false
        val lag = (sampleRate * PlaybackLagSeconds).toLong()
        val end = writeIndex - lag
        if (end < count) return false
        var r = end - count
        for (i in 0 until count) {
            into[i] = ring[(r and Mask.toLong()).toInt()]
            r++
        }
        return true
    }

    fun level(scratch: FloatArray): Float {
        if (!read(scratch)) return 0f
        var sum = 0.0
        for (v in scratch) sum += v * v
        return sqrt(sum / scratch.size).toFloat().coerceIn(0f, 1f)
    }
}

class SpectrumAnalyzer(private val bands: DoubleArray) {
    private val size = 2048
    private val samples = FloatArray(size)
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)
    private val window = DoubleArray(size) { 0.5 - 0.5 * cos(2.0 * PI * it / (size - 1)) }
    val levels = FloatArray(bands.size) { -90f }

    fun update(): Boolean {
        val live = AudioTap.read(samples)
        if (!live) {
            for (i in levels.indices) levels[i] = max(-90f, levels[i] - 2.5f)
            return false
        }
        for (i in 0 until size) {
            re[i] = samples[i] * window[i]
            im[i] = 0.0
        }
        fft(re, im)
        val rate = AudioTap.sampleRate.toDouble()
        val binHz = rate / size
        val norm = 2.0 / (size * 0.5)
        for (b in bands.indices) {
            val lo = if (b == 0) bands[0] / 1.12 else sqrt(bands[b - 1] * bands[b])
            val hi = if (b == bands.lastIndex) bands[b] * 1.12 else sqrt(bands[b] * bands[b + 1])
            var from = (lo / binHz).toInt().coerceIn(1, size / 2 - 1)
            val to = (hi / binHz).toInt().coerceIn(from, size / 2 - 1)
            var peak = 0.0
            while (from <= to) {
                val m = sqrt(re[from] * re[from] + im[from] * im[from]) * norm
                if (m > peak) peak = m
                from++
            }
            val db = (20.0 * log10(max(peak, 1e-6))).toFloat()
            levels[b] = if (db > levels[b]) levels[b] + (db - levels[b]) * 0.6f else max(db, levels[b] - 1.6f)
        }
        return true
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + half] * cr - im[i + k + half] * ci
                    val vi = re[i + k + half] * ci + im[i + k + half] * cr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + half] = ur - vr
                    im[i + k + half] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
