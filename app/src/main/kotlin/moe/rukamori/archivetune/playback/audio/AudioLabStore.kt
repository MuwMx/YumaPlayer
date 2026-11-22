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
import moe.rukamori.archivetune.audiodsp.SpatialMode
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
import moe.rukamori.archivetune.audiodsp.StageWalls
import moe.rukamori.archivetune.constants.AudioLabStateJsonKey
import org.json.JSONArray
import org.json.JSONObject
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
            val initialState = initialJson?.let { decode(it) } ?: AudioLabState()
            _state.value = initialState
            pushToDsp(initialState)

            _state
                .drop(1)
                .debounce(350L)
                .collect { currentState ->
                    runCatching {
                        val encoded = encode(currentState)
                        dataStore.edit { prefs ->
                            prefs[AudioLabStateJsonKey] = encoded
                        }
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

    private fun encode(state: AudioLabState): String {
        val root = JSONObject()

        val eqObj = JSONObject()
        eqObj.put("enabled", state.eq.enabled)
        eqObj.put("presetId", state.eq.presetId)
        eqObj.put("preampDb", state.eq.preampDb.toDouble())
        val bandsArr = JSONArray()
        state.eq.bandGainsDb.forEach { bandsArr.put(it.toDouble()) }
        eqObj.put("bandGainsDb", bandsArr)
        root.put("eq", eqObj)

        val revObj = JSONObject()
        revObj.put("enabled", state.reverb.enabled)
        revObj.put("mix", state.reverb.mix.toDouble())
        revObj.put("roomSize", state.reverb.roomSize.toDouble())
        revObj.put("damping", state.reverb.damping.toDouble())
        revObj.put("wet", state.reverb.wet.toDouble())
        revObj.put("dry", state.reverb.dry.toDouble())
        revObj.put("width", state.reverb.width.toDouble())
        revObj.put("preDelayMs", state.reverb.preDelayMs.toDouble())
        revObj.put("lowCutHz", state.reverb.lowCutHz.toDouble())
        root.put("reverb", revObj)

        val loudObj = JSONObject()
        loudObj.put("enabled", state.loudness.enabled)
        loudObj.put("boostDb", state.loudness.boostDb.toDouble())
        loudObj.put("compression", state.loudness.compression.toDouble())
        root.put("loudness", loudObj)

        val spatObj = JSONObject()
        spatObj.put("enabled", state.spatial.enabled)
        spatObj.put("mode", state.spatial.mode.name)
        spatObj.put("cutoffHz", state.spatial.cutoffHz.toDouble())
        spatObj.put("feedDb", state.spatial.feedDb.toDouble())
        spatObj.put("speakerAngle", state.spatial.speakerAngle.toDouble())
        spatObj.put("amount", state.spatial.amount.toDouble())
        spatObj.put("surround", state.spatial.surround)
        spatObj.put("mono", state.spatial.mono)
        root.put("spatial", spatObj)

        val eightObj = JSONObject()
        eightObj.put("enabled", state.eightD.enabled)
        eightObj.put("rotationSpeedHz", state.eightD.rotationSpeedHz.toDouble())
        eightObj.put("depth", state.eightD.depth.toDouble())
        eightObj.put("reverbLevel", state.eightD.reverbLevel.toDouble())
        root.put("eightD", eightObj)

        val stageObj = JSONObject()
        stageObj.put("enabled", state.stage.enabled)
        if (state.stage.presetId != null) stageObj.put("presetId", state.stage.presetId)
        stageObj.put("roomSize", state.stage.roomSize.toDouble())
        stageObj.put("walls", state.stage.walls.name)
        stageObj.put("roomAmount", state.stage.roomAmount.toDouble())
        stageObj.put("levelDb", state.stage.levelDb.toDouble())
        stageObj.put("onSpeakers", state.stage.onSpeakers.name)

        val listenerObj = JSONObject()
        listenerObj.put("x", state.stage.listener.x.toDouble())
        listenerObj.put("y", state.stage.listener.y.toDouble())
        listenerObj.put("z", state.stage.listener.z.toDouble())
        listenerObj.put("yaw", state.stage.listener.yaw.toDouble())
        stageObj.put("listener", listenerObj)

        val objectsArr = JSONArray()
        state.stage.objects.forEach { obj ->
            val o = JSONObject()
            o.put("id", obj.id)
            o.put("source", obj.source.name)
            o.put("x", obj.x.toDouble())
            o.put("y", obj.y.toDouble())
            o.put("z", obj.z.toDouble())
            o.put("levelDb", obj.levelDb.toDouble())
            o.put("muted", obj.muted)
            o.put("solo", obj.solo)
            o.put("width", obj.width.toDouble())
            objectsArr.put(o)
        }
        stageObj.put("objects", objectsArr)
        root.put("stage", stageObj)

        return root.toString()
    }

    private fun decode(json: String): AudioLabState {
        return runCatching {
            val root = JSONObject(json)

            val eq = if (root.has("eq")) {
                val o = root.getJSONObject("eq")
                val bands = mutableListOf<Float>()
                val arr = o.optJSONArray("bandGainsDb")
                if (arr != null) {
                    for (i in 0 until arr.length()) bands.add(arr.optDouble(i, 0.0).toFloat())
                }
                while (bands.size < 10) bands.add(0f)
                AudioLabEqSettings(
                    enabled = o.optBoolean("enabled", false),
                    presetId = o.optString("presetId", "flat"),
                    preampDb = o.optDouble("preampDb", 0.0).toFloat(),
                    bandGainsDb = bands.take(10),
                )
            } else AudioLabEqSettings()

            val reverb = if (root.has("reverb")) {
                val o = root.getJSONObject("reverb")
                ReverbParams(
                    enabled = o.optBoolean("enabled", false),
                    mix = o.optDouble("mix", 0.5).toFloat(),
                    roomSize = o.optDouble("roomSize", 0.55).toFloat(),
                    damping = o.optDouble("damping", 0.45).toFloat(),
                    wet = o.optDouble("wet", 0.6).toFloat(),
                    dry = o.optDouble("dry", 1.0).toFloat(),
                    width = o.optDouble("width", 1.0).toFloat(),
                    preDelayMs = o.optDouble("preDelayMs", 12.0).toFloat(),
                    lowCutHz = o.optDouble("lowCutHz", 180.0).toFloat(),
                )
            } else ReverbParams()

            val loudness = if (root.has("loudness")) {
                val o = root.getJSONObject("loudness")
                LoudnessParams(
                    enabled = o.optBoolean("enabled", false),
                    boostDb = o.optDouble("boostDb", 4.0).toFloat(),
                    compression = o.optDouble("compression", 0.3).toFloat(),
                )
            } else LoudnessParams()

            val spatial = if (root.has("spatial")) {
                val o = root.getJSONObject("spatial")
                val mode = runCatching { SpatialMode.valueOf(o.optString("mode")) }.getOrDefault(SpatialMode.Crossfeed)
                SpatialParams(
                    enabled = o.optBoolean("enabled", false),
                    mode = mode,
                    cutoffHz = o.optDouble("cutoffHz", 700.0).toFloat(),
                    feedDb = o.optDouble("feedDb", 4.5).toFloat(),
                    speakerAngle = o.optDouble("speakerAngle", 30.0).toFloat(),
                    amount = o.optDouble("amount", 1.0).toFloat(),
                    surround = o.optBoolean("surround", false),
                    mono = o.optBoolean("mono", false),
                )
            } else SpatialParams()

            val eightD = if (root.has("eightD")) {
                val o = root.getJSONObject("eightD")
                EightDParams(
                    enabled = o.optBoolean("enabled", false),
                    rotationSpeedHz = o.optDouble("rotationSpeedHz", 0.15).toFloat(),
                    depth = o.optDouble("depth", 0.85).toFloat(),
                    reverbLevel = o.optDouble("reverbLevel", 0.4).toFloat(),
                )
            } else EightDParams()

            val stage = if (root.has("stage")) {
                val o = root.getJSONObject("stage")
                val presetId = if (o.has("presetId")) o.optString("presetId") else null
                val walls = runCatching { StageWalls.valueOf(o.optString("walls")) }.getOrDefault(StagePresets.Default.walls)
                val speakers = runCatching { StageSpeakers.valueOf(o.optString("onSpeakers")) }.getOrDefault(StageSpeakers.Stereo)

                val listener = if (o.has("listener")) {
                    val l = o.getJSONObject("listener")
                    StageListener(
                        x = l.optDouble("x", 0.0).toFloat(),
                        y = l.optDouble("y", 0.4).toFloat(),
                        z = l.optDouble("z", 0.0).toFloat(),
                        yaw = l.optDouble("yaw", 0.0).toFloat(),
                    )
                } else StagePresets.Default.listener

                val objects = mutableListOf<StageObject>()
                val arr = o.optJSONArray("objects")
                if (arr != null && arr.length() > 0) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val source = runCatching { StageSource.valueOf(item.optString("source")) }.getOrDefault(StageSource.Vocal)
                        objects.add(
                            StageObject(
                                id = item.optInt("id", i),
                                source = source,
                                x = item.optDouble("x", 0.0).toFloat(),
                                y = item.optDouble("y", 0.4).toFloat(),
                                z = item.optDouble("z", 0.0).toFloat(),
                                levelDb = item.optDouble("levelDb", 0.0).toFloat(),
                                muted = item.optBoolean("muted", false),
                                solo = item.optBoolean("solo", false),
                                width = item.optDouble("width", 0.6).toFloat(),
                            ),
                        )
                    }
                }
                StageSettings(
                    enabled = o.optBoolean("enabled", false),
                    presetId = presetId,
                    objects = if (objects.isNotEmpty()) objects else StagePresets.Default.objects,
                    listener = listener,
                    roomSize = o.optDouble("roomSize", StagePresets.Default.roomSize.toDouble()).toFloat(),
                    walls = walls,
                    roomAmount = o.optDouble("roomAmount", StagePresets.Default.roomAmount.toDouble()).toFloat(),
                    levelDb = o.optDouble("levelDb", 0.0).toFloat(),
                    onSpeakers = speakers,
                )
            } else StageSettings()

            AudioLabState(
                eq = eq,
                reverb = reverb,
                loudness = loudness,
                spatial = spatial,
                eightD = eightD,
                stage = stage,
            )
        }.getOrDefault(AudioLabState())
    }
}
