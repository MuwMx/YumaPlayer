/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.ui.screens.RecapCream
import moe.rukamori.archivetune.ui.screens.RecapRed
import moe.rukamori.archivetune.ui.screens.RecapYellow
import moe.rukamori.archivetune.ui.screens.YearInMusicRecapCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RankedArtistsRecapCard(
    card: YearInMusicRecapCard.RankedArtists,
    applySafeContentInsets: Boolean,
    onArtistLongClick: (Artist) -> Unit,
) {
    RecapCardContent(
        badge = stringResource(R.string.top_artists),
        footer = stringResource(R.string.year_in_music_ranked_artists),
        verticalArrangement = Arrangement.SpaceBetween,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.year_in_music_ranked_artists),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                lineHeight = 42.sp,
            )
            Text(
                text =
                    card.artists
                        .firstOrNull()
                        ?.artist
                        ?.name
                        .orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RecapRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            card.artists.forEachIndexed { index, artist ->
                RankedArtistRow(
                    rank = index + 1,
                    artist = artist,
                    modifier =
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onArtistLongClick(artist) },
                        ),
                )
            }
        }
    }
}
@Composable
internal fun RankedAlbumsRecapCard(
    card: YearInMusicRecapCard.RankedAlbums,
    applySafeContentInsets: Boolean,
) {
    RecapCardContent(
        badge = stringResource(R.string.albums),
        footer = stringResource(R.string.year_in_music_ranked_albums),
        verticalArrangement = Arrangement.SpaceBetween,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.year_in_music_ranked_albums),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                lineHeight = 42.sp,
            )
            Text(
                text =
                    card.albums
                        .firstOrNull()
                        ?.album
                        ?.title
                        .orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RecapYellow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            card.albums.forEachIndexed { index, album ->
                RankedAlbumRow(
                    rank = index + 1,
                    album = album,
                )
            }
        }
    }
}
