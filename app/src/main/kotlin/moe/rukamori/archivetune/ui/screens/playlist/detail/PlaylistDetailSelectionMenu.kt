/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.SelectionMediaMetadataMenu
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.screens.playlist.CachedSong

/**
 * Opens the ORIGIN-accurate multi-select menu, exactly as the legacy screens
 * did per source:
 *  - Local (PlaylistSong): SelectionSongMenu with positions
 *    (LocalPlaylistWiring.kt showLocalPlaylistSelectionMenu).
 *  - Online (SongItem): SelectionMediaMetadataMenu over media metadata
 *    (OnlinePlaylistTopBar.kt:141-160).
 *  - Auto/Top (Song): SelectionSongMenu plain (TopPlaylistScreen.kt:601-613).
 *  - Cache (CachedSong): SelectionSongMenu isFromCache + remove-from-cache
 *    (CachePlaylistScreen.kt:555-566).
 * SpotifyTrack rows never opt into selection, so a Spotify-only selection
 * cannot occur.
 */
internal fun showPlaylistDetailSelectionMenu(
    menuState: MenuState,
    selectedTracks: List<PlaylistTrack>,
    likeSourceHint: LikeSource?,
    onClearSelection: () -> Unit,
    onRemoveFromCache: ((List<Song>) -> Unit)? = null,
) {
    val localSongs = selectedTracks.mapNotNull { it.origin as? PlaylistSong }
    val onlineItems = selectedTracks.mapNotNull { it.origin as? SongItem }
    val roomSongs = selectedTracks.mapNotNull { it.origin as? Song }
    val cachedSongs = selectedTracks.mapNotNull { it.origin as? CachedSong }

    val bucketSizes = listOf(localSongs.size, onlineItems.size, roomSongs.size, cachedSongs.size)

    menuState.show {
        when {
            // ONLINE branch — media metadata menu over SongItems:
            onlineItems.isNotEmpty() && bucketSizes.max() == onlineItems.size ->
                SelectionMediaMetadataMenu(
                    songSelection =
                        onlineItems.map { item ->
                            item.toMediaItem().metadata!!
                        },
                    currentItems = emptyList(),
                    onDismiss = menuState::dismiss,
                    clearAction = onClearSelection,
                    likeSourceHint = likeSourceHint,
                )

            // CACHE PLAYLIST SLOT — from-cache menu with removal (do not
            // delete this branch; the cache flip depends on it):
            cachedSongs.isNotEmpty() && bucketSizes.max() == cachedSongs.size && onRemoveFromCache != null ->
                SelectionSongMenu(
                    songSelection = cachedSongs.map { it.song },
                    onDismiss = menuState::dismiss,
                    clearAction = onClearSelection,
                    isFromCache = true,
                    onRemoveFromCache = onRemoveFromCache,
                )

            // LOCAL branch — positions travel with the maps; otherwise the
            // plain Song list the Auto/Top screens passed:
            else -> {
                val selection: List<Song> = localSongs.map { it.song }
                if (selection.isNotEmpty() || roomSongs.isNotEmpty()) {
                    SelectionSongMenu(
                        songSelection = if (selection.isNotEmpty()) selection else roomSongs,
                        onDismiss = menuState::dismiss,
                        clearAction = onClearSelection,
                        songPosition = localSongs.map { it.map },
                        likeSourceHint = likeSourceHint,
                    )
                }
            }
        }
    }
}
