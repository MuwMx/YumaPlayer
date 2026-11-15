/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads

/**
 * The remove-downloads confirm dialog every legacy hero showed (Top's
 * inline dialog at TopPlaylistScreen.kt:219-251, Auto's
 * AutoPlaylistRemoveDownloadDialog, Local's LocalPlaylistRemoveDownloadDialog)
 * — the unified hero download button routes a COMPLETED state through here
 * instead of removing downloads un-confirmed.
 */
@Composable
internal fun PlaylistDetailRemoveDownloadDialog(
    playlistName: String,
    songIds: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.remove_download_playlist_confirm, playlistName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            TextButton(
                onClick = {
                    onDismiss()
                    sendRemoveDownloads(
                        context = context,
                        songIds = songIds,
                    )
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}
