/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.playlist.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton

/**
 * Wraps a playlist song row with the behavior implied by [capabilities].
 *
 * The caller usually supplies the row content itself (built on
 * [moe.rukamori.archivetune.ui.component.SongListItem] / YouTubeListItem /
 * SpotifyTrackListItem) via [content]; this component adds ONLY the generic
 * wrappers:
 *  - swipe-to-delete (Material3 [SwipeToDismissBox]) when
 *    [PlaylistRowCapabilities.swipeDelete] is set, with identical guards to the
 *    pre-existing Local implementation (single-fire `processedDismiss` latch +
 *    confirm suppressed while the list is scrolling).
 *
 * The trailing drag handle ([PlaylistDragHandle]) is a separate composable
 * because it must be invoked INSIDE the `ReorderableCollectionItemScope` of
 * `ReorderableItem` and is wired per-list today (Local only); do not hoist it
 * here yet.
 *
 * Keys, sort headers and per-source menus stay with the calling list section —
 * this is the leaf, not the list.
 */
@Composable
fun PlaylistSongRow(
    capabilities: PlaylistRowCapabilities,
    isScrollInProgress: Boolean,
    onSwipeDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (!capabilities.swipeDelete) {
        Row(modifier = modifier.fillMaxWidth()) { content() }
        return
    }

    val currentOnDelete by rememberUpdatedState(onSwipeDelete)
    val currentIsScrollInProgress by rememberUpdatedState(isScrollInProgress)

    val dismissBoxState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance },
            confirmValueChange = { targetValue ->
                targetValue == SwipeToDismissBoxValue.Settled || !currentIsScrollInProgress
            },
        )
    var processedDismiss by remember { mutableStateOf(false) }
    LaunchedEffect(dismissBoxState.currentValue) {
        val dv = dismissBoxState.currentValue
        if (!processedDismiss && (
                dv == SwipeToDismissBoxValue.StartToEnd ||
                    dv == SwipeToDismissBoxValue.EndToStart
            )
        ) {
            processedDismiss = true
            currentOnDelete()
        }
        if (dv == SwipeToDismissBoxValue.Settled) {
            processedDismiss = false
        }
    }

    SwipeToDismissBox(
        state = dismissBoxState,
        backgroundContent = {},
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * Drag handle for reorderable playlist rows. The returned [Modifier] must be
 * applied inside a `ReorderableItem` scope's handle composable (invoke
 * `draggableHandle()` there); this only draws the icon+button chrome shared by
 * all reorderable playlist screens.
 */
@Composable
fun PlaylistDragHandle(
    modifier: Modifier = Modifier,
    onDragHandleModifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { },
        onLongClick = { },
        modifier =
            modifier
                .then(onDragHandleModifier)
                .graphicsLayer { alpha = 0.99f },
    ) {
        Icon(
            painter = painterResource(R.drawable.drag_handle),
            contentDescription = null,
        )
    }
}
