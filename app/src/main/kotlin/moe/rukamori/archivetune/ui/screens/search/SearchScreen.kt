/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryScreenState
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryTab
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    onSearchClick: () -> Unit,
    headerScrollConnection: NestedScrollConnection? = null,
    viewModel: SearchDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry
            ?.savedStateHandle
            ?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    // Step 2b: attach the shell's floating-header connection here so Search's
                    // scroll/fling writes Search's own header state and can't leak elsewhere.
                    if (headerScrollConnection != null) {
                        Modifier.nestedScroll(headerScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                key = "search_field",
                contentType = "search_field",
            ) {
                SearchEntryField(
                    onClick = onSearchClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateItem(),
                )
            }

            item(
                key = "search_tabs",
                contentType = "search_tabs",
            ) {
                SearchDiscoveryTabs(
                    selectedTab = selectedTab,
                    onTabSelected = viewModel::selectTab,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateItem(),
                )
            }

            when (val currentState = state) {
                SearchDiscoveryScreenState.Loading -> {
                    item(
                        key = "search_loading",
                        contentType = "search_loading",
                    ) {
                        SearchDiscoveryLoading(modifier = Modifier.animateItem())
                    }
                }

                SearchDiscoveryScreenState.Empty -> {
                    item(
                        key = "search_empty",
                        contentType = "search_empty",
                    ) {
                        SearchStateMessage(
                            message = stringResource(R.string.no_results_found),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                is SearchDiscoveryScreenState.Error -> {
                    item(
                        key = "search_error",
                        contentType = "search_error",
                    ) {
                        SearchStateMessage(
                            message = stringResource(currentState.messageResId),
                            action = {
                                Button(onClick = viewModel::retry) {
                                    Text(stringResource(R.string.retry_button))
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                is SearchDiscoveryScreenState.Success -> {
                    when (selectedTab) {
                        SearchDiscoveryTab.EXPLORE -> {
                            item(
                                key = "search_explore_moods_title",
                                contentType = "section_title",
                            ) {
                                NavigationTitle(
                                    title = stringResource(R.string.mood_and_genres),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "search_explore_moods",
                                contentType = "mood_genres_grid",
                            ) {
                                SearchMoodAndGenresGrid(
                                    data = currentState.data,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        SearchDiscoveryTab.SUGGESTIONS -> {
                            item(
                                key = "search_suggestions_songs",
                                contentType = "suggestion_songs",
                            ) {
                                SuggestedSongsSection(
                                    songs = currentState.data.suggestedSongs,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                key = "search_suggestions_artists",
                                contentType = "suggestion_artists",
                            ) {
                                SuggestedArtistsSection(
                                    artists = currentState.data.suggestedArtists,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                key = "search_suggestions_albums",
                                contentType = "suggestion_albums",
                            ) {
                                TrendingAlbumsSection(
                                    albums = currentState.data.trendingAlbums,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
