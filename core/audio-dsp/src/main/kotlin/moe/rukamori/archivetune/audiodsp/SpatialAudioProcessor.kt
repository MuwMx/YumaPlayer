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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

@UnstableApi
class SpatialAudioProcessor(private val config: AudioFxConfigProvider) : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var pendingSurround = false
    private var sampleRate = 0
    private var inChannels = 0
    private var surround = false

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var amount = 0f
    private var monoAmount = 0f
    private var crossfeed: Crossfeed? = null
    private var head: HeadRenderer? = null
    private var headAngle = Float.NaN

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val params = config.spatial
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return notSet()
        return when (inputAudioFormat.channelCount) {
            2 -> {
                pendingSurround = false
                pendingFormat = inputAudioFormat
                inputAudioFormat
            }
            in 3..8 -> if (params.surround) {
                pendingSurround = true
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
        if (inChannels == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val frames = bytes / (2 * inChannels)
        val params = config.spatial
        val scale = 1f / 32768f

        if (surround) {
            val renderer = head ?: HeadRenderer(sampleRate, surroundAzimuths(inChannels)).also { head = it }
            val out = replaceOutputBuffer(frames * 4)
            val frame = FloatArray(inChannels)
            for (f in 0 until frames) {
                for (c in 0 until inChannels) frame[c] = inputBuffer.getShort() * scale
                renderer.render(frame)
                out.putShort(toPcm(renderer.left * SurroundGain))
                out.putShort(toPcm(renderer.right * SurroundGain))
            }
            out.flip()
            return
        }

        val target = if (params.enabled) params.amount.coerceIn(0f, 1f) else 0f
        val monoTarget = if (params.mono) 1f else 0f
        val out = replaceOutputBuffer(bytes)
        if (target == 0f && amount == 0f && monoTarget == 0f && monoAmount == 0f) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val step = 1f / (sampleRate * ToggleSeconds)
        when (params.mode) {
            SpatialMode.Crossfeed -> {
                val cf = crossfeed?.takeIf { it.matches(params.cutoffHz, params.feedDb) }
                    ?: Crossfeed(sampleRate, params.cutoffHz, params.feedDb, crossfeed).also { crossfeed = it }
                for (f in 0 until frames) {
                    var l = inputBuffer.getShort() * scale
                    var r = inputBuffer.getShort() * scale
                    monoAmount = glide(monoAmount, monoTarget, step)
                    if (monoAmount > 0f) {
                        val mono = (l + r) * 0.5f
                        l += (mono - l) * monoAmount
                        r += (mono - r) * monoAmount
                    }
                    amount = glide(amount, target, step)
                    cf.feed(l, r)
                    val wetL = cf.outLeft
                    val wetR = cf.outRight
                    out.putShort(toPcm(l + (wetL - l) * amount))
                    out.putShort(toPcm(r + (wetR - r) * amount))
                }
            }
            SpatialMode.Speakers -> {
                val angle = params.speakerAngle.coerceIn(15f, 60f)
                val renderer = head?.takeIf { headAngle == angle }
                    ?: HeadRenderer(sampleRate, floatArrayOf(-angle, angle)).also {
                        head = it
                        headAngle = angle
                    }
                val stereo = FloatArray(2)
                for (f in 0 until frames) {
                    var l = inputBuffer.getShort() * scale
                    var r = inputBuffer.getShort() * scale
                    monoAmount = glide(monoAmount, monoTarget, step)
                    if (monoAmount > 0f) {
                        val mono = (l + r) * 0.5f
                        l += (mono - l) * monoAmount
                        r += (mono - r) * monoAmount
                    }
                    amount = glide(amount, target, step)
                    stereo[0] = l
                    stereo[1] = r
                    renderer.render(stereo)
                    out.putShort(toPcm(l + (renderer.left - l) * amount))
                    out.putShort(toPcm(r + (renderer.right - r) * amount))
                }
            }
        }
        out.flip()
    }

    private fun glide(current: Float, target: Float, step: Float): Float =
        if (target > current) minOf(target, current + step) else maxOf(target, current - step)

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
        if (format != AudioProcessor.AudioFormat.NOT_SET) {
            sampleRate = format.sampleRate
            inChannels = format.channelCount
            surround = pendingSurround
        }
        crossfeed?.clear()
        head?.clear()
        val params = config.spatial
        amount = if (params.enabled) params.amount.coerceIn(0f, 1f) else 0f
        monoAmount = if (params.mono) 1f else 0f
    }

    override fun reset() {
        flush()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        inChannels = 0
        surround = false
        crossfeed = null
        head = null
        headAngle = Float.NaN
    }

    private class Crossfeed(
        sampleRate: Int,
        private val cutoffHz: Float,
        private val feedDb: Float,
        previous: Crossfeed? = null,
    ) {
        private val a1: Float
        private val b0: Float
        private val b1: Float
        private val gain: Float
        private val delayLineL = FloatArray(CrossfeedDelay)
        private val delayLineR = FloatArray(CrossfeedDelay)
        private var delayPos = 0
        private var loL = 0f
        private var loR = 0f
        private var hiL = 0f
        private var hiR = 0f
        private var asisL = 0f
        private var asisR = 0f
        var outLeft = 0f
            private set
        var outRight = 0f
            private set

        init {
            val fc = cutoffHz.coerceIn(200f, 2000f)
            val w = 2.0 * PI * fc / sampleRate
            val cosW = cos(w)
            val alpha = sin(w) / (2.0 * 0.707)
            val norm = (1.0 + alpha).toFloat()
            a1 = ((-2.0 * cosW) / norm).toFloat()
            b0 = ((1.0 - cosW) / 2.0 / norm).toFloat()
            b1 = ((1.0 - cosW) / norm).toFloat()
            gain = 10f.pow(-abs(feedDb).coerceIn(1f, 12f) / 20f)
            if (previous != null) {
                loL = previous.loL; loR = previous.loR
                hiL = previous.hiL; hiR = previous.hiR
            }
        }

        fun matches(cutoff: Float, feed: Float): Boolean = cutoff == cutoffHz && feed == feedDb

        fun feed(inL: Float, inR: Float) {
            delayLineL[delayPos] = inL
            delayLineR[delayPos] = inR
            val readPos = if (delayPos + 1 == CrossfeedDelay) 0 else delayPos + 1
            val delayedL = delayLineL[readPos]
            val delayedR = delayLineR[readPos]
            delayPos = if (delayPos + 1 == CrossfeedDelay) 0 else delayPos + 1

            loL = b0 * delayedL + b1 * asisL - a1 * loL
            loR = b0 * delayedR + b1 * asisR - a1 * loR
            asisL = delayedL
            asisR = delayedR

            hiL = inL - loL
            hiR = inR - loR

            outLeft = (hiL + loR) * gain
            outRight = (hiR + loL) * gain
        }

        fun clear() {
            loL = 0f; loR = 0f; hiL = 0f; hiR = 0f; asisL = 0f; asisR = 0f
        }
    }

    private class HeadRenderer(sampleRate: Int, private val azimuths: FloatArray) {
        private val n = azimuths.size
        private val line = Array(n) { FloatArray(DelaySize) }
        private var write = 0
        private val delay = FloatArray(n * 2)
        private val b0 = FloatArray(n * 2)
        private val b1 = FloatArray(n * 2)
        private val a1 = FloatArray(n * 2)
        private val x1 = FloatArray(n * 2)
        private val y1 = FloatArray(n * 2)
        private val weight = FloatArray(n)
        var left = 0f
            private set
        var right = 0f
            private set

        init {
            val w0 = SpeedOfSound / HeadRadius
            val k = 2.0 * sampleRate
            val raw = DoubleArray(n * 2)
            for (s in 0 until n) {
                val az = azimuths[s]
                weight[s] = when {
                    az.isNaN() -> 0.5f
                    abs(az) < 1f -> 0.71f
                    else -> 1f
                }
                for (ear in 0..1) {
                    val i = s * 2 + ear
                    if (az.isNaN()) {
                        b0[i] = 1f; b1[i] = 0f; a1[i] = 0f
                        continue
                    }
                    val earAz = if (ear == 0) -90.0 else 90.0
                    val theta = Math.toRadians(abs(wrap(az - earAz)))
                    val alpha = (1 + AlphaMin / 2) + (1 - AlphaMin / 2) * cos(theta / ThetaMin * PI)
                    val norm = 2 * w0 + k
                    b0[i] = ((2 * w0 + alpha * k) / norm).toFloat()
                    b1[i] = ((2 * w0 - alpha * k) / norm).toFloat()
                    a1[i] = ((2 * w0 - k) / norm).toFloat()
                    raw[i] = if (theta < PI / 2) -HeadRadius / SpeedOfSound * cos(theta)
                    else HeadRadius / SpeedOfSound * (theta - PI / 2)
                }
            }
            val earliest = raw.minOrNull() ?: 0.0
            for (i in 0 until n * 2) {
                delay[i] = if (azimuths[i / 2].isNaN()) 0f else ((raw[i] - earliest) * sampleRate).toFloat()
                    .coerceIn(0f, (DelaySize - 2).toFloat())
            }
        }

        fun render(frame: FloatArray) {
            var l = 0f
            var r = 0f
            for (s in 0 until n) {
                line[s][write] = frame[s]
                for (ear in 0..1) {
                    val i = s * 2 + ear
                    val d = delay[i]
                    val whole = d.toInt()
                    val frac = d - whole
                    var p0 = write - whole
                    if (p0 < 0) p0 += DelaySize
                    val p1 = if (p0 == 0) DelaySize - 1 else p0 - 1
                    val x = line[s][p0] + (line[s][p1] - line[s][p0]) * frac
                    val y = b0[i] * x + b1[i] * x1[i] - a1[i] * y1[i]
                    x1[i] = x
                    y1[i] = y
                    if (ear == 0) l += y * weight[s] else r += y * weight[s]
                }
            }
            write = if (write + 1 == DelaySize) 0 else write + 1
            left = l
            right = r
        }

        fun clear() {
            line.forEach { it.fill(0f) }
            x1.fill(0f)
            y1.fill(0f)
        }

        private fun wrap(deg: Double): Double {
            var d = deg % 360.0
            if (d > 180) d -= 360
            if (d < -180) d += 360
            return d
        }
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private const val ToggleSeconds = 0.15f
        private const val SurroundGain = 0.85f
        private const val CrossfeedDelay = 12
        private const val DelaySize = 128
        private const val SpeedOfSound = 343.0
        private const val HeadRadius = 0.0875
        private const val AlphaMin = 0.5
        private const val ThetaMin = 150.0
        private const val Knee = 0.95f

        private fun surroundAzimuths(channels: Int): FloatArray = when (channels) {
            3 -> floatArrayOf(-30f, 30f, 0f)
            4 -> floatArrayOf(-30f, 30f, -110f, 110f)
            5 -> floatArrayOf(-30f, 30f, 0f, -110f, 110f)
            6 -> floatArrayOf(-30f, 30f, 0f, Float.NaN, -110f, 110f)
            7 -> floatArrayOf(-30f, 30f, 0f, Float.NaN, 180f, -90f, 90f)
            else -> floatArrayOf(-30f, 30f, 0f, Float.NaN, -150f, 150f, -90f, 90f)
        }

        private fun toPcm(sample: Float): Short {
            val a = abs(sample)
            val shaped = if (a <= Knee) sample else {
                (Knee + (1f - Knee) * tanh((a - Knee) / (1f - Knee))) * if (sample < 0) -1f else 1f
            }
            return (shaped * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
