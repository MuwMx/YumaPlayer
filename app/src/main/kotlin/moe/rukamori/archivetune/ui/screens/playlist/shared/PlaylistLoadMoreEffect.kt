/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.shared

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

/**
 * Triggers [onLoadMore] when the user scrolls within 5 rows of the end of the
 * list. Moved verbatim from OnlinePlaylistContinuationEffect; shared so
 * paginated playlist sources (InnerTube, Spotify) reuse it.
 */
@Composable
fun PlaylistLoadMoreEffect(
    lazyListState: LazyListState,
    itemsCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                itemsCount >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= itemsCount - 5
            ) {
                onLoadMore()
            }
        }
    }
}
