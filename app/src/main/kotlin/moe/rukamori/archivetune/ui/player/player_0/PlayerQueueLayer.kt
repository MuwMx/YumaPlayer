package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
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
        ) {
            val peekTopPadding = 16.dp
            val expandedHeaderHeight = CapsuleDefaults.totalHeaderHeight()
            val currentFraction = queueFractionProvider().coerceIn(0f, 1f)
            val expandProgress = ((currentFraction - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val dynamicTopPadding = lerp(peekTopPadding, expandedHeaderHeight, expandProgress)

            PlayerSheetBorderContainer(
                state = state,
                drawBackground = false,
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
                        top = dynamicTopPadding,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                    ),
                    onReorderStateChange = { isQueueReordering = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

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
    }
}
