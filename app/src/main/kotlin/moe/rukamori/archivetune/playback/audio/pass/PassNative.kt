/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import java.nio.ByteBuffer

internal object PassNative {

    val loaded: Boolean by lazy { runCatching { System.loadLibrary("yumapass") }.isSuccess }

    external fun usbOpen(fd: Int, plans: IntArray, bcdUsb: Int): Long
    external fun usbChosen(handle: Long): Int
    external fun usbCapacity(handle: Long): Int
    external fun usbWrite(handle: Long, buffer: ByteBuffer, offset: Int, length: Int): Int
    external fun usbPlay(handle: Long, playing: Boolean)
    external fun usbFlush(handle: Long)
    external fun usbPlayed(handle: Long): Long
    external fun usbControl(handle: Long, type: Int, request: Int, value: Int, index: Int, data: ByteArray): Int
    external fun usbClose(handle: Long)

    external fun exclusiveOpen(rate: Int, channels: Int, bits: Int, device: Int): Long
    external fun exclusiveDevice(handle: Long): Int
    external fun exclusiveCapacity(handle: Long): Int
    external fun exclusiveWrite(handle: Long, buffer: ByteBuffer, offset: Int, length: Int): Int
    external fun exclusivePlay(handle: Long, playing: Boolean)
    external fun exclusiveFlush(handle: Long)
    external fun exclusivePlayed(handle: Long): Long
    external fun exclusiveClose(handle: Long)
}
