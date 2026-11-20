/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.SpotifySongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder

/**
 * Origin-typed menu content — rendered inside a `MenuState.show { }` block.
 *
 * Extracted from [PlaylistDetailScreen] to keep that file under the 250-line
 * rule (`YUMA_RULES.md` §1.1).
 */
@Composable
internal fun PlaylistDetailMenuContent(
    track: PlaylistTrack,
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    uiState: PlaylistDetailUiState,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    when (val origin = track.origin) {
        is moe.rukamori.archivetune.db.entities.PlaylistSong ->
            SongMenu(
                originalSong = origin.song,
                playlistSong = if (!searchState.selection) origin else null,
                playlistBrowseId = uiState.browseId,
                navController = navController,
                onDismiss = onDismiss,
            )
        is moe.rukamori.archivetune.innertube.models.SongItem ->
            YouTubeSongMenu(
                song = origin,
                navController = navController,
                onDismiss = onDismiss,
            )
        is moe.rukamori.archivetune.spotify.models.SpotifyTrack ->
            SpotifySongMenu(
                track = origin,
                navController = navController,
                onDismiss = onDismiss,
            )
        is moe.rukamori.archivetune.db.entities.Song ->
            SongMenu(
                originalSong = origin,
                navController = navController,
                onDismiss = onDismiss,
            )
        is moe.rukamori.archivetune.playlist.cache.CachedSong ->
            SongMenu(
                originalSong = origin.song,
                navController = navController,
                onDismiss = onDismiss,
                isFromCache = true,
            )
        else -> Unit
    }
}
