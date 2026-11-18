/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.models.MediaMetadata
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.statsSongsSection(
    mostPlayedSongsStats: List<SongWithStats>,
    mostPlayedSongs: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    haptics: YumaHaptics,
) {
    item(key = "mostPlayedSongsHeader", contentType = "sectionHeader") {
        NavigationTitle(
            title = pluralStringResource(R.plurals.n_song, mostPlayedSongsStats.size, mostPlayedSongsStats.size),
            modifier = Modifier.animateItem(),
        )
    }

    val maxPlayCount = mostPlayedSongsStats.maxOfOrNull { it.songCountListened } ?: 1

    itemsIndexed(
        items = mostPlayedSongsStats,
        key = { index, song -> "${song.id}_$index" },
        contentType = { _, _ -> "ranked_song" },
    ) { index, song ->
        val playFraction = song.songCountListened.toFloat() / maxPlayCount
        val medalColor =
            when (index) {
                0 -> Color(0xFFFFD700)
                1 -> Color(0xFFC0C0C0)
                2 -> Color(0xFFCD7F32)
                else -> null
            }
        val progressBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        val songEntity = mostPlayedSongs.firstOrNull { it.id == song.id }

        ListItem(
            title = "${index + 1}. ${song.title}",
            subtitle =
                joinByBullet(
                    pluralStringResource(R.plurals.n_time, song.songCountListened, song.songCountListened),
                    makeTimeString(song.timeListened),
                ),
            thumbnailContent = {
                Box {
                    ItemThumbnail(
                        thumbnailUrl = song.thumbnailUrl,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(56.dp),
                    )
                    if (medalColor != null) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(medalColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                            )
                        }
                    }
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            color = progressBarColor,
                            size = Size(size.width * playFraction, size.height),
                        )
                    }.combinedClickable(
                        onClick = {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue(
                                        endpoint = WatchEndpoint(song.id),
                                        preloadItem = songEntity?.toMediaMetadata(),
                                    ),
                                )
                            }
                        },
                        onLongClick = {
                            val targetSong = songEntity ?: return@combinedClickable
                            haptics.longPress()
                            menuState.show {
                                SongMenu(
                                    originalSong = targetSong,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ).animateItem(),
        )
    }
}