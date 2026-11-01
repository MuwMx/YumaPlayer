package moe.rukamori.archivetune.ui.system

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
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState
import moe.rukamori.archivetune.ui.player.host.PlayerDockGeometry

@Composable
fun rememberPlayerBottomSheetState(
    maxHeight: Dp,
    bottomInset: Dp,
    shouldShowNav: Boolean,
    useRail: Boolean,
): BottomSheetState =
    rememberBottomSheetState(
        dismissedBound = 0.dp,
        collapsedBound = PlayerDockGeometry.sheetCollapsedBound(
            bottomInset = bottomInset,
            shouldShowNav = shouldShowNav,
            useRail = useRail,
        ),
        expandedBound = maxHeight,
    )

@Composable
fun rememberPlayerAwareWindowInsets(
    useRail: Boolean,
    bottomInset: Dp,
    shouldShowNavigationBar: Boolean,
    isMiniPlayerVisible: Boolean,
    floatingToolbarBottomPadding: Dp,
    windowsInsets: WindowInsets,
): WindowInsets =
    remember(
        useRail,
        bottomInset,
        shouldShowNavigationBar,
        isMiniPlayerVisible,
        floatingToolbarBottomPadding,
        windowsInsets,
    ) {
        val reservedBottom = PlayerDockGeometry.reservedBottomSpace(
            bottomInset = bottomInset,
            floatingToolbarBottomPadding = floatingToolbarBottomPadding,
            shouldShowNavigationBar = shouldShowNavigationBar,
            isMiniPlayerVisible = isMiniPlayerVisible,
            useRail = useRail,
        )
        val horizontalSides = if (useRail) WindowInsetsSides.Right else WindowInsetsSides.Horizontal

        windowsInsets
            .only(horizontalSides + WindowInsetsSides.Top)
            .add(WindowInsets(top = AppBarHeight, bottom = reservedBottom))
    }

@Composable
fun rememberPlayerAwareWindowInsets(
    playerViewModel: PlayerViewModel,
    useRail: Boolean,
    bottomInset: Dp,
    shouldShowNavigationBar: Boolean,
    floatingToolbarBottomPadding: Dp,
    windowsInsets: WindowInsets,
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
        windowsInsets = windowsInsets,
    )
}
