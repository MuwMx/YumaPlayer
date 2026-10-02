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
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.utils.shareLocalAudio

@Composable
internal fun SongMenuHeader(
    song: Song,
    likeSource: LikeSource,
    isLiked: Boolean,
    database: MusicDatabase,
    syncUtils: SyncUtils,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SongListItem(
            song = song,
            badges = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingContent = {
                IconButton(
                    onClick = {
                        val s = song.song.localToggleLike(likeSource)
                        database.query {
                            update(s)
                        }
                        val spotifyId = if (LikeSourceResolver.isSpotifyId(s.id, s.isLocal)) s.id else null
                        syncUtils.likeSong(s, likeSource, spotifyId)
                    },
                ) {
                    Icon(
                        painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null,
                    )
                }
            },
        )
    }
}

@Composable
internal fun SongMenuActionGrid(
    song: Song,
    isLocalSong: Boolean,
    playerConnection: PlayerConnection,
    context: Context,
    onDismiss: () -> Unit,
    onShowChoosePlaylistDialog: () -> Unit,
    onShowEditDialog: () -> Unit,
) {
    val startRadioText = stringResource(R.string.start_radio)
    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val addToPlaylistText = stringResource(R.string.add_to_playlist)
    val shareText = stringResource(R.string.share)
    val editText = stringResource(R.string.edit)

    val primaryActions =
        remember(
            song,
            startRadioText,
            playNextText,
            addToQueueText,
            addToPlaylistText,
            shareText,
            editText,
            isLocalSong,
            onDismiss,
            playerConnection,
        ) {
            buildList {
                if (!isLocalSong) {
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = startRadioText,
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                            },
                        ),
                    )
                }
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = playNextText,
                        onClick = {
                            onDismiss()
                            playerConnection.playNext(song.toMediaItem())
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = addToQueueText,
                        onClick = {
                            onDismiss()
                            playerConnection.addToQueue(song.toMediaItem())
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = addToPlaylistText,
                        onClick = onShowChoosePlaylistDialog,
                    ),
                )
                add(
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
                            if (isLocalSong) {
                                shareLocalAudio(context, song.id, song.format?.mimeType)
                            } else {
                                val intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                                    }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = editText,
                        onClick = onShowEditDialog,
                    ),
                )
            }
        }

    MenuSurfaceSection {
        NewActionGrid(actions = primaryActions)
    }
}
