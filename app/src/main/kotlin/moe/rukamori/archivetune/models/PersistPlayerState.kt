/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.models

import java.io.Serializable

data class PersistPlayerState(
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val volume: Float,
    val currentPosition: Long,
    val currentMediaItemIndex: Int,
    val playbackState: Int,
    val timestamp: Long = System.currentTimeMillis(),
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
