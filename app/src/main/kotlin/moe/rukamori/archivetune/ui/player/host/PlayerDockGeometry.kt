package moe.rukamori.archivetune.ui.player.host

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.FloatingToolbarBottomPadding
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerOnlyOffset
import moe.rukamori.archivetune.constants.MiniPlayerWithNavBarOffset
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth

internal object PlayerDockGeometry {
    val miniPlayerHeight: Dp = MiniPlayerHeight

    fun collapsedHorizontalInset(
        availableWidth: Dp,
        useRail: Boolean,
    ): Dp {
        if (useRail) return FloatingToolbarHorizontalPadding
        val centeredInset = (availableWidth - NavigationBarMaxWidth) / 2 + FloatingToolbarHorizontalPadding
        return maxOf(FloatingToolbarHorizontalPadding, centeredInset)
    }

    fun navigationBarBottomOffset(
        floatingToolbarBottomPadding: Dp,
        navigationBarHeight: Dp = FloatingToolbarHeight,
    ): Dp = floatingToolbarBottomPadding + navigationBarHeight

    fun sheetCollapsedOffset(
        safeBottomPadding: Dp,
        bottomBarHeight: Dp,
    ): Dp {
        val progress = if (FloatingToolbarHeight > 0.dp) {
            (bottomBarHeight / FloatingToolbarHeight).coerceIn(0f, 1f)
        } else 0f
        val navContribution = (FloatingToolbarHeight + FloatingToolbarBottomPadding) * progress
        return safeBottomPadding + navContribution + MiniPlayerBottomSpacing + MiniPlayerHeight
    }

    fun sheetCollapsedBound(
        bottomInset: Dp,
        shouldShowNav: Boolean,
        useRail: Boolean,
    ): Dp = bottomInset + if (shouldShowNav && !useRail) {
        MiniPlayerWithNavBarOffset
    } else {
        MiniPlayerOnlyOffset
    }

    fun reservedBottomSpace(
        bottomInset: Dp,
        floatingToolbarBottomPadding: Dp,
        shouldShowNavigationBar: Boolean,
        isMiniPlayerVisible: Boolean,
        useRail: Boolean,
    ): Dp {
        val navBottom = if (shouldShowNavigationBar && !useRail) {
            navigationBarBottomOffset(floatingToolbarBottomPadding)
        } else {
            bottomInset
        }
        val miniPlayerOffset = if (isMiniPlayerVisible) {
            MiniPlayerHeight + MiniPlayerBottomSpacing
        } else {
            0.dp
        }
        return navBottom + miniPlayerOffset
    }
}
