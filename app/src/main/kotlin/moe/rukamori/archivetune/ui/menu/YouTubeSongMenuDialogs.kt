/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.ui.component.ListDialog

internal data class YouTubeSongSplitArtist(
    val name: String,
    val originalArtist: MediaMetadata.Artist?,
)

@Composable
internal fun rememberYouTubeSongSplitArtists(
    artists: List<MediaMetadata.Artist>,
    artistSeparators: String,
): List<YouTubeSongSplitArtist> =
    remember(artists, artistSeparators) {
        if (artistSeparators.isEmpty()) {
            artists.map { YouTubeSongSplitArtist(it.name, it) }
        } else {
            val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
            artists.flatMap { artist ->
                val parts =
                    artist.name
                        .split(separatorRegex)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.mapIndexed { index, name ->
                        YouTubeSongSplitArtist(name, if (index == 0) artist else null)
                    }
                } else {
                    listOf(YouTubeSongSplitArtist(artist.name, artist))
                }
            }
        }
    }

@Composable
internal fun YouTubeSongAddToPlaylistDialog(
    isVisible: Boolean,
    song: SongItem,
    database: MusicDatabase,
    context: Context,
    onDismiss: () -> Unit,
) {
    AddToPlaylistDialog(
        isVisible = isVisible,
        onGetSong = {
            database.withTransaction {
                insert(song.toMediaMetadata())
            }
            listOf(song.id)
        },
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
internal fun YouTubeSongArtistSelectDialog(
    isVisible: Boolean,
    splitArtists: List<YouTubeSongSplitArtist>,
    onDismiss: () -> Unit,
    onSelectArtist: (String?) -> Unit,
) {
    if (!isVisible) return
    ListDialog(
        onDismiss = onDismiss,
    ) {
        items(splitArtists.distinctBy { it.name }) { splitArtist ->
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
