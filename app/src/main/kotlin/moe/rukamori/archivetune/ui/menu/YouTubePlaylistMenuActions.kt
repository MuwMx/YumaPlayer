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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads

@Composable
internal fun YouTubePlaylistActionGrid(
    playlist: PlaylistItem,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
) {
    val playText = stringResource(R.string.play)
    val shuffleText = stringResource(R.string.shuffle)
    val startRadioText = stringResource(R.string.start_radio)

    val primaryActions =
        remember(
            playlist.playEndpoint,
            playlist.shuffleEndpoint,
            playlist.radioEndpoint,
            playText,
            shuffleText,
            startRadioText,
            playerConnection,
            onDismiss,
        ) {
            buildList {
                playlist.playEndpoint?.let { playEndpoint ->
                    add(
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
                                playerConnection.playQueue(YouTubeQueue.playlist(playEndpoint))
                                onDismiss()
                            },
                        ),
                    )
                }
                playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                    add(
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
                                playerConnection.playQueue(YouTubeQueue.playlist(shuffleEndpoint))
                                onDismiss()
                            },
                        ),
                    )
                }
                playlist.radioEndpoint?.let { radioEndpoint ->
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
                                playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                onDismiss()
                            },
                        ),
                    )
                }
            }
        }

    MenuSurfaceSection {
        NewActionGrid(actions = primaryActions)
    }
}

@Composable
internal fun YouTubePlaylistShareAndSelectSection(
    playlist: PlaylistItem,
    canSelect: Boolean,
    context: Context,
    onDismiss: () -> Unit,
    selectAction: () -> Unit,
) {
    val shareSelectCount = 1 + (if (canSelect) 1 else 0)
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.share)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = null,
                )
            },
            onClick = {
                val intent =
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, playlist.shareLink)
                    }
                context.startActivity(Intent.createChooser(intent, null))
                onDismiss()
            },
            index = 0,
            count = shareSelectCount,
        )

        if (canSelect) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.select)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.select_all),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDismiss()
                    selectAction()
                },
                index = 1,
                count = shareSelectCount,
            )
        }
    }
}
