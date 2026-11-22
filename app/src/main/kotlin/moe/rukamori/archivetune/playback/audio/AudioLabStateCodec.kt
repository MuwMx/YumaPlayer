/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import moe.rukamori.archivetune.audiodsp.AudioLabEqSettings
import moe.rukamori.archivetune.audiodsp.AudioLabState
import moe.rukamori.archivetune.audiodsp.EightDParams
import moe.rukamori.archivetune.audiodsp.LoudnessParams
import moe.rukamori.archivetune.audiodsp.ReverbParams
import moe.rukamori.archivetune.audiodsp.SpatialMode
import moe.rukamori.archivetune.audiodsp.SpatialParams
import moe.rukamori.archivetune.audiodsp.StageListener
import moe.rukamori.archivetune.audiodsp.StageObject
import moe.rukamori.archivetune.audiodsp.StagePresets
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSource
import moe.rukamori.archivetune.audiodsp.StageSpeakers
import moe.rukamori.archivetune.audiodsp.StageWalls
import org.json.JSONArray
import org.json.JSONObject

internal object AudioLabStateCodec {
    fun encode(state: AudioLabState): String {
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

    fun decode(json: String): AudioLabState {
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
