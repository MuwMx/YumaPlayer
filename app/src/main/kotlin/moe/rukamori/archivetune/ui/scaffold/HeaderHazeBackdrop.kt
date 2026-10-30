package moe.rukamori.archivetune.ui.scaffold

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberPreference

// how far the plain backdrop (used without blur) fades out below the bar
private val FallbackFadeHeight = 20.dp

@Composable
fun HeaderHazeBackdrop(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    hasCollapsibleHeader: Boolean = false,
    hazeState: HazeState?,
    pureBlack: Boolean? = null,
    blurRadius: Float? = null,
    height: Dp? = null,
    alwaysShowFallback: Boolean = false,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
) {
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurNavBar) = rememberPreference(BlurNavBarKey, true)
    val animationsDisabled = LocalAnimationsDisabled.current
    val (pureBlackPref) = rememberPreference(PureBlackKey, false)
    val (blurRadiusPref) = rememberPreference(BlurRadiusKey, SettingsDimensions.BlurRadiusDefault)

    val effectivePureBlack = pureBlack ?: pureBlackPref
    val effectiveBlurRadius = blurRadius ?: blurRadiusPref

    val isBlurActive = blurNavBar && !disableBlur &&
        !animationsDisabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        hazeState != null

    val statusBarTop = with(LocalDensity.current) { WindowInsets.systemBars.getTop(this).toDp() }
    val effectiveHeight = height ?: (AppBarHeight + statusBarTop)

    val hazeStyle = if (isBlurActive) {
        remember(surfaceColor, effectiveBlurRadius, effectivePureBlack) {
            val fixedTintAlpha = if (effectivePureBlack) {
                SettingsDimensions.HazePureBlackTintAlpha
            } else {
                SettingsDimensions.HazeDefaultTintAlpha
            }
            val tintEffect = HazeColorEffect.tint(surfaceColor.copy(alpha = fixedTintAlpha))
            HazeBlurStyle {
                blurEnabled(true)
                backgroundColor(if (effectivePureBlack) Color.Black else surfaceColor)
                colorEffects(listOf(tintEffect))
                fallbackColorEffect(tintEffect)
                blurRadius(effectiveBlurRadius.dp)
                noiseFactor(SettingsDimensions.HazeNoiseFactor)
                mask(
                    Brush.verticalGradient(
                        0.0f to Color.Black,
                        0.65f to Color.Black.copy(alpha = 0.8f),
                        1.0f to Color.Transparent,
                    ),
                )
            }
        }
    } else {
        null
    }

    // the backdrop used without blur, in black when Pure Black is on so it matches the screen behind it
    val fallbackColor = if (effectivePureBlack) Color.Black else surfaceColor
    val fallbackBrush =
        remember(fallbackColor) {
            Brush.verticalGradient(
                listOf(
                    fallbackColor.copy(alpha = 0.95f),
                    fallbackColor.copy(alpha = 0.85f),
                    fallbackColor.copy(alpha = 0.6f),
                    Color.Transparent,
                ),
            )
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(effectiveHeight)
            .then(
                if (isBlurActive && hazeStyle != null && hazeState != null) {
                    Modifier
                        .graphicsLayer { alpha = backdropAlpha(progress(), hasCollapsibleHeader) }
                        .hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = hazeStyle,
                            performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    surfaceColor.copy(alpha = 0.75f),
                                    surfaceColor.copy(alpha = 0.50f),
                                    surfaceColor.copy(alpha = 0.20f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                } else if (alwaysShowFallback) {
                    Modifier.background(fallbackBrush)
                } else {
                    // Without blur (turned off, or before Android 12) content would scroll under the bar with
                    // nothing behind its title and buttons. The plain backdrop fades in exactly like the blurred
                    // one, so a header's artwork stays clear at the top of the list.
                    // It stays nearly solid behind the title and buttons and fades out just below the bar, so
                    // rows slide away under it instead of showing through the title.
                    Modifier.drawBehind {
                        val alpha = backdropAlpha(progress(), hasCollapsibleHeader)
                        if (alpha > 0f) {
                            val total = size.height + FallbackFadeHeight.toPx()
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        0f to fallbackColor.copy(alpha = 0.97f * alpha),
                                        size.height / total to fallbackColor.copy(alpha = 0.93f * alpha),
                                        1f to Color.Transparent,
                                        endY = total,
                                    ),
                                size = Size(size.width, total),
                            )
                        }
                    }
                },
            ),
    )
}

/** How far the backdrop has faded in: with a collapsible header, only once the header is nearly gone. */
private fun backdropAlpha(
    progress: Float,
    hasCollapsibleHeader: Boolean,
): Float = if (hasCollapsibleHeader) smoothstep(0.70f, 0.95f, progress) else progress

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
