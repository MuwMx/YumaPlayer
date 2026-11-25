/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.ui.component.LocalArtistsGrid
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.navigation.containerTransformSource
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.statsArtistsSection(
    mostPlayedArtists: List<Artist>,
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    haptics: YumaHaptics,
) {
    item(key = "mostPlayedArtists", contentType = "sectionHeader") {
        NavigationTitle(
            title = pluralStringResource(R.plurals.n_artist, mostPlayedArtists.size, mostPlayedArtists.size),
            modifier = Modifier.animateItem(),
        )
    }

    itemsIndexed(
        items = mostPlayedArtists.chunked(2),
        key = { _, rowArtists -> rowArtists.first().id },
        contentType = { _, _ -> "artist_row" },
    ) { _, rowArtists ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowArtists.forEach { artist ->
                val context = LocalContext.current
                LocalArtistsGrid(
                    title = artist.artist.name,
                    subtitle =
                        joinByBullet(
                            pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                            makeTimeString(artist.timeListened?.toLong(), context),
                        ),
                    thumbnailUrl = artist.artist.thumbnailUrl,
                    modifier =
                        Modifier
                            .weight(1f)
                            .containerTransformSource(
                                key = "artist-${artist.id}",
                                coverUrl = artist.artist.thumbnailUrl,
                            )
                            .combinedClickable(
                                onClick = { navController.navigate("artist/${artist.id}") },
                                onLongClick = {
                                    haptics.longPress()
                                    menuState.show {
                                        ArtistMenu(
                                            originalArtist = artist,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ).animateItem(),
                )
            }
            repeat(2 - rowArtists.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}
