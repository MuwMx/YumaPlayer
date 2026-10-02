package moe.rukamori.archivetune

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.valentinilk.shimmer.LocalShimmerTheme
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.home.effects.HomeBackgroundSettings
import moe.rukamori.archivetune.home.effects.LocalHomeBackgroundStyle
import moe.rukamori.archivetune.home.effects.ScreenBackground
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerTheme
import moe.rukamori.archivetune.ui.component.splash.SplashConfig
import moe.rukamori.archivetune.ui.component.splash.SplashOverlay
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.scaffold.ScaffoldNavigationRail
import moe.rukamori.archivetune.ui.scaffold.ScaffoldSearchBarHost
import moe.rukamori.archivetune.ui.scaffold.ScaffoldTopAppBar
import moe.rukamori.archivetune.ui.scaffold.rememberScaffoldAppState
import moe.rukamori.archivetune.ui.utils.LocalGlobalVisibility
import moe.rukamori.archivetune.utils.SyncUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScaffoldShell(
    activity: ComponentActivity, navController: NavHostController, database: MusicDatabase, downloadUtil: DownloadUtil,
    syncUtils: SyncUtils, playerConnection: PlayerConnection?, systemBarController: SystemBarController, playerViewModel: PlayerViewModel,
    bottomSheetPageState: BottomSheetPageState, menuState: MenuState, updateChannel: UpdateChannel, isHomeScreenVisible: Boolean,
    onExpansionFraction: (() -> Float) -> Unit, disableAnimations: Boolean, splashEnabled: Boolean, useDarkTheme: Boolean,
    pureBlack: Boolean, homeBackgroundStyle: HomeBackgroundStyle, homeBackgroundParallaxEnabled: Boolean, homeBackgroundParallaxStrength: Float,
    homeBackgroundBrightness: Float, contentAlpha: Float, contentVisible: Boolean, coldSplash: Boolean, onBurstStart: () -> Unit,
    splashDone: Boolean, onSplashDismiss: () -> Unit, pendingIntent: Intent?, onClearPendingIntent: () -> Unit,
    pendingBackupRestoreUri: Uri?, onClearPendingBackupRestoreUri: () -> Unit, aodModeLaunchRequestCount: Int,
    onResetAodLaunchRequestCount: () -> Unit, onHandleIntent: (Intent?, NavHostController) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface),
    ) {
        val appState = rememberScaffoldAppState(
            activity = activity, navController = navController, playerViewModel = playerViewModel,
            updateChannel = updateChannel, maxHeight = maxHeight, pendingIntent = pendingIntent,
            onClearPendingIntent = onClearPendingIntent, onHandleIntent = onHandleIntent,
        )

        CompositionLocalProvider(
            LocalYumaHaptics provides appState.yumaHaptics,
            LocalHapticFeedback provides appState.customHaptic,
            LocalAnimationsDisabled provides disableAnimations,
            LocalHomeBackgroundStyle provides HomeBackgroundSettings(homeBackgroundStyle, homeBackgroundParallaxEnabled, homeBackgroundParallaxStrength, homeBackgroundBrightness),
            LocalDatabase provides database,
            LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
            LocalPlayerConnection provides playerConnection,
            LocalPlayerAwareWindowInsets provides appState.playerAwareWindowInsets,
            LocalDownloadUtil provides downloadUtil,
            LocalShimmerTheme provides ShimmerTheme,
            LocalSyncUtils provides syncUtils,
            LocalBottomSheetPageState provides bottomSheetPageState,
            LocalMenuState provides menuState,
            LocalGlobalVisibility provides isHomeScreenVisible,
        ) {
            ScreenBackground(isVisible = LocalGlobalVisibility.current, modifier = Modifier.fillMaxSize())
            if (splashEnabled) {
                SplashOverlay(isDark = useDarkTheme, onBurstStart = onBurstStart, onDismiss = onSplashDismiss)
            }
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = (1f - contentAlpha) * SplashConfig.Reveal.RISE_DP.dp.toPx()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .pointerInput(contentVisible) {
                        if (!contentVisible) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            ) {
                ScaffoldNavigationRail(
                    visible = appState.useRail && appState.shouldShowNavigationBar, isTvDevice = appState.isTvDevice,
                    useRail = appState.useRail, active = appState.searchState.active, currentRoute = appState.currentRoute,
                    navigationItems = appState.navigationItems, topLevelScreens = appState.topLevelScreens,
                    navBackStackEntry = appState.navBackStackEntry, disableAnimations = disableAnimations,
                    pureBlack = pureBlack, playerBottomSheetState = appState.playerBottomSheetState,
                    tvRailFocusRequester = appState.searchState.tvRailFocusRequester,
                    searchBarFocusRequester = appState.searchState.searchBarFocusRequester,
                    contentAreaFocusRequester = appState.searchState.contentAreaFocusRequester,
                    onPrimaryNavigationClick = appState.handlePrimaryNavigationClick,
                )

                val scaffoldHazeState = rememberScaffoldHazeState(blurNavBar = appState.blurNavBar)
                val barScrollVisibility = rememberBarScrollVisibility()

                Box(
                    modifier = Modifier.fillMaxSize().nestedScroll(barScrollVisibility.nestedScrollConnection),
                ) {
                    Scaffold(
                        topBar = {
                            ScaffoldTopAppBar(
                                visible = appState.shouldShowTopBar, navController = navController,
                                navBackStackEntry = appState.navBackStackEntry, scrollController = appState.scrollController,
                                useRail = appState.useRail, pureBlack = pureBlack, splashDone = splashDone,
                                updateState = appState.updateState,
                            )
                            ScaffoldSearchBarHost(
                                searchState = appState.searchState, navController = navController,
                                navBackStackEntry = appState.navBackStackEntry, navigationItems = appState.navigationItems,
                                searchHazeState = scaffoldHazeState.effectiveSearchHazeState,
                                isMiniPlayerVisible = appState.isMiniPlayerVisible, disableAnimations = disableAnimations,
                                pureBlack = pureBlack, blurRadius = appState.blurRadius, modifier = Modifier.align(Alignment.TopCenter),
                            )
                        },
                        bottomBar = {},
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize(),
                    ) { _ ->
                        NavigationHost(
                            navController = navController, topAppBarScrollBehavior = appState.scrollController.topAppBarScrollBehavior,
                            hazeState = scaffoldHazeState.effectiveHazeState, updateState = appState.updateState,
                            modifier = Modifier.fillMaxSize().then(
                                if (scaffoldHazeState.effectiveSearchHazeState != null) Modifier.hazeSource(scaffoldHazeState.effectiveSearchHazeState) else Modifier,
                            ),
                            homeScrollConnection = appState.scrollController.homeScrollBehavior.nestedScrollConnection,
                            searchScrollConnection = appState.scrollController.searchScrollBehavior.nestedScrollConnection,
                            onClearUpdateBadge = { appState.updateViewModel.dismissUpdate() }, disableAnimations = disableAnimations,
                            isTvDevice = appState.isTvDevice, contentAreaFocusRequester = appState.searchState.contentAreaFocusRequester,
                            launchMusicRecognitionFromShortcut = appState.launchMusicRecognitionFromShortcut,
                            tabOpenedFromShortcut = appState.tabOpenedFromShortcut, defaultOpenTab = appState.defaultOpenTab,
                            navigationItems = appState.navigationItems, updateChannel = updateChannel,
                        )
                    }

                    PlayerOverlayHost(
                        modifier = Modifier.fillMaxSize(), navController = navController,
                        maxHeight = this@BoxWithConstraints.maxHeight, bottomInset = appState.bottomInset,
                        shouldShowNav = appState.shouldShowNavigationBar, isYearInMusic = appState.isYearInMusicScreen,
                        useRail = appState.useRail, hazeState = scaffoldHazeState.effectiveHazeState,
                        blurRadius = appState.blurRadius, pureBlack = pureBlack, playerViewModel = playerViewModel,
                        homeViewModel = appState.homeViewModel, playerConnection = playerConnection, database = database,
                        systemBarController = systemBarController, window = activity.window, sheetState = appState.playerBottomSheetState,
                        aodModeLaunchRequestCount = aodModeLaunchRequestCount, onResetAodLaunchRequestCount = onResetAodLaunchRequestCount,
                        useDarkTheme = useDarkTheme, shouldShowHomeShuffleButton = appState.shouldShowHomeShuffleButton,
                        navigationItems = appState.navigationItems, navBackStackEntry = appState.navBackStackEntry,
                        handlePrimaryNavigationClick = appState.handlePrimaryNavigationClick,
                        onSearchItemDoubleClick = {
                            appState.searchState.onSearchSourceChange(SearchSource.ONLINE)
                            appState.searchState.openSearch()
                        },
                        disableAnimations = disableAnimations, navVisibleHeight = appState.navVisibleHeight,
                        floatingToolbarBottomPadding = appState.floatingToolbarBottomPadding, onExpansionFraction = onExpansionFraction,
                        scrollVisibilityFactor = barScrollVisibility.scrollVisibilityFactor,
                    )

                    GlobalDialogsHost(
                        navController = navController, playerConnection = playerConnection, bottomSheetPageState = bottomSheetPageState,
                        menuState = menuState, networkBannerState = appState.networkBannerState, pendingBackupRestoreUri = pendingBackupRestoreUri,
                        onDismissBackupRestore = onClearPendingBackupRestoreUri, splashDone = splashDone, shouldShowTopBar = appState.shouldShowTopBar,
                        topInset = appState.topInset, updateChannel = updateChannel, coroutineScope = appState.coroutineScope,
                    )
                }
            }
        }
    }
}
