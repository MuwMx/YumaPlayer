package moe.rukamori.archivetune.playback.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor

/**
 * The shelf had its (A-1)*cos(w0) term negated in b0 and b2, which left the passband at half the
 * requested cut instead of unity. Nothing covered the absolute response, so it went unnoticed.
 */
class ShelfResponseTest {

    private val sampleRate = 48_000

    @Test
    fun passband_staysAtUnityWhileTheBassIsCut() {
        for (db in intArrayOf(-3, -6, -12, -18, -24)) {
            val atCorner = responseAt(db, 8_000.0)
            assertTrue("passband is ${atCorner} dB at $db dB cut, expected ~0", abs(atCorner) < 0.5)
        }
    }

    @Test
    fun lowEnd_actuallyReachesTheRequestedCut() {
        for (db in intArrayOf(-6, -12, -18, -24)) {
            val low = responseAt(db, 40.0)
            assertTrue("low end is $low dB at $db dB requested", abs(low - db) < 1.5)
        }
    }

    @Test
    fun response_isFlatAboveTheCorner() {
        val db = -24
        val reference = responseAt(db, 8_000.0)
        for (hz in intArrayOf(1_000, 2_000, 4_000, 8_000, 12_000)) {
            val value = responseAt(db, hz.toDouble())
            assertTrue("shelf tilted at $hz Hz: ${value} vs $reference", abs(value - reference) < 0.5)
        }
    }

    private fun responseAt(gainDb: Int, hz: Double): Double {
        val processor = DjFilterAudioProcessor()
        processor.configure(AudioFormat(sampleRate, 1, C.ENCODING_PCM_FLOAT))
        processor.bassGainDb = gainDb.toDouble()
        processor.flush()
        val total = sampleRate
        val buffer = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder())
        for (frame in 0 until total) {
            buffer.putFloat((0.5 * sin(2.0 * PI * hz * frame / sampleRate)).toFloat())
        }
        buffer.flip()
        processor.queueInput(buffer)
        val out = processor.output
        var sumSquares = 0.0
        var peak = 0.0
        if (out != null) {
            val from = sampleRate / 2
            for (index in from until total) {
                val value = out.getFloat(index * 4).toDouble()
                sumSquares += value * value
                peak = maxOf(peak, abs(value))
            }
        }
        val amplitude = maxOf(peak, sqrt(sumSquares / (total - sampleRate / 2)) * sqrt(2.0))
        return 20.0 * log10(amplitude / 0.5)
    }
}
