/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.LazyListState
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder

/**
 * Top bar wiring for the unified screen, extracted from PlaylistDetailScreen
 * to keep that file under the 250-line rule. Owns the show-title scroll rule
 * and the selection-menu dispatch. The cache route's remove-from-cache and
 * the Spotify route's copy button join via [onSelectionRemoveFromCache] /
 * [trailingAction] when their routes flip — keep this signature stable.
 */
@Composable
internal fun PlaylistDetailTopBarHost(
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    menuState: MenuState,
    navController: NavController,
    lazyListState: LazyListState,
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onSelectionRemoveFromCache: (() -> Unit)?,
    trailingAction: (@Composable () -> Unit)?,
) {
    // Legacy showTopBarTitle rule: the title appears once the list scrolls
    // past its first item (OnlinePlaylistScreen.kt:243).
    val showTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    PlaylistDetailTopBar(
        searchState = searchState,
        navController = navController,
        title = title,
        showTitle = showTitle,
        scrollBehavior = scrollBehavior,
        onSelectionMenu = {
            val selectedTracks =
                searchState.wrappedItems
                    .asSequence()
                    .filter { it.isSelected }
                    .map { it.item }
                    .toList()
            showPlaylistDetailSelectionMenu(
                menuState = menuState,
                selectedTracks = selectedTracks,
                likeSourceHint = null,
                onClearSelection = { searchState.clearSelection() },
                onRemoveFromCache = null,
            )
        },
        onSelectionRemoveFromCache = onSelectionRemoveFromCache,
        trailingAction = trailingAction,
    )
}
