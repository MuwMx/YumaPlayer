/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun YouTubeSongMenuHeader(
    song: SongItem,
    librarySong: Song?,
    database: MusicDatabase,
    syncUtils: SyncUtils,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = song.title,
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text =
                        joinByBullet(
                            song.artists.joinToString { it.name },
                            song.duration?.let { makeTimeString(it * 1000L) },
                        ),
                )
            },
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                ) {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                    )
                }
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        database.transaction {
                            val updatedSong: SongEntity
                            if (librarySong == null) {
                                insert(song.toMediaMetadata()) { it.localToggleLike(LikeSource.YTM) }
                                updatedSong = song.toMediaMetadata().toSongEntity().let { it.localToggleLike(LikeSource.YTM) }
                            } else {
                                updatedSong = librarySong.song.localToggleLike(LikeSource.YTM)
                                update(updatedSong)
                            }
                            syncUtils.likeSong(updatedSong, LikeSource.YTM)
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (librarySong?.song?.liked ==
                                    true
                                ) {
                                    R.drawable.favorite
                                } else {
                                    R.drawable.favorite_border
                                },
                            ),
                        tint = if (librarySong?.song?.liked == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
