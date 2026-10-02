package moe.rukamori.archivetune

import android.app.Activity
import android.content.ContextWrapper
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.NavigationBarAnimationSpec
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.FloatingNavigationToolbar
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YdsInsets
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import kotlin.math.roundToInt

@Composable
fun PlayerOverlayHost(
    navController: NavHostController,
    maxHeight: Dp,
    bottomInset: Dp,
    shouldShowNav: Boolean,
    isYearInMusic: Boolean,
    modifier: Modifier = Modifier,
    useRail: Boolean = false,
    hazeState: HazeState? = null,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    pureBlack: Boolean = false,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    playerConnection: PlayerConnection? = null,
    database: MusicDatabase? = null,
    systemBarController: SystemBarController? = null,
    window: Window? = null,
    sheetState: BottomSheetState = rememberPlayerBottomSheetState(
        maxHeight = maxHeight,
        bottomInset = bottomInset,
        shouldShowNav = shouldShowNav,
        useRail = useRail,
    ),
    aodModeLaunchRequestCount: Int = 0,
    onResetAodLaunchRequestCount: () -> Unit = {},
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    shouldShowHomeShuffleButton: Boolean = false,
    navigationItems: List<Screens> = Screens.MainScreens,
    navBackStackEntry: NavBackStackEntry? = null,
    handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { _, _ -> },
    onSearchItemDoubleClick: () -> Unit = {},
    disableAnimations: Boolean = false,
    navVisibleHeight: Dp = FloatingToolbarHeight,
    floatingToolbarBottomPadding: Dp = YdsInsets.floatingToolbarBottomPadding(),
    onExpansionFraction: (() -> Float) -> Unit = {},
    onExpansionState: (State<Float>) -> Unit = {},
    scrollVisibilityFactor: Float = 1f,
) {
    val playerExpansionAnimatable = remember { Animatable(0f) }
    val expansionFraction: () -> Float = remember { { playerExpansionAnimatable.value } }
    val expansionState: State<Float> = remember { derivedStateOf { playerExpansionAnimatable.value } }

    SideEffect {
        onExpansionFraction(expansionFraction)
        onExpansionState(expansionState)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val targetWindow = window ?: remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx.window
            ctx = ctx.baseContext
        }
        null
    }

    val isPlayerLyricsVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.isLyricsVisible }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val isPlayerQueueVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.isQueueVisible }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    BackHandler(enabled = playerExpansionAnimatable.value > 0.5f) {
        when {
            isPlayerLyricsVisible -> {
                playerViewModel.setLyricsVisible(false)
            }
            isPlayerQueueVisible -> {
                playerViewModel.setQueueVisible(false)
            }
            else -> {
                playerViewModel.requestSheetCollapse()
            }
        }
    }

    val aodModeEnabled by remember(playerConnection) {
        playerConnection?.aodModeEnabled ?: MutableStateFlow(false)
    }.collectAsStateWithLifecycle()

    PlayerOverlayEffects(
        playerViewModel = playerViewModel,
        playerConnection = playerConnection,
        navController = navController,
        sheetState = sheetState,
        targetWindow = targetWindow,
        aodModeEnabled = aodModeEnabled,
        aodModeLaunchRequestCount = aodModeLaunchRequestCount,
        onResetAodLaunchRequestCount = onResetAodLaunchRequestCount,
        isYearInMusic = isYearInMusic,
    )

    PlayerSystemBars(
        targetWindow = targetWindow,
        aodModeEnabled = aodModeEnabled,
        systemBarController = systemBarController,
        useDarkTheme = useDarkTheme,
        sheetState = sheetState,
        isYearInMusic = isYearInMusic,
    )

    val bottomNavigationBarHeight by animateDpAsState(
        targetValue = if (shouldShowNav && !useRail) navVisibleHeight else 0.dp,
        animationSpec = if (disableAnimations) snap() else NavigationBarAnimationSpec,
        label = "",
    )

    val isMiniPlayerActive by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.trackUrl.isNotEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val isDocked = bottomNavigationBarHeight > 0.dp && scrollVisibilityFactor > 0.01f
    val navSlideDistance =
        floatingToolbarBottomPadding + navVisibleHeight
    val miniPlayerSlideOffset = navSlideDistance * (1f - scrollVisibilityFactor)

    Box(modifier = modifier) {
        ScopedPlayerSheet(
            playerViewModel = playerViewModel,
            playerConnection = playerConnection,
            navController = navController,
            bottomNavigationBarHeight = bottomNavigationBarHeight,
            miniPlayerSlideOffset = miniPlayerSlideOffset,
            hazeState = hazeState,
            pureBlack = pureBlack,
            blurRadius = blurRadius,
            isDocked = isDocked,
            onExpansionFractionChanged = { fraction ->
                coroutineScope.launch {
                    playerExpansionAnimatable.snapTo(fraction)
                }
            },
        )

        if (useRail) return@Box

        PlayerDockContainer(
            modifier = Modifier.align(Alignment.BottomCenter),
            isPillVisible = isMiniPlayerActive,
            isBarVisible = isDocked,
            barHeight = navSlideDistance,
            scrollVisibilityFactor = scrollVisibilityFactor,
            miniPlayerSlot = {},
            barSlot = { barShape ->
                Box(
                    modifier =
                        Modifier
                            .height(navSlideDistance)
                            .offset {
                                if (bottomNavigationBarHeight == 0.dp) {
                                    IntOffset(
                                        x = 0,
                                        y = navSlideDistance.roundToPx(),
                                    )
                                } else {
                                    val slideOffset =
                                        navSlideDistance.toPx() *
                                                playerExpansionAnimatable.value.coerceIn(
                                                    0f,
                                                    1f,
                                                )
                                    val hideOffset =
                                        navSlideDistance.toPx() *
                                                (
                                                        1f -
                                                                bottomNavigationBarHeight.coerceAtMost(navVisibleHeight) /
                                                                navVisibleHeight
                                                        )
                                    IntOffset(
                                        x = 0,
                                        y = (slideOffset + hideOffset).roundToInt(),
                                    )
                                }
                            },
                ) {
                    FloatingNavigationToolbar(
                        items = navigationItems,
                        pureBlack = pureBlack,
                        capsuleShape = barShape,
                        hazeState = hazeState,
                        blurRadius = blurRadius,
                        showBorder = !isMiniPlayerActive || playerExpansionAnimatable.value >= SettingsDimensions.FullyExpandedThreshold,
                        blurEnabled = bottomNavigationBarHeight != 0.dp &&
                            playerExpansionAnimatable.value < bottomNavigationBarHeight.coerceAtMost(navVisibleHeight) / navVisibleHeight,
                        visibilityFactor = if (playerExpansionAnimatable.value < 0.01f) scrollVisibilityFactor else 1f,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = floatingToolbarBottomPadding,
                                ).height(navVisibleHeight),
                        onShuffleClick =
                            if (shouldShowHomeShuffleButton) {
                                {
                                    launchHomeShuffle(
                                        coroutineScope = coroutineScope,
                                        homeViewModel = homeViewModel,
                                        playerConnection = playerConnection,
                                        database = database,
                                    )
                                }
                            } else {
                                null
                            },
                        shuffleIconRes = if (shouldShowHomeShuffleButton) R.drawable.shuffle else null,
                        shuffleContentDescription =
                            if (shouldShowHomeShuffleButton) {
                                stringResource(
                                    R.string.shuffle,
                                )
                            } else {
                                ""
                            },
                        onMusicRecognitionClick =
                            if (shouldShowHomeShuffleButton) {
                                { navController.navigate(MusicRecognitionRoute) }
                            } else {
                                null
                            },
                        musicRecognitionContentDescription =
                            if (shouldShowHomeShuffleButton) {
                                stringResource(
                                    R.string.music_recognition,
                                )
                            } else {
                                ""
                            },
                        onMusicTogetherClick =
                            if (shouldShowHomeShuffleButton) {
                                { navController.navigate("settings/music_together") }
                            } else {
                                null
                            },
                        isSelected = { screen ->
                            navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } ==
                                true
                        },
                        onItemClick = { screen, isSelected ->
                            handlePrimaryNavigationClick(screen, isSelected)
                        },
                        onSearchItemDoubleClick = onSearchItemDoubleClick,
                    )
                }
            },
        )
    }
}
