package moe.rukamori.archivetune.ui

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.db.entities.formattedBitrate
import moe.rukamori.archivetune.db.entities.formattedQuality
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.state.PlayerUiState

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStateHolder(
    private val coroutineScope: CoroutineScope,
    private val connectionFlow: StateFlow<PlayerConnection?>,
    private val playerConnectionProvider: () -> PlayerConnection?,
    private val audioPlayerProvider: () -> Player?,
    private val uiStateProvider: () -> PlayerUiState,
    private val updateUiState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val onResetLyrics: () -> Unit,
    private val onManageTicker: (Boolean) -> Unit,
) {
    private val sleepTimer get() = playerConnectionProvider()?.service?.sleepTimer

    init {
        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { it.service.sleepTimer.remainingSeconds }
                .collect { seconds ->
                    updateUiState {
                        it.copy(sleepTimerRemainingSeconds = if (seconds > 0L) seconds.toInt() else null)
                    }
                }
        }

        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { connection -> connection.mediaMetadata }
                .collect { metadata ->
                    if (metadata == null) {
                        val oldId = uiStateProvider().trackUrl
                        if (oldId.isNotEmpty()) {
                            onResetLyrics()
                        }
                        updateUiState {
                            it.copy(
                                trackUrl = "",
                                title = "",
                                artist = "",
                                album = null,
                                coverUrl = "",
                                isPlaying = false,
                            )
                        }
                        return@collect
                    }
                    val title = metadata.title
                    val artist = metadata.artists.joinToString { it.name }
                    val album = metadata.album?.title

                    val resolvedDuration = if (metadata.duration > 0) {
                        metadata.duration * 1000L
                    } else {
                        audioPlayerProvider()?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
                    }

                    val incomingCoverUrl = metadata.thumbnailUrl?.trim()?.takeIf(String::isNotBlank)

                    val oldId = uiStateProvider().trackUrl
                    val newId = metadata.id
                    if (oldId != newId) {
                        onResetLyrics()
                    }

                    updateUiState { currentUi ->
                        val resolvedCoverUrl = incomingCoverUrl
                            ?: currentUi.coverUrl.takeIf { oldId == newId && it.isNotBlank() }
                            ?: ""
                        currentUi.copy(
                            title = title,
                            artist = artist,
                            album = album,
                            trackUrl = metadata.id,
                            durationMs = resolvedDuration,
                            coverUrl = resolvedCoverUrl,
                        )
                    }
                }
        }

        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { it.playbackState }
                .collect { playbackState ->
                    val realDuration = audioPlayerProvider()?.duration?.takeIf { it > 0L && it != C.TIME_UNSET }
                    updateUiState { current ->
                        current.copy(
                            isLoading = playbackState == Player.STATE_BUFFERING,
                            durationMs = if (realDuration != null && current.durationMs <= 0L) realDuration else current.durationMs,
                        )
                    }
                }
        }

        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { connection ->
                    combine(connection.mediaMetadata, connection.currentSong) { metadata, song ->
                        if (metadata == null || song == null) return@combine false
                        song.song.liked
                    }
                }
                .collect { isLiked ->
                    updateUiState { currentUi ->
                        currentUi.copy(isLiked = isLiked)
                    }
                }
        }

        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { connection -> connection.isPlaying }
                .collect { playing ->
                    updateUiState { it.copy(isPlaying = playing) }
                    onManageTicker(playing)
                }
        }

        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { connection ->
                    combine(
                        connection.currentFormat,
                        connection.audioFormat,
                    ) { dbFormat, liveFormat ->
                        if (!liveFormat.isNullOrBlank() && liveFormat != "UNKNOWN") {
                            liveFormat
                        } else if (dbFormat != null) {
                            val isLossless = dbFormat.codecLabel() == "FLAC" || dbFormat.codecLabel() == "ALAC"
                            val quality = if (isLossless) {
                                dbFormat.formattedQuality()
                            } else {
                                dbFormat.formattedBitrate()
                            }
                            "${dbFormat.codecLabel()} | $quality"
                        } else {
                            ""
                        }
                    }
                }
                .collect { format ->
                    if (format.isNotEmpty()) {
                        updateUiState { it.copy(codecInfo = format) }
                    }
                }
        }

        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { it.shuffleModeEnabled }
                .collect { enabled ->
                    updateUiState { it.copy(shuffleState = if (enabled) "on" else "off") }
                }
        }

        coroutineScope.launch {
            connectionFlow
                .filterNotNull()
                .flatMapLatest { it.repeatMode }
                .collect { mode ->
                    val state = when (mode) {
                        Player.REPEAT_MODE_OFF -> "off"
                        Player.REPEAT_MODE_ONE -> "one"
                        Player.REPEAT_MODE_ALL -> "all"
                        else -> "off"
                    }
                    updateUiState { it.copy(repeatState = state) }
                }
        }
    }

    fun togglePlayPause() {
        val player = audioPlayerProvider() ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        if (player.playWhenReady) player.pause() else player.play()
    }

    fun playNext() {
        playerConnectionProvider()?.seekToNext()
    }

    fun playPrevious() {
        playerConnectionProvider()?.seekToPrevious()
    }

    fun toggleShuffle() {
        val player = audioPlayerProvider() ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val player = audioPlayerProvider() ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleLike() {
        playerConnectionProvider()?.toggleLike()
    }

    fun adjustSleepTimer(minutes: Int) {
        val timer = sleepTimer ?: return
        val currentSeconds = timer.remainingSeconds.value
        val newMinutes = (currentSeconds / 60) + minutes
        if (newMinutes > 0) {
            timer.start(newMinutes.toInt())
        } else {
            timer.stop()
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimer?.start(minutes)
    }

    fun stopSleepTimer() {
        sleepTimer?.stop()
    }
}
