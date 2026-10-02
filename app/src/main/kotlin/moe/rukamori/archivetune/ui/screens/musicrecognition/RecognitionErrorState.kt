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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.MusicRecognitionErrorUi
import moe.rukamori.archivetune.viewmodels.RecognizedTrackUiModel

@Composable
internal fun RecognitionErrorState(
    error: MusicRecognitionErrorUi,
    onListen: () -> Unit,
    onAllowPermission: () -> Unit,
) {
    val isPermissionError = error == MusicRecognitionErrorUi.PermissionRequired
    val title =
        when (error) {
            MusicRecognitionErrorUi.PermissionRequired -> {
                stringResource(R.string.music_recognition_permission_title)
            }

            MusicRecognitionErrorUi.NoMatch -> {
                stringResource(R.string.music_recognition_no_match)
            }

            else -> {
                stringResource(R.string.music_recognition_error)
            }
        }
    val message =
        when (error) {
            MusicRecognitionErrorUi.PermissionRequired -> {
                stringResource(R.string.music_recognition_permission_desc)
            }

            MusicRecognitionErrorUi.SignatureFailed -> {
                stringResource(R.string.music_recognition_signature_failed)
            }

            MusicRecognitionErrorUi.RecordingFailed,
            MusicRecognitionErrorUi.RecognitionFailed,
            -> {
                stringResource(R.string.music_recognition_recognition_failed)
            }

            MusicRecognitionErrorUi.NoMatch -> {
                null
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            modifier = Modifier.size(112.dp),
            shape = CircleShape,
            color =
                if (isPermissionError) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter =
                        painterResource(
                            if (isPermissionError) R.drawable.mic else R.drawable.ic_search,
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint =
                        if (isPermissionError) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                )
            }
        }

        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = if (isPermissionError) onAllowPermission else onListen,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        stringResource(
                            if (isPermissionError) {
                                R.string.music_recognition_permission_action
                            } else {
                                R.string.music_recognition_listen_again
                            },
                        ),
                    )
                }
            }
        }
    }
}
@Composable
internal fun RecognitionResultContent(
    track: RecognizedTrackUiModel,
    useWideLayout: Boolean,
    onListenAgain: () -> Unit,
    onSearch: () -> Unit,
    onOpenUri: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            if (useWideLayout) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ResultIdentity(
                        track = track,
                        artworkSize = 200.dp,
                        modifier = Modifier.weight(0.9f),
                    )
                    ResultSupportingContent(
                        track = track,
                        onOpenUri = onOpenUri,
                        modifier = Modifier.weight(1.1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ResultIdentity(
                        track = track,
                        artworkSize = 184.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ResultSupportingContent(
                        track = track,
                        onOpenUri = onOpenUri,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        ResultActions(
            onListenAgain = onListenAgain,
            onSearch = onSearch,
        )
    }
}
