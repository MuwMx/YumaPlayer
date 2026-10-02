/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.RecognitionPhaseUi

@Composable
internal fun RecognitionTaskState(
    phase: RecognitionPhaseUi?,
    onListen: () -> Unit,
    onCancel: () -> Unit,
) {
    val isLoading = phase != null
    val title =
        when (phase) {
            RecognitionPhaseUi.Listening -> stringResource(R.string.music_recognition_listening)
            RecognitionPhaseUi.Processing -> stringResource(R.string.music_recognition_processing)
            null -> null
        }
    val icon =
        when (phase) {
            RecognitionPhaseUi.Listening -> R.drawable.listening
            RecognitionPhaseUi.Processing -> R.drawable.cached
            null -> R.drawable.mic
        }
    val heroShape = MaterialShapes.Cookie9Sided.toShape()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier.size(192.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (phase == RecognitionPhaseUi.Listening) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Surface(
                modifier = Modifier.size(152.dp),
                shape = heroShape,
                color =
                    if (isLoading) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                contentColor =
                    if (isLoading) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (phase == RecognitionPhaseUi.Processing) {
                        LoadingIndicator(
                            modifier = Modifier.size(72.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }
        }

        title?.let {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (isLoading) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        } else {
            Button(
                onClick = onListen,
                modifier =
                    Modifier
                        .widthIn(min = 180.dp)
                        .heightIn(min = 56.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_tap_to_listen))
            }
        }
    }
}
