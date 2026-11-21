/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import kotlin.math.pow

enum class StageSource { Vocal, Center, Left, Right, Ambience, Sub }

data class StageObject(
    val id: Int,
    val source: StageSource,
    val x: Float,
    val y: Float,
    val z: Float,
    val levelDb: Float = 0f,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val width: Float = 0.6f,
)

data class StageListener(
    val x: Float = 0f,
    val y: Float = 0.4f,
    val z: Float = 0f,
    val yaw: Float = 0f,
)

enum class StageWalls(val absorption: Float, val damping: Float) {
    Soft(0.45f, 0.7f),
    Wood(0.22f, 0.45f),
    Concrete(0.08f, 0.15f),
}

enum class StageSpeakers { Off, Stereo, Keep3D }

data class StageBox(val width: Float, val depth: Float, val height: Float)

data class StagePreset(
    val id: String,
    val objects: List<StageObject>,
    val listener: StageListener,
    val roomSize: Float,
    val walls: StageWalls,
    val roomAmount: Float,
)

object StagePresets {
    const val DefaultId = "studio"

    private class Spot(val x: Float, val y: Float, val z: Float)
    private fun at(x: Float, y: Float, z: Float) = Spot(x, y, z)

    private fun layout(
        vocal: Spot,
        center: Spot,
        left: Spot,
        right: Spot,
        ambience: Spot,
        width: Float,
        subs: List<Spot>,
    ): List<StageObject> {
        val main = listOf(
            StageSource.Vocal to vocal,
            StageSource.Center to center,
            StageSource.Left to left,
            StageSource.Right to right,
            StageSource.Ambience to ambience,
        ).mapIndexed { id, (source, spot) ->
            StageObject(id, source, spot.x, spot.y, spot.z, width = width)
        }
        return main + subs.mapIndexed { i, spot ->
            StageObject(main.size + i, StageSource.Sub, spot.x, spot.y, spot.z)
        }
    }

    val Default = StagePreset(
        id = DefaultId,
        objects = layout(
            vocal = at(0f, 0.42f, 0.45f), center = at(0f, 0.4f, 0.52f),
            left = at(-0.62f, 0.4f, 0.42f), right = at(0.62f, 0.4f, 0.42f),
            ambience = at(0f, 0.75f, -0.7f), width = 0.6f, subs = listOf(at(0f, 0.06f, 0.55f)),
        ),
        listener = StageListener(0f, 0.39f, -0.2f),
        roomSize = 0.18f,
        walls = StageWalls.Wood,
        roomAmount = 0.35f,
    )

    val All = listOf(
        Default,
        StagePreset(
            id = "living",
            objects = layout(
                vocal = at(0f, 0.38f, 0.6f), center = at(0f, 0.3f, 0.7f),
                left = at(-0.8f, 0.36f, 0.6f), right = at(0.8f, 0.36f, 0.6f),
                ambience = at(0f, 0.6f, -0.85f), width = 0.9f, subs = listOf(at(-0.85f, 0.05f, 0.85f)),
            ),
            listener = StageListener(0f, 0.35f, -0.15f),
            roomSize = 0.3f,
            walls = StageWalls.Soft,
            roomAmount = 0.45f,
        ),
        StagePreset(
            id = "front_row",
            objects = layout(
                vocal = at(0f, 0.3f, 0.55f), center = at(0f, 0.25f, 0.75f),
                left = at(-0.45f, 0.28f, 0.6f), right = at(0.45f, 0.28f, 0.6f),
                ambience = at(0f, 0.6f, -0.8f), width = 1f, subs = listOf(at(-0.6f, 0.04f, 0.7f), at(0.6f, 0.04f, 0.7f)),
            ),
            listener = StageListener(0f, 0.21f, -0.3f),
            roomSize = 0.6f,
            walls = StageWalls.Wood,
            roomAmount = 0.55f,
        ),
        StagePreset(
            id = "on_stage",
            objects = layout(
                vocal = at(0f, 0.3f, 0.25f), center = at(0f, 0.25f, -0.45f),
                left = at(-0.7f, 0.28f, 0.05f), right = at(0.7f, 0.28f, 0.05f),
                ambience = at(0f, 0.5f, 0.9f), width = 1f, subs = listOf(at(0f, 0.04f, -0.5f)),
            ),
            listener = StageListener(0f, 0.27f, 0f),
            roomSize = 0.45f,
            walls = StageWalls.Wood,
            roomAmount = 0.4f,
        ),
        StagePreset(
            id = "around",
            objects = layout(
                vocal = at(0f, 0.38f, 0.5f), center = at(0f, 0.9f, 0.1f),
                left = at(-0.75f, 0.38f, -0.35f), right = at(0.75f, 0.38f, -0.35f),
                ambience = at(0f, 0.85f, -0.6f), width = 0.8f, subs = listOf(at(0f, 0.05f, 0.6f)),
            ),
            listener = StageListener(0f, 0.35f, 0f),
            roomSize = 0.3f,
            walls = StageWalls.Soft,
            roomAmount = 0.4f,
        ),
        StagePreset(
            id = "hall",
            objects = layout(
                vocal = at(0f, 0.2f, 0.55f), center = at(0f, 0.18f, 0.7f),
                left = at(-0.4f, 0.2f, 0.6f), right = at(0.4f, 0.2f, 0.6f),
                ambience = at(0f, 0.6f, -0.2f), width = 1f, subs = listOf(at(0f, 0.02f, 0.6f)),
            ),
            listener = StageListener(0f, 0.15f, -0.35f),
            roomSize = 0.9f,
            walls = StageWalls.Concrete,
            roomAmount = 0.7f,
        ),
    )

