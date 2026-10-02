/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.utils.LikeSourceResolver
import java.time.LocalDateTime

@Composable
internal fun SelectionSongPrimaryMenuItems(
    songSelection: List<Song>,
    allInLibrary: Boolean,
    allLiked: Boolean,
    likeSourceHint: LikeSource?,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    val actionCount = 3
    MenuSurfaceSection {
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
            index = 0,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        stringResource(
                            if (allInLibrary) R.string.remove_from_library else R.string.add_to_library,
                        ),
                )
            },
            leadingContent = {
                Icon(
                    painter =
                        painterResource(
                            if (allInLibrary) R.drawable.library_add_check else R.drawable.library_add,
                        ),
                    contentDescription = null,
                )
            },
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val shouldAdd = !allInLibrary
                    val now = LocalDateTime.now()
                    val failed = LinkedHashSet<String>()
                    val updatedSongs = ArrayList<SongEntity>()
                    for (song in songSelection.asSequence().map { it.song }.distinctBy { it.id }) {
                        val remoteResult = YouTube.likeVideo(song.id, shouldAdd)
                        if (remoteResult.isFailure) {
                            failed += song.id
                            continue
                        }
                        updatedSongs +=
                            song.copy(
                                liked = shouldAdd,
                                likedDate = if (shouldAdd) now else null,
                                inLibrary = if (shouldAdd) now else null,
                            )
                    }

                    if (updatedSongs.isNotEmpty()) {
                        database.withTransaction {
                            updatedSongs.forEach(::update)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onDismiss()
                        clearAction()
                        if (failed.isNotEmpty()) {
                            Toast
                                .makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            },
            index = 1,
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
                val shouldUnlikeAll = songSelection.all {
                    when (likeSourceHint) {
                        LikeSource.YTM -> it.song.likedYtm
                        LikeSource.SPOTIFY -> it.song.likedSpotify
                        null -> it.song.liked
                    }
                }
                val updatedSongs =
                    songSelection
                        .asSequence()
                        .map { it.song }
                        .distinctBy { it.id }
                        .filter { song ->
                            val isLikedForHint = when (likeSourceHint) {
                                LikeSource.YTM -> song.likedYtm
                                LikeSource.SPOTIFY -> song.likedSpotify
                                null -> song.liked
                            }
                            shouldUnlikeAll || !isLikedForHint
                        }
                        .map { song ->
                            val src = likeSourceHint ?: LikeSourceResolver.resolve(mediaId = song.id, isLocal = song.isLocal)
                            song.localToggleLike(src)
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
            index = 2,
            count = actionCount,
        )
    }
}
