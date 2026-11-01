/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.screens.browse.BrowseViewModel
import moe.rukamori.archivetune.ui.screens.browse.YouTubeBrowseUiState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    browseId: String?,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        when (val state = uiState) {
            YouTubeBrowseUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(8, key = { "loading-$it" }) {
                        ShimmerHost { GridItemPlaceHolder(fillMaxWidth = true) }
                    }
                }
            }
            is YouTubeBrowseUiState.Success -> {
                val contentItems = state.result.items.flatMap { it.items }.distinctBy { it.id }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(contentItems, key = { it.id }) { item ->
                        YouTubeGridItem(
                            item = item,
                            isPlaying = isPlaying,
                            fillMaxWidth = true,
                            coroutineScope = coroutineScope,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    when (item) {
                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                        else -> Unit
                                    }
                                },
                                onLongClick = {
                                    menuState.show {
                                        when (item) {
                                            is AlbumItem -> YouTubeAlbumMenu(
                                                albumItem = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                            is PlaylistItem -> YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                            is ArtistItem -> YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = menuState::dismiss,
                                            )
                                            else -> Unit
                                        }
                                    }
                                },
                            ),
                        )
                    }
                }
            }
            YouTubeBrowseUiState.Empty -> EmptyPlaceholder(
                icon = R.drawable.music_note,
                text = stringResource(R.string.no_results_found),
                modifier = Modifier.padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
            is YouTubeBrowseUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(
                        horizontal = SettingsDimensions.ScreenHorizontalPadding,
                        vertical = SettingsDimensions.ScreenBottomPadding,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = stringResource(state.messageResId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(SettingsDimensions.SectionSpacing))
                Button(onClick = viewModel::retry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        TopAppBar(
            title = { Text((uiState as? YouTubeBrowseUiState.Success)?.result?.title.orEmpty()) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
