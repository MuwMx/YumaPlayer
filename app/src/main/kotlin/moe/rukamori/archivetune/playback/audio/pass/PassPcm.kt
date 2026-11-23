/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToLong

internal object PassPcm {

    val Encodings = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        else -> 4
    }

    fun sourceBits(encoding: Int, fileEncoding: Int?): Int = when (fileEncoding) {
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        else -> when (encoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_32BIT -> 32
            else -> 24
        }
    }

    fun convert(
        input: ByteBuffer,
        encoding: Int,
        channels: Int,
        sourceBits: Int,
        outBits: Int,
        output: ByteBuffer,
        muted: Boolean,
    ): Boolean {
        input.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        val inBytes = bytesPerSample(encoding)
        val samples = minOf(input.remaining() / (inBytes * channels), output.remaining() / (outBits / 8 * channels)) * channels
        val floatScale = (1L shl (minOf(sourceBits, 24) - 1)).toDouble()
        val floatShift = 32 - minOf(sourceBits, 24)
        var exact = true
        repeat(samples) {
            val sample = when (encoding) {
                C.ENCODING_PCM_16BIT -> input.short.toInt() shl 16
                C.ENCODING_PCM_24BIT -> {
                    val low = input.get().toInt() and 0xff
                    val mid = input.get().toInt() and 0xff
                    val high = input.get().toInt()
                    ((high shl 16) or (mid shl 8) or low) shl 8
                }
                C.ENCODING_PCM_32BIT -> input.int
                else -> {
                    val value = input.float.toDouble()
                    if (!value.isFinite()) {
                        0
                    } else {
                        val step = (value * floatScale).roundToLong().coerceIn(-floatScale.toLong(), floatScale.toLong() - 1)
                        if (exact && abs(value * floatScale - step) > 1e-3) exact = false
                        step.toInt() shl floatShift
                    }
                }
            }
            put(output, if (muted) 0 else sample, outBits)
        }
        return exact
    }

    private fun put(output: ByteBuffer, sample: Int, bits: Int) {
        when (bits) {
            16 -> output.putShort((sample shr 16).toShort())
            24 -> {
                output.put((sample shr 8).toByte())
                output.put((sample shr 16).toByte())
                output.put((sample shr 24).toByte())
            }
            else -> output.putInt(sample)
        }
    }
}
