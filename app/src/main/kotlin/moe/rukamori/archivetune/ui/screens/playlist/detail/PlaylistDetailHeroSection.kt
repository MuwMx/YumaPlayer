/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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
import moe.rukamori.archivetune.utils.makeTimeString

/**
 * The unified hero head: artwork, title, author and song-count from the
 * [source header][PlaylistHeaderInfo]. The action row lives in
 * [PlaylistDetailHeroActions] and is injected via [actions] so this file
 * stays a pure display component under 250 lines.
 */
@Composable
internal fun PlaylistDetailHeroSection(
    header: PlaylistHeaderInfo,
    systemBarsTopPadding: Dp,
    headerProgress: () -> Float,
    disableBlur: Boolean,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    artworkOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
) {
    val hasArtwork = !header.thumbnailUrl.isNullOrBlank()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (hasArtwork) {
                        Modifier.heightIn(min = ImmersiveArtworkTokens.HeaderMinHeight)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (hasArtwork) {
            YumaImmersiveArtworkHeader(
                thumbnailUrl = header.thumbnailUrl,
                progress = headerProgress,
                disableBlur = disableBlur,
                modifier = Modifier.matchParentSize(),
            )
        }

        // LOCAL PLAYLIST SLOT — the pick-cover button overlays the artwork's
        // top-end corner (legacy LocalPlaylistHeroSection.kt:177-208).
        if (artworkOverlay != null) {
            artworkOverlay(this)
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
            // Legacy heroes (LocalPlaylistHeroSection.kt:116-125) pin the
            // text block to the BOTTOM of the artwork box so it sits directly
            // above the track list; centering leaves dead space below.
            verticalArrangement = Arrangement.Bottom,
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

            if (header.songCount != null || header.totalDurationMs != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    header.songCount?.let { count ->
                        MetadataChip(
                            icon = R.drawable.music_note,
                            text = pluralStringResource(R.plurals.n_song, count, count),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    header.totalDurationMs?.takeIf { it > 0 }?.let { durationMs ->
                        MetadataChip(
                            icon = R.drawable.timer,
                            text = makeTimeString(durationMs),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            // Description line (legacy OnlinePlaylistHeroSection 160-171
            // rendered it after the chips; SpotifyPlaylistHeader 165-178
            // blank-guarded with isNotBlank — take the safer guard here).
            header.description?.takeIf(String::isNotBlank)?.let { desc ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            if (actions != null) {
                Spacer(modifier = Modifier.height(20.dp))
                actions()
            }
        }
    }
}
