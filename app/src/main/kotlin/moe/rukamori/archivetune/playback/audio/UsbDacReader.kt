/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.RequiresApi

object UsbDacReader {

    fun read(context: Context): UsbDacDetails? {
        val audio = context.getSystemService(AudioManager::class.java)
        val output = audio?.let(::usbOutput)
        val usb = context.getSystemService(UsbManager::class.java)?.let(::audioDevice)
        if (output == null && usb == null) return null

        val streaming = usb?.let(::streamingInterfaces).orEmpty()
        val outEndpoints = streaming.flatMap { iface ->
            (0 until iface.endpointCount).map(iface::getEndpoint).filter { ep ->
                ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
            }
        }
        val hasMic = streaming.any { iface ->
            (0 until iface.endpointCount).map(iface::getEndpoint).any { ep ->
                ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                    (ep.attributes shr 4) and 3 == 0
            }
        } || output?.type == AudioDeviceInfo.TYPE_USB_HEADSET

        return UsbDacDetails(
            name = usb?.productName?.trim()?.takeIf { it.isNotEmpty() }
                ?: output?.productName?.toString()?.removePrefix("USB-Audio - ")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "USB DAC",
            maker = usb?.manufacturerName?.trim()?.takeIf { it.isNotEmpty() },
            vendorId = usb?.vendorId,
            productId = usb?.productId,
            firmware = usb?.version?.takeIf { it.isNotBlank() },
            uacVersion = usb?.let(::uacVersion),
            asyncClock = outEndpoints.takeIf { it.isNotEmpty() }?.any { (it.attributes shr 2) and 3 == 1 },
            maxPowerMa = usb?.takeIf { it.configurationCount > 0 }?.getConfiguration(0)?.maxPower,
            hasMic = hasMic,
            rates = output?.sampleRates?.distinct()?.sorted().orEmpty(),
            bitDepths = output?.encodings?.toList().orEmpty().mapNotNull(AudioOutputs::bitDepthOf).distinct().sorted(),
            bitPerfect = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> null
                output == null || audio == null -> emptyList()
                else -> MixerFormats.bitPerfect(audio, output)
            },
            androidOutput = output != null,
        )
    }

    fun mixerRate(): Int? = runCatching { AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) }
        .getOrNull()?.takeIf { it > 0 }

    fun usbOutput(audio: AudioManager): AudioDeviceInfo? =
        runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
            .firstOrNull { it.isSink && AudioOutputs.kindOf(it.type) == AudioOutputs.Kind.USB }

    fun audioDevice(usb: UsbManager): UsbDevice? =
        runCatching { usb.deviceList.values.toList() }.getOrDefault(emptyList())
            .sortedBy { it.deviceId }
            .firstOrNull { streamingInterfaces(it).isNotEmpty() }

    private fun streamingInterfaces(device: UsbDevice) = (0 until device.interfaceCount).map(device::getInterface)
        .filter { it.interfaceClass == UsbConstants.USB_CLASS_AUDIO && it.interfaceSubclass == 2 }

    private fun uacVersion(device: UsbDevice): Int? = (0 until device.interfaceCount).map(device::getInterface)
        .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_AUDIO }
        ?.let { when (it.interfaceProtocol) { 0x20 -> 2; 0x30 -> 3; else -> 1 } }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object MixerFormats {
    fun bitPerfect(audio: AudioManager, device: AudioDeviceInfo): List<DacFormat> =
        runCatching { audio.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            .mapNotNull { attr -> AudioOutputs.bitDepthOf(attr.format.encoding)?.let { DacFormat(attr.format.sampleRate, it) } }
            .distinct()
            .sortedWith(compareBy({ it.bits }, { it.sampleRate }))
}
