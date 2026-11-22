/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
@Suppress("DEPRECATION")
class StageAudioProcessor(private val config: AudioFxConfigProvider) : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 0
    private var channels = 0
    private var handle = 0L

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var directCopy: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var pushed: StageParams? = null
    private var idle = true

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return notSet()
        if (!StageNative.loaded) {
            return if (inputAudioFormat.channelCount == 2) {
                pendingFormat = inputAudioFormat
                inputAudioFormat
            } else notSet()
        }
        return when (inputAudioFormat.channelCount) {
            2 -> {
                pendingFormat = inputAudioFormat
                inputAudioFormat
            }
            in 3..8 -> if (config.stage.enabled) {
                pendingFormat = inputAudioFormat
                AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
            } else notSet()
            else -> notSet()
        }
    }

    private fun notSet(): AudioProcessor.AudioFormat {
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return

        if (StageNative.loaded) {
            if (handle == 0L || channels == 0) {
                inputBuffer.position(inputBuffer.limit())
                return
            }
            val params = config.stage
            if (params !== pushed) {
                StageNative.setParams(handle, params.values)
                pushed = params
            }
            val frames = bytes / (2 * channels)
            if (channels == 2 && idle && !params.enabled) {
                val out = replaceOutputBuffer(bytes)
                out.put(inputBuffer)
                out.flip()
                return
            }

            val source = if (inputBuffer.isDirect) inputBuffer else copyToDirect(inputBuffer)
            val out = replaceOutputBuffer(frames * 4)
            idle = StageNative.process(handle, source, source.position(), out, frames)
            out.position(0)
            out.limit(frames * 4)
            inputBuffer.position(inputBuffer.limit())
            if (!idle) StageNative.levels(handle, config.stageLevels)
        } else {
            processKotlinFallback(inputBuffer, bytes)
        }
    }

    private fun processKotlinFallback(inputBuffer: ByteBuffer, bytes: Int) {
        if (channels != 2) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val params = config.stage
        if (!params.enabled) {
            val out = replaceOutputBuffer(bytes)
            out.put(inputBuffer)
            out.flip()
            for (i in config.stageLevels.indices) {
                config.stageLevels[i] = 0f
            }
            return
        }

        val frames = bytes / 4
        val values = params.values
        if (values.size < StageNativeConstants.ParamCount) {
            val out = replaceOutputBuffer(bytes)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val listenerX = values[StageNativeConstants.ListenerX]
        val listenerY = values[StageNativeConstants.ListenerY]
        val listenerZ = values[StageNativeConstants.ListenerZ]
        val listenerYaw = values[StageNativeConstants.ListenerYaw]
        val roomAmount = values[StageNativeConstants.RoomAmount].coerceIn(0f, 1f)
        val master = values[StageNativeConstants.Master]

        val cosYaw = cos(-listenerYaw)
        val sinYaw = sin(-listenerYaw)
        val directRoomGain = (1f - 0.25f * roomAmount)

        class SourceSlot(val sourceIdx: Int, val gainL: Float, val gainR: Float, val gain: Float)
        val activeSlots = ArrayList<SourceSlot>(StageNativeConstants.ObjectCount)
        val pcmScale = 1f / 32768f

        for (source in 0 until StageNativeConstants.Kinds) {
            for (copy in 0 until StageNativeConstants.Copies) {
                val slot = source * StageNativeConstants.Copies + copy
                val at = StageNativeConstants.Objects + slot * StageNativeConstants.ObjectStride
                val objX = values[at]
                val objY = values[at + 1]
                val objZ = values[at + 2]
                val objGain = values[at + 3]
                val objWidth = values[at + 4]

                if (objGain <= 0.0001f) continue

                val dx = objX - listenerX
                val dy = objY - listenerY
                val dz = objZ - listenerZ
                val relX = dx * cosYaw - dz * sinYaw
                val relZ = dx * sinYaw + dz * cosYaw
                val dist = sqrt(relX * relX + dy * dy + relZ * relZ).coerceAtLeast(0.1f)
                val distGain = (1f / (1f + 0.35f * (dist - 0.5f).coerceAtLeast(0f))).coerceIn(0.1f, 1.5f)
                val pan = (relX / dist).coerceIn(-1f, 1f)
                val panAngle = (pan + 1f) * (PI.toFloat() / 4f)
                val baseL = cos(panAngle)
                val baseR = sin(panAngle)
                val totalG = objGain * distGain * directRoomGain * master
                val gainL = (baseL + (1f - baseL) * (objWidth * 0.2f)) * totalG
                val gainR = (baseR + (1f - baseR) * (objWidth * 0.2f)) * totalG

                activeSlots.add(SourceSlot(source, gainL, gainR, totalG))
            }
        }

        val out = replaceOutputBuffer(bytes)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val sourcePower = FloatArray(StageNativeConstants.Kinds)

        for (f in 0 until frames) {
            val inL = input.getShort() * pcmScale
            val inR = input.getShort() * pcmScale

            var outL = 0f
            var outR = 0f

            for (i in 0 until activeSlots.size) {
                val slot = activeSlots[i]
                val s = when (slot.sourceIdx) {
                    StageSource.Vocal.ordinal -> (inL + inR) * 0.5f
                    StageSource.Center.ordinal -> (inL + inR) * 0.5f
                    StageSource.Left.ordinal -> inL
                    StageSource.Right.ordinal -> inR
                    StageSource.Ambience.ordinal -> (inL - inR) * 0.5f
                    StageSource.Sub.ordinal -> (inL + inR) * 0.5f
                    else -> (inL + inR) * 0.5f
                }
                outL += s * slot.gainL
                outR += s * slot.gainR
                val weighted = s * slot.gain
                sourcePower[slot.sourceIdx] += weighted * weighted
            }

            val pcmL = (outL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            val pcmR = (outR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            out.putShort(pcmL)
            out.putShort(pcmR)
        }
        out.flip()

        val invFrames = if (frames > 0) 1f / frames else 0f
        for (s in 0 until StageNativeConstants.Kinds) {
            val rms = (sqrt(sourcePower[s] * invFrames) * 3f).coerceIn(0f, 1f)
            config.stageLevels[s] = config.stageLevels[s] * 0.75f + rms * 0.25f
        }
    }

    private fun copyToDirect(input: ByteBuffer): ByteBuffer {
        val size = input.remaining()
        if (directCopy.capacity() < size) directCopy = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        directCopy.clear()
        directCopy.put(input.duplicate())
        directCopy.flip()
        return directCopy
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        val format = pendingFormat
        if (format == AudioProcessor.AudioFormat.NOT_SET) return
        if (StageNative.loaded) {
            if (handle == 0L || format.sampleRate != sampleRate || format.channelCount != channels) {
                release()
                sampleRate = format.sampleRate
                channels = format.channelCount
                handle = StageNative.create(sampleRate, channels)
            } else {
                StageNative.clear(handle)
            }
        } else {
            sampleRate = format.sampleRate
            channels = format.channelCount
        }
        pushed = null
        idle = true
    }

    override fun reset() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        release()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        channels = 0
    }

    private fun release() {
        if (handle != 0L) StageNative.destroy(handle)
        handle = 0L
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
