/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import androidx.media3.common.util.UnstableApi
import moe.rukamori.archivetune.audiodsp.CustomEqualizerAudioProcessor
import moe.rukamori.archivetune.audiodsp.ParametricEQ
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class EqualizerService @Inject constructor() {

    private val audioProcessors = CopyOnWriteArrayList<CustomEqualizerAudioProcessor>()

    @Volatile private var current: ParametricEQ? = null

    fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.add(processor)
        current?.let(processor::applyProfile) ?: processor.disable()
    }

    fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.remove(processor)
    }

    fun apply(eq: ParametricEQ?) {
        current = eq
        audioProcessors.forEach { processor ->
            if (eq != null) processor.applyProfile(eq) else processor.disable()
        }
    }

    fun disable() = apply(null)

    fun isEnabled(): Boolean = current != null

    fun release() {
        audioProcessors.clear()
    }
}
