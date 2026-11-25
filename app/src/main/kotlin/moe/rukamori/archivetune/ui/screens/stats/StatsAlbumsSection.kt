/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.models.MediaMetadata
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.ui.component.LocalAlbumsGrid
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.navigation.containerTransformSource
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.statsAlbumsSection(
    mostPlayedAlbums: List<Album>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    menuState: MenuState,
    haptics: YumaHaptics,
) {
    item(key = "mostPlayedAlbumsHeader", contentType = "sectionHeader") {
        NavigationTitle(
            title = pluralStringResource(R.plurals.n_album, mostPlayedAlbums.size, mostPlayedAlbums.size),
            modifier = Modifier.animateItem(),
        )
    }

    item(key = "albumsRow", contentType = "albums_row") {
        if (mostPlayedAlbums.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = mostPlayedAlbums,
                    key = { _, album -> album.id },
                    contentType = { _, _ -> "album_grid" },
                ) { index, album ->
                    val context = LocalContext.current
                    LocalAlbumsGrid(
                        title = "${index + 1}. ${album.album.title}",
                        subtitle =
                            joinByBullet(
                                pluralStringResource(
                                    R.plurals.n_time,
                                    album.songCountListened ?: 0,
                                    album.songCountListened ?: 0,
                                ),
                                makeTimeString(album.timeListened?.toLong(), context),
                            ),
                        thumbnailUrl = album.album.thumbnailUrl,
                        isActive = album.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .containerTransformSource(
                                    key = "album-${album.id}",
                                    coverUrl = album.album.thumbnailUrl,
                                )
                                .combinedClickable(
                                    onClick = { navController.navigate("album/${album.id}") },
                                    onLongClick = {
                                        haptics.longPress()
                                        menuState.show {
                                            AlbumMenu(
                                                originalAlbum = album,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ).animateItem(),
                    )
                }
            }
        }
    }
}
