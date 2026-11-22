/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.audiodsp.AudioLabEqPreset
import moe.rukamori.archivetune.audiodsp.AudioLabState
import moe.rukamori.archivetune.audiodsp.EightDParams
import moe.rukamori.archivetune.audiodsp.LoudnessParams
import moe.rukamori.archivetune.audiodsp.ReverbParams
import moe.rukamori.archivetune.audiodsp.SpatialParams
import moe.rukamori.archivetune.audiodsp.StageListener
import moe.rukamori.archivetune.audiodsp.StageObject
import moe.rukamori.archivetune.audiodsp.StagePreset
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSource
import moe.rukamori.archivetune.audiodsp.StageSpeakers
import moe.rukamori.archivetune.playback.audio.AudioFxService
import moe.rukamori.archivetune.playback.audio.AudioLabStore
import javax.inject.Inject

@HiltViewModel
class AudioLabViewModel @Inject constructor(
    private val store: AudioLabStore,
    private val audioFxService: AudioFxService,
) : ViewModel() {

    val state: StateFlow<AudioLabState> = store.state

    val stageLevels: FloatArray
        get() = audioFxService.stageLevels

    val loudnessReductionDb: Float
        get() = audioFxService.loudnessReductionDb

    fun setEqEnabled(enabled: Boolean) {
        store.setEqEnabled(enabled)
    }

    fun applyEqPreset(preset: AudioLabEqPreset) {
        store.applyEqPreset(preset)
    }

    fun setEqPreamp(preampDb: Float) {
        store.setEqPreamp(preampDb)
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        store.setEqBandGain(bandIndex, gainDb)
    }

    fun resetEq() {
        store.resetEq()
    }

    fun updateReverb(transform: (ReverbParams) -> ReverbParams) {
        store.updateReverb(transform)
    }

    fun updateLoudness(transform: (LoudnessParams) -> LoudnessParams) {
        store.updateLoudness(transform)
    }

    fun updateSpatial(transform: (SpatialParams) -> SpatialParams) {
        store.updateSpatial(transform)
    }

    fun updateEightD(transform: (EightDParams) -> EightDParams) {
        store.updateEightD(transform)
    }

    fun updateStage(transform: (StageSettings) -> StageSettings) {
        store.updateStage(transform)
    }

    fun updateStageObject(id: Int, transform: (StageObject) -> StageObject) {
        store.updateStageObject(id, transform)
    }

    fun updateStageListener(transform: (StageListener) -> StageListener) {
        store.updateStageListener(transform)
    }

    fun addStageObject(source: StageSource): Boolean {
        return store.addStageObject(source)
    }

    fun removeStageObject(id: Int): Boolean {
        return store.removeStageObject(id)
    }

    fun applyStagePreset(preset: StagePreset) {
        store.applyStagePreset(preset)
    }

    fun setStageSpeakers(speakers: StageSpeakers) {
        store.setStageSpeakers(speakers)
    }

    fun resetAllFx() {
        store.resetAllFx()
    }
}
