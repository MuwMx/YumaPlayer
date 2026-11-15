/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.source.AutoPlaylistSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu

/**
 * Route-specific trailing top-bar action, rendered beside the search icon
 * while no selection/search is active:
 *  - AUTO PLAYLIST SLOT (pinned): the more_horiz "more options" button
 *    (legacy AutoPlaylistSelectionTopBar.kt:179-188) opening a
 *    SelectionSongMenu over ALL songs (AutoPlaylistScreen.kt:311-319) —
 *    do not lose this in later phases.
 *  - SPOTIFY PLAYLIST SLOT: the copy-to-playlist button joins here with
 *    the spotify flip.
 *  - Other sources: null, no trailing action.
 */
@Composable
internal fun renderPlaylistDetailTrailingAction(
    viewModel: PlaylistDetailViewModel,
    menuState: MenuState,
    tracks: List<PlaylistTrack>,
    onClearSelection: () -> Unit,
): (@Composable () -> Unit)? {
    if (viewModel.source !is AutoPlaylistSource) return null
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
