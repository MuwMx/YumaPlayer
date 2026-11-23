/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class BluetoothCodecWatcher(context: Context) {

    sealed interface State {
        data object None : State
        data class NeedsPermission(val device: String) : State
        data class LeAudio(val device: String) : State
        data class Unknown(val device: String) : State
        data class Known(val device: String, val codec: BluetoothCodec) : State
    }

    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var proxy: BluetoothA2dp? = null

    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val status = intent.getParcelableExtra<Parcelable>(ExtraCodecStatus)
            val parsed = status?.let(BluetoothCodec::fromStatus)
            val device = (_state.value as? State.Known)?.device ?: (_state.value as? State.Unknown)?.device
            if (parsed != null && device != null) _state.value = State.Known(device, parsed) else refresh()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            this@BluetoothCodecWatcher.proxy = proxy as? BluetoothA2dp
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxy = null
        }
    }

    fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runCatching { audio?.registerAudioDeviceCallback(devices, main) }
        val filter = IntentFilter().apply {
            addAction(ActionCodecChanged)
            addAction(ActionActiveDeviceChanged)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
        connectProxy()
        refresh()
    }

    fun stop() {
        scope?.cancel()
        scope = null
        runCatching { audio?.unregisterAudioDeviceCallback(devices) }
        runCatching { app.unregisterReceiver(receiver) }
        proxy?.let { p -> runCatching { app.getSystemService(BluetoothManager::class.java)?.adapter?.closeProfileProxy(BluetoothProfile.A2DP, p) } }
        proxy = null
    }

    fun refresh() {
        val scope = scope ?: return
        scope.launch { _state.value = read() }
        if (proxy == null) connectProxy()
    }

    private fun connectProxy() {
        if (!hasPermission()) return
        runCatching {
            app.getSystemService(BluetoothManager::class.java)?.adapter
                ?.getProfileProxy(app, profileListener, BluetoothProfile.A2DP)
        }
    }

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun read(): State {
        val outputs = runCatching { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() }.getOrNull().orEmpty()
        val a2dp = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (a2dp == null) {
            val le = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER }
            return le?.let { State.LeAudio(it.productName?.toString().orEmpty()) } ?: State.None
        }
        val name = a2dp.productName?.toString()?.trim().orEmpty().ifEmpty { "Bluetooth" }
        if (!hasPermission()) return State.NeedsPermission(name)
        val codec = proxy?.let { p -> codecOf(p, a2dp) }
        val known = (_state.value as? State.Known)?.takeIf { it.device == name }
        return codec?.let { State.Known(name, it) } ?: known ?: State.Unknown(name)
    }

    @SuppressLint("MissingPermission")
    private fun codecOf(proxy: BluetoothA2dp, output: AudioDeviceInfo): BluetoothCodec? = runCatching {
        val connected = proxy.connectedDevices
        val device = connected.firstOrNull { output.address.isNotEmpty() && it.address.equals(output.address, ignoreCase = true) }
            ?: connected.firstOrNull()
            ?: return null
        val status = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java).invoke(proxy, device)
        status?.let(BluetoothCodec::fromStatus)
    }.onFailure { Timber.tag("BluetoothCodecs").i("codec lookup refused: ${it.cause ?: it}") }.getOrNull()

    private companion object {
        const val ActionCodecChanged = "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"
        const val ActionActiveDeviceChanged = "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED"
        const val ExtraCodecStatus = "android.bluetooth.extra.CODEC_STATUS"
    }
}

enum class LdacQuality(val kbps: Int?) { HIGH(990), STANDARD(660), LOW(330), ADAPTIVE(null) }

