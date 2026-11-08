package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.sheet.yumaSheetBackdropRecession
import moe.rukamori.archivetune.ui.navigation.NavigationHost

@Composable
internal fun ScaffoldBody(
    navController: NavHostController,
    appState: ScaffoldAppState,
    scaffoldHazeState: ScaffoldHazeState,
    updateChannel: UpdateChannel,
    playerExpansionFraction: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    disableAnimations: Boolean = LocalAnimationsDisabled.current,
) {
    val menuState = LocalMenuState.current

    NavigationHost(
        navController = navController,
        topAppBarScrollBehavior = appState.scrollController.topAppBarScrollBehavior,
        hazeState = scaffoldHazeState.effectiveHazeState,
        updateState = appState.updateState,
        modifier = modifier
            .fillMaxSize()
            .then(
                if (scaffoldHazeState.effectiveSearchHazeState != null) {
                    Modifier.hazeSource(scaffoldHazeState.effectiveSearchHazeState)
                } else {
                    Modifier
                },
            )
            .yumaSheetBackdropRecession(
                progressProvider = {
                    val playerProgress = if (disableAnimations) 0f else playerExpansionFraction().coerceIn(0f, 1f)
                    val menuProgress = if (disableAnimations) 0f else menuState.progressProvider().coerceIn(0f, 1f)
                    maxOf(playerProgress, menuProgress)
                },
                alphaProvider = {
                    val playerProgress = if (disableAnimations) 0f else playerExpansionFraction().coerceIn(0f, 1f)
                    if (playerProgress > 0.9f) ((1f - playerProgress) / 0.1f).coerceIn(0f, 1f) else 1f
                },
                enabled = !disableAnimations,
            )
            .graphicsLayer {
                val playerProgress = if (disableAnimations) 0f else playerExpansionFraction().coerceIn(0f, 1f)
                alpha = if (playerProgress > 0.9f) ((1f - playerProgress) / 0.1f).coerceIn(0f, 1f) else 1f
            },
        homeScrollConnection = appState.scrollController.homeScrollBehavior.nestedScrollConnection,
        searchScrollConnection = appState.scrollController.searchScrollBehavior.nestedScrollConnection,
        onClearUpdateBadge = { appState.updateViewModel.dismissUpdate() },
        disableAnimations = disableAnimations,
        isTvDevice = appState.isTvDevice,
        contentAreaFocusRequester = appState.searchState.contentAreaFocusRequester,
        launchMusicRecognitionFromShortcut = appState.launchMusicRecognitionFromShortcut,
        tabOpenedFromShortcut = appState.tabOpenedFromShortcut,
        defaultOpenTab = appState.defaultOpenTab,
        navigationItems = appState.navigationItems,
        updateChannel = updateChannel,
    )
}
