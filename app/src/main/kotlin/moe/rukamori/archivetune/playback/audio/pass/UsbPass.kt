/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import java.nio.ByteBuffer

internal class UsbPass private constructor(
    private val connection: UsbDeviceConnection,
    private var handle: Long,
    val usbDeviceId: Int,
    override val deviceName: String,
    plan: UsbAudioDescriptors.Plan,
    unit: UsbAudioDescriptors.VolumeUnit?,
    rememberedDb: Double?,
) : PassTransport {

    override val sampleRate = plan.rate
    override val channels = plan.channels
    override val bits = plan.subslot * 8
    val validBits = plan.bitResolution
    override val capacityFrames: Long = PassNative.usbCapacity(handle).toLong()

    private val transfer = UsbTransfer { type, request, value, index, data ->
        if (handle == 0L) -1 else PassNative.usbControl(handle, type, request, value, index, data)
    }

    val volume: UsbDacVolume? = unit?.let { runCatching { UsbDacVolume.open(transfer, it) }.getOrNull() }?.also { v ->
        rememberedDb?.let { db -> v.set(transfer, minOf(db, v.originalDb)) }
    }

    @Volatile private var unplugged = false

    @Synchronized
    fun setVolume(db: Double): Double? = volume?.set(transfer, db)

    fun markUnplugged() {
        unplugged = true
    }

    override fun write(buffer: ByteBuffer): Int {
        val taken = call { PassNative.usbWrite(handle, buffer, buffer.position(), buffer.remaining()) }
        buffer.position(buffer.position() + taken)
        return taken
    }

    override fun play() = call { PassNative.usbPlay(handle, true) }

    override fun pause() = call { PassNative.usbPlay(handle, false) }

    override fun flush() = call { PassNative.usbFlush(handle) }

    override fun playedFrames(): Long = call { PassNative.usbPlayed(handle) }

    @Synchronized
    override fun release() {
        if (handle == 0L) return
        if (!unplugged) runCatching { volume?.restore(transfer) }
        PassNative.usbClose(handle)
        handle = 0L
        runCatching { connection.close() }
    }

    private inline fun <T> call(block: () -> T): T {
        if (unplugged) throw PassFailure("the dac was unplugged")
        if (handle == 0L) throw PassFailure("the dac is closed")
        return try {
            block()
        } catch (error: IllegalStateException) {
            throw PassFailure(error.message ?: "the dac stopped", error)
        }
    }

    companion object {
        fun open(
            usb: UsbManager,
            device: UsbDevice,
            dac: UsbAudioDescriptors.Dac,
            rate: Int,
            channels: Int,
            sourceBits: Int,
            allowFixedVolume: Boolean,
            rememberedDb: Double?,
        ): UsbPass {
            if (!PassNative.loaded) throw PassFailure("the native part didn't load")
            val plans = UsbAudioDescriptors.plans(dac, rate, channels, sourceBits).take(16)
            if (plans.isEmpty()) throw PassFailure("the dac has no setting for $rate Hz ${sourceBits}-bit")
            if (dac.volume == null && !allowFixedVolume) throw PassFailure("the dac has no volume of its own")
            val connection = usb.openDevice(device) ?: throw PassFailure("android wouldn't open the dac")
            val handle = try {
                PassNative.usbOpen(connection.fileDescriptor, encode(plans), dac.bcdUsb)
            } catch (error: IllegalStateException) {
                connection.close()
                throw PassFailure(error.message ?: "the dac wouldn't start", error)
            }
            val plan = plans[PassNative.usbChosen(handle)]
            val name = device.productName?.trim()?.takeIf { it.isNotEmpty() } ?: "USB DAC"
            val pass = UsbPass(connection, handle, device.deviceId, name, plan, dac.volume, rememberedDb)
            if (pass.volume == null && !allowFixedVolume) {
                pass.release()
                throw PassFailure("the dac's volume couldn't be turned down first")
            }
            return pass
        }

        private fun encode(plans: List<UsbAudioDescriptors.Plan>): IntArray = plans.flatMap { p ->
            listOf(
                p.controlInterface, p.streamingInterface, p.alt, if (p.uac2) 1 else 0, p.clockId, p.selectorId, p.selectorPin,
                p.endpoint, p.endpointAttributes, p.maxPacket, p.interval, if (p.rateControl) 1 else 0,
                p.feedbackEndpoint, p.feedbackMaxPacket, p.rate, p.channels, p.subslot, p.bitResolution,
            )
        }.toIntArray()
    }
}
