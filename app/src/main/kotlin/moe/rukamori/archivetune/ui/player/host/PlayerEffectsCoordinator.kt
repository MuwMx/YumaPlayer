package moe.rukamori.archivetune.ui.player.host

import android.app.Activity
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.system.PlayerSystemBars
import moe.rukamori.archivetune.ui.system.SystemBarController

@Composable
internal fun PlayerEffectsCoordinator(
    playerViewModel: PlayerViewModel,
    playerConnection: PlayerConnection?,
    navController: NavController,
    sheetState: BottomSheetState,
    playerExpansionAnimatable: Animatable<Float, AnimationVector1D>,
    window: Window? = null,
    systemBarController: SystemBarController? = null,
    useDarkTheme: Boolean = false,
    aodModeLaunchRequestCount: Int = 0,
    onResetAodLaunchRequestCount: () -> Unit = {},
    isYearInMusic: Boolean = false,
) {
    val context = LocalContext.current
    val targetWindow = window ?: remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx.window
            ctx = ctx.baseContext
        }
        null
    }

    PlayerBackHandler(
        playerViewModel = playerViewModel,
        playerExpansionAnimatable = playerExpansionAnimatable,
    )

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
}
