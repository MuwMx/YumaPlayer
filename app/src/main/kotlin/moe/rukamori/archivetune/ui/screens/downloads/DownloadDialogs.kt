/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.DownloadRemovalConfirmation

@Composable
internal fun DownloadRemovalConfirmationDialog(
    pendingRemoval: DownloadRemovalConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_download)) },
        text = { Text(stringResource(pendingRemoval.confirmationMessageRes())) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun DownloadRemovalConfirmation.confirmationMessageRes(): Int =
    when (this) {
        is DownloadRemovalConfirmation.Entry -> R.string.remove_download_confirm
        is DownloadRemovalConfirmation.Section -> R.string.remove_download_section_confirm
    }
