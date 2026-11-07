/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.YumaImmersiveArtworkHeader
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                    backgroundColor = LocalYumaColors.current.glassBackground,
                )
                .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LocalPlaylistHeroSection(
    playlist: Playlist,
    playlistLength: Int,
    systemBarsTopPadding: Dp,
    downloadState: HeaderDownloadState,
    actions: LocalPlaylistActions,
    headerProgress: () -> Float,
    disableBlur: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val editable = playlist.playlist.isEditable
    val hasArtwork = playlist.thumbnails.isNotEmpty()
    val changeCoverLabel = stringResource(R.string.change_playlist_cover)

    Box(
        modifier = modifier
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
                progress = headerProgress,
                disableBlur = disableBlur,
                modifier = Modifier.matchParentSize(),
                artwork = {
                    if (playlist.thumbnails.size == 1) {
                        AsyncImage(
                            model = playlist.thumbnails[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(0),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(1),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(2),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(3),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                },
            )
        }

        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = if (hasArtwork) systemBarsTopPadding + AppBarHeight + 8.dp else 8.dp,
                        end = 16.dp,
                    )
                    .size(40.dp)
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = actions.onPickCover,
                    )
                    .yumaGlassCard(
                        shape = CircleShape,
                        backgroundColor = LocalYumaColors.current.glassBackground,
                    )
                    .clip(CircleShape)
                    .semantics(mergeDescendants = true) {
                        contentDescription = changeCoverLabel
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (hasArtwork) systemBarsTopPadding + AppBarHeight else 16.dp,
                    bottom = 16.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (!hasArtwork) {
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val remoteSongCount = playlist.playlist.remoteSongCount
            val songCount =
                if (playlist.songCount == 0 && remoteSongCount != null) {
                    remoteSongCount
                } else {
                    playlist.songCount
                }
            MetadataChip(
                icon = R.drawable.music_note,
                text = pluralStringResource(R.plurals.n_song, songCount, songCount),
                modifier = Modifier.weight(1f, fill = false),
            )

            if (playlistLength > 0) {
                MetadataChip(
                    icon = R.drawable.timer,
                    text = makeTimeString(playlistLength * 1000L),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val playLabel = stringResource(R.string.play)
        val shuffleLabel = stringResource(R.string.shuffle)
        val deleteLabel = stringResource(R.string.delete)
        val likeLabel = stringResource(R.string.liked)
        val downloadLabel = stringResource(R.string.download)
        val editLabel = stringResource(R.string.edit)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editable) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = actions.onDelete,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = deleteLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            } else {
                val liked = playlist.playlist.bookmarkedAt != null
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = actions.onToggleLike,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = likeLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (liked) R.drawable.favorite else R.drawable.favorite_border,
                            ),
                        contentDescription = null,
                        tint =
                            if (liked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = actions.onPlay,
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = playLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = actions.onShuffle,
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = shuffleLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = actions.onDownload,
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = downloadLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                val state = downloadState
                when (state) {
                    HeaderDownloadState.Completed -> {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                    }

                    is HeaderDownloadState.Partial -> {
                        HeaderDownloadProgressIndicator(progress = state.progress)
                    }

                    else -> {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
}

fun LazyListScope.localPlaylistHeroItem(
    playlist: Playlist,
    playlistLength: Int,
    systemBarsTopPadding: Dp,
    downloadState: HeaderDownloadState,
    actions: LocalPlaylistActions,
    headerProgress: () -> Float,
    disableBlur: Boolean = false,
) {
    item(key = LOCAL_PLAYLIST_KEY_HEADER, contentType = CONTENT_TYPE_LOCAL_PLAYLIST_HEADER) {
        LocalPlaylistHeroSection(
            playlist = playlist,
            playlistLength = playlistLength,
            systemBarsTopPadding = systemBarsTopPadding,
            downloadState = downloadState,
            actions = actions,
            headerProgress = headerProgress,
            disableBlur = disableBlur,
        )
    }
}
