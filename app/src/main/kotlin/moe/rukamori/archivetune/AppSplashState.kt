package moe.rukamori.archivetune

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.SplashOverlayEnabledKey
import moe.rukamori.archivetune.ui.component.splash.SplashConfig
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberPreference

data class AppSplashState(
    val splashEnabled: Boolean,
    val coldSplash: Boolean,
    val contentVisible: Boolean,
    val splashDone: Boolean,
    val contentAlpha: Float,
    val onBurstStart: () -> Unit,
    val onSplashDismiss: () -> Unit,
)

@Composable
fun rememberAppSplashState(
    activity: ComponentActivity,
    isReady: Boolean,
    disableAnimations: Boolean,
    onReadyChange: (Boolean) -> Unit,
): AppSplashState {
    val splashEnabled by rememberPreference(SplashOverlayEnabledKey, defaultValue = true)
    LaunchedEffect(Unit) {
        activity.dataStore.data.first()
        snapshotFlow { splashEnabled }.first()
        onReadyChange(true)
    }
    var coldSplash by remember(isReady) { mutableStateOf(splashEnabled) }
    var contentVisible by remember(isReady) { mutableStateOf(!coldSplash || disableAnimations) }
    var splashDone by remember(isReady) { mutableStateOf(!splashEnabled || disableAnimations) }
    LaunchedEffect(contentVisible) {
        if (contentVisible && (!splashEnabled || disableAnimations)) {
            splashDone = true
        }
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (disableAnimations) 0 else SplashConfig.Reveal.DURATION_MS, easing = EaseOut),
        label = "splashContentAlpha",
    )

    return AppSplashState(
        splashEnabled = splashEnabled,
        coldSplash = coldSplash,
        contentVisible = contentVisible,
        splashDone = splashDone,
        contentAlpha = contentAlpha,
        onBurstStart = {
            contentVisible = true
            coldSplash = false
        },
        onSplashDismiss = {
            splashDone = true
        },
    )
}
