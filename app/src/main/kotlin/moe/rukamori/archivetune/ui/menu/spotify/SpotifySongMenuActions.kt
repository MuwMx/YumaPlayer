/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu.spotify

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.MatchPriority
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid

/**
 * The YT-menu action grid (start radio / play next / add to queue / add to
 * playlist / share), Spotify-adjusted: radio shows a spinner while the
 * placeholder resolves and falls back to an error toast (YouTubeQueue takes
 * a real video id, so it can't start directly from the placeholder); share
 * points at open.spotify.com (the id's true destination).
 */
@Composable
internal fun SpotifySongPrimaryActionGrid(
    song: MediaMetadata,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var resolvingRadio by remember { mutableStateOf(false) }

    val startRadioText = stringResource(R.string.start_radio)
    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val addToPlaylistText = stringResource(R.string.add_to_playlist)
    val shareText = stringResource(R.string.share)

    val actions =
        listOf(
            NewAction(
                icon = {
                    if (resolvingRadio) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.radio),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                text = startRadioText,
                onClick = {
                    if (resolvingRadio) return@NewAction
                    coroutineScope.launch(Dispatchers.IO) {
                        resolvingRadio = true
                        val resolved =
                            SpotifyPlaceholders.resolve(
                                song.id,
                                database,
                                priority = MatchPriority.UPCOMING,
                                force = true,
                            )
                        if (resolved != null) {
                            onDismiss()
                            playerConnection.playQueue(YouTubeQueue.radio(resolved))
                        } else {
                            Toast
                                .makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT)
                                .show()
                        }
                        resolvingRadio = false
                    }
                },
            ),
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
                onClick = onAddToPlaylist,
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
                    val rawId = song.id.removePrefix("spotify:track:").removePrefix("spotify:")
                    val intent =
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://open.spotify.com/track/$rawId")
                        }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                },
            ),
        )

    MenuSurfaceSection {
        NewActionGrid(actions = actions)
    }
}
