/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

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
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistMenuDownloadSection(
    downloadState: Int,
    songs: List<Song>,
    context: Context,
    onShowRemoveDownloadDialog: () -> Unit,
) {
    val downloadUtil = LocalDownloadUtil.current
    MenuSurfaceSection {
        when (downloadState) {
            Download.STATE_COMPLETED -> {
                NewMenuItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.remove_download),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = onShowRemoveDownloadDialog,
                    index = 0,
                    count = 1,
                )
            }

            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.downloading)) },
                    leadingContent = {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = onShowRemoveDownloadDialog,
                    index = 0,
                    count = 1,
                )
            }

            else -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.action_download)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        sendAddMissingDownloads(
                            context = context,
                            songs =
                                songs.map { song ->
                                    HeaderDownloadItem(
                                        id = song.id,
                                        title = song.song.title,
                                    )
                                },
                            downloads = downloadUtil.downloads.value,
                        )
                    },
                    index = 0,
                    count = 1,
                )
            }
        }
    }
}

@Composable
internal fun PlaylistMenuSyncHideDeleteSection(
    playlist: Playlist,
    onSyncClick: () -> Unit,
    onHideClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.sync_playlist)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = null,
                )
            },
            onClick = onSyncClick,
            index = 0,
            count = 3,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        stringResource(
                            if (playlist.playlist.isHidden) {
                                R.string.unhide_playlist
                            } else {
                                R.string.hide_playlist
                            },
                        ),
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.visibility_off),
                    contentDescription = null,
                )
            },
            onClick = onHideClick,
            index = 1,
            count = 3,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = onDeleteClick,
            index = 2,
            count = 3,
        )
    }
}

@Composable
internal fun PlaylistMenuShareSection(
    shareLink: String,
    context: Context,
    onDismiss: () -> Unit,
) {
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
                        putExtra(Intent.EXTRA_TEXT, shareLink)
                    }
                context.startActivity(Intent.createChooser(intent, null))
                onDismiss()
            },
            index = 0,
            count = 1,
        )
    }
}
