package moe.rukamori.archivetune

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.navigationBuilder
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import moe.rukamori.archivetune.ui.screens.settings.SettingsScreen
import moe.rukamori.archivetune.ui.screens.settings.UpdateScreen
import moe.rukamori.archivetune.ui.state.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationHost(
    navController: NavHostController,
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
    hazeState: HazeState? = null,
    updateState: UpdateState,
    modifier: Modifier = Modifier,
    homeScrollConnection: NestedScrollConnection? = null,
    searchScrollConnection: NestedScrollConnection? = null,
    onClearUpdateBadge: () -> Unit = {},
    disableAnimations: Boolean = false,
    isTvDevice: Boolean = false,
    contentAreaFocusRequester: FocusRequester = remember { FocusRequester() },
    launchMusicRecognitionFromShortcut: Boolean = false,
    tabOpenedFromShortcut: NavigationTab? = null,
    defaultOpenTab: NavigationTab = NavigationTab.HOME,
    navigationItems: List<Screens> = remember(isTvDevice) {
        if (isTvDevice) Screens.TvMainScreens else Screens.MainScreens
    },
    @Suppress("UNUSED_PARAMETER") updateChannel: UpdateChannel = defaultUpdateChannel,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val (previousTab) = rememberSaveable { mutableStateOf("home") }
    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VARIABLE")
    var transitionDirection = AnimatedContentTransitionScope.SlideDirection.Left

    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
        if (navigationItems.fastAny { it.route == previousTab }) {
            val curIndex =
                navigationItems.indexOf(
                    navigationItems.fastFirstOrNull {
                        it.route == navBackStackEntry?.destination?.route
                    },
                )

            val prevIndex =
                navigationItems.indexOf(
                    navigationItems.fastFirstOrNull {
                        it.route == previousTab
                    },
                )

            if (prevIndex > curIndex) {
                AnimatedContentTransitionScope.SlideDirection.Right.also {
                    transitionDirection = it
                }
            }
        }
    }

    val topLevelScreens = remember(navigationItems) {
        navigationItems.map(Screens::route) + "settings"
    }

    NavHost(
        navController = navController,
        startDestination =
            if (launchMusicRecognitionFromShortcut) {
                MusicRecognitionRoute
            } else {
                when (tabOpenedFromShortcut ?: defaultOpenTab) {
                    NavigationTab.HOME -> Screens.Home.route
                    NavigationTab.LIBRARY -> Screens.Library.route
                    else -> Screens.Home.route
                }
            },
        enterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else if (initialState.destination.route in topLevelScreens &&
                targetState.destination.route in topLevelScreens
            ) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { it / 2 }
            }
        },
        exitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else if (initialState.destination.route in topLevelScreens &&
                targetState.destination.route in topLevelScreens
            ) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else if ((
                    initialState.destination.route in topLevelScreens ||
                        initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                ) &&
                targetState.destination.route in topLevelScreens
            ) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it }
            }
        },
        popExitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else if ((
                    initialState.destination.route in topLevelScreens ||
                        initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                ) &&
                targetState.destination.route in topLevelScreens
            ) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { it }
            }
        },
        modifier =
            modifier
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                .then(
                    if (isTvDevice) {
                        Modifier
                            .focusRequester(contentAreaFocusRequester)
                            .focusGroup()
                            .focusable()
                    } else {
                        Modifier
                    },
                ).nestedScroll(
                    topAppBarScrollBehavior.nestedScrollConnection,
                ),
    ) {
        if (BuildConfig.UPDATER_AVAILABLE) {
            composable(
                route = "settings/update?autostart={autostart}&download={download}",
                arguments = listOf(
                    navArgument("autostart") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("download") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val autostart = backStackEntry.arguments?.let { args ->
                    val raw = args.getString("autostart") ?: args.getString("download")
                    raw == "1" || raw.equals("true", ignoreCase = true)
                } ?: false

                UpdateScreen(
                    navController = navController,
                    onUpToDate = onClearUpdateBadge,
                    autostart = autostart,
                )
            }
        }

        composable("settings") {
            SettingsScreen(
                navController = navController,
                updateState = updateState,
                onClearUpdateBadge = onClearUpdateBadge,
            )
        }

        navigationBuilder(
            navController,
            topAppBarScrollBehavior,
            updateState,
            disableAnimations,
            onClearUpdateBadge = onClearUpdateBadge,
            homeScrollConnection = homeScrollConnection,
            searchScrollConnection = searchScrollConnection,
        )
    }
}
