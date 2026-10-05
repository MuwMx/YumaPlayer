/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.playback.EqCapabilities
import moe.rukamori.archivetune.playback.EqSettings
import moe.rukamori.archivetune.playback.EqualizerJson
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.readAudioEffectValue
import java.util.concurrent.ConcurrentHashMap

class ServiceAudioPolicyHolder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioScope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    private val onAudioOutputDeviceChanged: () -> Unit,
    private val ensureAudioEffects: (Int) -> Unit,
    var desiredEqSettings: MutableStateFlow<EqSettings> =
        MutableStateFlow(
            EqSettings(
                enabled = false,
                bandLevelsMb = emptyList(),
                outputGainEnabled = false,
                outputGainMb = 0,
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
            ),
        ),
) {
    lateinit var audioManager: AudioManager
    var audioFocusRequest: AudioFocusRequest? = null
    var lastAudioFocusState: Int = AudioManager.AUDIOFOCUS_NONE
    var wasPlayingBeforeAudioFocusLoss: Boolean = false
    var hasAudioFocus: Boolean = false

    val audioFocusVolumeFactor = MutableStateFlow(1f)
    var audioRouteRecoveryJob: Job? = null
    var audiblePlaybackRecoveryJob: Job? = null
    var lastAudioOutputDeviceSignature: String? = null
    var lastAudioRouteRecoveryRealtimeMs: Long = 0L

    val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                if (addedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                if (removedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }
        }

    var pauseOnDeviceMuteEnabled: Boolean = false
    var deviceMutePlaybackRecoveryVolumePercent: Int = 0
    var wasAutoPausedByDeviceMute: Boolean = false
    var muteRecoveryObserver: ContentObserver? = null
    var lastDeviceMutePlaybackNoticeAtElapsedMs: Long = 0L

    var playerVolume = MutableStateFlow(1f)
    var effectiveVolumeRampJob: Job? = null

    val normalizeFactor = MutableStateFlow(1f)
    val audioNormalizationFactorCache: ConcurrentHashMap<String, Float>
        get() = moe.rukamori.archivetune.playback.audioNormalizationFactorCache
    var audioNormalizationEnabled: Boolean = true
    val maxSafeGainFactor: Float = MusicService.MAX_AUDIO_NORMALIZATION_FACTOR

    var isAudioEffectSessionOpened: Boolean = false
    var openedAudioSessionId: Int? = null
    val eqCapabilities = MutableStateFlow<EqCapabilities?>(null)

    var audioEffectsSessionId: Int? = null
    var audioEffectsInitializationJob: Job? = null
    var equalizer: Equalizer? = null
    var bassBoost: BassBoost? = null
    var virtualizer: Virtualizer? = null
    var loudnessEnhancer: LoudnessEnhancer? = null

    fun hasAudioFocusForPlayback(): Boolean = hasAudioFocus

    fun setupAudioFocusRequest(onAudioFocusChange: (Int) -> Unit) {
        audioFocusRequest = AudioManager.AUDIOFOCUS_GAIN.let { _ ->
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setOnAudioFocusChangeListener { focusChange -> onAudioFocusChange(focusChange) }
                .setAcceptsDelayedFocusGain(true).build()
        }
    }

    fun registerAudioDeviceCallback(mainLooper: Looper, currentDeviceSignature: String?) {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(mainLooper))
        lastAudioOutputDeviceSignature = currentDeviceSignature
    }

    fun unregisterAudioDeviceCallback() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    fun cancelAudioRouteRecovery() {
        audioRouteRecoveryJob?.cancel()
    }

    fun cancelEffectiveVolumeRamp() {
        effectiveVolumeRampJob?.cancel()
        effectiveVolumeRampJob = null
    }

    fun decodeBandLevelsMb(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
    }

    fun encodeBandLevelsMb(levelsMb: List<Int>): String =
        runCatching {
            EqualizerJson.json.encodeToString(levelsMb)
        }.getOrNull().orEmpty()

    fun applyEqFlatPreset() {
        ioScope.launch {
            val caps = eqCapabilities.value
            val bandCount =
                caps?.bandCount ?: equalizer?.let { readAudioEffectValue("equalizer band count") { it.numberOfBands.toInt() } } ?: 0
            val encoded = encodeBandLevelsMb(List(bandCount.coerceAtLeast(0)) { 0 })
            dataStore.edit { prefs ->
                prefs[EqualizerEnabledKey] = true
                prefs[EqualizerBandLevelsMbKey] = encoded
                prefs[EqualizerSelectedProfileIdKey] = "flat"
            }
        }
    }

    fun applySystemEqPreset(presetIndex: Int, localPlayerAudioSessionId: () -> Int) {
        scope.launch {
            ensureAudioEffects(localPlayerAudioSessionId())
            val eq = equalizer ?: return@launch
            val maxPreset = readAudioEffectValue("equalizer preset count") { eq.numberOfPresets.toInt() } ?: 0
            if (presetIndex !in 0 until maxPreset) return@launch

            runCatching { eq.usePreset(presetIndex.toShort()) }.getOrNull() ?: return@launch

            val bandCount = readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt() } ?: 0
            val levels =
                (0 until bandCount).map { band ->
                    readAudioEffectValue("equalizer band level for band $band") {
                        eq.getBandLevel(band.toShort()).toInt()
                    } ?: 0
                }

            val encoded = encodeBandLevelsMb(levels)
            if (encoded.isBlank()) return@launch

            ioScope.launch {
                dataStore.edit { prefs ->
                    prefs[EqualizerEnabledKey] = true
                    prefs[EqualizerBandLevelsMbKey] = encoded
                    prefs[EqualizerSelectedProfileIdKey] = "system:$presetIndex"
                }
            }
        }
    }
}
