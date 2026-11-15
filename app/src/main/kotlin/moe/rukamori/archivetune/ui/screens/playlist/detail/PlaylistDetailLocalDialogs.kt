/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EditPlaylistDialog

/**
 * LOCAL PLAYLIST SLOT — the merged edit chooser. The legacy screen had TWO
 * pencil buttons (top-bar rename + hero-overlay change-cover); the unified
 * screen keeps ONE hero-overlay pencil that opens this chooser first.
 * Existing string keys only (edit_playlist / change_playlist_cover).
 */
@Composable
internal fun LocalPlaylistEditChooserDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onPickCover: () -> Unit,
) {
    if (!visible) return
    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
        title = { Text(text = stringResource(R.string.edit_playlist)) },
        content = {},
        buttons = {
            TextButton(
                onClick = onRename,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.edit))
            }
            TextButton(
                onClick = {
                    onDismiss()
                    onPickCover()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.change_playlist_cover))
            }
        },
    )
}

/**
 * LOCAL PLAYLIST SLOT — the local routes' dialog host, the
 * LocalPlaylistDialogsHost port (LocalPlaylistWiring.kt:1031-1106): the
 * rename dialog (EditPlaylistDialog) and the delete-playlist confirm.
 * Remove-downloads lives in [PlaylistDetailRemoveDownloadDialog] already,
 * seeded by the hero's download button; the local host's version additionally
 * clears a non-editable playlist's songs via removeDownloads(context, editable).
 */
@Composable
internal fun LocalPlaylistDetailDialogsHost(
    dialogState: LocalPlaylistDetailDialogState,
    playlistName: String?,
    editable: Boolean,
    onRename: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onPickCover: () -> Unit,
) {
    LocalPlaylistEditChooserDialog(
        visible = dialogState.showEditChooser,
        onDismiss = { dialogState.showEditChooser = false },
        onRename = {
            dialogState.showEditChooser = false
            dialogState.showEdit = true
        },
        onPickCover = onPickCover,
    )

    if (dialogState.showEdit && playlistName != null) {
        EditPlaylistDialog(
            initialName = playlistName,
            onDismiss = { dialogState.showEdit = false },
            onSave = { name ->
                dialogState.showEdit = false
                onRename(name)
            },
        )
    }

    if (dialogState.showDelete && playlistName != null) {
        DefaultDialog(
            onDismiss = { dialogState.showDelete = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlistName),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { dialogState.showDelete = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        dialogState.showDelete = false
                        onDeletePlaylist()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
}

/** The remember-scoped dialog flags the local host reads (legacy LocalPlaylistDialogState). */
internal class LocalPlaylistDetailDialogState {
    var showEditChooser by mutableStateOf(false)
    var showEdit by mutableStateOf(false)
    var showDelete by mutableStateOf(false)

    companion object {
        @Composable
        fun remember(): LocalPlaylistDetailDialogState = remember { LocalPlaylistDetailDialogState() }
    }
}
