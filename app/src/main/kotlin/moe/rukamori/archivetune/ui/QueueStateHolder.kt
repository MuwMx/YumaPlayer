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

    fun playQueueItem(windowUid: Any) {
        val player = audioPlayerProvider() ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            if (windowUid is Int) {
                player.seekToDefaultPosition(windowUid)
                player.playWhenReady = true
            }
            return
        }
        val window = Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            val w = timeline.getWindow(i, window)
            if (w.uid == windowUid || matchesQueueKey(w, windowUid)) {
                player.seekToDefaultPosition(i)
                player.playWhenReady = true
                return
            }
        }
    }

    fun removeQueueItem(windowUid: Any) {
        val player = audioPlayerProvider() ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            if (windowUid is Int) {
                player.removeMediaItem(windowUid)
            }
            return
        }
        val window = Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            val w = timeline.getWindow(i, window)
            if (w.uid == windowUid || matchesQueueKey(w, windowUid)) {
                player.removeMediaItem(i)
                return
            }
        }
    }

    fun moveQueueItem(fromUid: Any, toUid: Any) {
        val player = audioPlayerProvider() ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return

        val fromIndex = resolveWindowIndex(timeline, fromUid) ?: return
        val toIndex = resolveWindowIndex(timeline, toUid) ?: return

        if (fromIndex != toIndex && fromIndex in 0 until timeline.windowCount && toIndex in 0 until timeline.windowCount) {
            player.moveMediaItem(fromIndex, toIndex)
        }
    }

    private fun resolveWindowIndex(timeline: Timeline, uid: Any): Int? {
        val window = Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            val w = timeline.getWindow(i, window)
            if (w.uid == uid || matchesQueueKey(w, uid)) {
                return i
            }
        }
        if (uid is Int) {
            if (uid in 0 until timeline.periodCount) {
                val period = timeline.getPeriod(uid, Timeline.Period())
                val windowIndex = period.windowIndex
                if (windowIndex in 0 until timeline.windowCount) {
                    return windowIndex
                }
            }
            if (uid in 0 until timeline.windowCount) {
                return uid
            }
        }
        return null
    }

    private fun matchesQueueKey(window: Timeline.Window, key: Any): Boolean {
        if (key !is Long) return false
        val computed = (window.uid.hashCode().toLong() shl Int.SIZE_BITS) xor
            (window.mediaItem.mediaId.hashCode().toLong() and UInt.MAX_VALUE.toLong())
        return computed == key
    }

    fun clearQueue() {
        playerConnectionProvider()?.clearQueue()
    }
}
