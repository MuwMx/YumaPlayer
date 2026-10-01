package moe.rukamori.archivetune

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.utils.rememberEnumPreference

@Composable
internal fun PlayerSystemBars(
    targetWindow: Window?,
    aodModeEnabled: Boolean,
    systemBarController: SystemBarController?,
    useDarkTheme: Boolean,
    sheetState: BottomSheetState,
    isYearInMusic: Boolean,
) {
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT,
    )
    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V4,
    )

    LaunchedEffect(useDarkTheme, sheetState.isExpanded, playerBackground, aodModeEnabled) {
        if (aodModeEnabled) return@LaunchedEffect
        val isDarkStatusBar =
            if (sheetState.isExpanded &&
                playerBackground != PlayerBackgroundStyle.DEFAULT
            ) {
                true
            } else {
                useDarkTheme
            }
        if (systemBarController != null) {
            systemBarController.setSystemBarAppearance(isDarkStatusBar)
        } else if (targetWindow != null) {
            setSystemBarAppearance(targetWindow, isDarkStatusBar)
        }
    }

    val shouldHideStatusBars =
        isYearInMusic ||
            (sheetState.isExpanded && playerDesignStyle == PlayerDesignStyle.V7)

    LaunchedEffect(shouldHideStatusBars, aodModeEnabled) {
        if (aodModeEnabled) return@LaunchedEffect
        if (systemBarController != null) {
            systemBarController.setStatusBarsHidden(shouldHideStatusBars)
        } else if (targetWindow != null) {
            setStatusBarsHidden(targetWindow, shouldHideStatusBars)
        }
    }
}
