package moe.rukamori.archivetune.ui.player.player_0

import android.content.Context
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density = LocalDensity.current,
    context: Context = LocalContext.current,
    hapticView: View = LocalView.current,
    hapticFeedbackEnabled: Boolean,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissPlaylistAndShowUndo: () -> Unit,
    onDismissStarted: () -> Unit = {}
): MiniPlayerDismissGestureHandler {
    val onDismissPlaylistAndShowUndoState = rememberUpdatedState(onDismissPlaylistAndShowUndo)
    val onDismissStartedState = rememberUpdatedState(onDismissStarted)
    return remember(scope, density, context, hapticView, hapticFeedbackEnabled, offsetAnimatable, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            context = context,
            hapticView = hapticView,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissPlaylistAndShowUndo = { onDismissPlaylistAndShowUndoState.value() },
            onDismissStarted = { onDismissStartedState.value() }
        )
    }
}

internal fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragEnd() }
        )
    }
}
