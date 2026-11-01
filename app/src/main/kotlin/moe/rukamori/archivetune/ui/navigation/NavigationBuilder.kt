/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.UseSpotifyHomeKey
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.account.AccountScreen
import moe.rukamori.archivetune.ui.screens.browse.BrowseScreen
import moe.rukamori.archivetune.ui.screens.charts.ChartsScreen
import moe.rukamori.archivetune.ui.screens.history.HistoryScreen
import moe.rukamori.archivetune.ui.screens.home.HomeScreen
import moe.rukamori.archivetune.ui.screens.library.LibraryScreen
import moe.rukamori.archivetune.ui.screens.library.LocalSongScreen
import moe.rukamori.archivetune.ui.screens.moodandgenres.MoodAndGenresScreen
import moe.rukamori.archivetune.ui.screens.musicrecognition.MusicRecognitionScreen
import moe.rukamori.archivetune.ui.screens.newrelease.NewReleaseScreen
import moe.rukamori.archivetune.ui.screens.news.NewsScreen
import moe.rukamori.archivetune.ui.screens.news.ViewNewsScreen
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResult
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoute
import moe.rukamori.archivetune.ui.screens.search.SearchScreen
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyHomeScreen
import moe.rukamori.archivetune.ui.screens.stats.route.StatsScreen
import moe.rukamori.archivetune.ui.screens.yearinmusic.YearInMusicScreen
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.utils.rememberPreference


@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    updateState: UpdateState,
    disableAnimations: Boolean = false,
    homeScrollConnection: NestedScrollConnection? = null,
    searchScrollConnection: NestedScrollConnection? = null,
) {
    composable(Screens.Home.route) {
        val useSpotify by rememberPreference(UseSpotifyHomeKey, defaultValue = false)
        val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
        if (useSpotify && spDc.isNotBlank()) {
            SpotifyHomeScreen(navController, headerScrollConnection = homeScrollConnection)
        } else {
            HomeScreen(navController, headerScrollConnection = homeScrollConnection)
        }
    }

    composable(Screens.Library.route) {
        LibraryScreen(navController, scrollBehavior = scrollBehavior)
    }
    composable(Screens.Search.route) {
        SearchScreen(
            navController = navController,
            onSearchClick = {
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.set("openSearch", true)
            },
            headerScrollConnection = searchScrollConnection,
        )
    }
    composable("local_songs") {
        LocalSongScreen(navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable("news") {
        NewsScreen(navController)
    }
    composable(
        route = "view_news/{newsId}",
        arguments =
            listOf(
                navArgument("newsId") { type = NavType.StringType },
            ),
    ) {
        ViewNewsScreen(navController)
    }
    composable(
        route = "year_in_music?year={year}",
        arguments =
            listOf(
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
    ) { backStackEntry ->
        val selectedYear = backStackEntry.arguments?.getInt("year")?.takeIf { it > 0 }
        YearInMusicScreen(
            navController = navController,
            initialYear = selectedYear,
        )
    }
    composable(MusicRecognitionRoute) {
        MusicRecognitionScreen(navController)
    }
    composable(Screens.MoodAndGenres.route) {
        MoodAndGenresScreen(navController)
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("charts_screen") {
        ChartsScreen(navController)
    }
    composable(
        route = "browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId"),
        )
    }
    composable(
        route = OnlineSearchResultRoute,
        arguments =
            listOf(
                navArgument(OnlineSearchResultArgument) {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlineSearchResult(navController)
    }

    mediaNavigationRoutes(navController, scrollBehavior)
    settingsNavigationRoutes(navController, updateState)
}
