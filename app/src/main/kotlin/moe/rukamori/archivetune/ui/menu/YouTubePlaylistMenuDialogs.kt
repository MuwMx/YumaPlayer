/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.DownloadService
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun YouTubePlaylistAddToPlaylistDialog(
    isVisible: Boolean,
    playlist: PlaylistItem,
    songs: List<SongItem>,
    database: MusicDatabase,
    context: Context,
    onDismiss: () -> Unit,
) {
    AddToPlaylistDialog(
        isVisible = isVisible,
        onGetSong = {
            val allSongs =
                songs
                    .ifEmpty {
                        YouTube
                            .playlist(playlist.id)
                            .completed()
                            .getOrNull()
                            ?.songs
                            .orEmpty()
                    }.map {
                        it.toMediaMetadata()
                    }
            database.withTransaction {
                allSongs.forEach(::insert)
            }
            allSongs.map { it.id }
        },
        onDismiss = onDismiss,
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    songCount == 1 && playlistNames.size == 1 -> {
                        context.getString(R.string.added_to_playlist, playlistNames.first())
                    }

                    songCount > 1 && playlistNames.size == 1 -> {
                        context.getString(
                            R.string.added_n_songs_to_playlist,
                            songCount,
                            playlistNames.first(),
                        )
                    }

                    songCount == 1 -> {
                        context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }

                    else -> {
                        context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
                    }
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
internal fun YouTubePlaylistRemoveDownloadDialog(
    isVisible: Boolean,
    playlistTitle: String,
    songs: List<SongItem>,
    context: Context,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.remove_download_playlist_confirm, playlistTitle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onDismiss()
                    songs.forEach { song ->
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.id,
                            false,
                        )
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
internal fun YouTubePlaylistErrorDialog(
    isVisible: Boolean,
    notAddedList: List<MediaMetadata>,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return
    ListDialog(
        onDismiss = onDismiss,
    ) {
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                leadingContent = {
                    Image(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.size(ListThumbnailSize),
                    )
                },
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }

        items(notAddedList) { song ->
            ListItem(
                headlineContent = { Text(text = song.title) },
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(ListThumbnailSize),
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text =
                            joinByBullet(
                                song.artists.joinToString { it.name },
                                makeTimeString(song.duration * 1000L),
                            ),
                    )
                },
            )
        }
    }
}