data class BluetoothCodec(
    val name: String,
    val sampleRate: Int?,
    val bits: Int?,
    val ldac: LdacQuality?,
    val alternatives: List<String>,
) {
    val lossless: Boolean get() = name.contains("lossless", ignoreCase = true)

    val better: String?
        get() {
            val here = rank(name)
            return alternatives.maxByOrNull(::rank)?.takeIf { rank(it) > here && rank(it) >= Rank.getValue("aptx hd") }
        }

    companion object {
        fun fromStatus(status: Any): BluetoothCodec? = runCatching {
            val config = status.javaClass.getMethod("getCodecConfig").invoke(status) ?: return null
            val selectable = when (val raw = status.javaClass.getMethod("getCodecsSelectableCapabilities").invoke(status)) {
                is Collection<*> -> raw.toList()
                is Array<*> -> raw.toList()
                else -> emptyList()
            }
            val name = nameOf(config) ?: return null
            BluetoothCodec(
                name = name,
                sampleRate = int(config, "getSampleRate")?.let(::rateOf),
                bits = int(config, "getBitsPerSample")?.let(::bitsOf),
                ldac = if (int(config, "getCodecType") == 4) ldacOf(long(config, "getCodecSpecific1")) else null,
                alternatives = selectable.mapNotNull { it?.let(::nameOf) }.distinct().filter { it != name },
            )
        }.getOrNull()

        private fun nameOf(config: Any): String? {
            if (Build.VERSION.SDK_INT >= 35) {
                runCatching {
                    val extended = config.javaClass.getMethod("getExtendedCodecType").invoke(config)
                    (extended?.javaClass?.getMethod("getCodecName")?.invoke(extended) as? String)?.takeIf { it.isNotBlank() }
                }.getOrNull()?.let { return tidy(it) }
            }
            return when (int(config, "getCodecType")) {
                0 -> "SBC"
                1 -> "AAC"
                2 -> "aptX"
                3 -> "aptX HD"
                4 -> "LDAC"
                5 -> "LC3"
                6 -> "Opus"
                null -> null
                else -> runCatching { config.javaClass.getMethod("getCodecName").invoke(config) as? String }.getOrNull()?.let(::tidy)
            }
        }

        private fun tidy(name: String): String = name
            .replace("APTX", "aptX", ignoreCase = true)
            .replace("ADAPTIVE", "Adaptive", ignoreCase = true)
            .replace("LOSSLESS", "Lossless", ignoreCase = true)

        private fun int(target: Any, method: String): Int? =
            runCatching { (target.javaClass.getMethod(method).invoke(target) as? Number)?.toInt() }.getOrNull()

        private fun long(target: Any, method: String): Long? =
            runCatching { (target.javaClass.getMethod(method).invoke(target) as? Number)?.toLong() }.getOrNull()

        private fun rateOf(mask: Int): Int? = when {
            mask and 0x20 != 0 -> 192_000
            mask and 0x10 != 0 -> 176_400
            mask and 0x08 != 0 -> 96_000
            mask and 0x04 != 0 -> 88_200
            mask and 0x02 != 0 -> 48_000
            mask and 0x01 != 0 -> 44_100
            else -> null
        }

        private fun bitsOf(mask: Int): Int? = when {
            mask and 0x04 != 0 -> 32
            mask and 0x02 != 0 -> 24
            mask and 0x01 != 0 -> 16
            else -> null
        }

        private fun ldacOf(value: Long?): LdacQuality? = when (value?.let { it % 10 }) {
            0L -> LdacQuality.HIGH
            1L -> LdacQuality.STANDARD
            2L -> LdacQuality.LOW
            3L -> LdacQuality.ADAPTIVE
            else -> null
        }

        private val Rank = mapOf("sbc" to 0, "aac" to 1, "lc3" to 1, "opus" to 1, "aptx" to 2, "aptx hd" to 3, "aptx adaptive" to 4, "ldac" to 5, "aptx lossless" to 6)

        private fun rank(name: String): Int {
            val lower = name.lowercase()
            if (lower.startsWith("lhdc")) return 5
            return Rank[lower] ?: Rank.entries.filter { lower.startsWith(it.key) }.maxOfOrNull { it.value } ?: 0
        }
    }
}
