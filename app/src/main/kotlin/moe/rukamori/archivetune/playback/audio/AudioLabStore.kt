/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.audiodsp.AudioLabEqPreset
import moe.rukamori.archivetune.audiodsp.AudioLabEqSettings
import moe.rukamori.archivetune.audiodsp.AudioLabSoundProfiles
import moe.rukamori.archivetune.audiodsp.AudioLabState
import moe.rukamori.archivetune.audiodsp.EightDParams
import moe.rukamori.archivetune.audiodsp.LoudnessParams
import moe.rukamori.archivetune.audiodsp.ReverbParams
import moe.rukamori.archivetune.audiodsp.SpatialParams
import moe.rukamori.archivetune.audiodsp.StageListener
import moe.rukamori.archivetune.audiodsp.StageNativeConstants
import moe.rukamori.archivetune.audiodsp.StageObject
import moe.rukamori.archivetune.audiodsp.StageParams
import moe.rukamori.archivetune.audiodsp.StagePreset
import moe.rukamori.archivetune.audiodsp.StagePresets
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSource
import moe.rukamori.archivetune.audiodsp.StageSpeakers
import moe.rukamori.archivetune.constants.AudioLabStateJsonKey
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
class AudioLabStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val equalizerService: EqualizerService,
    private val audioFxService: AudioFxService,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(AudioLabState())
    val state: StateFlow<AudioLabState> = _state.asStateFlow()

    private val _bypassed = MutableStateFlow(false)
    val bypassed: StateFlow<Boolean> = _bypassed.asStateFlow()

    val isDspEqActive: StateFlow<Boolean> = equalizerService.isActiveFlow

    init {
        scope.launch {
            val initialJson = dataStore.data.firstOrNull()?.get(AudioLabStateJsonKey)
            val initialState = initialJson?.let { AudioLabStateCodec.decode(it) } ?: AudioLabState()
            _state.value = initialState
            pushToDsp(initialState)

            _state
                .drop(1)
                .debounce(350L)
                .collect { currentState ->
                    try {
                        val encoded = AudioLabStateCodec.encode(currentState)
                        dataStore.edit { prefs ->
                            prefs[AudioLabStateJsonKey] = encoded
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
        }
    }

    fun pushToDsp(state: AudioLabState) {
        if (_bypassed.value) {
            equalizerService.disable()
            audioFxService.reverb = ReverbParams(enabled = false)
            audioFxService.loudness = LoudnessParams(enabled = false)
            audioFxService.spatial = SpatialParams(enabled = false)
            audioFxService.eightD = EightDParams(enabled = false)
            audioFxService.stage = StageParams.Off
            return
        }
        if (state.eq.enabled) {
            equalizerService.apply(state.eq.toParametricEq())
        } else {
            equalizerService.disable()
        }
        audioFxService.reverb = state.reverb
        audioFxService.loudness = state.loudness
        audioFxService.spatial = state.spatial
        audioFxService.eightD = state.eightD
        audioFxService.stage = state.stage.toParams(speaker = false)
    }

    fun setBypassed(bypassed: Boolean) {
        _bypassed.value = bypassed
        pushToDsp(_state.value)
    }

    fun toggleBypass() {
        setBypassed(!_bypassed.value)
    }

    fun applySoundProfile(profileId: String) {
        _bypassed.value = false
        _state.update { current ->
            AudioLabSoundProfiles.apply(current, profileId)
        }
        pushToDsp(_state.value)
    }

    fun setEqEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(eq = current.eq.copy(enabled = enabled))
        }
        pushToDsp(_state.value)
    }

    fun applyEqPreset(preset: AudioLabEqPreset) {
        _state.update { current ->
            current.copy(
                eq = current.eq.copy(
                    presetId = preset.id,
                    preampDb = preset.preampDb,
                    bandGainsDb = preset.gainsDb,
                ),
            )
        }
        pushToDsp(_state.value)
    }

    fun setEqPreamp(preampDb: Float) {
        _state.update { current ->
            current.copy(eq = current.eq.copy(preampDb = preampDb, presetId = "custom"))
        }
        pushToDsp(_state.value)
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        _state.update { current ->
            val bands = current.eq.bandGainsDb.toMutableList()
            if (bandIndex in bands.indices) {
                bands[bandIndex] = gainDb
            }
            current.copy(eq = current.eq.copy(bandGainsDb = bands, presetId = "custom"))
        }
        pushToDsp(_state.value)
    }

    fun resetEq() {
        _state.update { current ->
            current.copy(eq = AudioLabEqSettings())
        }
        pushToDsp(_state.value)
    }

    fun updateReverb(transform: (ReverbParams) -> ReverbParams) {
        _state.update { current ->
            current.copy(reverb = transform(current.reverb))
        }
        pushToDsp(_state.value)
    }

    fun updateLoudness(transform: (LoudnessParams) -> LoudnessParams) {
        _state.update { current ->
            current.copy(loudness = transform(current.loudness))
        }
        pushToDsp(_state.value)
    }

    fun updateSpatial(transform: (SpatialParams) -> SpatialParams) {
        _state.update { current ->
            current.copy(spatial = transform(current.spatial))
        }
        pushToDsp(_state.value)
    }

    fun updateEightD(transform: (EightDParams) -> EightDParams) {
        _state.update { current ->
            current.copy(eightD = transform(current.eightD))
        }
        pushToDsp(_state.value)
    }

    fun updateStage(transform: (StageSettings) -> StageSettings) {
        _state.update { current ->
            current.copy(stage = transform(current.stage))
        }
        pushToDsp(_state.value)
    }

    fun updateStageObject(id: Int, transform: (StageObject) -> StageObject) {
        _state.update { current ->
            val updated = current.stage.objects.map { obj ->
                if (obj.id == id) transform(obj) else obj
            }
            current.copy(stage = current.stage.copy(presetId = null, objects = updated))
        }
        pushToDsp(_state.value)
    }

    fun updateStageListener(transform: (StageListener) -> StageListener) {
        _state.update { current ->
            current.copy(stage = current.stage.copy(presetId = null, listener = transform(current.stage.listener)))
        }
        pushToDsp(_state.value)
    }

    fun addStageObject(source: StageSource): Boolean {
        var added = false
        _state.update { current ->
            val existing = current.stage.objects.filter { it.source == source }
            if (existing.size >= StageNativeConstants.Copies) {
                return@update current
            }
            val nextId = (current.stage.objects.maxOfOrNull { it.id } ?: -1) + 1
            val reference = existing.firstOrNull()
                ?: StagePresets.Default.objects.firstOrNull { it.source == source }
                ?: StageObject(nextId, source, 0f, 0.4f, 0.5f)
            val offset = (existing.size * 0.15f)
            val newObj = reference.copy(
                id = nextId,
                x = (reference.x + offset).coerceIn(-1f, 1f),
                z = (reference.z - offset).coerceIn(-1f, 1f),
                muted = false,
                solo = false,
            )
            added = true
            current.copy(
                stage = current.stage.copy(
                    presetId = null,
                    objects = current.stage.objects + newObj,
                ),
            )
        }
        if (added) pushToDsp(_state.value)
        return added
    }

    fun removeStageObject(id: Int): Boolean {
        var removed = false
        _state.update { current ->
            val target = current.stage.objects.firstOrNull { it.id == id } ?: return@update current
            val sameSourceCount = current.stage.objects.count { it.source == target.source }
            if (sameSourceCount <= 1) {
                return@update current
            }
            removed = true
            current.copy(
                stage = current.stage.copy(
                    presetId = null,
                    objects = current.stage.objects.filterNot { it.id == id },
                ),
            )
        }
        if (removed) pushToDsp(_state.value)
        return removed
    }

    fun applyStagePreset(preset: StagePreset) {
        _state.update { current ->
            current.copy(stage = StagePresets.apply(current.stage, preset))
        }
        pushToDsp(_state.value)
    }

    fun setStageSpeakers(speakers: StageSpeakers) {
        _state.update { current ->
            current.copy(stage = current.stage.copy(onSpeakers = speakers))
        }
        pushToDsp(_state.value)
    }

    fun resetAllFx() {
        _state.value = AudioLabState()
        pushToDsp(_state.value)
    }
}
