/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistSearchStateHolder

/**
 * Search/selection state for the Online playlist screen, now backed by the
 * shared [PlaylistSearchStateHolder]. Rows wrap Pair<index, SongItem> so the
 * list key can be the setVideoId (see [onlinePlaylistSongKey]).
 */
typealias OnlinePlaylistSearchState = PlaylistSearchStateHolder<Pair<Int, SongItem>>

@Composable
fun rememberOnlinePlaylistSearchState(songs: List<SongItem>): OnlinePlaylistSearchState =
    rememberPlaylistSearchStateHolder(
        items = songs.mapIndexed { index, song -> index to song },
        filter = { (_, song), query ->
            song.title.contains(query, ignoreCase = true) ||
                song.artists.any { it.name.contains(query, ignoreCase = true) }
        },
        persistSearch = true,
    )

@Composable
fun OnlinePlaylistSearchFocusEffect(
    isSearching: Boolean,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
}
