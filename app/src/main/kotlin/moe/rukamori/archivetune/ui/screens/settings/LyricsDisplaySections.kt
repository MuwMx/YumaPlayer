/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference

@Composable
internal fun LyricsTextSizeDialog(
    initialTextSize: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var tempTextSize by remember { mutableFloatStateOf(initialTextSize) }

    DefaultDialog(
        onDismiss = {
            tempTextSize = initialTextSize
            onDismiss()
        },
        buttons = {
            TextButton(
                onClick = { tempTextSize = LyricsContract.RESET_TEXT_SIZE },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.reset))
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = {
                    tempTextSize = initialTextSize
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onConfirm(tempTextSize)
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
                text = stringResource(R.string.lyrics_text_size),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Text(
                text = "${tempTextSize.roundToInt()} sp",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Slider(
                value = tempTextSize,
                onValueChange = { tempTextSize = it },
                valueRange = 16f..36f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun LyricsLineSpacingDialog(
    initialLineSpacing: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var tempLineSpacing by remember { mutableFloatStateOf(initialLineSpacing) }

    DefaultDialog(
        onDismiss = {
            tempLineSpacing = initialLineSpacing
            onDismiss()
        },
        buttons = {
            TextButton(
                onClick = { tempLineSpacing = LyricsContract.RESET_LINE_SPACING },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.reset))
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = {
                    tempLineSpacing = initialLineSpacing
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onConfirm(tempLineSpacing)
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
                text = stringResource(R.string.lyrics_line_spacing),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Text(
                text = "${String.format("%.1f", tempLineSpacing)}x",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Slider(
                value = tempLineSpacing,
                onValueChange = { tempLineSpacing = it },
                valueRange = 1.0f..2.0f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun LyricsDisplaySection(
    lyricsClick: Boolean,
    onLyricsClickChange: (Boolean) -> Unit,
    lyricsScroll: Boolean,
    onLyricsScrollChange: (Boolean) -> Unit,
    lyricsLineBlur: Boolean,
    onLyricsLineBlurChange: (Boolean) -> Unit,
    lyricsTextSize: Float,
    lyricsLineSpacing: Float,
    onOpenTextSizeDialog: () -> Unit,
    onOpenLineSpacingDialog: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.display)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_click_change)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsClick,
                onCheckedChange = onLyricsClickChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsScroll,
                onCheckedChange = onLyricsScrollChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_line_blur)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsLineBlur,
                onCheckedChange = onLyricsLineBlurChange,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.lyrics_text_size)) },
                description = "${lyricsTextSize.roundToInt()} sp",
                icon = { Icon(painterResource(R.drawable.text_fields), null) },
                onClick = onOpenTextSizeDialog,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.lyrics_line_spacing)) },
                description = "${String.format("%.1f", lyricsLineSpacing)}x",
                icon = { Icon(painterResource(R.drawable.text_fields), null) },
                onClick = onOpenLineSpacingDialog,
            )
        }
    }
}
