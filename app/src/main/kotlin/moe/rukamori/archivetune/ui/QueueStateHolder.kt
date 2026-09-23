package moe.rukamori.archivetune.ui

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState

@OptIn(ExperimentalCoroutinesApi::class)
class QueueStateHolder(
    private val coroutineScope: CoroutineScope,
    private val connectionFlow: StateFlow<PlayerConnection?>,
    private val playerConnectionProvider: () -> PlayerConnection?,
    private val audioPlayerProvider: () -> Player?,
    private val updateUiState: ((PlayerUiState) -> PlayerUiState) -> Unit,
) {
    private val _queueState = MutableStateFlow(QueueUiState())
    val queueState: StateFlow<QueueUiState> = _queueState.asStateFlow()

    init {
        coroutineScope.launch {
            connectionFlow
                .flatMapLatest { connection ->
                    if (connection != null) {
                        combine(
                            connection.queueWindows,
                            connection.currentWindowIndex,
                            connection.queueTitle,
                        ) { windows, index, title ->
                            QueueUiState(
                                queueWindows = windows,
                                currentWindowIndex = index,
                                title = title,
                                songCount = windows.size,
                                queueDurationMs = windows.sumOf { (it.mediaItem.metadata?.duration ?: 0).toLong() } * 1000L,
                            )
                        }
                    } else {
                        flowOf(QueueUiState())
                    }
                }
                .collect { state ->
                    _queueState.value = state
                    updateUiState { it.copy(queueTitle = state.title) }
                }
        }
    }

    fun playQueueItem(index: Int) {
        val player = audioPlayerProvider() ?: return
        val resolvedIndex = resolveMediaItemIndex(index) ?: return
        player.seekToDefaultPosition(resolvedIndex)
        player.playWhenReady = true
    }

    fun removeQueueItem(index: Int) {
        val player = audioPlayerProvider() ?: return
        val resolvedIndex = resolveMediaItemIndex(index) ?: return
        player.removeMediaItem(resolvedIndex)
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = audioPlayerProvider() ?: return
        val resolvedFrom = resolveMediaItemIndex(from) ?: return
        val resolvedTo = resolveMediaItemIndex(to) ?: return
        if (resolvedFrom != resolvedTo) {
            player.moveMediaItem(resolvedFrom, resolvedTo)
        }
    }

    private fun resolveMediaItemIndex(index: Int): Int? {
        val player = audioPlayerProvider() ?: return null
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null

        val cachedWindow = _queueState.value.queueWindows.firstOrNull { it.firstPeriodIndex == index }
        if (cachedWindow != null) {
            val window = Timeline.Window()
            for (i in 0 until timeline.windowCount) {
                if (timeline.getWindow(i, window).uid == cachedWindow.uid) {
                    return i
                }
            }
        }

        if (index in 0 until timeline.periodCount) {
            val period = timeline.getPeriod(index, Timeline.Period())
            val windowIndex = period.windowIndex
            if (windowIndex in 0 until timeline.windowCount) {
                return windowIndex
            }
        }

        return if (index in 0 until timeline.windowCount) index else null
    }

    fun clearQueue() {
        playerConnectionProvider()?.clearQueue()
    }
}
