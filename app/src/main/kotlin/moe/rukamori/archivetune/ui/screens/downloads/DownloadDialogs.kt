/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.viewmodels.DownloadRemovalConfirmation

@Composable
internal fun DownloadRemovalConfirmationDialog(
    pendingRemoval: DownloadRemovalConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogShape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.remove_download),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(pendingRemoval.confirmationMessageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Box(
                modifier =
                    Modifier
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = onConfirm,
                        )
                        .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            Box(
                modifier =
                    Modifier
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = onDismiss,
                        )
                        .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun DownloadRemovalConfirmation.confirmationMessageRes(): Int =
    when (this) {
        is DownloadRemovalConfirmation.Entry -> R.string.remove_download_confirm
        is DownloadRemovalConfirmation.Section -> R.string.remove_download_section_confirm
    }
