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
internal fun AlbumHeroTitleMeta(
    albumWithSongs: AlbumWithSongs,
    songCount: Int,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            albumWithSongs.album.year?.let { year ->
                MetadataChip(
                    icon = R.drawable.calendar_today,
                    text = year.toString(),
                )
            }

            MetadataChip(
                icon = R.drawable.music_note,
                text =
                    pluralStringResource(
                        R.plurals.n_song,
                        songCount,
                        songCount,
                    ),
            )

            val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
            if (totalDuration > 0) {
                MetadataChip(
                    icon = R.drawable.timer,
                    text = makeTimeString(totalDuration * 1000L),
                )
            }
        }
    }
}
