/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.playlist.source.PlaylistHeaderInfo
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.YumaImmersiveArtworkHeader
import moe.rukamori.archivetune.ui.screens.playlist.MetadataChip

/**
 * The unified hero head: artwork, title, author and song-count from the
 * [source header][PlaylistHeaderInfo]. Buttons and download affordances are
 * capability-driven and live in [PlaylistDetailHeroActions] so this file
 * stays a pure display component under 250 lines.
 */
@Composable
internal fun PlaylistDetailHeroSection(
    header: PlaylistHeaderInfo,
    systemBarsTopPadding: Dp,
    headerProgress: () -> Float,
    disableBlur: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasArtwork = !header.thumbnailUrl.isNullOrBlank()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (hasArtwork) {
                        Modifier.height(ImmersiveArtworkTokens.HeaderMinHeight)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (hasArtwork) {
            YumaImmersiveArtworkHeader(
                thumbnailUrl = header.thumbnailUrl!!,
                progress = headerProgress,
                disableBlur = disableBlur,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (hasArtwork) systemBarsTopPadding + AppBarHeight else 16.dp,
                        bottom = 16.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            header.author?.let { author ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = author,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (header.songCount != null) {
                Spacer(modifier = Modifier.height(12.dp))
                MetadataChip(
                    icon = R.drawable.music_note,
                    text = header.songCount.toString(),
                )
            }
        }
    }
}
