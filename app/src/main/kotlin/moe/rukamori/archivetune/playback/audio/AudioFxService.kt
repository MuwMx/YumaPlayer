/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio

import moe.rukamori.archivetune.audiodsp.AudioFxConfigProvider
import moe.rukamori.archivetune.audiodsp.LoudnessParams
import moe.rukamori.archivetune.audiodsp.ReverbParams
import moe.rukamori.archivetune.audiodsp.SpatialParams
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFxService @Inject constructor() : AudioFxConfigProvider {
    @Volatile
    override var reverb: ReverbParams = ReverbParams()

    @Volatile
    override var loudness: LoudnessParams = LoudnessParams()

    @Volatile
    override var spatial: SpatialParams = SpatialParams()

    @Volatile
    override var loudnessReductionDb: Float = 0f
}