    fun apply(settings: StageSettings, preset: StagePreset) = settings.copy(
        presetId = preset.id,
        objects = preset.objects,
        listener = preset.listener,
        roomSize = preset.roomSize,
        walls = preset.walls,
        roomAmount = preset.roomAmount,
    )
}

data class StageSettings(
    val enabled: Boolean = false,
    val presetId: String? = StagePresets.DefaultId,
    val objects: List<StageObject> = StagePresets.Default.objects,
    val listener: StageListener = StagePresets.Default.listener,
    val roomSize: Float = StagePresets.Default.roomSize,
    val walls: StageWalls = StagePresets.Default.walls,
    val roomAmount: Float = StagePresets.Default.roomAmount,
    val levelDb: Float = 0f,
    val onSpeakers: StageSpeakers = StageSpeakers.Stereo,
    val outputKinds: Map<String, Boolean> = emptyMap(),
) {
    val box: StageBox
        get() {
            val width = 3.5f * (22f / 3.5f).pow(roomSize)
            return StageBox(width, width * 1.3f, 2.6f + 6.4f * roomSize.pow(1.5f))
        }

    fun playsOn(speaker: Boolean): Boolean = enabled && !(speaker && onSpeakers == StageSpeakers.Off)

    fun toParams(speaker: Boolean): StageParams {
        val values = FloatArray(StageNativeConstants.ParamCount)
        val box = box
        val wet = playsOn(speaker)
        values[StageNativeConstants.Enabled] = if (wet) 1f else 0f
        values[StageNativeConstants.Mode] = if (speaker && onSpeakers == StageSpeakers.Stereo) {
            StageNativeConstants.ModeSpeakers
        } else {
            StageNativeConstants.ModeHeadphones
        }
        values[StageNativeConstants.RoomWidth] = box.width
        values[StageNativeConstants.RoomDepth] = box.depth
        values[StageNativeConstants.RoomHeight] = box.height
        values[StageNativeConstants.Absorption] = walls.absorption
        values[StageNativeConstants.Damping] = walls.damping
        values[StageNativeConstants.RoomAmount] = roomAmount
        values[StageNativeConstants.ListenerX] = listener.x * box.width / 2
        values[StageNativeConstants.ListenerY] = listener.y * box.height
        values[StageNativeConstants.ListenerZ] = listener.z * box.depth / 2
        values[StageNativeConstants.ListenerYaw] = listener.yaw
        values[StageNativeConstants.Master] = dbToGain(levelDb)

        val soloing = objects.any { it.solo && !it.muted }
        fun gainOf(o: StageObject) = if (o.muted || (soloing && !o.solo)) 0f else dbToGain(o.levelDb)
        fun put(slot: Int, o: StageObject, gain: Float) {
            val at = StageNativeConstants.Objects + slot * StageNativeConstants.ObjectStride
            values[at] = o.x * box.width / 2
            values[at + 1] = o.y * box.height
            values[at + 2] = o.z * box.depth / 2
            values[at + 3] = gain
            values[at + 4] = o.width
        }
        StageSource.entries.forEach { source ->
            val copies = objects.filter { it.source == source }.take(StageNativeConstants.Copies)
            copies.forEachIndexed { i, o ->
                put(source.ordinal * StageNativeConstants.Copies + i, o, gainOf(o) / copies.size)
            }
        }
        return StageParams(wet, values)
    }
}

class StageParams(val enabled: Boolean, val values: FloatArray) {
    companion object {
        val Off = StageParams(false, FloatArray(StageNativeConstants.ParamCount))
    }
}

object StageNativeConstants {
    const val Enabled = 0
    const val Mode = 1
    const val RoomWidth = 2
    const val RoomDepth = 3
    const val RoomHeight = 4
    const val Absorption = 5
    const val Damping = 6
    const val RoomAmount = 7
    const val ListenerX = 8
    const val ListenerY = 9
    const val ListenerZ = 10
    const val ListenerYaw = 11
    const val Master = 12
    const val Objects = 13
    const val ObjectStride = 5
    const val Copies = 4
    const val Kinds = 6
    const val ObjectCount = Kinds * Copies
    const val ParamCount = Objects + ObjectCount * ObjectStride

    const val ModeHeadphones = 0f
    const val ModeSpeakers = 1f
}

internal fun dbToGain(db: Float): Float = 10f.pow(db / 20f)
