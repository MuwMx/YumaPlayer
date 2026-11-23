/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import java.nio.ByteBuffer

internal interface PassTransport {
    val sampleRate: Int
    val channels: Int
    val bits: Int
    val deviceName: String
    val capacityFrames: Long

    fun write(buffer: ByteBuffer): Int
    fun play()
    fun pause()
    fun flush()
    fun playedFrames(): Long
    fun release()
}

internal class PassFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
