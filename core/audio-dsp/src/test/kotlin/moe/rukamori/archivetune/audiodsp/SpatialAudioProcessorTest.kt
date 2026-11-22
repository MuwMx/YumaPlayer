/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class SpatialAudioProcessorTest {

    private class TestConfigProvider(
        override var spatial: SpatialParams = SpatialParams(),
        override var reverb: ReverbParams = ReverbParams(),
        override var loudness: LoudnessParams = LoudnessParams(),
        override var eightD: EightDParams = EightDParams(),
        override var stage: StageParams = StageParams.Off,
        override var stageLevels: FloatArray = FloatArray(StageNativeConstants.Kinds),
        override var loudnessReductionDb: Float = 0f,
    ) : AudioFxConfigProvider

    private fun createStereoBuffer(frames: Int, freqHz: Double = 440.0, sampleRate: Int = 48000): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val sample = (sin(2.0 * PI * freqHz * i / sampleRate) * 16000.0).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort((-sample).toShort())
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun crossfeed_processesFramesWithoutExceptionAndProducesBoundedOutput() {
        val config = TestConfigProvider(
            spatial = SpatialParams(
                enabled = true,
                mode = SpatialMode.Crossfeed,
                cutoffHz = 700f,
                feedDb = 4.5f,
                amount = 1f,
            ),
        )
        val processor = SpatialAudioProcessor(config)
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(format, processor.configure(format))
        processor.flush()

        val frames = 10000
        val input = createStereoBuffer(frames)
        processor.queueInput(input)
        val output = processor.output

        assertEquals(frames * 4, output.remaining())
        while (output.hasRemaining()) {
            val sample = output.short
            assertTrue(sample in Short.MIN_VALUE..Short.MAX_VALUE)
        }

        config.spatial = config.spatial.copy(cutoffHz = 1200f, feedDb = 6f)
        val nextInput = createStereoBuffer(frames, freqHz = 880.0)
        processor.queueInput(nextInput)
        val nextOutput = processor.output

        assertEquals(frames * 4, nextOutput.remaining())
        while (nextOutput.hasRemaining()) {
            val sample = nextOutput.short
            assertTrue(sample in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }

    @Test
    fun speakers_processesFramesWithoutExceptionAndProducesBoundedOutput() {
        val config = TestConfigProvider(
            spatial = SpatialParams(
                enabled = true,
                mode = SpatialMode.Speakers,
                speakerAngle = 30f,
                amount = 1f,
            ),
        )
        val processor = SpatialAudioProcessor(config)
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(format, processor.configure(format))
        processor.flush()

        val frames = 5000
        val input = createStereoBuffer(frames)
        processor.queueInput(input)
        val output = processor.output

        assertEquals(frames * 4, output.remaining())
        while (output.hasRemaining()) {
            val sample = output.short
            assertTrue(sample in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }

    @Test
    fun toPcm_handlesNonFiniteSafely() {
        assertEquals(0.toShort(), SpatialAudioProcessor.toPcm(Float.NaN))
        assertEquals(0.toShort(), SpatialAudioProcessor.toPcm(Float.POSITIVE_INFINITY))
        assertEquals(0.toShort(), SpatialAudioProcessor.toPcm(Float.NEGATIVE_INFINITY))
    }
}
