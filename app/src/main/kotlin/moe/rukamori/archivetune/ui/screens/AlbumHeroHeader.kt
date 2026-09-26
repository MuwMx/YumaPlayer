/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun AlbumHeroHeader(
    albumWithSongs: AlbumWithSongs,
    songCount: Int,
    downloadState: HeaderDownloadState,
    actions: AlbumActions,
    topPadding: Dp,
    heroSpacerHeight: Dp,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(heroSpacerHeight))

        // Album Title
        Text(
            text = albumWithSongs.album.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist Names (Clickable)
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
                        albumWithSongs.artists.fastForEachIndexed { index, artist ->
                            val link =
                                LinkAnnotation.Clickable(artist.id) {
                                    onArtistClick(artist.id)
                                }
                            withLink(link) {
                                append(artist.name)
                            }
                            if (index != albumWithSongs.artists.lastIndex) {
                                append(", ")
                            }
                        }
                    }
                },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Metadata Row - Year, Song Count, Duration
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Year
            albumWithSongs.album.year?.let { year ->
                MetadataChip(
                    icon = R.drawable.calendar_today,
                    text = year.toString(),
                )
            }

            // Song Count
            MetadataChip(
                icon = R.drawable.music_note,
                text =
                    pluralStringResource(
                        R.plurals.n_song,
                        songCount,
                        songCount,
                    ),
            )

            // Duration
            val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
            if (totalDuration > 0) {
                MetadataChip(
                    icon = R.drawable.timer,
                    text = makeTimeString(totalDuration * 1000L),
                )
            }
        }

        // Action Buttons Row
        val isBookmarked = albumWithSongs.album.bookmarkedAt != null
        val likeContentDescription =
            stringResource(
                if (isBookmarked) R.string.subscribed else R.string.subscribe,
            )
        val likeBg =
            if (isBookmarked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                LocalYumaColors.current.glassBackground
            }
        val likeFg =
            if (isBookmarked) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        val isDownloaded = downloadState == HeaderDownloadState.Completed
        val downloadContentDescription = stringResource(R.string.download)
        val shuffleLabel = stringResource(R.string.shuffle)
        val moreOptionsLabel = stringResource(R.string.more_options)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = { actions.onLike() },
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = likeBg,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = likeContentDescription
                            role = Role.Checkbox
                            toggleableState = ToggleableState(isBookmarked)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (isBookmarked) R.drawable.favorite else R.drawable.favorite_border,
                        ),
                    contentDescription = null,
                    tint = likeFg,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = { actions.onPlay() },
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
                        text = stringResource(R.string.play),
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
                            onClick = { actions.onShuffle() },
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
                            onClick = { actions.onDownload() },
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor =
                                if (isDownloaded) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    LocalYumaColors.current.glassBackground
                                },
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = downloadContentDescription
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                when (val state = downloadState) {
                    HeaderDownloadState.Completed -> {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = { actions.onMenu() },
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = moreOptionsLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
