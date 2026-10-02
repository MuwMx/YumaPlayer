package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.resetHeightOffset

@OptIn(ExperimentalMaterial3Api::class)
@Stable
class ScaffoldScrollController(
    val homeScrollBehavior: TopAppBarScrollBehavior,
    val searchScrollBehavior: TopAppBarScrollBehavior,
    val topAppBarScrollBehavior: TopAppBarScrollBehavior,
) {
    fun currentScrollBehavior(route: String?): TopAppBarScrollBehavior =
        when (route) {
            Screens.Home.route -> homeScrollBehavior
            Screens.Search.route -> searchScrollBehavior
            else -> topAppBarScrollBehavior
        }

    suspend fun resetHomeOffset() {
        homeScrollBehavior.state.resetHeightOffset()
    }

    suspend fun resetSearchOffset() {
        searchScrollBehavior.state.resetHeightOffset()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberScaffoldScrollController(
    navBackStackEntry: NavBackStackEntry?,
    playerBottomSheetState: BottomSheetState,
    topLevelScreens: List<String>,
    active: Boolean = false,
): ScaffoldScrollController {
    val homeScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )
    val searchScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )
    val topAppBarScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )

    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screens.Home.route -> {
                homeScrollBehavior.state.resetHeightOffset()
            }

            Screens.Search.route -> {
                searchScrollBehavior.state.resetHeightOffset()
            }

            else -> {}
        }
    }

    LaunchedEffect(navBackStackEntry) {
        val currentEntryRoute = navBackStackEntry?.destination?.route

        val isEnteringSubScreen =
            currentEntryRoute != null &&
                currentEntryRoute !in topLevelScreens &&
                currentEntryRoute.startsWith(OnlineSearchResultRoutePrefix) != true
        if (isEnteringSubScreen) {
            topAppBarScrollBehavior.state.heightOffset = 0f
            topAppBarScrollBehavior.state.contentOffset = 0f
        }
    }

    LaunchedEffect(active) {
        if (active) {
            when (currentRoute) {
                Screens.Home.route -> {
                    homeScrollBehavior.state.resetHeightOffset()
                }

                Screens.Search.route -> {
                    searchScrollBehavior.state.resetHeightOffset()
                }

                else -> {}
            }
        }
    }

    return remember(homeScrollBehavior, searchScrollBehavior, topAppBarScrollBehavior) {
        ScaffoldScrollController(
            homeScrollBehavior = homeScrollBehavior,
            searchScrollBehavior = searchScrollBehavior,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
        )
    }
}
