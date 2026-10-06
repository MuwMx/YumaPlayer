/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.queue_0

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.Timeline
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.component.MediaMetadataListItem

@Composable
internal fun QueueItem(
    window: Timeline.Window,
    isActive: Boolean,
    isDragging: Boolean,
    cropToSquare: Boolean,
    itemWidthPx: Float,
    shouldLoadImage: Boolean,
    isSheetActive: Boolean,
    enableHapticFeedback: Boolean,
    hapticView: View,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = window.mediaItem.metadata ?: return
    val dismissScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissOffsetAnimatable = remember(window.queueItemKey) { Animatable(0f) }

    val dismissEnabled = !isDragging
    val dismissHandler =
        remember(window.queueItemKey, dismissEnabled, itemWidthPx, enableHapticFeedback) {
            if (dismissEnabled && itemWidthPx > 0f) {
                QueueItemDismissGestureHandler(
                    scope = dismissScope,
                    density = density,
                    hapticView = hapticView,
                    hapticFeedbackEnabled = enableHapticFeedback,
                    offsetAnimatable = dismissOffsetAnimatable,
                    itemWidthPx = itemWidthPx,
                    onDismiss = onRemove,
                )
            } else {
                null
            }
        }

    val isDismissActive by remember {
        derivedStateOf { dismissOffsetAnimatable.value != 0f }
    }

    val dismissGestureModifier =
        if (dismissEnabled && dismissHandler != null) {
            Modifier.pointerInput(window.queueItemKey, dismissHandler) {
                detectHorizontalDragGestures(
                    onDragStart = { dismissHandler.onDragStart() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dismissHandler.onHorizontalDrag(dragAmount)
                    },
                    onDragEnd = { dismissHandler.onDragEnd() },
                    onDragCancel = { dismissHandler.onDragCancel() },
                )
            }
        } else {
            Modifier
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.weight(1f),
        ) {
            if (isDismissActive) {
                QueueItemDismissReveal(
                    dismissHandler = dismissHandler,
                    dismissOffsetAnimatable = dismissOffsetAnimatable,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = dismissOffsetAnimatable.value }
                        .then(dismissGestureModifier),
            ) {
                MediaMetadataListItem(
                    mediaMetadata = metadata,
                    isActive = isActive,
                    isPlaying = isActive,
                    cropToSquare = cropToSquare,
                    shouldLoadImage = shouldLoadImage,
                    isSheetActive = isSheetActive,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDismissActive) {
                                if (dismissOffsetAnimatable.value == 0f) {
                                    onPlay()
                                }
                            },
                )
            }
        }

        dragHandle()
    }
}
