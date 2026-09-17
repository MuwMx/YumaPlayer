package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.SplashOverlayEnabledKey
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun SplashOverlay(
    modifier: Modifier = Modifier,
) {
    val splashOverlayEnabled by rememberPreference(SplashOverlayEnabledKey, defaultValue = true)
    if (!splashOverlayEnabled) return

    val animationsDisabled = LocalAnimationsDisabled.current
    var showSplash by remember { mutableStateOf(!animationsDisabled) }
    var sizeKey by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current.density
    val engine = remember { SplashEngine() }
    val renderer = remember { SplashRenderer() }
    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sizeKey) {
        if (sizeKey == 0 || !showSplash) return@LaunchedEffect
        engine.startGather(SplashSlots.SHAPE_LOGO)
        delay(SplashConfig.Timings.GATHER_LOGO_MS.toLong())
        engine.setPhase(SplashPhase.Ignite)
        delay(SplashConfig.Timings.IGNITE_FULL_MS.toLong())
        engine.triggerBurst()
    }

    LaunchedEffect(showSplash) {
        if (!showSplash) return@LaunchedEffect
        var lastTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0f, 0.033f)
                lastTime = now
                engine.update(dt, now / 1_000_000)
                frameTick = now

                if (engine.currentPhase == SplashPhase.Idle && showSplash) {
                    showSplash = false
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showSplash,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(durationMillis = 300)),
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color(0xFF0D0D11)) // Монолитный глухой фон
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w > 0f && h > 0f && (engine.width != w || engine.height != h)) {
                        engine.density = density
                        engine.init(w, h)
                        sizeKey++
                    }
                },
        ) {
            frameTick
            with(renderer) {
                render(engine)
            }
        }
    }
}