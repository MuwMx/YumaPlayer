/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.PlaylistListItem

@Composable
internal fun PlaylistMenuHeader(
    playlist: Playlist,
    dbPlaylist: Playlist?,
    database: MusicDatabase,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PlaylistListItem(
            playlist = playlist,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingContent = {
                if (playlist.playlist.isEditable != true) {
                    IconButton(
                        onClick = {
                            database.query {
                                dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (dbPlaylist?.playlist?.bookmarkedAt != null) {
                                        R.drawable.favorite
                                    } else {
                                        R.drawable.favorite_border
                                    },
                                ),
                            tint =
                                if (dbPlaylist?.playlist?.bookmarkedAt != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    LocalContentColor.current
                                },
                            contentDescription = null,
                        )
                    }
                }
            },
        )
    }
}

@Composable
internal fun PlaylistMenuActionGrid(
    songs: List<Song>,
    playlist: Playlist,
    dbPlaylist: Playlist?,
    playerConnection: PlayerConnection,
    context: Context,
    onDismiss: () -> Unit,
) {
    val playText = stringResource(R.string.play)
    val shuffleText = stringResource(R.string.shuffle)
    val shareText = stringResource(R.string.share)

    val primaryActions =
        remember(
            songs,
            playText,
            shuffleText,
            shareText,
            playlist.playlist.name,
            dbPlaylist?.playlist?.browseId,
            onDismiss,
            playerConnection,
        ) {
            listOf(
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = playText,
                    onClick = {
                        onDismiss()
                        if (songs.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = playlist.playlist.name,
                                    items = songs.map(Song::toMediaItem),
                                ),
                            )
                        }
                    },
                ),
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = shuffleText,
                    onClick = {
                        onDismiss()
                        if (songs.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = playlist.playlist.name,
                                    items = songs.shuffled().map(Song::toMediaItem),
                                ),
                            )
                        }
                    },
                ),
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = shareText,
                    onClick = {
                        onDismiss()
                        val intent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/playlist?list=${dbPlaylist?.playlist?.browseId}")
                            }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                ),
            )
        }

    MenuSurfaceSection {
        NewActionGrid(actions = primaryActions)
    }
}
