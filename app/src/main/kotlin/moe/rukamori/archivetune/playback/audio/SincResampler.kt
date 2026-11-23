/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

class SincResampler private constructor(
    val inRate: Int,
    val outRate: Int,
    private val channels: Int,
    private val table: Table,
) {
    val taps: Int get() = table.taps
    val passbandHz: Double get() = table.passbandHz
    val stopbandHz: Double get() = minOf(inRate, outRate) / 2.0

    private var history = Array(channels) { FloatArray(InitialCapacity) }
    private var stored = 0
    private var base = 0L
    private var nextInput = 0L
    private var phase = 0

    init {
        reset()
    }

    fun reset() {
        stored = table.taps - 1
        for (channel in history) channel.fill(0f, 0, stored)
        base = -stored.toLong()
        nextInput = 0L
        phase = 0
    }

    fun maxOutputFrames(frames: Int): Int = ((frames.toLong() + 2) * table.up / table.down + 2).toInt()

    fun process(input: Array<FloatArray>, frames: Int, output: Array<FloatArray>): Int {
        append(input, frames)
        val taps = table.taps
        val up = table.up
        val down = table.down
        val coefficients = table.coefficients
        val last = base + stored - 1
        var written = 0
        while (nextInput <= last) {
            val newest = (nextInput - base).toInt()
            val offset = phase * taps
            for (c in 0 until channels) {
                val x = history[c]
                var acc = 0f
                var j = 0
                while (j < taps) {
                    acc += coefficients[offset + j] * x[newest - j]
                    j++
                }
                output[c][written] = acc
            }
            written++
            phase += down
            nextInput += phase / up
            phase %= up
        }
        val keepFrom = (nextInput - (taps - 1) - base).toInt().coerceIn(0, stored)
        if (keepFrom > 0) {
            for (channel in history) System.arraycopy(channel, keepFrom, channel, 0, stored - keepFrom)
            stored -= keepFrom
            base += keepFrom
        }
        return written
    }

    private fun append(input: Array<FloatArray>, frames: Int) {
        if (stored + frames > history[0].size) {
            val size = maxOf(stored + frames, history[0].size * 2)
            history = Array(channels) { c -> history[c].copyOf(size) }
        }
        for (c in 0 until channels) System.arraycopy(input[c], 0, history[c], stored, frames)
        stored += frames
    }

    private class Table(val up: Int, val down: Int, val taps: Int, val passbandHz: Double, val coefficients: FloatArray)

    companion object {
        const val StopbandDb = 120.0
        private const val BaseTaps = 192
        private const val InitialCapacity = 8192
        private const val MaxCoefficients = 200_000

        private val tables = ConcurrentHashMap<Long, Table>()

        fun create(inRate: Int, outRate: Int, channels: Int): SincResampler? {
            if (inRate <= 0 || outRate <= 0 || inRate == outRate || channels <= 0) return null
            val key = inRate.toLong() shl 32 or outRate.toLong()
            val table = tables[key] ?: design(inRate, outRate)?.also { tables[key] = it } ?: return null
            return SincResampler(inRate, outRate, channels, table)
        }

        private fun design(inRate: Int, outRate: Int): Table? {
            val gcd = gcd(inRate, outRate)
            val up = outRate / gcd
            val down = inRate / gcd
            val taps = ceil(BaseTaps * maxOf(1.0, inRate.toDouble() / outRate)).toInt()
            val length = up.toLong() * taps
            if (length > MaxCoefficients) return null

            val transitionHz = (StopbandDb - 7.95) / (2.285 * 2 * PI * taps) * inRate
            val stopHz = minOf(inRate, outRate) / 2.0
            val passbandHz = stopHz - transitionHz
            val cutoff = (stopHz - transitionHz / 2) / (up.toDouble() * inRate)
            val beta = 0.1102 * (StopbandDb - 8.7)
            val i0Beta = besselI0(beta)
            val centre = (length - 1) / 2.0

            val coefficients = FloatArray(length.toInt())
            for (k in 0 until length.toInt()) {
                val t = k - centre
                val sinc = if (t == 0.0) 2 * cutoff else sin(2 * PI * cutoff * t) / (PI * t)
                val ratio = t / centre
                val window = besselI0(beta * sqrt((1 - ratio * ratio).coerceAtLeast(0.0))) / i0Beta
                val p = k % up
                val j = k / up
                coefficients[p * taps + j] = (sinc * window * up).toFloat()
            }
            return Table(up, down, taps, passbandHz, coefficients)
        }

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val half = x / 2
            var k = 1
            while (k < 64) {
                term *= (half / k) * (half / k)
                sum += term
                if (term < sum * 1e-12) break
                k++
            }
            return sum
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
