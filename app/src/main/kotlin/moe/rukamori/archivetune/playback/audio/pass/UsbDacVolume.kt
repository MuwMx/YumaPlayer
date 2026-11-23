/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import kotlin.math.floor

internal fun interface UsbTransfer {
    fun transfer(type: Int, request: Int, value: Int, index: Int, data: ByteArray): Int
}

internal class UsbDacVolume private constructor(
    private val unit: UsbAudioDescriptors.VolumeUnit,
    private val ranges: List<IntRange>,
    private val steps: List<Int>,
    private val original: List<Int>,
    val minDb: Double,
    val maxDb: Double,
) {
    var currentDb: Double = minDb
        private set

    val originalDb: Double get() = original.min() / 256.0

    fun set(transfer: UsbTransfer, db: Double): Double {
        val target = floor(db.coerceIn(minDb, maxDb) * 256.0).toInt()
        apply(transfer, unit.channels.indices.map { target })
        return currentDb
    }

    fun restore(transfer: UsbTransfer) = apply(transfer, original)

    private fun apply(transfer: UsbTransfer, targets: List<Int>) {
        var loudest = Short.MIN_VALUE.toInt()
        unit.channels.forEachIndexed { i, channel ->
            val range = ranges[i]
            val step = steps[i].coerceAtLeast(1)
            val value = if (targets[i] <= range.first) range.first else range.first + (targets[i].coerceAtMost(range.last) - range.first) / step * step
            val bytes = byteArrayOf(value.toByte(), (value shr 8).toByte())
            transfer.transfer(0x21, SetCur, (VolumeControl shl 8) or channel, index(), bytes)
            loudest = maxOf(loudest, read(transfer, if (unit.uac2) Uac2Cur else Uac1GetCur, channel) ?: value)
        }
        currentDb = loudest / 256.0
    }

    private fun index() = (unit.unitId shl 8) or unit.controlInterface

    private fun read(transfer: UsbTransfer, request: Int, channel: Int): Int? = Companion.read(transfer, unit, request, channel)

    companion object {
        private const val VolumeControl = 0x02
        private const val SetCur = 0x01
        private const val Uac2Cur = 0x01
        private const val Uac2Range = 0x02
        private const val Uac1GetCur = 0x81
        private const val Uac1GetMin = 0x82
        private const val Uac1GetMax = 0x83
        private const val Uac1GetRes = 0x84

        private const val StartDb = -40

        private fun read(transfer: UsbTransfer, unit: UsbAudioDescriptors.VolumeUnit, request: Int, channel: Int): Int? {
            val data = ByteArray(2)
            val got = transfer.transfer(0xa1, request, (VolumeControl shl 8) or channel, (unit.unitId shl 8) or unit.controlInterface, data)
            return if (got == 2) ((data[0].toInt() and 0xff) or (data[1].toInt() shl 8)) else null
        }

        fun open(transfer: UsbTransfer, unit: UsbAudioDescriptors.VolumeUnit): UsbDacVolume? {
            if (unit.channels.isEmpty()) return null
            val ranges = mutableListOf<IntRange>()
            val steps = mutableListOf<Int>()
            val original = mutableListOf<Int>()
            for (channel in unit.channels) {
                val current = read(transfer, unit, if (unit.uac2) Uac2Cur else Uac1GetCur, channel) ?: return null
                original += current
                if (unit.uac2) {
                    val head = ByteArray(2)
                    val index = (unit.unitId shl 8) or unit.controlInterface
                    if (transfer.transfer(0xa1, Uac2Range, (VolumeControl shl 8) or channel, index, head) != 2) return null
                    val count = (head[0].toInt() and 0xff) or ((head[1].toInt() and 0xff) shl 8)
                    if (count !in 1..16) return null
                    val all = ByteArray(2 + count * 6)
                    if (transfer.transfer(0xa1, Uac2Range, (VolumeControl shl 8) or channel, index, all) != all.size) return null
                    fun s16(at: Int) = (all[at].toInt() and 0xff) or (all[at + 1].toInt() shl 8)
                    ranges += s16(2)..s16(2 + (count - 1) * 6 + 2)
                    steps += (0 until count).minOf { s16(2 + it * 6 + 4) }
                } else {
                    val min = read(transfer, unit, Uac1GetMin, channel) ?: return null
                    val max = read(transfer, unit, Uac1GetMax, channel) ?: return null
                    ranges += min..max
                    steps += read(transfer, unit, Uac1GetRes, channel) ?: 1
                }
            }
            val minDb = ranges.maxOf { it.first } / 256.0
            val maxDb = minOf(ranges.minOf { it.last }, 0) / 256.0
            if (minDb >= maxDb || minDb > StartDb) return null
            val volume = UsbDacVolume(unit, ranges, steps, original, minDb, maxDb)
            volume.apply(transfer, original.map { minOf(it, StartDb * 256) })
            return volume
        }
    }
}
