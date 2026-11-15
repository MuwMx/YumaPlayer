/*
 * YumaPlayer (2026) | Modified by MuwMx
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
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder

/**
 * Top bar wiring for the unified screen, extracted from PlaylistDetailScreen
 * to keep that file under the 250-line rule. Owns the show-title scroll rule
 * and the selection-menu dispatch.
 *
 * [onRemoveSongFromCache] is the CACHE PLAYLIST SLOT (pinned): per-song-id
 * cache removal, null on every other route. It backs BOTH the top-bar delete
 * button (legacy CachePlaylistScreen.kt:526-536 — selected ids, then clear
 * selection) and the selection menu's from-cache branch (555-566).
 * [trailingAction] carries the auto more-menu and the future Spotify copy
 * button. [likeSourceHint] is the LOCAL PLAYLIST SLOT: legacy
 * showLocalPlaylistSelectionMenu pinned the selection like toggle to the
 * playlist's own browse-id space (LikeSource.SPOTIFY for "spotify:"-linked
 * playlists instead of auto-resolving per song). Keep this signature stable.
 */
@Composable
internal fun PlaylistDetailTopBarHost(
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    menuState: MenuState,
    navController: NavController,
    lazyListState: LazyListState,
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onRemoveSongFromCache: ((songId: String) -> Unit)?,
    likeSourceHint: LikeSource?,
    trailingAction: (@Composable () -> Unit)?,
) {
    // Legacy showTopBarTitle rule: the title appears once the list scrolls
    // past its first item (OnlinePlaylistScreen.kt:243).
    val showTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    // Top-bar delete: removes every SELECTED song's cache, then clears.
    val selectionDelete: (() -> Unit)? =
        if (onRemoveSongFromCache != null) {
            {
                searchState.wrappedItems
                    .filter { it.isSelected }
                    .forEach { onRemoveSongFromCache(it.item.id) }
                searchState.clearSelection()
            }
        } else {
            null
        }

    // Menu branch: remove the passed songs' caches, then clear.
    val menuRemoveFromCache: ((List<Song>) -> Unit)? =
        if (onRemoveSongFromCache != null) {
            { songs ->
                songs.forEach { onRemoveSongFromCache(it.id) }
                searchState.clearSelection()
            }
        } else {
            null
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
                likeSourceHint = likeSourceHint,
                onClearSelection = { searchState.clearSelection() },
                onRemoveFromCache = menuRemoveFromCache,
            )
        },
        onSelectionRemoveFromCache = selectionDelete,
        trailingAction = trailingAction,
    )
}
