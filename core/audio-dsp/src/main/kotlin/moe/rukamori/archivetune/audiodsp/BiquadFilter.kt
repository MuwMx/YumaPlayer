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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Biquad {
    const val Stride = 5
    const val MaxGainDb = 12.0

    fun coefficients(band: ParametricEQBand, sampleRate: Int, out: DoubleArray, offset: Int = 0) =
        coefficients(band.filterType, band.frequency, band.gain, band.q, sampleRate, out, offset)

    fun coefficients(
        type: FilterType,
        frequency: Double,
        gainDb: Double,
        quality: Double,
        sampleRate: Int,
        out: DoubleArray,
        offset: Int = 0,
    ) {
        val nyquistSafe = frequency.coerceIn(10.0, sampleRate * 0.49)
        val omega = 2.0 * PI * nyquistSafe / sampleRate
        val sinW = sin(omega)
        val cosW = cos(omega)
        val q = quality.coerceIn(0.05, 30.0)
        val alpha = sinW / (2.0 * q)
        val a = 10.0.pow(gainDb / 40.0)

        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        when (type) {
            FilterType.PK -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha / a
            }
            FilterType.LSC -> {
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cosW + twoSqrtAAlpha)
                b1 = 2.0 * a * ((a - 1) - (a + 1) * cosW)
                b2 = a * ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha)
                a0 = (a + 1) + (a - 1) * cosW + twoSqrtAAlpha
                a1 = -2.0 * ((a - 1) + (a + 1) * cosW)
                a2 = (a + 1) + (a - 1) * cosW - twoSqrtAAlpha
            }
            FilterType.HSC -> {
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cosW + twoSqrtAAlpha)
                b1 = -2.0 * a * ((a - 1) + (a + 1) * cosW)
                b2 = a * ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha)
                a0 = (a + 1) - (a - 1) * cosW + twoSqrtAAlpha
                a1 = 2.0 * ((a - 1) - (a + 1) * cosW)
                a2 = (a + 1) - (a - 1) * cosW - twoSqrtAAlpha
            }
            FilterType.LPQ -> {
                b0 = (1.0 - cosW) / 2.0
                b1 = 1.0 - cosW
                b2 = (1.0 - cosW) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha
            }
            FilterType.HPQ -> {
                b0 = (1.0 + cosW) / 2.0
                b1 = -(1.0 + cosW)
                b2 = (1.0 + cosW) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha
            }
        }
        out[offset] = b0 / a0
        out[offset + 1] = b1 / a0
        out[offset + 2] = b2 / a0
        out[offset + 3] = a1 / a0
        out[offset + 4] = a2 / a0
    }

    fun magnitudeDb(c: DoubleArray, offset: Int, frequency: Double, sampleRate: Int): Double {
        val w = 2.0 * PI * frequency / sampleRate
        val cos1 = cos(w)
        val cos2 = cos(2.0 * w)
        return magnitudeDb(c, offset, cos1, cos2)
    }

    fun magnitudeDb(c: DoubleArray, offset: Int, cos1: Double, cos2: Double): Double {
        val b0 = c[offset]
        val b1 = c[offset + 1]
        val b2 = c[offset + 2]
        val a1 = c[offset + 3]
        val a2 = c[offset + 4]
        val num = b0 * b0 + b1 * b1 + b2 * b2 + 2.0 * (b0 * b1 + b1 * b2) * cos1 + 2.0 * b0 * b2 * cos2
        val den = 1.0 + a1 * a1 + a2 * a2 + 2.0 * (a1 + a1 * a2) * cos1 + 2.0 * a2 * cos2
        return 10.0 * log10(max(num, 1e-20) / max(den, 1e-20))
    }

    fun isAudible(band: ParametricEQBand): Boolean = band.enabled && when (band.filterType) {
        FilterType.LPQ, FilterType.HPQ -> true
        else -> kotlin.math.abs(band.gain) >= 0.05
    }
}

class ResponseCurve(val frequencies: DoubleArray, private val sampleRate: Int = 48_000) {
    private val cos1 = DoubleArray(frequencies.size) { cos(2.0 * PI * frequencies[it] / sampleRate) }
    private val cos2 = DoubleArray(frequencies.size) { cos(4.0 * PI * frequencies[it] / sampleRate) }
    private val scratch = DoubleArray(Biquad.Stride)

    fun accumulate(band: ParametricEQBand, into: DoubleArray) {
        if (!Biquad.isAudible(band)) return
        Biquad.coefficients(band, sampleRate, scratch)
        for (i in frequencies.indices) into[i] += Biquad.magnitudeDb(scratch, 0, cos1[i], cos2[i])
    }

    fun response(bands: List<ParametricEQBand>, into: DoubleArray = DoubleArray(frequencies.size)): DoubleArray {
        into.fill(0.0)
        bands.forEach { accumulate(it, into) }
        return into
    }

    companion object {
        fun logSpaced(count: Int, from: Double = 20.0, to: Double = 20_000.0): DoubleArray {
            val lo = log10(from)
            val hi = log10(to)
            return DoubleArray(count) { 10.0.pow(lo + (hi - lo) * it / (count - 1)) }
        }
    }
}
