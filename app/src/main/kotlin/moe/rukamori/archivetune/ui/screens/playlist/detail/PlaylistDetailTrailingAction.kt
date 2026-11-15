/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.source.AutoPlaylistSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playlist.source.SpotifyPlaylistSource
import moe.rukamori.archivetune.playlistcopy.PlaylistCopySource
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlaylistCopyDialog
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu

/**
 * Route-specific trailing top-bar action, rendered beside the search icon
 * while no selection/search is active:
 *  - AUTO PLAYLIST SLOT (pinned): the more_horiz "more options" button
 *    (legacy AutoPlaylistSelectionTopBar.kt:179-188) opening a
 *    SelectionSongMenu over ALL songs (AutoPlaylistScreen.kt:311-319) —
 *    do not lose this in later phases.
 *  - SPOTIFY PLAYLIST SLOT (pinned): the copy-to-playlist button
 *    (legacy SpotifyPlaylistScreen.kt:389-397) opening a PlaylistCopyDialog
 *    seeded from this playlist (SpotifyPlaylistScreen.kt:403-415) —
 *    do not lose this in later phases.
 *  - LOCAL PLAYLIST SLOT (pinned): sync-only for non-editable synced
 *    playlists (legacy LocalPlaylistTopBar, LocalPlaylistWiring.kt:1252-
 *    1262). The edit AFFORDANCE lives on the hero-overlay pencil (edit
 *    chooser) since the two-pencil merge; editable playlists get no top-bar
 *    trailing action.
 *  - Other sources: null, no trailing action.
 */
@Composable
internal fun renderPlaylistDetailTrailingAction(
    viewModel: PlaylistDetailViewModel,
    menuState: MenuState,
    tracks: List<PlaylistTrack>,
    header: moe.rukamori.archivetune.playlist.source.PlaylistHeaderInfo?,
    onClearSelection: () -> Unit,
    editable: Boolean? = null,
    onSync: (() -> Unit)? = null,
): (@Composable () -> Unit)? {
    if (viewModel.source is AutoPlaylistSource) {
        val moreOptions = stringResource(R.string.more_options)
        return {
            IconButton(
                onClick = {
                    menuState.show {
                        SelectionSongMenu(
                            songSelection =
                                tracks.mapNotNull { it.origin as? moe.rukamori.archivetune.db.entities.Song },
                            onDismiss = menuState::dismiss,
                            clearAction = {},
                        )
                    }
                },
                onLongClick = {},
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = moreOptions,
                )
            }
        }
    }
    if (viewModel.source is SpotifyPlaylistSource) {
        // The copy dialog seeds from the loaded playlist's identity; the
        // header carries name + legacy thumbnail (SpotifyMapper-picked).
        return {
            SpotifyCopyPlaylistAction(
                playlistId = viewModel.playlistId,
                playlistName = header?.title,
                thumbnailUrl = header?.thumbnailUrl,
            )
        }
    }
    if (viewModel.source is moe.rukamori.archivetune.playlist.source.RoomPlaylistSource && onSync != null) {
        // LOCAL PLAYLIST SLOT — MERGED EDIT AFFORDANCE: editable playlists
        // edit from the hero-overlay pencil (edit chooser); the top bar
        // shows the SYNC icon for non-editable synced playlists only
        // (LocalPlaylistTopBar, Wiring.kt:1252-1262 — legacy labeled both
        // icons with R.string.edit).
        if (editable == false) {
            val syncLabel = stringResource(R.string.edit)
            return {
                IconButton(onClick = onSync, onLongClick = {}) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = syncLabel,
                    )
                }
            }
        }
        return null
    }
    return null
}

/**
 * SPOTIFY COPY SLOT — the playlist_add button + its PlaylistCopyDialog,
 * verbatim the legacy shape (SpotifyPlaylistScreen.kt:389-397, 403-415).
 */
@Composable
private fun SpotifyCopyPlaylistAction(
    playlistId: String,
    playlistName: String?,
    thumbnailUrl: String?,
) {
    var showCopyDialog by remember { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.copy_to_playlist)
    IconButton(
        onClick = { showCopyDialog = true },
        onLongClick = {},
    ) {
        Icon(
            painter = painterResource(R.drawable.playlist_add),
            contentDescription = copyLabel,
        )
    }
    if (showCopyDialog && playlistName != null) {
        PlaylistCopyDialog(
            source =
                PlaylistCopySource.Spotify(
                    playlistId = playlistId,
                    name = playlistName,
                    thumbnailUrl = thumbnailUrl,
                ),
            onDismiss = { showCopyDialog = false },
        )
    }
}
