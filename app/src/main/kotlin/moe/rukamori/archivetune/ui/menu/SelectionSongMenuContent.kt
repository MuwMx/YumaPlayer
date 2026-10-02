/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads

@Composable
internal fun SelectionSongMenuContent(
    songSelection: List<Song>,
    allInLibrary: Boolean,
    allLiked: Boolean,
    likeSourceHint: LikeSource?,
    downloadState: Int,
    songPosition: List<PlaylistSongMap>?,
    isFromCache: Boolean,
    downloads: Map<String, androidx.media3.exoplayer.offline.Download>,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    onShowChoosePlaylistDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onRemoveFromCache: ((List<Song>) -> Unit)?,
) {
    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            SelectionMenuActionGrid(
                onPlay = {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = "Selection",
                            items = songSelection.map { it.toMediaItem() },
                        ),
                    )
                    clearAction()
                },
                onShuffle = {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = "Selection",
                            items = songSelection.shuffled().map { it.toMediaItem() },
                        ),
                    )
                    clearAction()
                },
                onAddToPlaylist = onShowChoosePlaylistDialog,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SelectionSongPrimaryMenuItems(
                songSelection = songSelection,
                allInLibrary = allInLibrary,
                allLiked = allLiked,
                likeSourceHint = likeSourceHint,
                playerConnection = playerConnection,
                database = database,
                syncUtils = syncUtils,
                coroutineScope = coroutineScope,
                context = context,
                onDismiss = onDismiss,
                clearAction = clearAction,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SelectionMenuDownloadSection(
                downloadState = downloadState,
                onShowRemoveDownloadDialog = onShowRemoveDownloadDialog,
                onDownload = {
                    sendAddMissingDownloads(
                        context = context,
                        songs =
                            songSelection.map { song ->
                                HeaderDownloadItem(
                                    id = song.id,
                                    title = song.song.title,
                                )
                            },
                        downloads = downloads,
                    )
                },
            )
        }

        if (songPosition?.isNotEmpty() == true) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection {
                    SelectionSongPlaylistDeleteMenuItem(
                        positions = songPosition,
                        database = database,
                        coroutineScope = coroutineScope,
                        context = context,
                        onDismiss = onDismiss,
                        clearAction = clearAction,
                    )
                }
            }
        }

        if (isFromCache && onRemoveFromCache != null) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection {
                    NewMenuItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.remove_from_cache),
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
                        onClick = {
                            onDismiss()
                            onRemoveFromCache(songSelection)
                            clearAction()
                        },
                        index = 0,
                        count = 1,
                    )
                }
            }
        }
    }
}
