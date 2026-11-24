/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.ui.navigation.containerTransformSource
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun StatsHighlightsSection(
    topArtist: Artist?,
    topSong: SongWithStats?,
    topSongEntity: Song?,
    navController: NavController,
) {
    if (topArtist == null && topSong == null) return

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (topArtist != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_artist),
                mainText = topArtist.artist.name,
                subText = "${pluralStringResource(
                    R.plurals.n_song,
                    topArtist.songCount,
                    topArtist.songCount,
                )} • ${makeTimeString(topArtist.timeListened?.toLong())}",
                imageUrl = topArtist.artist.thumbnailUrl,
                useCircleShape = true,
                onClick = { navController.navigate("artist/${topArtist.id}") },
                modifier =
                    Modifier.containerTransformSource(
                        key = "artist-${topArtist.id}",
                        cornerRadius = SettingsDimensions.GroupCardCornerRadius,
                        coverUrl = topArtist.artist.thumbnailUrl,
                    ),
            )
        }
        if (topSong != null && topSongEntity != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_song),
                mainText = topSong.title,
                subText = "${pluralStringResource(
                    R.plurals.n_time,
                    topSong.songCountListened,
                    topSong.songCountListened,
                )} • ${makeTimeString(topSong.timeListened)}",
                imageUrl = topSong.thumbnailUrl,
                useCircleShape = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun StatsHighlightCard(
    title: String,
    mainText: String,
    subText: String,
    imageUrl: String?,
    useCircleShape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .yumaGlassCard(shape = RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(if (useCircleShape) CircleShape else MaterialTheme.shapes.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
