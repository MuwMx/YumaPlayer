/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class AudioOutputs(private val context: Context) {

    enum class Kind { PHONE, WIRED, USB, BLUETOOTH, HDMI }

    data class Device(
        val id: Int,
        val type: Int,
        val name: String,
        val kind: Kind,
        val isActive: Boolean,
        val isChosen: Boolean,
        val maxSampleRate: Int? = null,
        val bitDepths: List<Int> = emptyList(),
    )

    data class Mixer(
        val deviceName: String,
        val sampleRate: Int,
        val encoding: Int,
        val bitPerfect: Boolean,
    )

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _mixer = MutableStateFlow<Mixer?>(null)
    val mixer: StateFlow<Mixer?> = _mixer.asStateFlow()

    @Volatile
    private var chosenId: Int? = null

    @Volatile
    var preferUsb: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            routeChanged()
        }

    @Volatile
    var passthrough: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) clearMixer()
            routeChanged()
        }

    @Volatile
    var floatAllowed: Boolean = false

    @Volatile
    var resampler: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            routeChanged()
        }

    @Volatile
    var fixedOutputRate: Int? = null
        private set

    @Volatile
    var effectRateCap: Int? = null

    private val systemMixerRate: Int? by lazy {
        runCatching { audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() }.getOrNull()?.takeIf { it > 0 }
    }

    var onRouteChanged: (AudioDeviceInfo?) -> Unit = {}

    @Volatile
    private var mixedDevice: AudioDeviceInfo? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = routeChanged()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == chosenId }) chosenId = null
            if (removedDevices.any { it.id == mixedDevice?.id }) {
                mixedDevice = null
                _mixer.value = null
            }
            routeChanged()
        }
    }

    fun start() {
        runCatching { audioManager.registerAudioDeviceCallback(callback, mainHandler) }
        routeChanged()
    }

    fun release() {
        runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
        clearMixer()
    }

    fun select(id: Int?) {
        chosenId = id
        routeChanged()
    }

    fun preferredDevice(): AudioDeviceInfo? {
        val outputs = outputs()
        chosenId?.let { id -> outputs.firstOrNull { it.id == id }?.let { return it } }
        if (preferUsb || passthrough) outputs.firstOrNull { kindOf(it.type) == Kind.USB }?.let { return it }
        return null
    }

    fun activeDevice(): AudioDeviceInfo? {
        preferredDevice()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { audioManager.getAudioDevicesForAttributes(MediaAttributes).firstOrNull() }.getOrNull()?.let { return it }
        }
        val outputs = outputs()
        return outputs.firstOrNull { kindOf(it.type) == Kind.BLUETOOTH }
            ?: outputs.firstOrNull { kindOf(it.type) == Kind.WIRED }
            ?: outputs.firstOrNull { kindOf(it.type) == Kind.USB }
            ?: outputs.firstOrNull { kindOf(it.type) == Kind.HDMI }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    fun prepareMixer(sampleRate: Int, channels: Int, encoding: Int) {
        if (!passthrough || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val device = activeDevice()?.takeIf { kindOf(it.type) == Kind.USB }
        if (device == null) {
            clearMixer()
            return
        }
        if (mixedDevice != null && mixedDevice?.id != device.id) clearMixer()
        val set = runCatching { UsbMixer.apply(audioManager, device, sampleRate, channels, encoding) }
            .onFailure { Timber.tag(Tag).w(it, "couldn't set the dac's mixer") }
            .getOrNull()
        mixedDevice = device.takeIf { set != null }
        _mixer.value = set?.copy(deviceName = device.productName?.toString().orEmpty().ifBlank { "USB DAC" })
    }

    fun releaseMixer() = clearMixer()

    private fun clearMixer() {
        val device = mixedDevice ?: return
        mixedDevice = null
        _mixer.value = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { UsbMixer.clear(audioManager, device) }
        }
    }

    private fun routeChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { routeChanged() }
            return
        }
        val preferred = preferredDevice()
        val active = activeDevice()
        val activeKind = active?.let { kindOf(it.type) }
        fixedOutputRate = systemMixerRate.takeIf { resampler && (activeKind == Kind.PHONE || activeKind == Kind.WIRED) }
        _devices.value = outputs()
            .mapNotNull { info -> kindOf(info.type)?.let { info to it } }
            .map { (info, kind) ->
                Device(
                    id = info.id,
                    type = info.type,
                    name = if (kind == Kind.PHONE || kind == Kind.WIRED) "" else info.productName?.toString()?.trim().orEmpty(),
                    kind = kind,
                    isActive = info.id == active?.id,
                    isChosen = info.id == chosenId,
                    maxSampleRate = info.sampleRates.maxOrNull(),
                    bitDepths = info.encodings.toList().mapNotNull(::bitDepthOf).distinct().sorted(),
                )
            }
            .groupBy { it.name to it.kind }
            .map { (_, same) -> same.minBy { preferenceWithin(it.type) } }
            .sortedBy { it.kind.ordinal }
        onRouteChanged(preferred)
    }

    private fun outputs(): List<AudioDeviceInfo> =
        runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink && kindOf(it.type) != null } }
            .getOrDefault(emptyList())

    companion object {
        private const val Tag = "AudioOutputs"

        val MediaAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        fun kindOf(type: Int): Kind? = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.PHONE
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
            -> Kind.WIRED
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            -> Kind.USB
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            -> Kind.BLUETOOTH
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            -> Kind.HDMI
            else -> null
        }

        private fun preferenceWithin(type: Int): Int = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> 1
            else -> 2
        }

        fun bitDepthOf(encoding: Int): Int? = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object UsbMixer {
    fun apply(audioManager: AudioManager, device: AudioDeviceInfo, sampleRate: Int, channels: Int, encoding: Int): AudioOutputs.Mixer? {
        val mask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> {
                clear(audioManager, device)
                return null
            }
        }
        val atRate = audioManager.getSupportedMixerAttributes(device)
            .filter { it.format.sampleRate == sampleRate && it.format.channelMask == mask }
        val bitPerfect = atRate.firstOrNull {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT && it.format.encoding == encoding
        }
        val pick = bitPerfect ?: atRate
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT }
            .maxByOrNull { AudioOutputs.bitDepthOf(it.format.encoding) ?: 0 }
        if (pick == null) {
            clear(audioManager, device)
            return null
        }
        val current = audioManager.getPreferredMixerAttributes(AudioOutputs.MediaAttributes, device)
        if (current != pick && !audioManager.setPreferredMixerAttributes(AudioOutputs.MediaAttributes, device, pick)) return null
        return AudioOutputs.Mixer(
            deviceName = "",
            sampleRate = pick.format.sampleRate,
            encoding = pick.format.encoding,
            bitPerfect = pick.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT,
        )
    }

    fun clear(audioManager: AudioManager, device: AudioDeviceInfo) {
        audioManager.clearPreferredMixerAttributes(AudioOutputs.MediaAttributes, device)
    }
}
