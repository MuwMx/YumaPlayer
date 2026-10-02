package moe.rukamori.archivetune.ui.scaffold

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ACTION_LIBRARY
import moe.rukamori.archivetune.ACTION_SEARCH
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.isTvDevice
import moe.rukamori.archivetune.musicrecognition.ACTION_MUSIC_RECOGNITION
import moe.rukamori.archivetune.network.NetworkBannerUiState
import moe.rukamori.archivetune.rememberPlayerAwareWindowInsets
import moe.rukamori.archivetune.rememberPlayerBottomSheetState
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.haptics.YumaHapticsImpl
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.YdsInsets
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.viewmodels.NetworkBannerViewModel
import moe.rukamori.archivetune.viewmodels.UpdateViewModel

@Stable
class ScaffoldAppState(
    val topInset: Dp,
    val bottomInset: Dp,
    val isTvDevice: Boolean,
    val useRail: Boolean,
    val updateViewModel: UpdateViewModel,
    val updateState: UpdateState,
    val coroutineScope: CoroutineScope,
    val homeViewModel: HomeViewModel,
    val networkBannerState: NetworkBannerUiState,
    val navBackStackEntry: NavBackStackEntry?,
    val currentRoute: String?,
    val isYearInMusicScreen: Boolean,
    val navigationItems: List<Screens>,
    val defaultOpenTab: NavigationTab,
    val blurNavBar: Boolean,
    val blurRadius: Float,
    val tabOpenedFromShortcut: NavigationTab?,
    val launchMusicRecognitionFromShortcut: Boolean,
    val topLevelScreens: List<String>,
    val searchState: ScaffoldSearchState,
    val shouldShowNavigationBar: Boolean,
    val shouldShowTopBar: Boolean,
    val shouldShowHomeShuffleButton: Boolean,
    val floatingToolbarBottomPadding: Dp,
    val navVisibleHeight: Dp,
    val playerBottomSheetState: BottomSheetState,
    val isMiniPlayerVisible: Boolean,
    val playerAwareWindowInsets: WindowInsets,
    val scrollController: ScaffoldScrollController,
    val handlePrimaryNavigationClick: (Screens, Boolean) -> Unit,
    val yumaHaptics: YumaHaptics,
    val customHaptic: HapticFeedback,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberScaffoldAppState(
    activity: ComponentActivity,
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    updateChannel: UpdateChannel,
    maxHeight: Dp,
    pendingIntent: Intent?,
    onClearPendingIntent: () -> Unit,
    onHandleIntent: (Intent?, NavHostController) -> Unit,
): ScaffoldAppState {
    val density = LocalDensity.current
    val windowsInsets = WindowInsets.systemBars
    val topInset = with(density) { windowsInsets.getTop(density).toDp() }
    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
    val isTvDevice = remember { activity.applicationContext.isTvDevice() }
    val useRail = isTvDevice || currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val updateViewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(updateChannel) { updateViewModel.forceCheck(updateChannel) }
    val updateState by updateViewModel.updateState.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val networkBannerViewModel: NetworkBannerViewModel = hiltViewModel()
    val networkBannerState by networkBannerViewModel.bannerState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isYearInMusicScreen = currentRoute?.startsWith("year_in_music") == true

    val navigationItems = remember(isTvDevice) { if (isTvDevice) Screens.TvMainScreens else Screens.MainScreens }
    val defaultOpenTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.HOME)
    val blurNavBar by rememberPreference(BlurNavBarKey, defaultValue = true)
    val blurRadius by rememberPreference(BlurRadiusKey, defaultValue = SettingsDimensions.BlurRadiusDefault)
    val tabOpenedFromShortcut = remember {
        when (activity.intent?.action) {
            ACTION_LIBRARY -> NavigationTab.LIBRARY
            ACTION_SEARCH -> NavigationTab.SEARCH
            else -> null
        }
    }
    val launchMusicRecognitionFromShortcut = remember { activity.intent?.action == ACTION_MUSIC_RECOGNITION }
    val topLevelScreens = remember(navigationItems) { navigationItems.map(Screens::route) + "settings" }

    val searchState = rememberScaffoldSearchState(
        activity = activity, navController = navController, navBackStackEntry = navBackStackEntry,
        navigationItems = navigationItems, topLevelScreens = topLevelScreens,
        playerViewModel = playerViewModel, focusManager = LocalFocusManager.current,
    )

    val shouldShowNavigationBar = remember(navBackStackEntry, searchState.active) {
        navBackStackEntry?.destination?.route == null ||
            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } && !searchState.active
    }
    val shouldShowTopBar = !searchState.active && currentRoute in topLevelScreens && currentRoute != "settings"

    val allLocalItems by homeViewModel.allLocalItems.collectAsStateWithLifecycle()
    val allYtItems by homeViewModel.allYtItems.collectAsStateWithLifecycle()
    val shouldShowHomeShuffleButton = currentRoute == Screens.Home.route && (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty())

    val floatingToolbarBottomPadding = YdsInsets.floatingToolbarBottomPadding()
    val navVisibleHeight = FloatingToolbarHeight

    val playerBottomSheetState = rememberPlayerBottomSheetState(
        maxHeight = maxHeight, bottomInset = bottomInset, shouldShowNav = shouldShowNavigationBar, useRail = useRail,
    )

    val isMiniPlayerVisible by remember(playerViewModel) {
        playerViewModel.uiState.map { it.trackUrl.isNotEmpty() }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val playerAwareWindowInsets = rememberPlayerAwareWindowInsets(
        useRail = useRail, bottomInset = bottomInset, shouldShowNavigationBar = shouldShowNavigationBar,
        isMiniPlayerVisible = isMiniPlayerVisible, floatingToolbarBottomPadding = floatingToolbarBottomPadding, windowsInsets = windowsInsets,
    )

    val scrollController = rememberScaffoldScrollController(
        navBackStackEntry = navBackStackEntry, playerBottomSheetState = playerBottomSheetState,
        topLevelScreens = topLevelScreens, active = searchState.active,
    )

    val handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { screen, isSelected ->
        if (isSelected) {
            if (screen == Screens.Search) {
                searchState.openSearch()
                coroutineScope.launch { scrollController.resetSearchOffset() }
            } else {
                navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                if (screen == Screens.Home) coroutineScope.launch { scrollController.resetHomeOffset() }
            }
        } else if (navController.currentDestination?.route != screen.route) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route
        if ((route?.startsWith("artist/") == true || route?.startsWith("album/") == true) && playerBottomSheetState.isExpanded) {
            playerBottomSheetState.collapseSoft()
        }
    }

    LaunchedEffect(Unit) {
        if (pendingIntent != null) {
            onHandleIntent(pendingIntent, navController)
            onClearPendingIntent()
        } else {
            onHandleIntent(activity.intent, navController)
        }
    }

    val haptic = LocalHapticFeedback.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val hapticView = LocalView.current
    val yumaHaptics = remember(enableHapticFeedback, hapticView) { YumaHapticsImpl(hapticView, enableHapticFeedback) }
    val customHaptic = remember(haptic, enableHapticFeedback) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enableHapticFeedback) haptic.performHapticFeedback(hapticFeedbackType)
            }
        }
    }

    return remember(
        topInset, bottomInset, isTvDevice, useRail, updateViewModel, updateState, coroutineScope, homeViewModel,
        networkBannerState, navBackStackEntry, currentRoute, isYearInMusicScreen, navigationItems, defaultOpenTab,
        blurNavBar, blurRadius, tabOpenedFromShortcut, launchMusicRecognitionFromShortcut, topLevelScreens,
        searchState, shouldShowNavigationBar, shouldShowTopBar, shouldShowHomeShuffleButton, floatingToolbarBottomPadding,
        navVisibleHeight, playerBottomSheetState, isMiniPlayerVisible, playerAwareWindowInsets, scrollController,
        yumaHaptics, customHaptic,
    ) {
        ScaffoldAppState(
            topInset = topInset, bottomInset = bottomInset, isTvDevice = isTvDevice, useRail = useRail,
            updateViewModel = updateViewModel, updateState = updateState, coroutineScope = coroutineScope,
            homeViewModel = homeViewModel, networkBannerState = networkBannerState, navBackStackEntry = navBackStackEntry,
            currentRoute = currentRoute, isYearInMusicScreen = isYearInMusicScreen, navigationItems = navigationItems,
            defaultOpenTab = defaultOpenTab, blurNavBar = blurNavBar, blurRadius = blurRadius,
            tabOpenedFromShortcut = tabOpenedFromShortcut, launchMusicRecognitionFromShortcut = launchMusicRecognitionFromShortcut,
            topLevelScreens = topLevelScreens, searchState = searchState, shouldShowNavigationBar = shouldShowNavigationBar,
            shouldShowTopBar = shouldShowTopBar, shouldShowHomeShuffleButton = shouldShowHomeShuffleButton,
            floatingToolbarBottomPadding = floatingToolbarBottomPadding, navVisibleHeight = navVisibleHeight,
            playerBottomSheetState = playerBottomSheetState, isMiniPlayerVisible = isMiniPlayerVisible,
            playerAwareWindowInsets = playerAwareWindowInsets, scrollController = scrollController,
            handlePrimaryNavigationClick = handlePrimaryNavigationClick, yumaHaptics = yumaHaptics, customHaptic = customHaptic,
        )
    }
}
