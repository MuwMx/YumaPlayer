/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem

@Composable
internal fun AlbumMenuPrimaryActions(
    isInSpeedDial: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
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
            onClick = onPlayNext,
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
            onClick = onAddToQueue,
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
internal fun AlbumMenuDownloadSection(
    downloadState: Int,
    onRemoveDownload: () -> Unit,
    onDownload: () -> Unit,
) {
    MenuSurfaceSection {
        when (downloadState) {
            STATE_COMPLETED -> {
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
                    onClick = onRemoveDownload,
                    index = 0,
                    count = 1,
                )
            }

            STATE_QUEUED, STATE_DOWNLOADING -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.downloading)) },
                    leadingContent = {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = onRemoveDownload,
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
                    onClick = onDownload,
                    index = 0,
                    count = 1,
                )
            }
        }
    }
}

@Composable
internal fun AlbumMenuArtistAndSyncSection(
    isLocalAlbum: Boolean,
    rotationAnimation: Float,
    onViewArtist: () -> Unit,
    onRefetch: () -> Unit,
) {
    val navItemCount = if (!isLocalAlbum) 2 else 1
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
            count = navItemCount,
        )

        if (!isLocalAlbum) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.refetch)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                    )
                },
                onClick = onRefetch,
                index = 1,
                count = navItemCount,
            )
        }
    }
}
