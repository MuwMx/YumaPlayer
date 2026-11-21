/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@UnstableApi
class EightDAudioProcessor(private val config: AudioFxConfigProvider) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var phase = 0.0
    private var smoothedEnabled = 0f
    private var smoothedDepth = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            sampleRate = 0
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val byteCount = input.remaining()
        if (byteCount == 0) return
        if (sampleRate <= 0) {
            val output = replaceOutputBuffer(byteCount)
            output.put(input)
            output.flip()
            return
        }

        val settings = config.eightD
        val targetEnabled = if (settings.enabled) 1f else 0f
        if (targetEnabled == 0f && smoothedEnabled == 0f) {
            val output = replaceOutputBuffer(byteCount)
            output.put(input)
            output.flip()
            return
        }

        val targetDepth = settings.depth.coerceIn(0f, 1f)
        val speed = settings.rotationSpeedHz.coerceIn(0.05f, 0.5f).toDouble()
        val smoothingStep = 1f / (sampleRate * SmoothingSeconds).coerceAtLeast(1f)
        val output = replaceOutputBuffer(byteCount).order(ByteOrder.nativeOrder())
        val frameCount = byteCount / StereoFrameBytes
        val phaseStep = TwoPi * speed / sampleRate

        repeat(frameCount) {
            val left = input.short.toInt()
            val right = input.short.toInt()
            smoothedEnabled = approach(smoothedEnabled, targetEnabled, smoothingStep)
            smoothedDepth = approach(smoothedDepth, targetDepth, smoothingStep)

            val pan = sin(phase) * smoothedDepth
            val theta = (pan + 1.0) * PI / 4.0
            val gainLeft = cos(theta)
            val gainRight = sin(theta)
            val processedLeft = left + (left * gainLeft - left) * smoothedEnabled
            val processedRight = right + (right * gainRight - right) * smoothedEnabled

            output.putShort(
                processedLeft.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
            )
            output.putShort(
                processedRight.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
            )
            phase = (phase + phaseStep) % TwoPi
        }
        output.put(input)
        output.flip()
    }

    override fun onFlush() {
        phase = 0.0
        smoothedEnabled = 0f
        smoothedDepth = config.eightD.depth.coerceIn(0f, 1f)
    }

    override fun onReset() {
        sampleRate = 0
        phase = 0.0
        smoothedEnabled = 0f
        smoothedDepth = 0f
    }

    private fun approach(current: Float, target: Float, step: Float): Float = when {
        current < target -> min(target, current + step)
        current > target -> max(target, current - step)
        else -> current
    }

    private companion object {
        const val StereoFrameBytes = 4
        const val SmoothingSeconds = 0.01f
        const val TwoPi = 2.0 * PI
    }
}
