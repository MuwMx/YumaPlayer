/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HeaderDownloadProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val boundedProgress =
        remember(progress) {
            progress.coerceIn(0f, 1f)
        }

    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { boundedProgress },
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeWidth = 3.dp,
        )
    }
}
