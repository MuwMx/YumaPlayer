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
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

fun LazyListScope.playlistsRowSection(
    visiblePlaylists: List<Playlist>,
    visibleSpotifyPlaylists: List<SpotifyPlaylist>,
    isSpotifyActive: Boolean,
    likedSongsTotal: Int,
    onOpenPlaylist: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onPlaySpotifyLiked: () -> Unit,
    onOpenSpotifyPlaylist: (SpotifyPlaylist) -> Unit,
    onSeeAll: () -> Unit,
    onOpenSpotifyLiked: () -> Unit = {},
) {
    if (visiblePlaylists.isNotEmpty() || visibleSpotifyPlaylists.isNotEmpty() || isSpotifyActive) {
        item(key = "your_playlists") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.your_playlists),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.see_all),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .clickable { onSeeAll() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.ScreenHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(
                        items = visiblePlaylists.take(8),
                        key = { playlist -> "playlist_${playlist.id}" },
                        contentType = { "library_playlist" },
                    ) { playlist ->
                        Column(
                            modifier =
                                Modifier
                                    .width(130.dp)
                                    .yumaClickable(
                                        pressedScale = SettingsAnimations.PressScale,
                                        onClick = { onOpenPlaylist(playlist) },
                                    )
                                    .yumaGlassCard(
                                        shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                                        position = YumaSegmentPosition.Single,
                                    )
                                    .padding(SettingsDimensions.SectionSpacing),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(106.dp)
                                        .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius)),
                            ) {
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(0),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable { onPlayPlaylist(playlist) },
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
                                text = playlist.playlist.name,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    }

                    if (isSpotifyActive) {
                        item(key = "spotify_liked_songs_card", contentType = "spotify_liked_card") {
                            Column(
                                modifier =
                                    Modifier
                                        .width(130.dp)
                                        .yumaClickable(
                                            pressedScale = SettingsAnimations.PressScale,
                                            onClick = onOpenSpotifyLiked,
                                        )
                                        .yumaGlassCard(
                                            shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                                            position = YumaSegmentPosition.Single,
                                        )
                                        .padding(SettingsDimensions.SectionSpacing),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(106.dp)
                                            .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius))
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.favorite),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .clickable { onPlaySpotifyLiked() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.play),
                                            contentDescription = stringResource(R.string.play),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.spotify_liked_songs),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "$likedSongsTotal ${stringResource(R.string.tracks_label)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleSpotifyPlaylists.take(8),
                        key = { playlist -> "spotify_playlist_${playlist.id}" },
                        contentType = { "library_spotify_playlist" },
                    ) { playlist ->
                        SpotifyPlaylistCompactCard(
                            playlist = playlist,
                            onClick = { onOpenSpotifyPlaylist(playlist) },
                        )
                    }

                    item {
                        Column(
                            modifier =
                                Modifier
                                    .width(130.dp)
                                    .height(168.dp)
                                    .yumaClickable(
                                        pressedScale = SettingsAnimations.PressScale,
                                        onClick = onSeeAll,
                                    )
                                    .yumaGlassCard(
                                        shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                                        position = YumaSegmentPosition.Single,
                                    ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.expand_more),
                                    contentDescription = stringResource(R.string.more_playlists_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(SettingsDimensions.SectionSpacing))
                            Text(
                                text = stringResource(R.string.more_label),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
        }
    }
}
