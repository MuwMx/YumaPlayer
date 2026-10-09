package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val fraction = queueFractionProvider().coerceIn(0f, 1f)

                alpha = 1f
                translationY = (1f - fraction) * size.height

                shape = RoundedCornerShape(
                    topStart = 32.dp,
                    topEnd = 32.dp
                )
                clip = true
            }
            .background(Color(state.darkMutedColor))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            QueueSheetHeader(
                queueState = queueState,
                queueFractionProvider = queueFractionProvider,
                onCloseClick = onCloseQueueClick,
                onMoreQueueClick = onMoreQueueClick,
                onToggleAutoMix = { onAction(PlayerAction.ToggleAutoMix) },
                isAutoMixEnabled = state.isAutoMixEnabled,
                state = state,
                isVisible = isQueueVisible
            )

            Spacer(modifier = Modifier.height(8.dp))


            PlayerSheetBorderContainer(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                        top = 16.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                    ),
                    onReorderStateChange = { isQueueReordering = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
