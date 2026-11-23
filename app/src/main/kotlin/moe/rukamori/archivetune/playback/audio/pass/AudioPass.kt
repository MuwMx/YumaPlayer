/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.constants.AudioPassMode
import moe.rukamori.archivetune.playback.audio.AudioOutputs
import moe.rukamori.archivetune.playback.audio.UsbDacReader
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class AudioPass(context: Context, private val outputs: AudioOutputs) {

    sealed interface Status {
        data object Off : Status
        data object Waiting : Status
        data class Playing(val mode: AudioPassMode, val device: String, val sampleRate: Int, val bits: Int, val sourceBits: Int, val lossless: Boolean, val exact: Boolean) : Status
        data class Fell(val mode: AudioPassMode, val sampleRate: Int, val sourceBits: Int) : Status
    }

    data class DacVolume(val minDb: Double, val maxDb: Double, val currentDb: Double)

    enum class Skip { Busy, Format, Lossy, Decoded16, FellBefore, NoDac, NoUsbAccess, DacCantPlay }

    private val audio = context.getSystemService(AudioManager::class.java)
    internal val usb = UsbDacAccess(context) { bump() }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _skip = MutableStateFlow<Skip?>(null)
    val skip: StateFlow<Skip?> = _skip.asStateFlow()

    private val _dacVolume = MutableStateFlow<DacVolume?>(null)
    val dacVolume: StateFlow<DacVolume?> = _dacVolume.asStateFlow()

    val usbPermitted: StateFlow<Boolean?> get() = usb.permitted

    @Volatile private var enabled = false
    @Volatile var mode: AudioPassMode = AudioPassMode.Default
        private set
    @Volatile private var blocked = true
    @Volatile private var allowFixedVolume = false

    @Volatile var generation = 0
        private set

    private var owner: Any? = null
    private var active: PassTransport? = null
    private val failed = HashSet<String>()
    @Volatile private var directReleasedAt = 0L
    private val remembered = HashMap<String, Double>()
    private val volumeWork = Executors.newSingleThreadExecutor { Thread(it, "AudioPassVolume") }
    private val pendingVolume = AtomicReference<Double?>(null)
    private val volumeListeners = mutableListOf<WeakReference<() -> Unit>>()

    init {
        usb.onDetached = { id -> synchronized(this) { (active as? UsbPass)?.takeIf { it.usbDeviceId == id }?.markUnplugged() } }
    }

    fun start() = usb.start()

    fun release() = usb.stop()

    @Synchronized
    fun update(enabled: Boolean, mode: AudioPassMode, blocked: Boolean, allowFixedVolume: Boolean) {
        if (enabled == this.enabled && mode == this.mode && blocked == this.blocked && allowFixedVolume == this.allowFixedVolume) return
        if (mode != this.mode || allowFixedVolume != this.allowFixedVolume) failed.clear()
        _skip.value = null
        this.enabled = enabled
        this.mode = mode
        this.blocked = blocked
        this.allowFixedVolume = allowFixedVolume
        if (engaged && mode == AudioPassMode.DIRECT_USB) usb.requestPermission()
        if (owner == null) _status.value = if (engaged) Status.Waiting else Status.Off
        bump()
    }

    fun requestUsbPermission() = usb.requestPermission()

    private fun bump() {
        generation++
    }

    val engaged: Boolean get() = enabled && mode.exclusive && Build.VERSION.SDK_INT >= mode.minSdk && !blocked

    private val takesLossy: Boolean get() = mode == AudioPassMode.DIRECT_USB

    fun wantsFloat(source: Format?): Boolean =
        engaged && (takesLossy || isLossless(source)) && (source?.pcmEncoding?.let { PassPcm.sourceBits(it, it) > 16 } ?: true)

    @Synchronized
    internal fun canTake(format: Format, source: Format?, requester: Any): Boolean {
        if (!engaged) return false
        val skip = skipFor(format, source, requester)
        if (skip != _skip.value && skip != null) Timber.tag("AudioPass").i("$mode left a ${format.sampleRate} Hz song to android: $skip")
        _skip.value = skip
        return skip == null
    }

    private fun skipFor(format: Format, source: Format?, requester: Any): Skip? {
        if (owner != null && owner !== requester) return Skip.Busy
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW || format.channelCount !in 1..2 || format.sampleRate <= 0) return Skip.Format
        if (format.pcmEncoding !in PassPcm.Encodings) return Skip.Format
        if (!takesLossy && !isLossless(source)) return Skip.Lossy
        val bits = PassPcm.sourceBits(format.pcmEncoding, source?.pcmEncoding)
        if (format.pcmEncoding == C.ENCODING_PCM_16BIT && bits > 16) return Skip.Decoded16
        if (key(format.sampleRate, format.channelCount, bits) in failed) return Skip.FellBefore
        val rate = format.sampleRate
        val channels = format.channelCount
        return when (mode) {
            AudioPassMode.SONG_RATE -> Skip.Format
            AudioPassMode.BIT_PERFECT -> {
                val output = usbOutput() ?: return Skip.NoDac
                Skip.DacCantPlay.takeIf {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || MixerPass.pick(audio, output, rate, channels, bits) == null
                }
            }
            AudioPassMode.EXCLUSIVE_USB -> when {
                usbOutput() == null -> Skip.NoDac
                ExclusivePass.containerFor(bits) == null -> Skip.DacCantPlay
                else -> null
            }
            AudioPassMode.EXCLUSIVE_DAC -> when {
                wiredOutput() == null -> Skip.NoDac
                ExclusivePass.containerFor(bits) == null -> Skip.DacCantPlay
                else -> null
            }
            AudioPassMode.DIRECT_USB -> {
                val device = usb.device() ?: return Skip.NoDac
                if (!usb.hasPermission(device)) return Skip.NoUsbAccess
                val dac = usb.dac(device) ?: return Skip.NoUsbAccess
                Skip.DacCantPlay.takeIf { UsbAudioDescriptors.plans(dac, rate, channels, bits).isEmpty() }
            }
        }
    }

    @Synchronized
    internal fun open(format: Format, source: Format?, requester: Any): PassTransport {
        if (owner != null && owner !== requester) throw PassFailure("another player holds the output")
        val rate = format.sampleRate
        val channels = format.channelCount
        val bits = PassPcm.sourceBits(format.pcmEncoding, source?.pcmEncoding)
        val transport = when (mode) {
            AudioPassMode.SONG_RATE -> throw PassFailure("song's rate isn't exclusive")
            AudioPassMode.BIT_PERFECT -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) throw PassFailure("needs android 14")
                val device = usbOutput() ?: throw PassFailure("no usb dac")
                outputs.releaseMixer()
                MixerPass.open(audio, device, rate, channels, bits)
            }
            AudioPassMode.EXCLUSIVE_USB -> ExclusivePass.open(usbOutput() ?: throw PassFailure("no usb dac"), rate, channels, bits)
            AudioPassMode.EXCLUSIVE_DAC -> ExclusivePass.open(wiredOutput() ?: throw PassFailure("no headphones"), rate, channels, bits)
            AudioPassMode.DIRECT_USB -> {
                val manager = usb.manager ?: throw PassFailure("no usb")
                val device = usb.device() ?: throw PassFailure("no usb dac")
                val dac = usb.dac(device) ?: throw PassFailure("no usb access")
                UsbPass.open(manager, device, dac, rate, channels, bits, allowFixedVolume, remembered[dacKey(device.vendorId, device.productId)])
            }
        }
        owner = requester
        active = transport
        publishVolume()
        return transport
    }

    @Synchronized
    internal fun release(requester: Any) {
        if (owner !== requester) return
        if (active is UsbPass) directReleasedAt = SystemClock.elapsedRealtime()
        owner = null
        active = null
        _dacVolume.value = null
        notifyVolume()
        _status.value = if (engaged) Status.Waiting else Status.Off
    }

    @Synchronized
    internal fun failed(format: Format, source: Format?, why: String) {
        val bits = PassPcm.sourceBits(format.pcmEncoding, source?.pcmEncoding)
        failed += key(format.sampleRate, format.channelCount, bits)
        Timber.tag("AudioPass").i("$mode fell back for ${format.sampleRate} Hz ${bits}-bit: $why")
        _status.value = Status.Fell(mode, format.sampleRate, bits)
    }

    internal fun playing(transport: PassTransport, sourceBits: Int, source: Format?, exact: Boolean) {
        _status.value = Status.Playing(mode, transport.deviceName, transport.sampleRate, transport.bits, sourceBits, isLossless(source), exact)
    }

    fun holdsNormal(): Boolean {
        val at = directReleasedAt
        if (at == 0L) return false
        if (SystemClock.elapsedRealtime() - at > HandBackMs || usbOutput() != null) {
            directReleasedAt = 0L
            return false
        }
        return true
    }

    fun setDacVolume(db: Double) {
        if (pendingVolume.getAndSet(db) != null) return
        volumeWork.execute {
            val target = pendingVolume.getAndSet(null) ?: return@execute
            val pass = synchronized(this) { active as? UsbPass } ?: return@execute
            runCatching { pass.setVolume(target) }
                .onSuccess { publishVolume() }
                .onFailure { Timber.tag("AudioPass").w(it, "dac volume change failed") }
        }
    }

    fun stepDacVolume(up: Boolean) {
        val volume = _dacVolume.value ?: return
        setDacVolume(dbAt(volume, (levelOf(volume) + if (up) 1f / VolumeSteps else -1f / VolumeSteps).coerceIn(0f, 1f)))
    }

    fun setDacLevel(level: Float) {
        val volume = _dacVolume.value ?: return
        setDacVolume(dbAt(volume, level.coerceIn(0f, 1f)))
    }

    @Synchronized
    fun addVolumeListener(listener: () -> Unit) {
        volumeListeners.removeAll { it.get() == null }
        volumeListeners += WeakReference(listener)
    }

    @Synchronized
    private fun publishVolume() {
        val pass = active as? UsbPass
        _dacVolume.value = pass?.volume?.let { DacVolume(it.minDb, it.maxDb, it.currentDb) }
        pass?.let { p -> usb.device()?.let { remembered[dacKey(it.vendorId, it.productId)] = p.volume?.currentDb ?: return@let } }
        notifyVolume()
    }

    private fun notifyVolume() {
        val listeners = synchronized(this) { volumeListeners.mapNotNull { it.get() } }
        listeners.forEach { it() }
    }

    private fun usbOutput(): AudioDeviceInfo? = audio?.let(UsbDacReader::usbOutput)

    private fun wiredOutput(): AudioDeviceInfo? =
        runCatching { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() }.getOrNull().orEmpty()
            .firstOrNull { it.isSink && AudioOutputs.kindOf(it.type) == AudioOutputs.Kind.WIRED }

    private fun key(rate: Int, channels: Int, bits: Int): String {
        val device = when (mode) {
            AudioPassMode.DIRECT_USB -> usb.device()?.deviceId
            AudioPassMode.EXCLUSIVE_DAC -> wiredOutput()?.id
            else -> usbOutput()?.id
        }
        return "$mode/$device/$rate/$channels/$bits"
    }

    companion object {
        private const val HandBackMs = 3_000L
        const val VolumeSteps = 16

        private const val FloorDb = -80.0

        private val LosslessTypes = setOf(MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_ALAC, MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV)

        fun isLossless(source: Format?): Boolean = source?.sampleMimeType in LosslessTypes

        private fun dacKey(vendor: Int, product: Int) = "$vendor:$product"

        fun levelOf(volume: DacVolume): Float {
            val floor = maxOf(volume.minDb, FloorDb)
            if (volume.currentDb <= floor || volume.maxDb <= floor) return 0f
            return ((volume.currentDb - floor) / (volume.maxDb - floor)).toFloat().coerceIn(0f, 1f)
        }

        fun dbAt(volume: DacVolume, level: Float): Double {
            if (level <= 0f) return volume.minDb
            val floor = maxOf(volume.minDb, FloorDb)
            return floor + level * (volume.maxDb - floor)
        }
    }
}
