/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import java.util.Locale

@Composable
fun ArtistStatsButtonGroup(
    stats: List<ArtistStatItemUiModel>,
    modifier: Modifier = Modifier,
) {
    val chipShape = remember { RoundedCornerShape(SettingsDimensions.LibraryCardRadius) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.fastForEach { stat ->
            Row(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .yumaGlassCard(
                            shape = chipShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(chipShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = stat.contentDescription
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun buildArtistStats(
    showLocal: Boolean,
    artistPage: ArtistPage?,
    librarySongCount: Int,
    libraryAlbumCount: Int,
    songsLabel: String,
    albumsLabel: String,
    monthlyListenersLabel: String,
    subscribersLabel: String,
): List<ArtistStatItemUiModel> {
    val songSections =
        artistPage?.sections?.filter { section ->
            section.items.any { it is SongItem }
        }
    val songCount =
        if (showLocal) {
            librarySongCount
        } else {
            songSections
                ?.asSequence()
                ?.flatMap { it.items.asSequence() }
                ?.filterIsInstance<SongItem>()
                ?.distinctBy { it.id }
                ?.count() ?: librarySongCount
        }
    val hasMoreSongs = !showLocal && songSections?.any { it.moreEndpoint != null } == true

    val albumSections =
        artistPage?.sections?.filter { section ->
            section.items.any { it is AlbumItem }
        }
    val albumCount =
        if (showLocal) {
            libraryAlbumCount
        } else {
            albumSections
                ?.asSequence()
                ?.flatMap { it.items.asSequence() }
                ?.filterIsInstance<AlbumItem>()
                ?.distinctBy { it.id }
                ?.count() ?: libraryAlbumCount
        }
    val hasMoreAlbums = !showLocal && albumSections?.any { it.moreEndpoint != null } == true

    return buildList {
        artistPage?.artist?.monthlyListenerCountText?.toArtistCompactCountText()?.let { value ->
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.Hearing,
                    value = value,
                    contentDescription = "$monthlyListenersLabel $value",
                ),
            )
        }

        artistPage?.artist?.subscriberCountText?.toArtistCompactCountText()?.let { value ->
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.PersonAdd,
                    value = value,
                    contentDescription = "$subscribersLabel $value",
                ),
            )
        }

        if (songCount > 0) {
            val value = compactCountText(songCount, hasMoreSongs)
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.MusicNote,
                    value = value,
                    contentDescription = "$songsLabel $value",
                ),
            )
        }

        if (albumCount > 0) {
            val value = compactCountText(albumCount, hasMoreAlbums)
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.Album,
                    value = value,
                    contentDescription = "$albumsLabel $value",
                ),
            )
        }
    }
}

fun compactCountText(
    count: Int,
    hasMore: Boolean,
): String {
    val value = formatCompactCount(count.toLong())
    return if (hasMore) "$value+" else value
}

private val CompactArtistCountPattern = Regex("""\d+(?:[.,]\d+)?\s*[KMB]""", RegexOption.IGNORE_CASE)
private val ArtistCountPattern = Regex("""\d+(?:[.,]\d+)*""")

fun String.toArtistCompactCountText(): String? {
    val compactText = CompactArtistCountPattern.find(this)?.value
    if (compactText != null) {
        return compactText
            .filterNot { it.isWhitespace() }
            .replace(',', '.')
            .uppercase(Locale.US)
    }

    val count =
        ArtistCountPattern
            .find(this)
            ?.value
            ?.filter { it.isDigit() }
            ?.toLongOrNull()
            ?: return null

    return formatCompactCount(count)
}
