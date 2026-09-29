package moe.rukamori.archivetune.playback.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor

class DjFilterAudioProcessorTest {

    private val sampleRate = 48_000
    private val toneAmplitude = 0.5
    private val warmupFrames = 24_000
    private val measureFrames = 4_800
    private val probeHz = 1_000.0
    private val ENCODING_24BIT_PACKED = 21

    @Test
    fun bypass_passesToneUnchanged() {
        val processor = configuredProcessor()
        assertTrue(processor.isBypassed)
        assertEquals(1.0, amplitude(processor, probeHz), 0.02)
    }

    @Test
    fun lowPass_isSecondOrderButterworth() {
        val processor = configuredProcessor()
        val cutoff = 1000.0
        processor.lowPassCutoffHz = cutoff
        val passband = amplitude(processor, 40.0)
        // |H| for an nth-order Butterworth is 1/sqrt(1 + (f/fc)^(2n)); n = 2 here.
        for ((octaves, expected) in listOf(0.0 to -3.01, 1.0 to -12.30, 2.0 to -24.08)) {
            val db = 20.0 * log10(amplitude(processor, cutoff * 2.0.pow(octaves.toInt())) / passband)
            assertEquals("butterworth mismatch at +$octaves octaves", expected, db, 0.8)
        }
    }

    @Test
    fun lowPass_neverRisesAboveItsPassbandAcrossTheSweep() {
        val processor = configuredProcessor()
        processor.lowPassCutoffHz = 2000.0
        val passband = amplitude(processor, 40.0)
        var previous = Double.MAX_VALUE
        for (hz in 40..10_000 step 40) {
            val level = amplitude(processor, hz.toDouble())
            assertTrue("response rose at $hz Hz: $level > $previous", level <= previous + 1e-9)
            assertTrue("response exceeded the passband at $hz Hz", level <= passband + 1e-6)
            previous = level
        }
    }

    @Test
    fun highPass_rejectsLowEndAndPassesUpper() {
        val processor = configuredProcessor()
        processor.highPassHz = probeHz

        assertTrue(amplitude(processor, 100.0) < 0.02)
        assertEquals(0.707, amplitude(processor, probeHz), 0.02)
        assertTrue(amplitude(processor, 8_000.0) > 0.95)
    }

    @Test
    fun blockRunsOnOneCoefficientSetEvenWhenAutomationMovesMidBlock() {
        val processor = configuredProcessor()
        processor.lowPassCutoffHz = 20000.0
        val wide = amplitude(processor, 4000.0)
        processor.lowPassCutoffHz = 500.0
        val narrow = amplitude(processor, 4000.0)
        assertTrue("automation must reach the filter", narrow < wide * 0.5)
        processor.lowPassCutoffHz = 20000.0
        assertEquals("bypass must return exactly", wide, amplitude(processor, 4000.0), 1e-3)
    }

    @Test
    fun shelfReachesItsTargetWithinASingleBlock() {
        val processor = configuredProcessor()
        val passband = amplitude(processor, 8000.0)
        processor.bassGainDb = -24.0
        val cut = amplitude(processor, 8000.0)
        assertTrue("passband must be unaffected by a shelf cut", abs(cut - passband) / passband < 0.02)
        assertTrue("the low end must actually be cut", amplitude(processor, 40.0) < passband * 0.1)
    }

    @Test
    fun twentyFourBitPacked_isFilteredNotBypassed() {
        val processor = DjFilterAudioProcessor()
        val configured = processor.configure(AudioFormat(sampleRate, 1, ENCODING_24BIT_PACKED))
        assertEquals(ENCODING_24BIT_PACKED, configured.encoding)
        assertTrue(processor.isActive)

        processor.lowPassCutoffHz = probeHz
        val filtered = amplitude(processor, 4_000.0, ENCODING_24BIT_PACKED)
        assertTrue("24-bit path must attenuate, got $filtered", filtered < 0.2)
    }

    @Test
    fun twentyFourBitPacked_roundTripsFullScale() {
        val processor = DjFilterAudioProcessor()
        processor.configure(AudioFormat(sampleRate, 1, ENCODING_24BIT_PACKED))
        assertEquals(1.0, amplitude(processor, probeHz, ENCODING_24BIT_PACKED), 0.02)
    }

    private fun configuredProcessor(): DjFilterAudioProcessor {
        val processor = DjFilterAudioProcessor()
        val configured = processor.configure(AudioFormat(sampleRate, 1, C.ENCODING_PCM_FLOAT))
        assertEquals(C.ENCODING_PCM_FLOAT, configured.encoding)
        return processor
    }

    private fun feed(processor: DjFilterAudioProcessor, frames: Int, hz: Double) {
        processor.queueInput(toneBuffer(0, frames, hz))
        processor.output?.position(0)
    }

    private fun amplitude(processor: DjFilterAudioProcessor, hz: Double, encoding: Int = C.ENCODING_PCM_FLOAT): Double {
        processor.flush()
        val total = warmupFrames + measureFrames
        processor.queueInput(toneBuffer(0, total, hz, encoding))
        val out = processor.output ?: return 0.0
        var peak = 0.0
        var sumSquares = 0.0
        val bytesPerSample = if (encoding == ENCODING_24BIT_PACKED) 3 else 4
        for (index in warmupFrames until total) {
            val offset = index * bytesPerSample
            val value = if (encoding == ENCODING_24BIT_PACKED) {
                val low = out.get(offset).toInt() and 0xFF
                val mid = out.get(offset + 1).toInt() and 0xFF
                val high = out.get(offset + 2).toInt()
                val packed = low or (mid shl 8) or (high shl 16)
                val signed = if (packed and 0x800000 != 0) packed or -0x1000000 else packed
                signed / 8388608.0
            } else {
                out.getFloat(offset).toDouble()
            }
            peak = maxOf(peak, abs(value))
            sumSquares += value * value
        }
        val frames = measureFrames
        return maxOf(peak, sqrt(sumSquares / frames) * sqrt(2.0)) / toneAmplitude
    }

    private fun toneBuffer(startFrame: Int, frames: Int, hz: Double, encoding: Int = C.ENCODING_PCM_FLOAT): ByteBuffer {
        val bytesPerSample = if (encoding == ENCODING_24BIT_PACKED) 3 else 4
        val buffer = ByteBuffer.allocateDirect(frames * bytesPerSample).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) {
            val phase = (startFrame + frame).toDouble()
            val value = toneAmplitude * sin(2.0 * PI * hz * phase / sampleRate)
            if (encoding == ENCODING_24BIT_PACKED) {
                val packed = (value * 8388608.0).toInt()
                buffer.put((packed and 0xFF).toByte())
                buffer.put(((packed shr 8) and 0xFF).toByte())
                buffer.put(((packed shr 16) and 0xFF).toByte())
            } else {
                buffer.putFloat(value.toFloat())
            }
        }
        buffer.flip()
        return buffer
    }
}
