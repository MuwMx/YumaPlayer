/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.extractThemeColor
import moe.rukamori.archivetune.utils.joinByBullet

private val LibraryCardThumbnailSize = 72.dp
private val LibraryCardGlowElevation = 34.dp
private const val LibraryCardGlowAmbientAlpha = 0.82f
private const val LibraryCardGlowSpotAlpha = 0.96f

@Composable
fun LibraryPinnedCollectionTile(
    title: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    accentColor.copy(alpha = 0.28f),
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                        ),
                    ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                    shape = CircleShape,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryPlaylistFeatureCard(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    autoPlaylist: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitleText = playlistCountText(playlist = playlist, autoPlaylist = autoPlaylist)
    val thumbnailSize = LibraryCardThumbnailSize
    val thumbnailShape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    val primaryThumbnailUrl = playlist.thumbnails.getOrNull(0)
    var extractedGlowColor by remember(primaryThumbnailUrl) { mutableStateOf(Color.Transparent) }
    val glowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(400),
        label = "playlistItemGlow",
    )
    LaunchedEffect(primaryThumbnailUrl) {
        if (primaryThumbnailUrl == null) return@LaunchedEffect
        val bitmap =
            runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(primaryThumbnailUrl)
                            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull() ?: return@LaunchedEffect
        extractedGlowColor = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = shape,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(thumbnailSize)
                        .shadow(
                            elevation = LibraryCardGlowElevation,
                            shape = thumbnailShape,
                            clip = false,
                            ambientColor = glowColor.copy(alpha = LibraryCardGlowAmbientAlpha),
                            spotColor = glowColor.copy(alpha = LibraryCardGlowSpotAlpha),
                        ),
            ) {
                PlaylistThumbnail(
                    thumbnails = playlist.thumbnails,
                    size = thumbnailSize,
                    placeHolder = {
                        Icon(
                            painter = painterResource(playlistPlaceholderIcon(playlist, autoPlaylist)),
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.8f),
                            modifier = Modifier.size(thumbnailSize / 2),
                        )
                    },
                    shape = thumbnailShape,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = playlist.playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
fun LibraryAlbumSpotlightCard(
    album: Album,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onPlay: (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitle =
        joinByBullet(
            album.artists.joinToString { it.name },
            pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        )
    val context = LocalContext.current
    var extractedGlowColor by remember(album.album.thumbnailUrl) { mutableStateOf(Color.Transparent) }
    val glowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(400),
        label = "albumItemGlow",
    )
    LaunchedEffect(album.album.thumbnailUrl) {
        val url = album.album.thumbnailUrl ?: return@LaunchedEffect
        val bitmap =
            runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull() ?: return@LaunchedEffect
        extractedGlowColor = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    }

    Card(
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(LibraryCardThumbnailSize)
                        .shadow(
                            elevation = LibraryCardGlowElevation,
                            shape = RoundedCornerShape(18.dp),
                            clip = false,
                            ambientColor = glowColor.copy(alpha = LibraryCardGlowAmbientAlpha),
                            spotColor = glowColor.copy(alpha = LibraryCardGlowSpotAlpha),
                        ),
            ) {
                LocalThumbnail(
                    thumbnailUrl = album.album.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxSize(),
                )
                if (onPlay != null) {
                    AlbumPlayButton(
                        visible = !isActive,
                        onClick = onPlay,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = album.album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isActive) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
fun LibraryArtistSpotlightCard(
    artist: Artist,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    var extractedGlowColor by remember(artist.artist.thumbnailUrl) { mutableStateOf(Color.Transparent) }
    val glowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(400),
        label = "artistItemGlow",
    )
    LaunchedEffect(artist.artist.thumbnailUrl) {
        val url = artist.artist.thumbnailUrl ?: return@LaunchedEffect
        val bitmap =
            runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull() ?: return@LaunchedEffect
        extractedGlowColor = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(LibraryCardThumbnailSize)
                        .shadow(
                            elevation = LibraryCardGlowElevation,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = glowColor.copy(alpha = LibraryCardGlowAmbientAlpha),
                            spotColor = glowColor.copy(alpha = LibraryCardGlowSpotAlpha),
                        ),
            ) {
                LocalThumbnail(
                    thumbnailUrl = artist.artist.thumbnailUrl,
                    isActive = false,
                    isPlaying = false,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = artist.artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                trailingContent()
            }
        }
    }
}
