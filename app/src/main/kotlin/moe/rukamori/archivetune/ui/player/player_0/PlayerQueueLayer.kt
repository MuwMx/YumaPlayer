package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.ActiveDragSheet
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.queue_0.QueueScreen
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState

@Composable
internal fun PlayerQueueLayer(
    state: PlayerUiState,
    queueState: QueueUiState,
    queueFractionProvider: () -> Float,
    onAction: (PlayerAction) -> Unit,
    onCloseQueueClick: () -> Unit = {},
    onMoreQueueClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    dragHandler: SheetVerticalDragGestureHandler? = null,
) {
    var isQueueReordering by remember { mutableStateOf(false) }
    val queueListState = rememberLazyListState()
    val canDragQueue by remember(queueListState, isQueueReordering) {
        derivedStateOf {
            queueFractionProvider() > 0.05f &&
                    !isQueueReordering &&
                    queueListState.firstVisibleItemIndex == 0 &&
                    queueListState.firstVisibleItemScrollOffset == 0
        }
    }
    val queueNestedScrollConnection = remember(dragHandler) {
        dragHandler?.createNestedScrollConnection(
            canDragProvider = { canDragQueue },
            targetSheet = ActiveDragSheet.QUEUE
        )
    }

    val isQueueVisible by remember {
        derivedStateOf { queueFractionProvider() > 0.05f }
    }



    PlayerOverlaySheet(
        fractionProvider = queueFractionProvider,
        backgroundColor = Color.Transparent,
        modifier = modifier,
        showDragHandle = true,
        dragHandler = dragHandler,
        headerContent = {
            val isHeaderVisible by remember {
                derivedStateOf { queueFractionProvider() > 0.85f }
            }

            if (isHeaderVisible) {
                QueueSheetHeader(
                    queueState = queueState,
                    queueFractionProvider = queueFractionProvider,
                    onCloseClick = onCloseQueueClick,
                    onMoreQueueClick = onMoreQueueClick,
                    onToggleAutoMix = { onAction(PlayerAction.ToggleAutoMix) },
                    isAutoMixEnabled = state.isAutoMixEnabled,
                    state = state,
                    isVisible = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (queueNestedScrollConnection != null && !isQueueReordering) {
                        Modifier.nestedScroll(queueNestedScrollConnection)
                    } else {
                        Modifier
                    }
                )
        ) {
            QueueScreen(
                state = queueState,
                onAction = onAction,
                queueFractionProvider = queueFractionProvider,
                isSheetActive = isQueueVisible,
                onCloseClick = onCloseQueueClick,
                lazyListState = queueListState,
                contentPadding = PaddingValues(
                    top = 28.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
                onReorderStateChange = { isQueueReordering = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
