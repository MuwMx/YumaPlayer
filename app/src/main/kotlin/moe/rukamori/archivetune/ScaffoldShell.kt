package moe.rukamori.archivetune

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowSizeClass
import com.valentinilk.shimmer.LocalShimmerTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.PauseSearchHistoryKey
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.constants.SearchSourceKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.home.effects.HomeBackgroundSettings
import moe.rukamori.archivetune.home.effects.LocalHomeBackgroundStyle
import moe.rukamori.archivetune.home.effects.ScreenBackground
import moe.rukamori.archivetune.musicrecognition.ACTION_MUSIC_RECOGNITION
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.TopSearch
import moe.rukamori.archivetune.ui.component.TvNavigationRail
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerTheme
import moe.rukamori.archivetune.ui.component.splash.SplashConfig
import moe.rukamori.archivetune.ui.component.splash.SplashOverlay
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.haptics.YumaHapticsImpl
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.LocalSearchScreen
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchScreen
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.YdsInsets
import moe.rukamori.archivetune.ui.utils.LocalGlobalVisibility
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.resetHeightOffset
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.viewmodels.NetworkBannerViewModel
import moe.rukamori.archivetune.viewmodels.NewsViewModel
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel
import moe.rukamori.archivetune.viewmodels.UpdateViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScaffoldShell(
    activity: ComponentActivity,
    navController: NavHostController,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
    playerConnection: PlayerConnection?,
    systemBarController: SystemBarController,
    playerViewModel: PlayerViewModel,
    bottomSheetPageState: BottomSheetPageState,
    menuState: MenuState,
    updateChannel: UpdateChannel,
    isHomeScreenVisible: Boolean,
    onExpansionFraction: (() -> Float) -> Unit,
    disableAnimations: Boolean,
    splashEnabled: Boolean,
    useDarkTheme: Boolean,
    pureBlack: Boolean,
    homeBackgroundStyle: HomeBackgroundStyle,
    homeBackgroundParallaxEnabled: Boolean,
    homeBackgroundParallaxStrength: Float,
    homeBackgroundBrightness: Float,
    contentAlpha: Float,
    contentVisible: Boolean,
    coldSplash: Boolean,
    onBurstStart: () -> Unit,
    splashDone: Boolean,
    onSplashDismiss: () -> Unit,
    pendingIntent: Intent?,
    onClearPendingIntent: () -> Unit,
    pendingBackupRestoreUri: Uri?,
    onClearPendingBackupRestoreUri: () -> Unit,
    aodModeLaunchRequestCount: Int,
    onResetAodLaunchRequestCount: () -> Unit,
    onHandleIntent: (Intent?, NavHostController) -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                ),
    ) {
        val focusManager = LocalFocusManager.current
        val density = LocalDensity.current
        val windowsInsets = WindowInsets.systemBars
        val topInset = with(density) { windowsInsets.getTop(density).toDp() }
        val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }

        val isTvDevice = remember { activity.applicationContext.isTvDevice() }
        val useRail =
            isTvDevice ||
                currentWindowAdaptiveInfo()
                    .windowSizeClass
                    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

        val updateViewModel: UpdateViewModel = hiltViewModel()
        LaunchedEffect(updateChannel) {
            updateViewModel.forceCheck(updateChannel)
        }
        val updateState by updateViewModel.updateState.collectAsStateWithLifecycle()

        val coroutineScope = rememberCoroutineScope()
        val homeViewModel: HomeViewModel = hiltViewModel()
        val networkBannerViewModel: NetworkBannerViewModel = hiltViewModel()
        val newsViewModel: NewsViewModel = hiltViewModel()
        val networkBannerState by networkBannerViewModel.bannerState.collectAsStateWithLifecycle()
        val hasUnreadNews by newsViewModel.hasUnreadNews.collectAsStateWithLifecycle()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val onlineSearchViewModel: OnlineSearchViewModel? =
            if (currentRoute?.startsWith(OnlineSearchResultRoutePrefix) == true && navBackStackEntry != null) {
                hiltViewModel(navBackStackEntry!!)
            } else {
                null
            }
        val onlineSearchSort by
            onlineSearchViewModel
                ?.sort
                ?.collectAsStateWithLifecycle()
                ?: remember { mutableStateOf(OnlineSearchSort.DEFAULT) }
        val isYearInMusicScreen = currentRoute?.startsWith("year_in_music") == true

        val navigationItems =
            remember(isTvDevice) {
                if (isTvDevice) Screens.TvMainScreens else Screens.MainScreens
            }
        val defaultOpenTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.HOME)
        val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
        val blurNavBar by rememberPreference(BlurNavBarKey, defaultValue = true)
        val blurRadius by rememberPreference(BlurRadiusKey, defaultValue = SettingsDimensions.BlurRadiusDefault)
        val tabOpenedFromShortcut =
            remember {
                when (activity.intent?.action) {
                    ACTION_LIBRARY -> NavigationTab.LIBRARY
                    ACTION_SEARCH -> NavigationTab.SEARCH
                    else -> null
                }
            }
        val launchMusicRecognitionFromShortcut =
            remember {
                activity.intent?.action == ACTION_MUSIC_RECOGNITION
            }

        val topLevelScreens =
            remember(navigationItems) {
                navigationItems.map(Screens::route) + "settings"
            }

        val (query, onQueryChange) =
            rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue())
            }

        var active by rememberSaveable {
            mutableStateOf(false)
        }

        val onActiveChange: (Boolean) -> Unit = { newActive ->
            active = newActive
            if (!newActive) {
                focusManager.clearFocus()
                if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                    onQueryChange(TextFieldValue())
                }
            }
        }

        var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

        val searchBarFocusRequester = remember { FocusRequester() }
        val tvRailFocusRequester = remember { FocusRequester() }
        val contentAreaFocusRequester = remember { FocusRequester() }

        val openSearch: () -> Unit = {
            onActiveChange(true)
            searchBarFocusRequester.requestFocus()
        }

        val onSearch: (String) -> Unit = { queryText ->
            if (queryText.isNotEmpty()) {
                onActiveChange(false)
                navController.navigate(onlineSearchResultRoute(queryText))
                playerViewModel.addSearchHistory(queryText)
            }
        }

        val voiceSearchLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val spokenText =
                        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        onQueryChange(TextFieldValue(spokenText, TextRange(spokenText.length)))
                        onSearch(spokenText)
                    }
                }
            }

        var openSearchImmediately: Boolean by remember {
            mutableStateOf(activity.intent?.action == ACTION_SEARCH)
        }

        val shouldShowSearchBar =
            remember(active, navBackStackEntry) {
                active ||
                    navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                    navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
            }

        val shouldShowNavigationBar =
            remember(navBackStackEntry, active) {
                navBackStackEntry?.destination?.route == null ||
                    navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } &&
                    !active
            }

        val allLocalItems by homeViewModel.allLocalItems.collectAsStateWithLifecycle()
        val allYtItems by homeViewModel.allYtItems.collectAsStateWithLifecycle()
        val shouldShowHomeShuffleButton =
            currentRoute == Screens.Home.route &&
                (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty())

        val floatingToolbarBottomPadding = YdsInsets.floatingToolbarBottomPadding()
        val navVisibleHeight = FloatingToolbarHeight

        val playerBottomSheetState =
            rememberPlayerBottomSheetState(
                maxHeight = maxHeight,
                bottomInset = bottomInset,
                shouldShowNav = shouldShowNavigationBar,
                useRail = useRail,
            )

        val isMiniPlayerVisible by remember(playerViewModel) {
            playerViewModel.uiState
                .map { it.trackUrl.isNotEmpty() }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = false)

        val playerAwareWindowInsets =
            rememberPlayerAwareWindowInsets(
                useRail = useRail,
                bottomInset = bottomInset,
                shouldShowNavigationBar = shouldShowNavigationBar,
                isMiniPlayerVisible = isMiniPlayerVisible,
                floatingToolbarBottomPadding = floatingToolbarBottomPadding,
                windowsInsets = windowsInsets,
            )

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

        val handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { screen, isSelected ->
            if (isSelected) {
                if (screen == Screens.Search) {
                    openSearch()
                    coroutineScope.launch { searchScrollBehavior.state.resetHeightOffset() }
                } else {
                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                    when (screen) {
                        Screens.Home -> {
                            coroutineScope.launch { homeScrollBehavior.state.resetHeightOffset() }
                        }

                        else -> {}
                    }
                }
            } else {
                if (navController.currentDestination?.route != screen.route) {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }

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

        var previousRoute by rememberSaveable { mutableStateOf<String?>(null) }

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

            previousRoute = currentEntryRoute

            if ((
                    currentEntryRoute?.startsWith("artist/") == true ||
                        currentEntryRoute?.startsWith("album/") == true
                ) &&
                playerBottomSheetState.isExpanded
            ) {
                playerBottomSheetState.collapseSoft()
            }

            if (navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                val searchQuery =
                    decodeOnlineSearchQuery(
                        navBackStackEntry
                            ?.arguments
                            ?.getString(OnlineSearchResultArgument)
                            .orEmpty(),
                    )
                onQueryChange(
                    TextFieldValue(
                        searchQuery,
                        TextRange(searchQuery.length),
                    ),
                )
            } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                navBackStackEntry?.destination?.route in topLevelScreens
            ) {
                onQueryChange(TextFieldValue())
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
                searchBarFocusRequester.requestFocus()
            }
        }

        LaunchedEffect(isTvDevice, useRail, active, currentRoute, shouldShowNavigationBar) {
            if (
                isTvDevice &&
                useRail &&
                shouldShowNavigationBar &&
                !active &&
                currentRoute in topLevelScreens
            ) {
                delay(100)
                tvRailFocusRequester.requestFocus()
            }
        }

        var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(navBackStackEntry) {
            shouldShowTopBar =
                !active && navBackStackEntry?.destination?.route in topLevelScreens &&
                navBackStackEntry?.destination?.route != "settings"
        }

        LaunchedEffect(Unit) {
            if (pendingIntent != null) {
                onHandleIntent(pendingIntent, navController)
                onClearPendingIntent()
            } else {
                onHandleIntent(activity.intent, navController)
            }
        }

        val currentTitleRes =
            remember(navBackStackEntry) {
                when (navBackStackEntry?.destination?.route) {
                    Screens.Home.route -> R.string.home
                    Screens.Search.route -> R.string.search
                    Screens.Library.route -> R.string.filter_library
                    else -> null
                }
            }
        val haptic = LocalHapticFeedback.current
        val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
        val hapticView = LocalView.current
        val yumaHaptics = remember(enableHapticFeedback, hapticView) { YumaHapticsImpl(hapticView, enableHapticFeedback) }
        val customHaptic =
            remember(haptic, enableHapticFeedback) {
                object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        if (enableHapticFeedback) {
                            haptic.performHapticFeedback(hapticFeedbackType)
                        }
                    }
                }
            }

        CompositionLocalProvider(
            LocalYumaHaptics provides yumaHaptics,
            LocalHapticFeedback provides customHaptic,
            LocalAnimationsDisabled provides disableAnimations,
            LocalHomeBackgroundStyle provides
                HomeBackgroundSettings(
                    style = homeBackgroundStyle,
                    parallaxEnabled = homeBackgroundParallaxEnabled,
                    parallaxSensitivity = homeBackgroundParallaxStrength,
                    brightness = homeBackgroundBrightness,
                ),
            LocalDatabase provides database,
            LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
            LocalPlayerConnection provides playerConnection,
            LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
            LocalDownloadUtil provides downloadUtil,
            LocalShimmerTheme provides ShimmerTheme,
            LocalSyncUtils provides syncUtils,
            LocalBottomSheetPageState provides bottomSheetPageState,
            LocalMenuState provides menuState,
            LocalGlobalVisibility provides isHomeScreenVisible,
        ) {
            ScreenBackground(
                isVisible = LocalGlobalVisibility.current,
                modifier = Modifier.fillMaxSize(),
            )
            if (splashEnabled) {
                SplashOverlay(
                    isDark = useDarkTheme,
                    onBurstStart = onBurstStart,
                    onDismiss = onSplashDismiss,
                )
            }
            Row(
                modifier =
                    Modifier
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = (1f - contentAlpha) * SplashConfig.Reveal.RISE_DP.dp.toPx()
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .pointerInput(contentVisible) {
                            if (!contentVisible) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                        },
            ) {
                AnimatedVisibility(
                    visible = useRail && shouldShowNavigationBar,
                    enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 150)),
                    exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 100)),
                ) {
                    if (isTvDevice) {
                        TvNavigationRail(
                            items = navigationItems,
                            selectedItemRoute =
                                if (active) {
                                    Screens.Search.route
                                } else {
                                    currentRoute
                                },
                            modifier = Modifier,
                            firstItemFocusRequester = tvRailFocusRequester,
                            contentFocusRequester =
                                if (active ||
                                    navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                                ) {
                                    searchBarFocusRequester
                                } else {
                                    contentAreaFocusRequester
                                },
                            onItemClick = { screen ->
                                val wasPlayerActive = playerBottomSheetState.isExpanded
                                if (wasPlayerActive) {
                                    playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                }
                                val isSelected =
                                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                if (wasPlayerActive && isSelected) {
                                    return@TvNavigationRail
                                }
                                handlePrimaryNavigationClick(screen, isSelected)
                            },
                        )
                    } else {
                        NavigationRail(
                            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            header = { Spacer(Modifier.height(24.dp)) },
                        ) {
                            navigationItems.fastForEach { screen ->
                                val isSelected =
                                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                                NavigationRailItem(
                                    selected = isSelected,
                                    icon = {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    id = if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                ),
                                            contentDescription = null,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(screen.titleId),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        val wasPlayerActive = playerBottomSheetState.isExpanded

                                        if (wasPlayerActive) {
                                            playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                        }

                                        if (wasPlayerActive && isSelected) return@NavigationRailItem
                                        handlePrimaryNavigationClick(screen, isSelected)
                                    },
                                )
                            }
                        }
                    }
                }

                val hazeState = remember { HazeState() }
                val effectiveHazeState = if (blurNavBar) hazeState else null
                val searchHazeState = remember { HazeState() }
                val effectiveSearchHazeState = if (blurNavBar) searchHazeState else null

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                    topBar = {
                        if (shouldShowTopBar) {
                            val shouldUseFloatingTopBar =
                                remember(navBackStackEntry) {
                                    navBackStackEntry?.destination?.route == Screens.Home.route ||
                                        navBackStackEntry?.destination?.route == Screens.Search.route ||
                                        navBackStackEntry?.destination?.route == Screens.Library.route
                                }
                            val shouldShowBlurBackground =
                                remember(navBackStackEntry) {
                                    shouldUseFloatingTopBar
                                }

                            val surfaceColor = MaterialTheme.colorScheme.surface
                            val currentScrollBehavior =
                                when (navBackStackEntry?.destination?.route) {
                                    Screens.Home.route -> homeScrollBehavior

                                    Screens.Search.route -> searchScrollBehavior

                                    else -> topAppBarScrollBehavior
                                }
                            val isLibraryRoute = navBackStackEntry?.destination?.route == Screens.Library.route

                            var headerHeightPx by remember { mutableStateOf(0) }
                            LaunchedEffect(currentScrollBehavior, headerHeightPx) {
                                if (headerHeightPx > 0 && !isLibraryRoute) {
                                    val limit = -headerHeightPx.toFloat()
                                    val state = currentScrollBehavior.state
                                    if (state.heightOffsetLimit != limit) {
                                        state.heightOffsetLimit = limit
                                        state.heightOffset = state.heightOffset.coerceIn(limit, 0f)
                                    }
                                }
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .onSizeChanged { size ->
                                            if (size.height > 0) headerHeightPx = size.height
                                        }.offset {
                                            IntOffset(
                                                x = 0,
                                                y =
                                                    if (isLibraryRoute) {
                                                        0
                                                    } else {
                                                        currentScrollBehavior.state.heightOffset
                                                            .roundToInt()
                                                    },
                                            )
                                        },
                            ) {
                                if (shouldShowBlurBackground) {
                                    val appBarHeightPx = with(LocalDensity.current) { AppBarHeight.toPx() }
                                    Box(
                                        modifier =
                                            Modifier
                                                .offset {
                                                    if (isLibraryRoute) {
                                                        IntOffset(x = 0, y = 0)
                                                    } else {
                                                        val raw = currentScrollBehavior.state.heightOffset
                                                        val clamped = raw.coerceAtLeast(-appBarHeightPx)
                                                        IntOffset(x = 0, y = (clamped - raw).roundToInt())
                                                    }
                                                }.fillMaxWidth()
                                                .height(
                                                    AppBarHeight +
                                                        with(LocalDensity.current) {
                                                            WindowInsets.systemBars.getTop(LocalDensity.current).toDp()
                                                        },
                                                ).background(
                                                    Brush.verticalGradient(
                                                        colors =
                                                            listOf(
                                                                surfaceColor.copy(alpha = 0.95f),
                                                                surfaceColor.copy(alpha = 0.85f),
                                                                surfaceColor.copy(alpha = 0.6f),
                                                                Color.Transparent,
                                                            ),
                                                    ),
                                                ),
                                    )
                                }

                                TopAppBar(
                                    windowInsets =
                                        WindowInsets.safeDrawing.only(
                                            (
                                                if (useRail) {
                                                    WindowInsetsSides.Right
                                                } else {
                                                    WindowInsetsSides.Horizontal
                                                }
                                            ) + WindowInsetsSides.Top,
                                        ),
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.about_appbar),
                                                contentDescription = null,
                                                modifier =
                                                    Modifier
                                                        .size(35.dp)
                                                        .padding(end = 3.dp),
                                            )
                                            Text(
                                                text = stringResource(R.string.app_name),
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { navController.navigate("history") },
                                            onLongClick = {},
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.history),
                                                contentDescription = stringResource(R.string.history),
                                            )
                                        }
                                        TooltipBox(
                                            positionProvider =
                                                if (hasUnreadNews) {
                                                    TooltipDefaults.rememberRichTooltipPositionProvider()
                                                } else {
                                                    TooltipDefaults.rememberPlainTooltipPositionProvider()
                                                },
                                            tooltip = {
                                                if (hasUnreadNews) {
                                                    RichTooltip(
                                                        title = { Text(stringResource(R.string.news_tooltip_title)) },
                                                    ) {
                                                        Text(stringResource(R.string.news_tooltip_body))
                                                    }
                                                } else {
                                                    PlainTooltip {
                                                        Text(stringResource(R.string.news))
                                                    }
                                                }
                                            },
                                            state = rememberTooltipState(),
                                        ) {
                                            IconButton(
                                                onClick = { navController.navigate("news") },
                                                onLongClick = {},
                                            ) {
                                                BadgedBox(badge = {
                                                    if (hasUnreadNews) {
                                                        Badge()
                                                    }
                                                }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.newspaper),
                                                        contentDescription = stringResource(R.string.news),
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = { navController.navigate("new_release") },
                                            onLongClick = {},
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.new_release),
                                                contentDescription = stringResource(R.string.new_release_albums),
                                            )
                                        }
                                        IconButton(
                                            onClick = { navController.navigate("settings") },
                                            onLongClick = {},
                                        ) {
                                            BadgedBox(badge = {
                                                if (splashDone && (updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate)) {
                                                    Badge()
                                                }
                                            }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_settings),
                                                    contentDescription = stringResource(R.string.settings),
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    },
                                    scrollBehavior =
                                        if (navBackStackEntry?.destination?.route == Screens.Library.route ||
                                            shouldUseFloatingTopBar
                                        ) {
                                            null
                                        } else {
                                            topAppBarScrollBehavior
                                        },
                                    colors =
                                        TopAppBarDefaults.topAppBarColors(
                                            containerColor =
                                                if (shouldUseFloatingTopBar) {
                                                    Color.Transparent
                                                } else if (pureBlack) {
                                                    Color.Black
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                            scrolledContainerColor =
                                                if (shouldUseFloatingTopBar) {
                                                    Color.Transparent
                                                } else if (pureBlack) {
                                                    Color.Black
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                )
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible =
                                active ||
                                    navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true,
                            enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300)),
                            exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 200)),
                        ) {
                            TopSearch(
                                query = query,
                                onQueryChange = onQueryChange,
                                onSearch = onSearch,
                                active = active,
                                onActiveChange = onActiveChange,
                                placeholder = {
                                    Text(
                                        text =
                                            stringResource(
                                                when (searchSource) {
                                                    SearchSource.LOCAL -> R.string.search_library
                                                    SearchSource.ONLINE -> R.string.search_yt_music
                                                },
                                            ),
                                    )
                                },
                                 leadingIcon = {
                                     val iconMotionDuration = if (disableAnimations) 0 else 300
                                     IconButton(
                                         onClick = {
                                             if (active) {
                                                 onActiveChange(false)
                                             } else {
                                                 onActiveChange(true)
                                             }
                                         },
                                         onLongClick = {
                                             when {
                                                 active -> {}

                                                 !navigationItems.fastAny {
                                                     it.route == navBackStackEntry?.destination?.route
                                                 } -> {
                                                     navController.backToMain()
                                                 }

                                                 else -> {}
                                             }
                                         },
                                     ) {
                                         AnimatedContent(
                                             targetState = active,
                                             transitionSpec = {
                                                 (fadeIn(animationSpec = tween(iconMotionDuration)) + scaleIn(initialScale = 0.8f, animationSpec = tween(iconMotionDuration)))
                                                     .togetherWith(fadeOut(animationSpec = tween(iconMotionDuration)) + scaleOut(targetScale = 0.8f, animationSpec = tween(iconMotionDuration)))
                                             },
                                             label = "LeadingIconMorph",
                                         ) { isExpanded ->
                                             Icon(
                                                 painter =
                                                     painterResource(
                                                         if (isExpanded) {
                                                             R.drawable.arrow_back
                                                         } else {
                                                             R.drawable.ic_search
                                                         },
                                                     ),
                                                 contentDescription = null,
                                             )
                                         }
                                     }
                                 },
                                 trailingIcon = {
                                     val iconMotionDuration = if (disableAnimations) 0 else 300
                                     AnimatedContent(
                                         targetState = active,
                                         transitionSpec = {
                                             (fadeIn(animationSpec = tween(iconMotionDuration)) + scaleIn(initialScale = 0.85f, animationSpec = tween(iconMotionDuration)))
                                                 .togetherWith(fadeOut(animationSpec = tween(iconMotionDuration)) + scaleOut(targetScale = 0.85f, animationSpec = tween(iconMotionDuration)))
                                         },
                                         label = "TrailingIconMorph",
                                     ) { isExpanded ->
                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                             if (isExpanded) {
                                                 if (query.text.isNotEmpty()) {
                                                     IconButton(
                                                         onClick = {
                                                             onQueryChange(
                                                                 TextFieldValue(
                                                                     "",
                                                                 ),
                                                             )
                                                         },
                                                     ) {
                                                         Icon(
                                                             painter = painterResource(R.drawable.close),
                                                             contentDescription = null,
                                                         )
                                                     }
                                                 } else {
                                                     IconButton(
                                                         onClick = {
                                                             val intent =
                                                                 Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                                     putExtra(
                                                                         RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                                         RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                                                     )
                                                                 }
                                                             runCatching { voiceSearchLauncher.launch(intent) }
                                                         },
                                                     ) {
                                                         Icon(
                                                             painter = painterResource(R.drawable.mic),
                                                             contentDescription = stringResource(R.string.voice_search),
                                                         )
                                                     }
                                                 }
                                                 IconButton(
                                                     onClick = {
                                                         searchSource =
                                                             if (searchSource ==
                                                                 SearchSource.ONLINE
                                                             ) {
                                                                 SearchSource.LOCAL
                                                             } else {
                                                                 SearchSource.ONLINE
                                                             }
                                                     },
                                                 ) {
                                                     Icon(
                                                         painter =
                                                             painterResource(
                                                                 when (searchSource) {
                                                                     SearchSource.LOCAL -> R.drawable.library_music
                                                                     SearchSource.ONLINE -> R.drawable.language
                                                                 },
                                                             ),
                                                         contentDescription = null,
                                                     )
                                                 }
                                             } else if (onlineSearchViewModel != null) {
                                                 OnlineSearchSortMenu(
                                                     selectedSort = onlineSearchSort,
                                                     onSortSelected = onlineSearchViewModel::updateSort,
                                                 )
                                             }
                                         }
                                     }
                                 },
                                 modifier =
                                     Modifier
                                         .focusRequester(searchBarFocusRequester)
                                         .let { with(this@BoxWithConstraints) { it.align(Alignment.TopCenter) } },
                                 focusRequester = searchBarFocusRequester,
                                 leftFocusRequester = tvRailFocusRequester,
                                 colors =
                                     if (pureBlack && active) {
                                         SearchBarDefaults.colors(
                                             containerColor = Color.Black,
                                             dividerColor = Color.DarkGray,
                                             inputFieldColors =
                                                 TextFieldDefaults.colors(
                                                     focusedTextColor = Color.White,
                                                     unfocusedTextColor = Color.Gray,
                                                     focusedContainerColor = Color.Transparent,
                                                     unfocusedContainerColor = Color.Transparent,
                                                     cursorColor = Color.White,
                                                     focusedIndicatorColor = Color.Transparent,
                                                     unfocusedIndicatorColor = Color.Transparent,
                                                 ),
                                         )
                                      } else {
                                          SearchBarDefaults.colors(
                                              containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                          )
                                      },
                                 hazeState = effectiveSearchHazeState,
                                pureBlack = pureBlack,
                                blurRadius = blurRadius,
                            ) {
                                Crossfade(
                                    targetState = searchSource,
                                    animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300),
                                    label = "",
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(
                                                bottom = if (isMiniPlayerVisible) MiniPlayerHeight else 0.dp,
                                            ).navigationBarsPadding(),
                                ) { source ->
                                    when (source) {
                                        SearchSource.LOCAL -> {
                                            LocalSearchScreen(
                                                query = query.text,
                                                navController = navController,
                                                onDismiss = { onActiveChange(false) },
                                                pureBlack = pureBlack,
                                            )
                                        }

                                        SearchSource.ONLINE -> {
                                            OnlineSearchScreen(
                                                query = query.text,
                                                onQueryChange = onQueryChange,
                                                navController = navController,
                                                onSearch = { onlineQuery ->
                                                    navController.navigate(onlineSearchResultRoute(onlineQuery))
                                                },
                                                onDismiss = { onActiveChange(false) },
                                                pureBlack = pureBlack,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    bottomBar = {},
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize(),
                ) { _ ->
                    NavigationHost(
                        navController = navController,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                        hazeState = effectiveHazeState,
                        updateState = updateState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (effectiveSearchHazeState != null) {
                                        Modifier.hazeSource(effectiveSearchHazeState)
                                    } else {
                                        Modifier
                                    },
                                ),
                        homeScrollConnection = homeScrollBehavior.nestedScrollConnection,
                        searchScrollConnection = searchScrollBehavior.nestedScrollConnection,
                        onClearUpdateBadge = { updateViewModel.dismissUpdate() },
                        disableAnimations = disableAnimations,
                        isTvDevice = isTvDevice,
                        contentAreaFocusRequester = contentAreaFocusRequester,
                        launchMusicRecognitionFromShortcut = launchMusicRecognitionFromShortcut,
                        tabOpenedFromShortcut = tabOpenedFromShortcut,
                        defaultOpenTab = defaultOpenTab,
                        navigationItems = navigationItems,
                        updateChannel = updateChannel,
                    )
                }

                PlayerOverlayHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    maxHeight = this@BoxWithConstraints.maxHeight,
                    bottomInset = bottomInset,
                    shouldShowNav = shouldShowNavigationBar,
                    isYearInMusic = isYearInMusicScreen,
                    useRail = useRail,
                    hazeState = effectiveHazeState,
                    blurRadius = blurRadius,
                    pureBlack = pureBlack,
                    playerViewModel = playerViewModel,
                    homeViewModel = homeViewModel,
                    playerConnection = playerConnection,
                    database = database,
                    systemBarController = systemBarController,
                    window = activity.window,
                    sheetState = playerBottomSheetState,
                    aodModeLaunchRequestCount = aodModeLaunchRequestCount,
                    onResetAodLaunchRequestCount = onResetAodLaunchRequestCount,
                    useDarkTheme = useDarkTheme,
                    shouldShowHomeShuffleButton = shouldShowHomeShuffleButton,
                    navigationItems = navigationItems,
                    navBackStackEntry = navBackStackEntry,
                    handlePrimaryNavigationClick = handlePrimaryNavigationClick,
                    onSearchItemDoubleClick = {
                        searchSource = SearchSource.ONLINE
                        openSearch()
                    },
                    disableAnimations = disableAnimations,
                    navVisibleHeight = navVisibleHeight,
                    floatingToolbarBottomPadding = floatingToolbarBottomPadding,
                    onExpansionFraction = onExpansionFraction,
                )

                GlobalDialogsHost(
                    navController = navController,
                    playerConnection = playerConnection,
                    bottomSheetPageState = bottomSheetPageState,
                    menuState = menuState,
                    networkBannerState = networkBannerState,
                    pendingBackupRestoreUri = pendingBackupRestoreUri,
                    onDismissBackupRestore = onClearPendingBackupRestoreUri,
                    splashDone = splashDone,
                    shouldShowTopBar = shouldShowTopBar,
                    topInset = topInset,
                    updateChannel = updateChannel,
                    coroutineScope = coroutineScope,
                )
            }
        }
    }

        LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
            if (shouldShowSearchBar && openSearchImmediately) {
                onActiveChange(true)
                try {
                    delay(100)
                    searchBarFocusRequester.requestFocus()
                } catch (_: Exception) {
                }
                openSearchImmediately = false
            }
        }

        val openSearchFromRoute by
            navBackStackEntry
                ?.savedStateHandle
                ?.getStateFlow("openSearch", false)
                ?.collectAsStateWithLifecycle()
                ?: remember { mutableStateOf(false) }

        LaunchedEffect(openSearchFromRoute) {
            if (openSearchFromRoute) {
                navBackStackEntry?.savedStateHandle?.set("openSearch", false)
                openSearch()
            }
        }
    }
}
