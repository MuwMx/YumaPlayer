/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.SongListItem

@Composable
internal fun rememberYouTubeAlbumSplitArtists(
    artists: List<ArtistEntity>?,
    artistSeparators: String,
): List<SplitArtist> =
    remember(artists, artistSeparators) {
        val list = artists ?: emptyList()
        if (artistSeparators.isEmpty()) {
            list.map { SplitArtist(it.name, it) }
        } else {
            val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
            list.flatMap { artist ->
                val parts =
                    artist.name
                        .split(separatorRegex)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.mapIndexed { index, name ->
                        SplitArtist(name, if (index == 0) artist else null)
                    }
                } else {
                    listOf(SplitArtist(artist.name, artist))
                }
            }
        }
    }

@Composable
internal fun YouTubeAlbumAddToPlaylistDialog(
    isVisible: Boolean,
    songIds: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AddToPlaylistDialog(
        isVisible = isVisible,
        onGetSong = { songIds },
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
internal fun YouTubeAlbumErrorPlaylistAddDialog(
    isVisible: Boolean,
    notAddedList: List<Song>,
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
                modifier = Modifier.clickable { onDismiss() },
            )
        }

        items(notAddedList) { song ->
            SongListItem(song = song)
        }
    }
}

@Composable
internal fun YouTubeAlbumArtistSelectDialog(
    isVisible: Boolean,
    splitArtists: List<SplitArtist>,
    onDismiss: () -> Unit,
    onSelectArtist: (String) -> Unit,
) {
    if (!isVisible) return
    ListDialog(
        onDismiss = onDismiss,
    ) {
        items(
            items = splitArtists.distinctBy { it.name },
            key = { it.name },
        ) { splitArtist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .height(ListItemHeight)
                        .clickable {
                            splitArtist.originalArtist?.let { artist ->
                                onSelectArtist(artist.id)
                            }
                        }.padding(horizontal = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier =
                        Modifier
                            .fillParentMaxWidth()
                            .height(ListItemHeight)
                            .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = splitArtist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
