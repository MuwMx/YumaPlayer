/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

data class UsbDacDetails(
    val name: String,
    val maker: String?,
    val vendorId: Int?,
    val productId: Int?,
    val firmware: String?,
    val uacVersion: Int?,
    val asyncClock: Boolean?,
    val maxPowerMa: Int?,
    val hasMic: Boolean,
    val rates: List<Int>,
    val bitDepths: List<Int>,
    val bitPerfect: List<DacFormat>?,
    val androidOutput: Boolean,
)

data class DacFormat(val sampleRate: Int, val bits: Int)
