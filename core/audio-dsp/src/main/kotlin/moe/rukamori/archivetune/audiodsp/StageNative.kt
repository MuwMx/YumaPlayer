/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import java.nio.ByteBuffer

object StageNative {
    val loaded: Boolean by lazy { runCatching { System.loadLibrary("mochystage") }.isSuccess }

    external fun loadHrtf(data: ByteArray): Boolean
    external fun create(sampleRate: Int, channels: Int): Long
    external fun setParams(handle: Long, params: FloatArray)
    external fun process(handle: Long, input: ByteBuffer, inputOffset: Int, output: ByteBuffer, frames: Int): Boolean
    external fun levels(handle: Long, out: FloatArray)
    external fun clear(handle: Long)
    external fun destroy(handle: Long)
}
