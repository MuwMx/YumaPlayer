/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.utils.LikeSourceResolver

@Composable
internal fun SelectionMediaMetadataPrimaryMenuItems(
    songSelection: List<MediaMetadata>,
    currentItems: List<Timeline.Window>,
    allLiked: Boolean,
    likeSourceHint: LikeSource?,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
    onRemoveFromHistory: (() -> Unit)?,
    onRemoveFromQueue: ((List<Timeline.Window>) -> Unit)?,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    val actionCount =
        (if (onRemoveFromHistory != null) 1 else 0) +
            (if (currentItems.isNotEmpty()) 1 else 0) +
            1 +
            1
    val removeHistoryOffset = if (onRemoveFromHistory != null) 1 else 0
    val removeQueueOffset = removeHistoryOffset + (if (currentItems.isNotEmpty()) 1 else 0)

    MenuSurfaceSection {
        if (onRemoveFromHistory != null) {
            NewMenuItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.remove_from_history),
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
                    onRemoveFromHistory()
                    clearAction()
                },
                index = 0,
                count = actionCount,
            )
        }

        if (currentItems.isNotEmpty()) {
            NewMenuItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.remove_from_queue),
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
                    if (onRemoveFromQueue != null) {
                        onRemoveFromQueue(currentItems)
                    } else {
                        var i = 0
                        currentItems.sortedBy { it.firstPeriodIndex }.forEach { cur ->
                            if (playerConnection.player.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                                playerConnection.player.removeMediaItem(cur.firstPeriodIndex - i++)
                            }
                        }
                    }
                    clearAction()
                },
                index = removeHistoryOffset,
                count = actionCount,
            )
        }

        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismiss()
                playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                clearAction()
            },
            index = removeQueueOffset,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        stringResource(
                            if (allLiked) R.string.dislike_all else R.string.like_all,
                        ),
                )
            },
            leadingContent = {
                Icon(
                    painter =
                        painterResource(
                            if (allLiked) R.drawable.favorite else R.drawable.favorite_border,
                        ),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismiss()
                val updatedSongs =
                    songSelection
                        .asSequence()
                        .distinctBy { it.id }
                        .map { it.toSongEntity() }
                        .filter { song ->
                            val isLikedForHint = when (likeSourceHint) {
                                LikeSource.YTM -> song.likedYtm
                                LikeSource.SPOTIFY -> song.likedSpotify
                                null -> song.liked
                            }
                            allLiked || !isLikedForHint
                        }
                        .map { entity ->
                            val src = likeSourceHint ?: LikeSourceResolver.resolve(mediaId = entity.id, isLocal = entity.isLocal)
                            entity.localToggleLike(src)
                        }
                        .toList()

                if (updatedSongs.isEmpty()) return@NewMenuItem

                coroutineScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        updatedSongs.forEach(::update)
                    }
                    syncUtils.likeSongs(updatedSongs, likeSourceHint)
                }
            },
            index = removeQueueOffset + 1,
            count = actionCount,
        )
    }
}
