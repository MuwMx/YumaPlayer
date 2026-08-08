/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.screens.library.rememberArtworkCardColor
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
fun SpotifyLibraryPlaylistListItem(
    playlist: SpotifyPlaylist,
    navController: NavController,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = remember(playlist) { SpotifyMapper.getPlaylistThumbnail(playlist) }
    val cardBgColor = rememberArtworkCardColor(
        thumbnailUrl = thumbnailUrl,
        fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "SpotifyPlaylistListCardScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(32.dp))
            .background(cardBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { navController.navigate("spotify_playlist/${playlist.id}") },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${playlist.tracks?.total ?: 0} ${stringResource(R.string.tracks_label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Spotify",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        IconButton(
            onClick = onPlay,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.play),
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = { /* No-op for now, or show options */ },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.spotify_icon),
                contentDescription = stringResource(R.string.spotify_account),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SpotifyLikedSongsListCard(
    likedSongsTotal: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "SpotifyLikedSongsListCardScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.spotify_liked_songs),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$likedSongsTotal ${stringResource(R.string.tracks_label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Spotify",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        IconButton(
            onClick = onPlay,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.play),
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun SpotifyTrackListItem(
    track: SpotifyTrack,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (track.explicit) {
            Icon(
                painter = painterResource(R.drawable.explicit),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    showSongIconPlaceholder: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val duration =
        track.durationMs
            .takeIf { it > 0 }
            ?.toLong()
            ?.let(::makeTimeString)
    val subtitle =
        joinByBullet(
            track.artists.joinToString { it.name },
            duration,
        )

    ListItem(
        title = track.name,
        subtitle = subtitle,
        badges = badges,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = SpotifyMapper.getTrackThumbnail(track)?.resize(200, 200),
                albumIndex = albumIndex,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                placeholderIconRes = if (showSongIconPlaceholder) R.drawable.music_note else null,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive,
    )
}
