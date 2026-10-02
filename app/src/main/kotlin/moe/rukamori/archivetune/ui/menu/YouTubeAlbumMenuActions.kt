/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeAlbumRadio
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.NewMenuItem

@Composable
internal fun YouTubeAlbumActionGrid(
    albumItem: AlbumItem,
    album: AlbumWithSongs?,
    playerConnection: PlayerConnection,
    context: Context,
    onDismiss: () -> Unit,
) {
    MenuSurfaceSection {
        NewActionGrid(
            actions =
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
                        text = stringResource(R.string.play),
                        onClick = {
                            onDismiss()
                            album?.songs?.let { songs ->
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
                                }
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
                        text = stringResource(R.string.shuffle),
                        onClick = {
                            onDismiss()
                            album?.songs?.let { songs ->
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
                                }
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
                        text = stringResource(R.string.share),
                        onClick = {
                            onDismiss()
                            val intent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, albumItem.shareLink)
                                }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                    ),
                ),
        )
    }
}

@Composable
internal fun YouTubeAlbumPrimaryActions(
    album: AlbumWithSongs?,
    isInSpeedDial: Boolean,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleSpeedDial: () -> Unit,
) {
    val actionCount = 4
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.play_next)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.playlist_play),
                    contentDescription = null,
                )
            },
            onClick = {
                album
                    ?.songs
                    ?.map { it.toMediaItem() }
                    ?.let(playerConnection::playNext)
                onDismiss()
            },
            index = 0,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                )
            },
            onClick = {
                album
                    ?.songs
                    ?.map { it.toMediaItem() }
                    ?.let(playerConnection::addToQueue)
                onDismiss()
            },
            index = 1,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.add_to_playlist)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.playlist_add),
                    contentDescription = null,
                )
            },
            onClick = onAddToPlaylist,
            index = 2,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        stringResource(
                            if (isInSpeedDial) {
                                R.string.remove_from_speed_dial
                            } else {
                                R.string.pin_to_speed_dial
                            },
                        ),
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                    contentDescription = null,
                )
            },
            onClick = onToggleSpeedDial,
            index = 3,
            count = actionCount,
        )
    }
}

@Composable
internal fun YouTubeAlbumArtistSection(
    onViewArtist: () -> Unit,
) {
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.view_artist)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.artist),
                    contentDescription = null,
                )
            },
            onClick = onViewArtist,
            index = 0,
            count = 1,
        )
    }
}
