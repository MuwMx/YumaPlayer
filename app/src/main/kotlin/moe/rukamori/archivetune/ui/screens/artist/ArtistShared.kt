/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ARTIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_PLAYLIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.backToMain

@Composable
fun TopAppBarBackButton(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = navController::navigateUp,
        onLongClick = navController::backToMain,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = null,
        )
    }
}

@Composable
fun ArtistBackButton(
    navController: NavController,
    modifier: Modifier = Modifier,
) = TopAppBarBackButton(navController = navController, modifier = modifier)

val YTItem.contentKey: String
    get() {
        val type =
            when (this) {
                is SongItem -> "song"
                is AlbumItem -> "album"
                is ArtistItem -> "artist"
                is PlaylistItem -> "playlist"
            }
        return "${type}_$id"
    }

val YTItem.contentType: Int
    get() =
        when (this) {
            is SongItem -> CONTENT_TYPE_SONG
            is AlbumItem -> CONTENT_TYPE_ALBUM
            is ArtistItem -> CONTENT_TYPE_ARTIST
            is PlaylistItem -> CONTENT_TYPE_PLAYLIST
        }

fun YTItem.isActive(mediaMetadata: MediaMetadata?): Boolean =
    when (this) {
        is SongItem -> mediaMetadata?.id == id
        is AlbumItem -> mediaMetadata?.album?.id == id
        else -> false
    }

fun isYTItemActive(
    item: YTItem,
    mediaMetadata: MediaMetadata?,
): Boolean = item.isActive(mediaMetadata)

@Composable
fun YTItemMenu(
    item: YTItem,
    navController: NavController,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    when (item) {
        is SongItem -> {
            YouTubeSongMenu(
                song = item,
                navController = navController,
                onDismiss = onDismiss,
            )
        }

        is AlbumItem -> {
            YouTubeAlbumMenu(
                albumItem = item,
                navController = navController,
                onDismiss = onDismiss,
            )
        }

        is ArtistItem -> {
            YouTubeArtistMenu(
                artist = item,
                onDismiss = onDismiss,
            )
        }

        is PlaylistItem -> {
            YouTubePlaylistMenu(
                playlist = item,
                coroutineScope = coroutineScope,
                onDismiss = onDismiss,
            )
        }
    }
}

fun showYTItemMenu(
    menuState: MenuState,
    item: YTItem,
    navController: NavController,
    coroutineScope: CoroutineScope,
) {
    menuState.show {
        YTItemMenu(
            item = item,
            navController = navController,
            coroutineScope = coroutineScope,
            onDismiss = menuState::dismiss,
        )
    }
}

fun onYTItemClick(
    item: YTItem,
    navController: NavController,
    playerConnection: PlayerConnection,
) {
    when (item) {
        is SongItem -> {
            playerConnection.playQueue(
                YouTubeQueue(
                    item.endpoint ?: WatchEndpoint(videoId = item.id),
                    item.toMediaMetadata(),
                ),
            )
        }

        is AlbumItem -> {
            navController.navigate("album/${item.id}")
        }

        is ArtistItem -> {
            navController.navigate("artist/${item.id}")
        }

        is PlaylistItem -> {
            navController.navigate("online_playlist/${item.id}")
        }
    }
}
