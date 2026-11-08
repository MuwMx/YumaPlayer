package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalAnimationsDisabled

@Composable
internal fun ScaffoldNavigationRailChrome(
    appState: ScaffoldAppState,
    modifier: Modifier = Modifier,
    disableAnimations: Boolean = LocalAnimationsDisabled.current,
    pureBlack: Boolean = false,
) {
    ScaffoldNavigationRail(
        visible = appState.useRail && appState.shouldShowNavigationBar,
        isTvDevice = appState.isTvDevice,
        useRail = appState.useRail,
        active = appState.searchState.active,
        currentRoute = appState.currentRoute,
        navigationItems = appState.navigationItems,
        topLevelScreens = appState.topLevelScreens,
        navBackStackEntry = appState.navBackStackEntry,
        disableAnimations = disableAnimations,
        pureBlack = pureBlack,
        playerBottomSheetState = appState.playerBottomSheetState,
        tvRailFocusRequester = appState.searchState.tvRailFocusRequester,
        searchBarFocusRequester = appState.searchState.searchBarFocusRequester,
        contentAreaFocusRequester = appState.searchState.contentAreaFocusRequester,
        onPrimaryNavigationClick = appState.handlePrimaryNavigationClick,
        modifier = modifier,
    )
}

@Composable
internal fun ScaffoldTopBarChrome(
    appState: ScaffoldAppState,
    navController: NavHostController,
    scaffoldHazeState: ScaffoldHazeState,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    splashDone: Boolean = true,
    disableAnimations: Boolean = LocalAnimationsDisabled.current,
) {
    val accountName by appState.homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by appState.homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountAccentColor by appState.homeViewModel.accountAccentColor.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxWidth()) {
        ScaffoldTopAppBar(
            visible = appState.shouldShowTopBar,
            navController = navController,
            navBackStackEntry = appState.navBackStackEntry,
            scrollController = appState.scrollController,
            useRail = appState.useRail,
            pureBlack = pureBlack,
            splashDone = splashDone,
            updateState = appState.updateState,
            avatarUrl = accountImageUrl,
            accountName = accountName,
            accentColor = accountAccentColor,
            hazeState = scaffoldHazeState.effectiveHazeState,
            blurRadius = appState.blurRadius,
            blurNavBar = appState.blurNavBar,
        )
        ScaffoldSearchBarHost(
            searchState = appState.searchState,
            navController = navController,
            navBackStackEntry = appState.navBackStackEntry,
            navigationItems = appState.navigationItems,
            searchHazeState = scaffoldHazeState.effectiveSearchHazeState,
            isMiniPlayerVisible = appState.isMiniPlayerVisible,
            disableAnimations = disableAnimations,
            pureBlack = pureBlack,
            blurRadius = appState.blurRadius,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun ScaffoldChrome(
    appState: ScaffoldAppState,
    navController: NavHostController,
    scaffoldHazeState: ScaffoldHazeState,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    splashDone: Boolean = true,
    disableAnimations: Boolean = LocalAnimationsDisabled.current,
    content: @Composable () -> Unit,
) {
    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        topBar = {
            ScaffoldTopBarChrome(
                appState = appState,
                navController = navController,
                scaffoldHazeState = scaffoldHazeState,
                pureBlack = pureBlack,
                splashDone = splashDone,
                disableAnimations = disableAnimations,
            )
        },
        bottomBar = {},
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
    ) { _ ->
        content()
    }
}
