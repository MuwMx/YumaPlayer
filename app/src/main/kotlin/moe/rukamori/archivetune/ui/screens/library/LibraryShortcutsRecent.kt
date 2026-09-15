/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

fun LazyListScope.shortcutsSection(
    likedSongsCount: Int,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenLocal: () -> Unit,
) {
    item(key = "shortcuts_grid") {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ShortcutCard(
                    title = stringResource(R.string.liked_songs),
                    countText = "$likedSongsCount ${stringResource(R.string.tracks_label)}",
                    iconRes = R.drawable.favorite,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    iconColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLiked,
                )

                ShortcutCard(
                    title = stringResource(R.string.offline_shortcut),
                    countText = stringResource(R.string.downloaded_desc),
                    iconRes = R.drawable.offline,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDownloads,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ShortcutCard(
                    title = stringResource(R.string.cached),
                    countText = stringResource(R.string.instant_playback),
                    iconRes = R.drawable.cached,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCache,
                )

                ShortcutCard(
                    title = stringResource(R.string.local_files),
                    countText = stringResource(R.string.on_device),
                    iconRes = R.drawable.snippet_folder,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLocal,
                )
            }
        }
    }
}

fun LazyListScope.recentlyPlayedSection(
    recentSongs: List<Song>,
    onPlayRecent: (Song) -> Unit,
) {
    if (recentSongs.isNotEmpty()) {
        item(key = "recently_played") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.recently_played),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(recentSongs) { song ->
                        Column(
                            modifier =
                                Modifier
                                    .width(110.dp)
                                    .clickable {
                                        onPlayRecent(song)
                                    },
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(28.dp)),
                            ) {
                                AsyncImage(
                                    model = song.song.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.play),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = song.song.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
