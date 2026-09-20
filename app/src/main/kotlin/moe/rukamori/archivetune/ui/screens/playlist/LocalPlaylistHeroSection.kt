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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun LocalPlaylistMeshGradient(
    disableBlur: Boolean,
    gradientColors: List<Color>,
    gradientAlpha: Float,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.55f)
                    .zIndex(-1f)
                    .drawBehind {
                        val width = size.width
                        val height = size.height

                        if (gradientColors.size >= 3) {
                            val c0 = gradientColors[0]
                            val c1 = gradientColors[1]
                            val c2 = gradientColors[2]
                            val c3 = gradientColors.getOrElse(3) { c0 }
                            val c4 = gradientColors.getOrElse(4) { c1 }

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c0.copy(alpha = gradientAlpha * 0.75f),
                                                c0.copy(alpha = gradientAlpha * 0.4f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.15f),
                                        radius = width * 0.8f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c1.copy(alpha = gradientAlpha * 0.55f),
                                                c1.copy(alpha = gradientAlpha * 0.3f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.1f, height * 0.4f),
                                        radius = width * 0.6f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c2.copy(alpha = gradientAlpha * 0.5f),
                                                c2.copy(alpha = gradientAlpha * 0.25f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.9f, height * 0.35f),
                                        radius = width * 0.55f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c3.copy(alpha = gradientAlpha * 0.35f),
                                                c3.copy(alpha = gradientAlpha * 0.18f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.25f, height * 0.65f),
                                        radius = width * 0.75f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c4.copy(alpha = gradientAlpha * 0.3f),
                                                c4.copy(alpha = gradientAlpha * 0.15f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.55f, height * 0.85f),
                                        radius = width * 0.9f,
                                    ),
                            )
                        } else if (gradientColors.isNotEmpty()) {
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.7f),
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.35f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.25f),
                                        radius = width * 0.85f,
                                    ),
                            )
                        }

                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                            surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                            surfaceColor,
                                        ),
                                    startY = height * 0.4f,
                                    endY = height,
                                ),
                        )
                    },
        )
    }
}

@Composable
fun LocalPlaylistHeroSection(
    playlist: Playlist,
    playlistLength: Int,
    gradientColors: List<Color>,
    systemBarsTopPadding: Dp,
    downloadState: HeaderDownloadState,
    actions: LocalPlaylistActions,
    modifier: Modifier = Modifier,
) {
    val editable = playlist.playlist.isEditable

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = systemBarsTopPadding + AppBarHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 8.dp, bottom = 20.dp),
        ) {
            if (playlist.thumbnails.size == 1) {
                Surface(
                    modifier =
                        Modifier
                            .size(240.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor =
                                    gradientColors.getOrNull(0)?.copy(alpha = 0.5f)
                                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    AsyncImage(
                        model = playlist.thumbnails[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else if (playlist.thumbnails.size > 1) {
                Surface(
                    modifier =
                        Modifier
                            .size(240.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor =
                                    gradientColors.getOrNull(0)?.copy(alpha = 0.5f)
                                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        listOf(
                            Alignment.TopStart,
                            Alignment.TopEnd,
                            Alignment.BottomStart,
                            Alignment.BottomEnd,
                        ).fastForEachIndexed { index, alignment ->
                            AsyncImage(
                                model = playlist.thumbnails.getOrNull(index),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .align(alignment)
                                        .size(120.dp),
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier =
                        Modifier
                            .size(240.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(16.dp),
                            ),
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
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (editable) {
                Surface(
                    onClick = actions.onPickCover,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 6.dp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.edit),
                            contentDescription = stringResource(R.string.change_playlist_cover),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
                    .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
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
            )

            if (playlistLength > 0) {
                MetadataChip(
                    icon = R.drawable.timer,
                    text = makeTimeString(playlistLength * 1000L),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editable) {
                ToggleButton(
                    checked = false,
                    onCheckedChange = { actions.onDelete() },
                    modifier = Modifier.size(48.dp),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors =
                        ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.error,
                            checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            checkedContentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                val liked = playlist.playlist.bookmarkedAt != null
                ToggleButton(
                    checked = liked,
                    onCheckedChange = {
                        actions.onToggleLike()
                    },
                    modifier = Modifier.size(48.dp),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors =
                        ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            checkedContentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (liked) R.drawable.favorite else R.drawable.favorite_border,
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            ToggleButton(
                checked = false,
                onCheckedChange = { actions.onPlay() },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(28.dp),
                )
            }

            ToggleButton(
                checked = false,
                onCheckedChange = { actions.onShuffle() },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.shuffle),
                    modifier = Modifier.size(28.dp),
                )
            }

            ToggleButton(
                checked = downloadState == HeaderDownloadState.Completed,
                onCheckedChange = { actions.onDownload() },
                modifier = Modifier.size(48.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedContentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                val state = downloadState
                when (state) {
                    HeaderDownloadState.Completed -> {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    is HeaderDownloadState.Partial -> {
                        HeaderDownloadProgressIndicator(progress = state.progress)
                    }

                    else -> {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            ToggleButton(
                checked = false,
                onCheckedChange = {
                    if (editable) {
                        actions.onEdit()
                    } else {
                        actions.onSync()
                    }
                },
                modifier = Modifier.size(48.dp),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (editable) R.drawable.edit else R.drawable.sync,
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = actions.onMix,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.mix),
                    contentDescription = "Start Mix",
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

fun LazyListScope.localPlaylistHeroItem(
    playlist: Playlist,
    playlistLength: Int,
    gradientColors: List<Color>,
    systemBarsTopPadding: Dp,
    downloadState: HeaderDownloadState,
    actions: LocalPlaylistActions,
) {
    item(key = LOCAL_PLAYLIST_KEY_HEADER, contentType = CONTENT_TYPE_LOCAL_PLAYLIST_HEADER) {
        LocalPlaylistHeroSection(
            playlist = playlist,
            playlistLength = playlistLength,
            gradientColors = gradientColors,
            systemBarsTopPadding = systemBarsTopPadding,
            downloadState = downloadState,
            actions = actions,
        )
    }
}
