/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.monochrome

import java.util.Locale

data class FlacInfo(val bitsPerSample: Int, val sampleRateHz: Int) {
    val isHiRes: Boolean get() = bitsPerSample > 16 || sampleRateHz > 48_000
    val shortLabel: String get() = if (isHiRes) "Hi-Res FLAC" else "FLAC"
    val label: String
        get() {
            val rate = if (sampleRateHz % 1000 == 0) {
                "${sampleRateHz / 1000}"
            } else {
                String.format(Locale.US, "%.1f", sampleRateHz / 1000.0)
            }
            return "$shortLabel · $bitsPerSample-bit / $rate kHz"
        }
}

object FlacStreamInfo {
    fun parse(bytes: ByteArray): FlacInfo? {
        if (bytes.size < 4 || bytes.copyOfRange(0, 4).decodeToString() != "fLaC") return null
        if (bytes.size < 4 + 4 + 18) return FlacInfo(16, 44_100)
        val b = bytes.copyOfRange(8, 26).map { it.toInt() and 0xff }
        val sampleRate = (b[10] shl 12) or (b[11] shl 4) or (b[12] shr 4)
        val bits = (((b[12] and 0x01) shl 4) or (b[13] shr 4)) + 1
        return if (sampleRate in 8_000..768_000 && bits in 8..32) {
            FlacInfo(bits, sampleRate)
        } else {
            FlacInfo(16, 44_100)
        }
    }
}
