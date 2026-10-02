/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.DefaultDialog
import kotlin.math.roundToInt

@Composable
internal fun LyricsSyncOffsetDialog(
    isVisible: Boolean,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    if (!isVisible) return

    var tempLyricsSyncOffset by remember { mutableFloatStateOf(lyricsSyncOffset.toFloat()) }

    DefaultDialog(
        onDismiss = {
            tempLyricsSyncOffset = lyricsSyncOffset.toFloat()
            onDismiss()
        },
        icon = {
            Icon(painter = painterResource(R.drawable.speed), contentDescription = null)
        },
        title = { Text(stringResource(R.string.lyrics_sync_offset)) },
        buttons = {
            TextButton(
                onClick = { tempLyricsSyncOffset = 0f },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.reset))
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    tempLyricsSyncOffset = lyricsSyncOffset.toFloat()
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onLyricsSyncOffsetChange(tempLyricsSyncOffset.roundToInt())
                    onDismiss()
                    onDismissMenu()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = formatLyricsSyncOffset(tempLyricsSyncOffset.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Slider(
                value = tempLyricsSyncOffset,
                onValueChange = { tempLyricsSyncOffset = it },
                valueRange = -1000f..1000f,
                steps = 79,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
