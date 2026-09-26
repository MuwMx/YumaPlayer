/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.ui.screens.RecapCream
import moe.rukamori.archivetune.ui.screens.RecapRed
import moe.rukamori.archivetune.ui.screens.RecapSurfaceHigh
import moe.rukamori.archivetune.ui.screens.RecapTokens
import moe.rukamori.archivetune.ui.screens.RecapYellow
import moe.rukamori.archivetune.ui.screens.rememberShareSafeImageRequest
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun RecapCardContent(
    badge: String,
    footer: String,
    verticalArrangement: Arrangement.Vertical,
    applySafeContentInsets: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(24.dp),
        verticalArrangement = verticalArrangement,
    ) {
        RecapBadge(text = badge)
        content()
        Text(
            text = footer,
            style = MaterialTheme.typography.labelMedium,
            color = RecapCream.copy(alpha = 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HeroMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(RecapTokens.SectionRadius))
                .background(RecapCream.copy(alpha = 0.13f))
                .border(
                    width = 1.dp,
                    color = RecapCream.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(RecapTokens.SectionRadius),
                ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = RecapCream.copy(alpha = 0.66f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = RecapCream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RankedArtistRow(
    rank: Int,
    artist: Artist,
    modifier: Modifier = Modifier,
) {
    val imageModel = rememberShareSafeImageRequest(artist.artist.thumbnailUrl)

    RankedRowContainer(modifier = modifier) {
        RankNumber(rank = rank, color = RecapRed)
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = artist.artist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = makeTimeString(artist.timeListened?.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = RecapCream.copy(alpha = 0.58f),
                maxLines = 1,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RecapRed,
        )
    }
}

@Composable
internal fun RankedAlbumRow(
    rank: Int,
    album: Album,
) {
    val imageModel = rememberShareSafeImageRequest(album.thumbnailUrl)
    val artistNames =
        remember(album.artists) {
            album.artists.take(2).joinToString(" / ") { it.name }
        }

    RankedRowContainer {
        RankNumber(rank = rank, color = RecapYellow)
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = album.album.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistNames.ifBlank { makeTimeString(album.timeListened) },
                style = MaterialTheme.typography.labelSmall,
                color = RecapCream.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = makeTimeString(album.timeListened),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RecapYellow,
        )
    }
}

@Composable
internal fun RankedRowContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RecapTokens.ItemRadius))
                .background(RecapSurfaceHigh.copy(alpha = 0.82f))
                .border(
                    width = 1.dp,
                    color = RecapCream.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(RecapTokens.ItemRadius),
                ).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun RankNumber(
    rank: Int,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = color,
        )
    }
}

@Composable
internal fun SummaryHighlightRow(
    icon: Int,
    label: String,
    value: String,
    color: Color,
) {
    RecapStatRow(
        icon = icon,
        label = label,
        value = value,
        color = color,
    )
}

@Composable
internal fun RecapStatRow(
    icon: Int,
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RecapTokens.SectionRadius))
                .background(RecapSurfaceHigh.copy(alpha = 0.72f))
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = icon,
            background = color.copy(alpha = 0.18f),
            tint = color,
            modifier = Modifier.size(46.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RecapCream.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RecapChip(
    icon: Int,
    text: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.2f))
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.34f),
                    shape = RoundedCornerShape(999.dp),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = RecapCream,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RecapBadge(text: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(RecapCream.copy(alpha = 0.14f))
                .border(
                    width = 1.dp,
                    color = RecapCream.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp),
                ).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(RecapRed),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = RecapCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun IconBadge(
    icon: Int,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
