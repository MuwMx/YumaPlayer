/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

val StandardEqFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

data class AudioLabEqSettings(
    val enabled: Boolean = false,
    val presetId: String = "flat",
    val preampDb: Float = 0f,
    val bandGainsDb: List<Float> = List(10) { 0f },
) {
    fun toParametricEq(): ParametricEQ {
        val bands = StandardEqFrequencies.mapIndexed { index, freq ->
            val gain = bandGainsDb.getOrElse(index) { 0f }.toDouble()
            ParametricEQBand(
                frequency = freq.toDouble(),
                gain = gain,
                q = 1.41,
                filterType = FilterType.PK,
                enabled = true,
            )
        }
        return ParametricEQ(
            preamp = preampDb.toDouble(),
            bands = bands,
            metadata = mapOf("preset" to presetId),
        )
    }
}

fun AudioLabEqSettings.lowMidHighAverages(): Triple<Float, Float, Float> {
    val b = bandGainsDb
    val lowSum = (0..2).sumOf { b.getOrElse(it) { 0f }.toDouble() }.toFloat()
    val midSum = (3..6).sumOf { b.getOrElse(it) { 0f }.toDouble() }.toFloat()
    val highSum = (7..9).sumOf { b.getOrElse(it) { 0f }.toDouble() }.toFloat()
    val lowAvg = (lowSum / 3f / 12f).coerceIn(-1f, 1f)
    val midAvg = (midSum / 4f / 12f).coerceIn(-1f, 1f)
    val highAvg = (highSum / 3f / 12f).coerceIn(-1f, 1f)
    return Triple(lowAvg, midAvg, highAvg)
}

data class AudioLabEqPreset(
    val id: String,
    val name: String,
    val gainsDb: List<Float>,
    val preampDb: Float = 0f,
)

object AudioLabEqPresets {
    val Flat = AudioLabEqPreset(
        id = "flat",
        name = "Flat",
        gainsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        preampDb = 0f,
    )

    val BassBoost = AudioLabEqPreset(
        id = "bass_boost",
        name = "Bass Boost",
        gainsDb = listOf(5.5f, 4.8f, 3.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f),
        preampDb = -2f,
    )

    val Vocal = AudioLabEqPreset(
        id = "vocal",
        name = "Vocal",
        gainsDb = listOf(-1.5f, -1.0f, 0f, 1.5f, 3.0f, 3.5f, 2.0f, 0.5f, -0.5f, -1.0f),
        preampDb = 0f,
    )

    val TrebleBoost = AudioLabEqPreset(
        id = "treble_boost",
        name = "Treble Boost",
        gainsDb = listOf(0f, 0f, 0f, 0f, 0.5f, 1.5f, 3.0f, 4.5f, 5.5f, 6.0f),
        preampDb = -2f,
    )

    val Warm = AudioLabEqPreset(
        id = "warm",
        name = "Warm",
        gainsDb = listOf(3.0f, 2.5f, 2.0f, 1.0f, 0.5f, 0f, -0.5f, -1.5f, -2.5f, -3.0f),
        preampDb = 0f,
    )

    val Studio = AudioLabEqPreset(
        id = "studio",
        name = "Studio",
        gainsDb = listOf(1.0f, 0.5f, 0f, 0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 1.5f),
        preampDb = 0f,
    )

    val Club = AudioLabEqPreset(
        id = "club",
        name = "Club",
        gainsDb = listOf(4.5f, 4.0f, 2.5f, 0f, 0f, 0f, 1.5f, 2.5f, 3.0f, 3.5f),
        preampDb = -1.5f,
    )

    val Acoustic = AudioLabEqPreset(
        id = "acoustic",
        name = "Acoustic",
        gainsDb = listOf(3.5f, 2.5f, 1.5f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f, 2.5f, 2.0f),
        preampDb = -1f,
    )

    val All: List<AudioLabEqPreset> = listOf(
        Flat,
        BassBoost,
        Vocal,
        TrebleBoost,
        Warm,
        Studio,
        Club,
        Acoustic,
    )

    fun byId(id: String): AudioLabEqPreset? = All.firstOrNull { it.id.equals(id, ignoreCase = true) }
}

data class AudioLabState(
    val eq: AudioLabEqSettings = AudioLabEqSettings(),
    val reverb: ReverbParams = ReverbParams(),
    val loudness: LoudnessParams = LoudnessParams(),
    val spatial: SpatialParams = SpatialParams(),
    val eightD: EightDParams = EightDParams(),
    val stage: StageSettings = StageSettings(),
)

data class AudioLabSoundProfile(
    val id: String,
    val name: String,
)

object AudioLabSoundProfiles {
    val Pure = AudioLabSoundProfile("pure", "Pure")
    val Studio3D = AudioLabSoundProfile("studio_3d", "Studio 3D")
    val ConcertHall = AudioLabSoundProfile("concert_hall", "Concert Hall")
    val LateNight = AudioLabSoundProfile("late_night", "Late Night")
    val ClubBass = AudioLabSoundProfile("club_bass", "Club Bass")

