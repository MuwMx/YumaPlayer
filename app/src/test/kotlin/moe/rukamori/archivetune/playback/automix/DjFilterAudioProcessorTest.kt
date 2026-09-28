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

    @Test
    fun bypass_passesToneUnchanged() {
        val processor = configuredProcessor()
        assertTrue(processor.isBypassed)
        assertEquals(1.0, amplitude(processor, probeHz), 0.02)
    }

    @Test
    fun lowPass_isFourthOrderAndMinusThreeDbAtCutoff() {
        val processor = configuredProcessor()
        processor.lowPassCutoffHz = probeHz

        assertEquals(0.707, amplitude(processor, probeHz), 0.02)
        val oneOctaveUp = amplitude(processor, 2_000.0)
        val twoOctavesUp = amplitude(processor, 4_000.0)
        val firstSlope = -20.0 * log10(oneOctaveUp / 0.707)
        val secondSlope = -20.0 * log10(twoOctavesUp / oneOctaveUp)
        assertTrue("expected ~24 dB/octave, got $firstSlope", firstSlope in 20.0..28.0)
        assertTrue("expected ~24 dB/octave, got $secondSlope", secondSlope in 20.0..28.0)
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
    fun glide_snapsBackToBypassWhenAutomationClears() {
        val processor = configuredProcessor()
        processor.lowPassCutoffHz = 500.0
        feed(processor, 40_000, probeHz)
        assertTrue(amplitude(processor, 4_000.0) < 0.2)

        processor.clearAutomation()
        feed(processor, 2_400, probeHz)
        assertEquals(1.0, amplitude(processor, 4_000.0), 0.05)
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

    private fun amplitude(processor: DjFilterAudioProcessor, hz: Double): Double {
        processor.flush()
        val total = warmupFrames + measureFrames
        processor.queueInput(toneBuffer(0, total, hz))
        val out = processor.output ?: return 0.0
        var peak = 0.0
        var sumSquares = 0.0
        for (index in warmupFrames until total) {
            val value = out.getFloat(index * 4).toDouble()
            peak = maxOf(peak, abs(value))
            sumSquares += value * value
        }
        val frames = measureFrames
        return maxOf(peak, sqrt(sumSquares / frames) * sqrt(2.0)) / toneAmplitude
    }

    private fun toneBuffer(startFrame: Int, frames: Int, hz: Double): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) {
            val phase = (startFrame + frame).toDouble()
            buffer.putFloat((toneAmplitude * sin(2.0 * PI * hz * phase / sampleRate)).toFloat())
        }
        buffer.flip()
        return buffer
    }
}
