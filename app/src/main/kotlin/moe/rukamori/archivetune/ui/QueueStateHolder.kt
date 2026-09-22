package moe.rukamori.archivetune.ui

import androidx.media3.common.Player
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
        audioPlayerProvider()?.seekToDefaultPosition(index)
    }

    fun removeQueueItem(index: Int) {
        audioPlayerProvider()?.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        audioPlayerProvider()?.moveMediaItem(from, to)
    }

    fun clearQueue() {
        playerConnectionProvider()?.clearQueue()
    }
}
