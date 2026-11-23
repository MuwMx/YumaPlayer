/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.audiodsp.AudioLabState
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.playback.audio.AudioLabStore
import kotlin.math.abs

enum class PassBlocker { EQUALIZER, AUDIO_FX, VOLUME, NORMALIZATION, SPEED, TRANSITIONS, SILENCE_OR_HAPTICS }

data class LabSound(
    val eq: Boolean = false,
    val reverb: Boolean = false,
    val loudness: Boolean = false,
    val spatial: Boolean = false,
    val mono: Boolean = false,
    val eightD: Boolean = false,
    val stage: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float = 1f,
) {
    companion object {
        fun of(state: AudioLabState, speed: Float = 1f, pitch: Float = 1f): LabSound = LabSound(
            eq = state.eq.enabled,
            reverb = state.reverb.enabled,
            loudness = state.loudness.enabled,
            spatial = state.spatial.enabled,
            mono = state.spatial.mono,
            eightD = state.eightD.enabled,
            stage = state.stage.enabled,
            speed = speed,
            pitch = pitch,
        )
    }
}

fun passBlockersOf(prefs: Preferences, lab: LabSound, playerVolume: Float): Set<PassBlocker> = buildSet {
    if (lab.eq) add(PassBlocker.EQUALIZER)
    if (lab.reverb || lab.loudness || lab.spatial || lab.mono || lab.eightD || lab.stage) add(PassBlocker.AUDIO_FX)
    if (playerVolume < 0.999f) add(PassBlocker.VOLUME)
    if (prefs[AudioNormalizationKey] ?: true) add(PassBlocker.NORMALIZATION)
    if (abs(lab.speed - 1f) > 0.001f || abs(lab.pitch - 1f) > 0.001f) add(PassBlocker.SPEED)
    if (prefs[CrossfadeEnabledKey] ?: false) add(PassBlocker.TRANSITIONS)
    if (prefs[SkipSilenceKey] ?: false) add(PassBlocker.SILENCE_OR_HAPTICS)
}

@Serializable
data class PassBlockersBefore(
    val normalization: Boolean?,
    val crossfade: Boolean?,
    val skipSilence: Boolean?,
    val eq: Boolean,
    val reverb: Boolean,
    val loudness: Boolean,
    val spatial: Boolean,
    val mono: Boolean,
    val eightD: Boolean,
    val stage: Boolean,
    val playerVolume: Float,
    val speed: Float = 1f,
    val pitch: Float = 1f,
) {
    companion object {
        fun of(prefs: Preferences, store: AudioLabStore, playerVolume: Float, speed: Float = 1f, pitch: Float = 1f): PassBlockersBefore {
            val state = store.state.value
            return PassBlockersBefore(
                normalization = prefs[AudioNormalizationKey],
                crossfade = prefs[CrossfadeEnabledKey],
                skipSilence = prefs[SkipSilenceKey],
                eq = state.eq.enabled,
                reverb = state.reverb.enabled,
                loudness = state.loudness.enabled,
                spatial = state.spatial.enabled,
                mono = state.spatial.mono,
                eightD = state.eightD.enabled,
                stage = state.stage.enabled,
                playerVolume = playerVolume,
                speed = speed,
                pitch = pitch,
            )
        }
    }
}

fun MutablePreferences.restorePassBlockers(before: PassBlockersBefore) {
    fun <T> put(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
    put(AudioNormalizationKey, before.normalization)
    put(CrossfadeEnabledKey, before.crossfade)
    put(SkipSilenceKey, before.skipSilence)
}

fun AudioLabStore.restorePassBlockers(before: PassBlockersBefore) {
    setEqEnabled(before.eq)
    updateReverb { it.copy(enabled = before.reverb) }
    updateLoudness { it.copy(enabled = before.loudness) }
    updateSpatial { it.copy(enabled = before.spatial, mono = before.mono) }
    updateEightD { it.copy(enabled = before.eightD) }
    updateStage { it.copy(enabled = before.stage) }
}

fun MutablePreferences.clearPassBlockers() {
    this[AudioNormalizationKey] = false
    this[CrossfadeEnabledKey] = false
    this[SkipSilenceKey] = false
}

fun AudioLabStore.clearPassBlockers() {
    setEqEnabled(false)
    updateReverb { it.copy(enabled = false) }
    updateLoudness { it.copy(enabled = false) }
    updateSpatial { it.copy(enabled = false, mono = false) }
    updateEightD { it.copy(enabled = false) }
    updateStage { it.copy(enabled = false) }
}
