/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu.spotify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog

/**
 * Body for [moe.rukamori.archivetune.ui.menu.SpotifySongMenu] — the
 * YouTubeSongMenuContent section order (action grid → library → artist)
 * with Spotify-safe operations: radio resolves the placeholder first
 * (YouTubeQueue takes a real video id); view-artist resolves through a
 * YouTube artist search exactly like SpotifyHomeViewModel.resolveArtist
 * (no spotify-artist route exists); share links the Spotify web page; no
 * download section (the download pipeline has no placeholder resolution —
 * an entry would only ever fail).
 */
@Composable
internal fun SpotifySongMenuContent(
    song: MediaMetadata,
    librarySong: Song?,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    var showChoosePlaylistDialog by remember { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.insert(song)
            listOf(song.id)
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

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
            SpotifySongPrimaryActionGrid(
                song = song,
                playerConnection = playerConnection,
                database = database,
                coroutineScope = coroutineScope,
                onAddToPlaylist = { showChoosePlaylistDialog = true },
                onDismiss = onDismiss,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SpotifySongLibrarySection(
                song = song,
                librarySong = librarySong,
                database = database,
                coroutineScope = coroutineScope,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SpotifySongArtistSection(
                song = song,
                coroutineScope = coroutineScope,
                navController = navController,
                onDismiss = onDismiss,
            )
        }
    }
}
