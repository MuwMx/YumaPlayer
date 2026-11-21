/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max

class PeakLimiter {

    var latencyFrames = 0
        private set

    private var channels = 0
    private var window = 0
    private var holdFrames = 0
    private var releaseStep = 0.0

    private var delay = DoubleArray(0)
    private var delayPos = 0

    private var pcmDelay = ByteArray(0)
    private var inPcm = false

    private var minGain = DoubleArray(0)
    private var minFrame = LongArray(0)
    private var minHead = 0
    private var minSize = 0

    private var held = DoubleArray(0)
    private var heldPos = 0
    private var heldSum = 0.0
    private var heldOnes = 0

    private var envelope = 1.0
    private var holdLeft = 0
    private var frame = 0L

    val isResting: Boolean
        get() = envelope == 1.0 && heldOnes == window

    fun configure(sampleRate: Int, channels: Int) {
        this.channels = channels
        latencyFrames = (sampleRate * LookaheadSeconds).toInt().coerceAtLeast(1)
        window = latencyFrames + 1
        holdFrames = (sampleRate * HoldSeconds).toInt()
        releaseStep = 1.0 - exp(-1.0 / (ReleaseSeconds * sampleRate))
        delay = DoubleArray(latencyFrames * channels)
        pcmDelay = ByteArray(latencyFrames * channels * 2)
        minGain = DoubleArray(window)
        minFrame = LongArray(window)
        held = DoubleArray(window)
        reset()
    }

    fun reset() {
        delay.fill(0.0)
        delayPos = 0
        inPcm = false
        minHead = 0
        minSize = 0
        held.fill(1.0)
        heldPos = 0
        heldSum = window.toDouble()
        heldOnes = window
        envelope = 1.0
        holdLeft = 0
        frame = 0
    }

    fun process(buffer: DoubleArray, frames: Int) {
        if (channels == 0) return
        if (inPcm) pcmToDoubles()
        if (envelope == 1.0 && heldOnes == window && peak(buffer, frames) <= Ceiling) {
            delayOnly(buffer, frames)
            return
        }
        var i = 0
        for (f in 0 until frames) {
            var peak = 0.0
            for (c in 0 until channels) peak = max(peak, abs(buffer[i + c]))
            val needed = if (peak > Ceiling) Ceiling / peak else 1.0
            if (needed < 1.0) holdLeft = holdFrames else if (holdLeft > 0) holdLeft--
            val recovered = envelope + (1.0 - envelope) * releaseStep
            val resting = when {
                holdLeft > 0 -> envelope
                recovered > 1.0 - Settled -> 1.0
                else -> recovered
            }
            envelope = minOf(needed, resting)

            val gain = smooth(lowestInWindow(envelope))
            for (c in 0 until channels) {
                val d = delayPos + c
                val late = delay[d]
                delay[d] = buffer[i + c]
                buffer[i + c] = late * gain
            }
            delayPos += channels
            if (delayPos == delay.size) delayPos = 0
            i += channels
            frame++
        }
    }

    private fun peak(buffer: DoubleArray, frames: Int): Double {
        var peak = 0.0
        for (i in 0 until frames * channels) peak = max(peak, abs(buffer[i]))
        return peak
    }

    private fun delayOnly(buffer: DoubleArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                val d = delayPos + c
                val late = delay[d]
                delay[d] = buffer[i]
                buffer[i] = late
                i++
            }
            delayPos += channels
            if (delayPos == delay.size) delayPos = 0
        }
        frame += frames
        minSize = 0
    }

    fun delayPcm(input: ByteBuffer, out: ByteBuffer) {
        if (!inPcm) doublesToPcm()
        val bytes = input.remaining()
        val held = pcmDelay.size
        if (bytes >= held) {
            out.put(pcmDelay)
            val end = input.limit()
            input.limit(input.position() + bytes - held)
            out.put(input)
            input.limit(end)
            input.get(pcmDelay)
        } else {
            out.put(pcmDelay, 0, bytes)
            System.arraycopy(pcmDelay, bytes, pcmDelay, 0, held - bytes)
            input.get(pcmDelay, held - bytes, bytes)
        }
        frame += bytes / (2 * channels)
        minSize = 0
    }

    private fun doublesToPcm() {
        val pcm = ByteBuffer.wrap(pcmDelay).order(ByteOrder.nativeOrder())
        for (k in delay.indices) {
            var d = delayPos + k
            if (d >= delay.size) d -= delay.size
            pcm.putShort(toPcm(delay[d]))
        }
        inPcm = true
    }

    private fun pcmToDoubles() {
        val pcm = ByteBuffer.wrap(pcmDelay).order(ByteOrder.nativeOrder())
        for (k in delay.indices) delay[k] = pcm.getShort() / 32768.0
        delayPos = 0
        inPcm = false
    }

    private fun lowestInWindow(value: Double): Double {
        while (minSize > 0 && minFrame[minHead] <= frame - window) {
            minHead = if (minHead + 1 == window) 0 else minHead + 1
            minSize--
        }
        while (minSize > 0) {
            var back = minHead + minSize - 1
            if (back >= window) back -= window
            if (minGain[back] < value) break
            minSize--
        }
        var slot = minHead + minSize
        if (slot >= window) slot -= window
        minGain[slot] = value
        minFrame[slot] = frame
        minSize++
        return minGain[minHead]
    }

    private fun smooth(gain: Double): Double {
        val oldest = held[heldPos]
        if (oldest == 1.0) heldOnes--
        if (gain == 1.0) heldOnes++
        heldSum += gain - oldest
        held[heldPos] = gain
        heldPos = if (heldPos + 1 == window) 0 else heldPos + 1
        if (heldOnes == window) {
            heldSum = window.toDouble()
            return 1.0
        }
        return heldSum / window
    }

    companion object {
        private const val LookaheadSeconds = 0.005
        private const val HoldSeconds = 0.025
        private const val ReleaseSeconds = 0.15
        private const val Ceiling = 1.0
        private const val Settled = 1e-6
        private const val BoostRoomDb = 4.0

        fun boostRoomDb(frequency: Double): Double =
            BoostRoomDb + 3.0 * max(0.0, log2(frequency / 1000.0))
    }
}
