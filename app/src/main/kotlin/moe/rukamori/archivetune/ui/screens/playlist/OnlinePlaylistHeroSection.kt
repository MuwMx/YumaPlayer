/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.YumaImmersiveArtworkHeader
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
    systemBarsTopPadding: Dp,
    actions: OnlinePlaylistActions,
    headerProgress: () -> Float,
    disableBlur: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hasArtwork = !playlist.thumbnail.isNullOrBlank()

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
                thumbnailUrl = playlist.thumbnail,
                progress = headerProgress,
                disableBlur = disableBlur,
                modifier = Modifier.matchParentSize(),
            )
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
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
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
                                            color = MaterialTheme.colorScheme.primary,
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

            playlist.songCountText?.let { songCountText ->
                Spacer(modifier = Modifier.height(12.dp))
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

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
        }
    }
}
