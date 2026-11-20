/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.source.RoomPlaylistSource
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSuggestionsSection

/**
 * LOCAL PLAYLIST SLOT — the local-route glue extracted from
 * PlaylistDetailScreen so that file stays under the 250-line rule:
 *  - [rememberLocalPlaylistGlue]: pick-cover launcher, dialog state and the
 *    live liked/name flags read off the Room source;
 *  - [localPlaylistSuggestionsItem]: the "You might like" footer (legacy
 *    LocalPlaylistScreen.kt:251-257), detail-keyed and rendered only while
 *    no selection/search is active.
 */
internal class LocalPlaylistGlue(
    val editable: Boolean,
    val liked: Boolean,
    val playlistName: String?,
    val dialogState: LocalPlaylistDetailDialogState,
    val onPickCover: () -> Unit,
    val onDeletePlaylist: () -> Unit,
)

/**
 * Resolves the local-route glue, or null on every other route. The liked
 * flag and playlist name read the live playlist row (the hero heart button
 * and both confirm dialogs key on them).
 */
@Composable
internal fun rememberLocalPlaylistGlue(
    room: RoomPlaylistSource?,
    editable: Boolean?,
    context: Context,
    onCoverPicked: (android.net.Uri?) -> Unit,
    onDeletePlaylist: () -> Unit,
): LocalPlaylistGlue? {
    if (room == null || editable == null) return null
    val dialogState = LocalPlaylistDetailDialogState.remember()
    val playlistRow by room.playlist.collectAsStateWithLifecycle(initialValue = null)
    val pickCoverLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onCoverPicked(uri)
        }
    return LocalPlaylistGlue(
        editable = editable,
        liked = playlistRow?.playlist?.bookmarkedAt != null,
        playlistName = playlistRow?.playlist?.name,
        dialogState = dialogState,
        onPickCover = { pickCoverLauncher.launch(arrayOf("image/*")) },
        onDeletePlaylist = onDeletePlaylist,
    )
}

/**
 * The suggestions footer — verbatim LocalPlaylistScreen.kt:251-257: rendered
 * while no selection/search is active. The section composable resolves its
 * own LocalPlaylistViewModel from the NavBackStackEntry (the "playlistId"
 * nav arg is unchanged), so it keeps working untouched under the flipped
 * route.
 */
internal fun LazyListScope.localPlaylistSuggestionsItem(show: Boolean) {
    if (!show) return
    item(key = PLAYLIST_DETAIL_KEY_SUGGESTIONS, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_SUGGESTIONS) {
        PlaylistSuggestionsSection(
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

/**
 * The hero action row for the local route (unified host + local leading
 * button + the editable-aware remove-downloads confirm), or the plain
 * unified host on every other route.
 */
@Composable
internal fun localPlaylistHeroActionsOrUnified(
    viewModel: PlaylistDetailViewModel,
    localGlue: LocalPlaylistGlue?,
    capabilities: moe.rukamori.archivetune.playlist.source.PlaylistCapabilities,
    playerConnection: moe.rukamori.archivetune.playback.PlayerConnection,
    downloadHolder: moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder,
    title: String,
    visibleTracks: List<moe.rukamori.archivetune.playlist.source.PlaylistTrack>,
    context: Context,
) {
    if (localGlue == null) {
        PlaylistDetailHeroActionsHost(
            viewModel, capabilities, viewModel.source,
            playerConnection, downloadHolder, title, visibleTracks,
        )
        return
    }
    PlaylistDetailHeroActionsHost(
        viewModel, capabilities, viewModel.source,
        playerConnection, downloadHolder, title, visibleTracks,
        leadingAction =
            localPlaylistHeroLeadingAction(
                room = viewModel.source as moe.rukamori.archivetune.playlist.source.RoomPlaylistSource,
                editable = localGlue.editable,
                liked = localGlue.liked,
                onDelete = { localGlue.dialogState.showDelete = true },
                onToggleLike = viewModel::togglePlaylistLike,
            ),
        localOnRemoveDownloads = { viewModel.removeDownloads(context, localGlue.editable) },
    )
}

/**
 * The pick-cover overlay for editable local playlists, or null elsewhere.
 * The BoxScope receiver lets the overlay align itself inside the hero. NOT
 * @Composable: the LazyListScope block calls it while building the item
 * list; only the RETURNED lambda runs inside composition.
 *
 * NOTE: The edit pencil moved to the top bar in 2026-11; this overlay is
 * now null for editable playlists. The cover-picker still opens through the
 * edit chooser's "Change cover" option.
 */
internal fun localPlaylistPickCoverOverlayOrNull(
    localGlue: LocalPlaylistGlue?,
    systemBarsTopPadding: Dp,
    hasArtwork: Boolean,
): (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? {
    return null
}

/**
 * LOCAL PLAYLIST SLOT — the selection menu's like hint: legacy
 * showLocalPlaylistSelectionMenu pinned the like toggle to the playlist's
 * browse-id space, so a "spotify:"-linked playlist hints LikeSource.SPOTIFY.
 */
internal fun localPlaylistLikeSourceHint(
    browseId: String?,
): moe.rukamori.archivetune.constants.LikeSource? =
    if (browseId?.startsWith("spotify:") == true) {
        moe.rukamori.archivetune.constants.LikeSource.SPOTIFY
    } else {
        null
    }

/**
 * The local route's dialog host (edit chooser / rename / delete) plus the
 * trailing top-bar action (EDIT PENCIL for editable; SYNC for non-editable
 * synced playlists). Every other route gets the standard trailing action.
 */
@Composable
internal fun localPlaylistTrailingAndDialogsOrUnified(
    viewModel: PlaylistDetailViewModel,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
    tracks: List<moe.rukamori.archivetune.playlist.source.PlaylistTrack>,
    header: moe.rukamori.archivetune.playlist.source.PlaylistHeaderInfo?,
    localGlue: LocalPlaylistGlue?,
    editable: Boolean?,
    onClearSelection: () -> Unit,
    onStartSelection: () -> Unit = {},
): (@Composable () -> Unit)? {
    if (localGlue != null) {
        // LOCAL PLAYLIST SLOT — edit-chooser + rename + delete dialogs
        LocalPlaylistDetailDialogsHost(
            dialogState = localGlue.dialogState,
            playlistName = localGlue.playlistName,
            editable = localGlue.editable,
            onRename = viewModel::renamePlaylist,
            onDeletePlaylist = localGlue.onDeletePlaylist,
            onPickCover = localGlue.onPickCover,
        )
        // Top-bar edit or sync icon
        if (localGlue.editable) {
            val editLabel = stringResource(R.string.edit)
            return {
                IconButton(
                    onClick = { localGlue.dialogState.showEditChooser = true },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = editLabel,
                    )
                }
            }
        } else {
            val syncLabel = stringResource(R.string.edit)
            return {
                IconButton(
                    onClick = viewModel::refresh,
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = syncLabel,
                    )
                }
            }
        }
    }
    return renderPlaylistDetailTrailingAction(
        viewModel, menuState, tracks, header,
        onClearSelection = onClearSelection,
        editable = editable,
        onSync = viewModel::refresh,
        onStartSelection = onStartSelection,
    )
}
