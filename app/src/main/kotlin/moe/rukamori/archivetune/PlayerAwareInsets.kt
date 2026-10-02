package moe.rukamori.archivetune

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerOnlyOffset
import moe.rukamori.archivetune.constants.MiniPlayerWithNavBarOffset
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState

@Composable
fun rememberPlayerBottomSheetState(
    maxHeight: Dp,
    bottomInset: Dp,
    shouldShowNav: Boolean,
    useRail: Boolean,
): BottomSheetState =
    rememberBottomSheetState(
        dismissedBound = 0.dp,
        collapsedBound = bottomInset + if (shouldShowNav && !useRail) {
            MiniPlayerWithNavBarOffset
        } else {
            MiniPlayerOnlyOffset
        },
        expandedBound = maxHeight,
    )

@Composable
fun rememberPlayerAwareWindowInsets(
    useRail: Boolean,
    bottomInset: Dp,
    shouldShowNavigationBar: Boolean,
    isMiniPlayerVisible: Boolean,
    floatingToolbarBottomPadding: Dp,
    windowInsets: WindowInsets,
): WindowInsets =
    remember(
        useRail,
        bottomInset,
        shouldShowNavigationBar,
        isMiniPlayerVisible,
        floatingToolbarBottomPadding,
        windowInsets,
    ) {
        val navBottom = if (shouldShowNavigationBar && !useRail) {
            floatingToolbarBottomPadding + FloatingToolbarHeight
        } else {
            bottomInset
        }
        val miniPlayerOffset = if (isMiniPlayerVisible) {
            MiniPlayerHeight + MiniPlayerBottomSpacing
        } else {
            0.dp
        }
        val horizontalSides = if (useRail) WindowInsetsSides.Right else WindowInsetsSides.Horizontal

        windowInsets
            .only(horizontalSides + WindowInsetsSides.Top)
            .add(WindowInsets(top = AppBarHeight, bottom = navBottom + miniPlayerOffset))
    }

@Composable
fun rememberPlayerAwareWindowInsets(
    playerViewModel: PlayerViewModel,
    useRail: Boolean,
    bottomInset: Dp,
    shouldShowNavigationBar: Boolean,
    floatingToolbarBottomPadding: Dp,
    windowInsets: WindowInsets,
): WindowInsets {
    val isMiniPlayerVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.trackUrl.isNotEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    return rememberPlayerAwareWindowInsets(
        useRail = useRail,
        bottomInset = bottomInset,
        shouldShowNavigationBar = shouldShowNavigationBar,
        isMiniPlayerVisible = isMiniPlayerVisible,
        floatingToolbarBottomPadding = floatingToolbarBottomPadding,
        windowInsets = windowInsets,
    )
}