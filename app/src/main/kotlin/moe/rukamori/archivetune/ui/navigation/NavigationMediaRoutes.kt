/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import moe.rukamori.archivetune.ui.screens.album.AlbumScreen
import moe.rukamori.archivetune.ui.screens.album.SpotifyAlbumScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistAlbumsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistItemsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistSongsScreen
import moe.rukamori.archivetune.ui.screens.browse.YouTubeBrowseScreen
import moe.rukamori.archivetune.ui.screens.library.SpotifyLikedSongsScreen
import moe.rukamori.archivetune.ui.screens.playlist.AutoPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.CachePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.LocalPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.OnlinePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.SpotifyPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.TopPlaylistScreen

private val ArtworkDetailPrefixes = listOf(
    "album/",
    "spotify_album/",
    "local_playlist/",
    "online_playlist/",
    "spotify_playlist/",
    "auto_playlist/",
    "cache_playlist/",
    "top_playlist/",
)

internal fun isArtworkDetailRoute(route: String?): Boolean {
    if (route == null) return false
    val cleanRoute = route.substringBefore('?')
    return ArtworkDetailPrefixes.any { cleanRoute.startsWith(it) } ||
            cleanRoute in setOf(
        "album/{albumId}",
        "spotify_album/{albumId}",
        "local_playlist/{playlistId}",
        "online_playlist/{playlistId}",
        "spotify_playlist/{playlistId}",
        "auto_playlist/{playlist}",
        "cache_playlist/{playlist}",
        "top_playlist/{top}",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun NavGraphBuilder.mediaNavigationRoutes(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController)
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "spotify_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        SpotifyPlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "spotify_album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        SpotifyAlbumScreen(navController, scrollBehavior)
    }

    composable("spotify_liked_songs") {
        SpotifyLikedSongsScreen(navController, scrollBehavior)
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }
}

internal fun encodeNavigationParam(value: String): String =
    java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name())
        .replace("+", "%20")

internal fun buildYouTubeBrowseRoute(
    browseId: String,
    params: String? = null,
): String {
    val encodedBrowseId = encodeNavigationParam(browseId)
    val trimmedParams = params?.takeIf { it.isNotBlank() }
    return if (trimmedParams != null) {
        "youtube_browse/$encodedBrowseId?params=${encodeNavigationParam(trimmedParams)}"
    } else {
        "youtube_browse/$encodedBrowseId"
    }
}

internal fun buildBrowseRoute(
    browseId: String,
    params: String? = null,
): String {
    val encodedBrowseId = encodeNavigationParam(browseId)
    val validParams = params?.takeIf { it.isNotBlank() }
    return if (validParams != null) {
        "browse/$encodedBrowseId?params=${encodeNavigationParam(validParams)}"
    } else {
        "browse/$encodedBrowseId"
    }
}
