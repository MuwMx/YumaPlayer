/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.auto

import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistSearchStateHolder

/**
 * Adapter over the shared [PlaylistSearchStateHolder] for Song-backed (Auto /
 * Liked / Downloaded) playlist screens. The search/selection logic now lives
 * in one place; call sites use the holder's property names directly
 * (wrappedItems / filteredItems).
 */
typealias AutoPlaylistSearchState = PlaylistSearchStateHolder<Song>

@Composable
fun rememberAutoPlaylistSearchState(
    songs: List<Song>,
): AutoPlaylistSearchState =
    rememberPlaylistSearchStateHolder(
        items = songs,
        filter = { song, query ->
            song.song.title.contains(query, true) ||
                song.artists.any { it.name.contains(query, true) }
        },
    )
