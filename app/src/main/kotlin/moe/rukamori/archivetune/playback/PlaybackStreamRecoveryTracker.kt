/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

internal class PlaybackStreamRecoveryTracker {
    private var attemptedMediaId: String? = null

    fun registerRetryAttempt(mediaId: String): Boolean {
        if (attemptedMediaId == mediaId) return false
        attemptedMediaId = mediaId
        return true
    }

    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && attemptedMediaId == mediaId) {
            attemptedMediaId = null
        }
    }

    fun onMediaItemChanged(currentMediaId: String?) {
        if (attemptedMediaId != currentMediaId) {
            attemptedMediaId = null
        }
    }
}
