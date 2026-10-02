package moe.rukamori.archivetune.ui.player.host

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.launchHomeShuffle
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.FloatingNavigationToolbar
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.viewmodels.HomeViewModel

@Composable
internal fun BoxScope.NavigationToolbarBridge(
    barShape: Shape,
    navigationItems: List<Screens>,
    pureBlack: Boolean,
    hazeState: HazeState?,
    blurRadius: Float,
    isMiniPlayerActive: Boolean,
    playerExpansionAnimatable: Animatable<Float, AnimationVector1D>,
    bottomNavigationBarHeight: Dp,
    navVisibleHeight: Dp,
    scrollVisibilityFactor: Float,
    floatingToolbarBottomPadding: Dp,
    shouldShowHomeShuffleButton: Boolean,
    coroutineScope: CoroutineScope,
    homeViewModel: HomeViewModel,
    playerConnection: PlayerConnection?,
    database: MusicDatabase?,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    handlePrimaryNavigationClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        visibilityFactor = scrollVisibilityFactor * (1f - playerExpansionAnimatable.value.coerceIn(0f, 1f)),
        modifier =
            modifier
                .fillMaxWidth()
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
