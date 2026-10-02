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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.utils.resize

internal data class SongMenuSplitArtist(
    val name: String,
    val originalArtist: ArtistEntity?,
)

@Composable
internal fun rememberSongMenuSplitArtists(
    orderedArtists: List<ArtistEntity>,
    artistSeparators: String,
): List<SongMenuSplitArtist> =
    remember(orderedArtists, artistSeparators) {
        if (artistSeparators.isEmpty()) {
            orderedArtists.map { SongMenuSplitArtist(it.name, it) }
        } else {
            val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
            orderedArtists.flatMap { artist ->
                val parts =
                    artist.name
                        .split(separatorRegex)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.mapIndexed { index, name ->
                        SongMenuSplitArtist(name, if (index == 0) artist else null)
                    }
                } else {
                    listOf(SongMenuSplitArtist(artist.name, artist))
                }
            }
        }
    }

@Composable
internal fun SongMenuAddToPlaylistDialog(
    isVisible: Boolean,
    songId: String,
    context: Context,
    onDismiss: () -> Unit,
) {
    AddToPlaylistDialog(
        isVisible = isVisible,
        onGetSong = { listOf(songId) },
        onDismiss = onDismiss,
        onAddComplete = { _, playlistNames ->
            val message =
                when {
                    playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
internal fun SongMenuErrorDialog(
    isVisible: Boolean,
    song: Song,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return
    ListDialog(onDismiss = onDismiss) {
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

        items(listOf(song)) { s ->
            SongListItem(song = s)
        }
    }
}

@Composable
internal fun SongMenuSelectArtistDialog(
    isVisible: Boolean,
    splitArtists: List<SongMenuSplitArtist>,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return
    ListDialog(onDismiss = onDismiss) {
        items(
            items = splitArtists.distinctBy { it.name },
            key = { it.name },
        ) { splitArtist ->
            ListItem(
                headlineContent = {
                    Text(
                        text = splitArtist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    AsyncImage(
                        model = splitArtist.originalArtist?.thumbnailUrl?.resize(200, 200),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            splitArtist.originalArtist?.let { artist ->
                                navController.navigate("artist/${artist.id}")
                                onDismiss()
                            }
                        },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}
