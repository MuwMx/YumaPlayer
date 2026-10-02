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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.RecognizedTrackUiModel

@Composable
internal fun ResultIdentity(
    track: RecognizedTrackUiModel,
    artworkSize: Dp,
    modifier: Modifier = Modifier,
) {
    val artworkShape = MaterialShapes.Cookie4Sided.toShape()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverArt(
            artworkUrl = track.artworkUrl,
            displaySize = artworkSize,
            shape = artworkShape,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            track.album?.takeIf(String::isNotBlank)?.let {
                MetadataPill(iconRes = R.drawable.album, text = it)
            }
            track.metadata.takeIf(String::isNotBlank)?.let {
                MetadataPill(iconRes = R.drawable.ic_about, text = it)
            }
            track.isrc?.let {
                MetadataPill(iconRes = R.drawable.link, text = it)
            }
        }
    }
}
@Composable
internal fun ResultSupportingContent(
    track: RecognizedTrackUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shazamUrl = track.shazamUrl
    val openShazam: () -> Unit =
        remember(shazamUrl, onOpenUri) {
            { shazamUrl?.let(onOpenUri) }
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        track.lyricsPreview?.let { lyrics ->
            ResultInformationBlock(
                iconRes = R.drawable.lyrics,
                title = stringResource(R.string.music_recognition_lyrics_preview),
                body = lyrics,
            )
        }

        track.label?.let { label ->
            ResultInformationBlock(
                iconRes = R.drawable.ic_about,
                title = label,
                body = null,
            )
        }

        if (shazamUrl != null) {
            OutlinedButton(
                onClick = openShazam,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.link),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_open_shazam))
            }
        }
    }
}
@Composable
private fun ResultInformationBlock(
    iconRes: Int,
    title: String,
    body: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
