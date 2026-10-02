/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import kotlin.math.roundToInt

@Composable
internal fun UpdateCheckingDialog() {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.updates_status_checking),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        confirmButton = {},
    )
}

@Composable
internal fun UpdateDownloadDialog(
    updateChannel: UpdateChannel,
    latestCommitSha: String?,
    version: String?,
    progress: Float?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "updateDownloadProgress",
    )
    val centeredDialogContentModifier = remember { Modifier.fillMaxWidth() }
    val determinateProgressModifier = remember { Modifier.size(96.dp) }
    val determinateIndicatorModifier = remember { Modifier.fillMaxSize() }
    val indeterminateIndicatorModifier = remember { Modifier.size(72.dp) }

    val downloadTitle =
        buildString {
            when (updateChannel) {
                UpdateChannel.DAILY_NIGHTLY -> append("${context.getString(R.string.app_name)} Nightly")
                else -> append(context.getString(R.string.app_name))
            }
            append(' ')
            if (updateChannel == UpdateChannel.NIGHTLY) {
                append(latestCommitSha?.take(7) ?: version ?: "?")
            } else {
                append(version ?: "?")
            }
        }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = downloadTitle,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = centeredDialogContentModifier,
            )
        },
        text = {
            Column(
                modifier = centeredDialogContentModifier,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (progress != null) {
                    Box(
                        modifier = determinateProgressModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = determinateIndicatorModifier,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.download_progress_percent,
                                    (animatedProgress * 100f).roundToInt().coerceIn(0, 100),
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    CircularWavyProgressIndicator(
                        modifier = indeterminateIndicatorModifier,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun UpdateUpToDateDialog(
    version: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.updates_status_current),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = version ?: BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
    )
}

@Composable
internal fun UpdateErrorDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.error_loading_changelog),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