    val All = listOf(Pure, Studio3D, ConcertHall, LateNight, ClubBass)

    fun apply(current: AudioLabState, profileId: String): AudioLabState {
        return when (profileId) {
            Pure.id -> current.copy(
                eq = current.eq.copy(enabled = false),
                stage = current.stage.copy(enabled = false),
                reverb = current.reverb.copy(enabled = false),
                loudness = current.loudness.copy(enabled = false),
                spatial = current.spatial.copy(enabled = false),
                eightD = current.eightD.copy(enabled = false),
            )
            Studio3D.id -> {
                val eqPreset = AudioLabEqPresets.Studio
                val stagePreset = StagePresets.Default
                current.copy(
                    eq = current.eq.copy(
                        enabled = true,
                        presetId = eqPreset.id,
                        preampDb = eqPreset.preampDb,
                        bandGainsDb = eqPreset.gainsDb,
                    ),
                    stage = StagePresets.apply(current.stage, stagePreset).copy(enabled = true),
                    reverb = current.reverb.copy(enabled = false),
                    loudness = current.loudness.copy(enabled = false),
                    spatial = current.spatial.copy(enabled = false),
                    eightD = current.eightD.copy(enabled = false),
                )
            }
            ConcertHall.id -> {
                val hallPreset = StagePresets.All.firstOrNull { it.id == "hall" } ?: StagePresets.Default
                current.copy(
                    eq = current.eq.copy(enabled = false),
                    stage = StagePresets.apply(current.stage, hallPreset).copy(enabled = true),
                    reverb = current.reverb.copy(enabled = true),
                    loudness = current.loudness.copy(enabled = false),
                    spatial = current.spatial.copy(enabled = false),
                    eightD = current.eightD.copy(enabled = false),
                )
            }
            LateNight.id -> {
                val warmPreset = AudioLabEqPresets.Warm
                current.copy(
                    eq = current.eq.copy(
                        enabled = true,
                        presetId = warmPreset.id,
                        preampDb = warmPreset.preampDb,
                        bandGainsDb = warmPreset.gainsDb,
                    ),
                    stage = current.stage.copy(enabled = false),
                    reverb = current.reverb.copy(enabled = false),
                    loudness = current.loudness.copy(enabled = true),
                    spatial = current.spatial.copy(enabled = false),
                    eightD = current.eightD.copy(enabled = false),
                )
            }
            ClubBass.id -> {
                val clubPreset = AudioLabEqPresets.Club
                current.copy(
                    eq = current.eq.copy(
                        enabled = true,
                        presetId = clubPreset.id,
                        preampDb = clubPreset.preampDb,
                        bandGainsDb = clubPreset.gainsDb,
                    ),
                    stage = current.stage.copy(enabled = false),
                    reverb = current.reverb.copy(enabled = false),
                    loudness = current.loudness.copy(enabled = true),
                    spatial = current.spatial.copy(enabled = false),
                    eightD = current.eightD.copy(enabled = false),
                )
            }
            else -> current
        }
    }

    fun matchingProfileId(state: AudioLabState): String? {
        val allDisabled = !state.eq.enabled && !state.stage.enabled && !state.reverb.enabled &&
            !state.loudness.enabled && !state.spatial.enabled && !state.eightD.enabled
        if (allDisabled) return Pure.id

        val studio3dMatch = state.eq.enabled && state.eq.presetId == AudioLabEqPresets.Studio.id &&
            state.stage.enabled && state.stage.presetId == StagePresets.DefaultId &&
            !state.reverb.enabled && !state.loudness.enabled && !state.spatial.enabled && !state.eightD.enabled
        if (studio3dMatch) return Studio3D.id

        val concertHallMatch = state.stage.enabled && state.stage.presetId == "hall" &&
            state.reverb.enabled && !state.eq.enabled &&
            !state.loudness.enabled && !state.spatial.enabled && !state.eightD.enabled
        if (concertHallMatch) return ConcertHall.id

        val lateNightMatch = state.eq.enabled && state.eq.presetId == AudioLabEqPresets.Warm.id &&
            state.loudness.enabled && !state.stage.enabled &&
            !state.reverb.enabled && !state.spatial.enabled && !state.eightD.enabled
        if (lateNightMatch) return LateNight.id

        val clubBassMatch = state.eq.enabled &&
            (state.eq.presetId == AudioLabEqPresets.Club.id || state.eq.presetId == AudioLabEqPresets.BassBoost.id) &&
            state.loudness.enabled && !state.stage.enabled &&
            !state.reverb.enabled && !state.spatial.enabled && !state.eightD.enabled
        if (clubBassMatch) return ClubBass.id

        return null
    }
}
