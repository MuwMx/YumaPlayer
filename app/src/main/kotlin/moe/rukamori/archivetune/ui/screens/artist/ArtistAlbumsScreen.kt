/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.ui.component.LibraryAlbumGridItem
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.ui.screens.artist.ArtistAlbumsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyGridState = rememberLazyGridState()
    val hazeState = remember { HazeState() }
    val headerProgress = rememberHeaderScrollProgress(
        lazyGridState = lazyGridState,
        hasCollapsibleHeader = false,
        standardThreshold = 48.dp,
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.hazeSource(hazeState),
        ) {
            item(
                key = "header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            items(
                items = albums.distinctBy { it.id },
                key = { it.id },
                contentType = { CONTENT_TYPE_ALBUM },
            ) { album ->
                LibraryAlbumGridItem(
                    navController = navController,
                    menuState = menuState,
                    coroutineScope = coroutineScope,
                    album = album,
                    isActive = album.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        HeaderHazeBackdrop(
            progress = headerProgress,
            hasCollapsibleHeader = false,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        TopAppBar(
            title = { Text(artist?.artist?.name.orEmpty()) },
            navigationIcon = {
                TopAppBarBackButton(navController = navController)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}
