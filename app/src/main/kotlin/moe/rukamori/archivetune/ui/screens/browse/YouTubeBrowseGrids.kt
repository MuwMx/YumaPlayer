/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("UNUSED_EXPRESSION")

package moe.rukamori.archivetune.ui.screens.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu

internal fun LazyListScope.youtubeBrowseSongGridItem(
    sectionItems: List<YTItem>,
    lazyGridState: LazyGridState,
    snapLayoutInfoProvider: SnapLayoutInfoProvider,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    menuState: MenuState,
    navController: NavController,
    playerConnection: PlayerConnection,
    sectionKey: String = "song_grid",
) {
    item(
        key = sectionKey,
        contentType = "song_grid_section",
    ) {
        LazyHorizontalGrid(
            state = lazyGridState,
            rows = GridCells.Fixed(4),
            flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
            contentPadding =
                WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ListItemHeight * 4),
        ) {
            items(
                items = sectionItems,
                key = { it.id },
                contentType = { "song_item" },
            ) { song ->
                Box(Modifier.width(350.dp)) {
                    YouTubeListItem(
                        item = song as SongItem,
                        isActive = mediaMetadata?.id == song.id,
                        isPlaying = isPlaying,
                        isSwipeable = false,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(
                                                song.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                }
                                .animateItem(),
                    )
                }
            }
        }
    }
}