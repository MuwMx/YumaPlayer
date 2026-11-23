/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu.spotify

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.main.LocalDatabase
import java.time.LocalDateTime

/**
 * add-to-library / remove-from-library — the
 * YouTubeSongLibraryAndSpeedDialSection shape minus the remote likeVideo
 * call (the row is a Spotify placeholder; the Spotify side of the library
 * push happens through the like syncs).
 */
@Composable
internal fun SpotifySongLibrarySection(
    song: MediaMetadata,
    librarySong: Song?,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
) {
    val inLibrary = librarySong?.song?.inLibrary != null

    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        if (inLibrary) {
                            stringResource(R.string.remove_from_library)
                        } else {
                            stringResource(R.string.add_to_library)
                        },
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(if (inLibrary) R.drawable.library_add_check else R.drawable.library_add),
                    contentDescription = null,
                )
            },
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val seed = librarySong?.song ?: song.toSongEntity()
                    val now = LocalDateTime.now()
                    database.upsert(
                        if (inLibrary) {
                            // SongEntity.toggleLibrary() parity: leaving the
                            // library clears like flags (kept only if the
                            // like was the reason for membership).
                            seed.toggleLibrary()
                        } else {
                            seed.copy(inLibrary = now)
                        },
                    )
                }
            },
            index = 0,
            count = 1,
        )
    }
}

/**
 * view-artist — Spotify artists have no route of their own; resolve
 * through a YouTube artist search identically to
 * SpotifyHomeViewModel.resolveArtist, then navigate on the main thread.
 */
@Composable
internal fun SpotifySongArtistSection(
    song: MediaMetadata,
    coroutineScope: CoroutineScope,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var resolving by remember { mutableStateOf(false) }

    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.view_artist)) },
            leadingContent = {
                if (resolving) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(painter = painterResource(R.drawable.artist), contentDescription = null)
                }
            },
            onClick = {
                if (resolving) return@NewMenuItem
                coroutineScope.launch(Dispatchers.IO) {
                    resolving = true
                    val artistName = song.artists.firstOrNull()?.name
                    val artistItem =
                        if (artistName.isNullOrBlank()) {
                            null
                        } else {
                            YouTube.search(artistName, YouTube.SearchFilter.FILTER_ARTIST)
                                .getOrNull()
                                ?.items
                                ?.firstOrNull() as? ArtistItem
                        }
                    if (artistItem != null) {
                        withContext(Dispatchers.Main) {
                            onDismiss()
                            navController.navigate("artist/${artistItem.id}")
                        }
                    } else {
                        Toast
                            .makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT)
                            .show()
                    }
                    resolving = false
                }
            },
            index = 0,
            count = 1,
        )
    }
}
