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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState

@Composable
fun OnlinePlaylistHeroSection(
    playlist: PlaylistItem,
    isBookmarked: Boolean,
    downloadState: HeaderDownloadState,
    gradientColors: List<Color>,
    systemBarsTopPadding: Dp,
    actions: OnlinePlaylistActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = systemBarsTopPadding + AppBarHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            Surface(
                modifier =
                    Modifier
                        .size(240.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor =
                                gradientColors
                                    .getOrNull(0)
                                    ?.copy(alpha = 0.5f)
                                    ?: MaterialTheme.colorScheme.primary
                                        .copy(alpha = 0.3f),
                        ),
                shape = RoundedCornerShape(16.dp),
            ) {
                AsyncImage(
                    model = playlist.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        playlist.author?.let { artist ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(
                            style =
                                MaterialTheme.typography.titleMedium
                                    .copy(
                                        fontWeight = FontWeight.Normal,
                                        color =
                                            MaterialTheme.colorScheme
                                                .primary,
                                    ).toSpanStyle(),
                        ) {
                            if (artist.id != null) {
                                val link =
                                    LinkAnnotation.Clickable(artist.id!!) {
                                        actions.onArtistClick(artist.id!!)
                                    }
                                withLink(link) { append(artist.name) }
                            } else {
                                append(artist.name)
                            }
                        }
                    },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        playlist.songCountText?.let { songCountText ->
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataChip(
                    icon = R.drawable.music_note,
                    text = songCountText,
                )
            }
        }

        playlist.description?.let { desc ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasLike = playlist.id != "LM"
            val hasPlay = playlist.playEndpoint != null
            val hasShuffle = playlist.shuffleEndpoint != null
            val hasRadio = playlist.radioEndpoint != null
            val activeIndices =
                remember(hasLike, hasPlay, hasShuffle, hasRadio) {
                    listOf(hasLike, hasPlay, hasShuffle, hasRadio, true, true)
                        .withIndex()
                        .filter { it.value }
                        .map { it.index }
                }

            @Composable
            fun shapeFor(slotIndex: Int) =
                when {
                    activeIndices.first() == slotIndex && activeIndices.last() == slotIndex -> {
                        ButtonGroupDefaults
                            .connectedLeadingButtonShapes()
                    }

                    activeIndices.first() == slotIndex -> {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    }

                    activeIndices.last() == slotIndex -> {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    }

                    else -> {
                        ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                }

            if (hasLike) {
                ToggleButton(
                    checked = isBookmarked,
                    onCheckedChange = { actions.onLike() },
                    modifier = Modifier.size(56.dp),
                    shapes = shapeFor(0),
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
                                if (isBookmarked) {
                                    R.drawable.favorite
                                } else {
                                    R.drawable.favorite_border
                                },
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            playlist.playEndpoint?.let {
                ToggleButton(
                    checked = false,
                    onCheckedChange = { actions.onPlay() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shapes = shapeFor(1),
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
            }

            playlist.shuffleEndpoint?.let {
                ToggleButton(
                    checked = false,
                    onCheckedChange = { actions.onShuffle() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shapes = shapeFor(2),
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
            }

            playlist.radioEndpoint?.let {
                ToggleButton(
                    checked = false,
                    onCheckedChange = { actions.onRadio() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shapes = shapeFor(3),
                    colors =
                        ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = stringResource(R.string.radio),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            ToggleButton(
                checked = downloadState == HeaderDownloadState.Completed,
                onCheckedChange = { actions.onDownload() },
                modifier = Modifier.size(56.dp),
                shapes = shapeFor(4),
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

                    HeaderDownloadState.None -> {
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
                onCheckedChange = { actions.onMenu() },
                modifier = Modifier.size(56.dp),
                shapes = shapeFor(5),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
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
            val mixEndpoint = playlist.shuffleEndpoint ?: playlist.radioEndpoint
            if (mixEndpoint != null) {
                Button(
                    onClick = actions.onMix,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.mix),
                        contentDescription = stringResource(R.string.start_radio),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
