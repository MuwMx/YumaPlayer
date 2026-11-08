package moe.rukamori.archivetune.ui.player.host

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YdsInsets

@Composable
internal fun BoxScope.PlayerDockHost(
    playerViewModel: PlayerViewModel,
    playerExpansionAnimatable: Animatable<Float, AnimationVector1D>,
    isPlayerExpandedForZIndex: Boolean,
    bottomNavigationBarHeight: Dp,
    useRail: Boolean,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    hazeState: HazeState? = null,
    blurNavBar: Boolean = true,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    navVisibleHeight: Dp = FloatingToolbarHeight,
    floatingToolbarBottomPadding: Dp = YdsInsets.floatingToolbarBottomPadding(),
    scrollVisibilityFactor: Float = 1f,
    navSlideDistance: Dp = PlayerDockGeometry.navigationBarBottomOffset(
        floatingToolbarBottomPadding = floatingToolbarBottomPadding,
        navigationBarHeight = navVisibleHeight,
    ),
    dropState: MiniPlayerDropState = rememberMiniPlayerDropState(
        navSlideDistance = navSlideDistance,
        scrollVisibilityFactor = scrollVisibilityFactor,
        bottomNavigationBarHeight = bottomNavigationBarHeight,
    ),
    navigationItems: List<Screens> = Screens.MainScreens,
    shouldShowHomeShuffleButton: Boolean = false,
    onShuffleClick: (() -> Unit)? = null,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry? = null,
    handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { _, _ -> },
    onSearchItemDoubleClick: () -> Unit = {},
) {
    if (useRail) return

    val navigationSlideOffset = rememberNavigationSlideOffset(
        navSlideDistance = navSlideDistance,
        bottomNavigationBarHeight = bottomNavigationBarHeight,
        navVisibleHeight = navVisibleHeight,
        playerExpansionAnimatable = playerExpansionAnimatable,
    )

    val isMiniPlayerActive by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.trackUrl.isNotEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    PlayerDockContainer(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .zIndex(if (isPlayerExpandedForZIndex) 0f else 1f),
        isPillVisible = isMiniPlayerActive,
        isBarVisible = dropState.isDocked,
        barHeight = navSlideDistance,
        scrollVisibilityFactor = scrollVisibilityFactor,
        miniPlayerSlot = {},
        barSlot = { barShape ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navSlideDistance)
                    .offset { navigationSlideOffset.value },
            ) {
                NavigationToolbarBridge(
                    barShape = barShape,
                    navigationItems = navigationItems,
                    pureBlack = pureBlack,
                    hazeState = hazeState,
                    blurNavBar = blurNavBar,
                    blurRadius = blurRadius,
                    isMiniPlayerActive = isMiniPlayerActive,
                    playerExpansionAnimatable = playerExpansionAnimatable,
                    bottomNavigationBarHeight = bottomNavigationBarHeight,
                    navVisibleHeight = navVisibleHeight,
                    scrollVisibilityFactor = scrollVisibilityFactor,
                    floatingToolbarBottomPadding = floatingToolbarBottomPadding,
                    shouldShowHomeShuffleButton = shouldShowHomeShuffleButton,
                    onShuffleClick = onShuffleClick,
                    navController = navController,
                    navBackStackEntry = navBackStackEntry,
                    handlePrimaryNavigationClick = handlePrimaryNavigationClick,
                    onSearchItemDoubleClick = onSearchItemDoubleClick,
                )
            }
        },
    )
}
