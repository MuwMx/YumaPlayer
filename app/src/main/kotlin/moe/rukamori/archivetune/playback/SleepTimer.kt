package moe.rukamori.archivetune.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdvancedSleepTimer(
    private val scope: CoroutineScope,
    private val player: Player,
) : Player.Listener {

    private var timerJob: Job? = null

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds = _remainingSeconds.asStateFlow()

    private val _pauseWhenSongEnd = MutableStateFlow(false)
    val pauseWhenSongEnd = _pauseWhenSongEnd.asStateFlow()

    fun start(minutes: Int) {
        stop()
        if (minutes == -1) {
            _pauseWhenSongEnd.value = true
            player.addListener(this)
        } else {
            _remainingSeconds.value = minutes * 60L
            timerJob = scope.launch {
                while (_remainingSeconds.value > 0) {
                    delay(1000)
                    _remainingSeconds.value -= 1
                }
                player.pause()
                stop()
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        _remainingSeconds.value = 0L
        _pauseWhenSongEnd.value = false
        player.removeListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (_pauseWhenSongEnd.value) {
            player.pause()
            stop()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && _pauseWhenSongEnd.value) {
            player.pause()
            stop()
        }
    }
}