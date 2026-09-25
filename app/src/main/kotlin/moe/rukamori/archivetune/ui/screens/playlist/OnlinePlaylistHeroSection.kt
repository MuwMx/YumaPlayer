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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
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
                    Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataChip(
                    icon = R.drawable.music_note,
                    text = songCountText,
                    modifier = Modifier.weight(1f, fill = false),
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
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasLike = playlist.id != "LM"
            val playLabel = stringResource(R.string.play)
            val shuffleLabel = stringResource(R.string.shuffle)
            val radioLabel = stringResource(R.string.radio)
            val likeLabel = stringResource(R.string.liked)
            val downloadLabel = stringResource(R.string.download)

            if (hasLike) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = actions.onLike,
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
                                if (isBookmarked) {
                                    R.drawable.favorite
                                } else {
                                    R.drawable.favorite_border
                                },
                            ),
                        contentDescription = null,
                        tint =
                            if (isBookmarked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            } else if (playlist.radioEndpoint != null) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = actions.onRadio,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = radioLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }

            playlist.playEndpoint?.let {
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
            }

            playlist.shuffleEndpoint?.let {
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

                    HeaderDownloadState.None -> {
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
