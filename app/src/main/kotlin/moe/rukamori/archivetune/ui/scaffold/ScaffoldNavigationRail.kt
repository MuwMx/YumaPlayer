package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.TvNavigationRail
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix

@Composable
fun ScaffoldNavigationRail(
    visible: Boolean,
    isTvDevice: Boolean,
    useRail: Boolean,
    active: Boolean,
    currentRoute: String?,
    navigationItems: List<Screens>,
    topLevelScreens: List<String>,
    navBackStackEntry: NavBackStackEntry?,
    disableAnimations: Boolean,
    pureBlack: Boolean,
    playerBottomSheetState: BottomSheetState,
    tvRailFocusRequester: FocusRequester,
    searchBarFocusRequester: FocusRequester,
    contentAreaFocusRequester: FocusRequester,
    onPrimaryNavigationClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(isTvDevice, useRail, visible, active, currentRoute) {
        if (isTvDevice && useRail && visible && !active && currentRoute in topLevelScreens) {
            delay(100)
            tvRailFocusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 150)),
        exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 100)),
        modifier = modifier,
    ) {
        if (isTvDevice) {
            TvNavigationRail(
                items = navigationItems,
                selectedItemRoute = if (active) Screens.Search.route else currentRoute,
                modifier = Modifier,
                firstItemFocusRequester = tvRailFocusRequester,
                contentFocusRequester =
                    if (active || navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                        searchBarFocusRequester
                    } else {
                        contentAreaFocusRequester
                    },
                onItemClick = { screen ->
                    val wasPlayerActive = playerBottomSheetState.isExpanded
                    if (wasPlayerActive) {
                        playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                    }
                    val isSelected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                    if (wasPlayerActive && isSelected) return@TvNavigationRail
                    onPrimaryNavigationClick(screen, isSelected)
                },
            )
        } else {
            NavigationRail(
                containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                header = { Spacer(Modifier.height(24.dp)) },
            ) {
                navigationItems.fastForEach { screen ->
                    val isSelected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationRailItem(
                        selected = isSelected,
                        icon = {
                            Icon(
                                painter = painterResource(id = if (isSelected) screen.iconIdActive else screen.iconIdInactive),
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
                            onPrimaryNavigationClick(screen, isSelected)
                        },
                    )
                }
            }
        }
    }
}
