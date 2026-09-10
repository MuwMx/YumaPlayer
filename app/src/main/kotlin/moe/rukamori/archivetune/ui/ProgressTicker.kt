package moe.rukamori.archivetune.ui

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProgressTicker(
    private val coroutineScope: CoroutineScope,
    private val audioPlayerProvider: () -> Player?,
    private val updatePlaybackProgress: (Long) -> Unit,
    private val updateDuration: (Long) -> Unit,
    private val onLyricsProgressUpdate: (Long) -> Unit,
) {
    var isUserSeeking: Boolean = false

    var tickerJob: Job? = null
        private set

    fun manageTicker(isPlaying: Boolean) {
        tickerJob?.cancel()
        if (isPlaying) {
            tickerJob = coroutineScope.launch {
                while (isActive) {
                    val player = audioPlayerProvider()
                    if (player != null && !isUserSeeking) {
                        val position = player.currentPosition
                        updatePlaybackProgress(position)
                        val duration = player.duration
                        val validDuration = if (duration > 0L && duration != C.TIME_UNSET) duration else null
                        if (validDuration != null) {
                            updateDuration(validDuration)
                        }
                        onLyricsProgressUpdate(position)
                    }
                    delay(250)
                }
            }
        }
    }

    fun onPlaybackProgress(currentTimeSec: Int, durationSec: Int) {
        if (!isUserSeeking) {
            val progressMs = currentTimeSec * 1000L
            val durationMs = durationSec * 1000L
            updatePlaybackProgress(progressMs)
            updateDuration(durationMs)
            onLyricsProgressUpdate(progressMs)
        }
    }

    fun onSeekStarted() {
        isUserSeeking = true
    }

    fun onSeekFinished() {
        isUserSeeking = false
    }
}
