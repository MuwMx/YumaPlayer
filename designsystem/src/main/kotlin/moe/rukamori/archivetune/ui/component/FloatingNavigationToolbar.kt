@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import moe.rukamori.archivetune.constants.NavigationBarCornerRadius
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarHideMinScale
import moe.rukamori.archivetune.constants.NavigationBarHideOffsetY
import moe.rukamori.archivetune.constants.NavigationBarInnerPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.NavigationBarOverflowPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationBarShadowAlpha
import moe.rukamori.archivetune.constants.NavigationBarShadowBlur
import moe.rukamori.archivetune.constants.NavigationBarShadowDy
import moe.rukamori.archivetune.constants.NavigationBarShadowLayerAlpha
import moe.rukamori.archivetune.constants.NavigationBarVisibilityDurationMs
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassStroke

internal object NavBarColors {
    @Composable
    fun container(pureBlack: Boolean) = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

    @Composable
    fun iconActive(pureBlack: Boolean) = if (pureBlack) Color.White else MaterialTheme.colorScheme.primary

    @Composable
    fun iconInactive(pureBlack: Boolean) = if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    capsuleShape: Shape = RoundedCornerShape(NavigationBarCornerRadius),
    hazeState: HazeState? = null,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    showBorder: Boolean = true,
    blurEnabled: Boolean = true,
    visibilityFactor: Float = 1f,
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onMusicTogetherClick: (() -> Unit)? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val hasOverflow = false

    val containerColor = NavBarColors.container(pureBlack)

    val fixedTintAlpha = if (pureBlack) SettingsDimensions.HazePureBlackTintAlpha else SettingsDimensions.HazeDefaultTintAlpha
    val hazeStyle = remember(containerColor, blurRadius, pureBlack) {
        HazeDefaults.style(
            backgroundColor = containerColor,
            tint = HazeTint(containerColor.copy(alpha = fixedTintAlpha)),
            blurRadius = blurRadius.dp,
            noiseFactor = SettingsDimensions.HazeNoiseFactor,
        )
    }

    val density = LocalDensity.current
    val shadowDyPx = remember(density) { with(density) { NavigationBarShadowDy.toPx() } }
    val shadowBlurPx = remember(density) { with(density) { NavigationBarShadowBlur.toPx() } }
    val shadowPaint = remember(shadowDyPx, shadowBlurPx) {
        Paint().apply {
            nativePaint.apply {
                isAntiAlias = true
                color = android.graphics.Color.argb((NavigationBarShadowAlpha * 255).toInt(), 0, 0, 0)
                setShadowLayer(
                    shadowBlurPx,
                    0f,
                    shadowDyPx,
                    android.graphics.Color.argb((NavigationBarShadowLayerAlpha * 255).toInt(), 0, 0, 0),
                )
            }
        }
    }

    val animatedVisibilityFactor by animateFloatAsState(
        targetValue = visibilityFactor.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = NavigationBarVisibilityDurationMs,
            easing = EaseOut,
        ),
        label = "FloatingToolbarVisibility",
    )



    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = NavigationBarHideOffsetY.toPx() * (1f - animatedVisibilityFactor)
                val scale = lerp(NavigationBarHideMinScale, 1.0f, animatedVisibilityFactor)
                scaleX = scale
                scaleY = scale
                alpha = animatedVisibilityFactor
            }
            .widthIn(max = NavigationBarMaxWidth)
            .fillMaxWidth()
            .height(NavigationBarHeight)
            .drawBehind {
                val outline = capsuleShape.createOutline(size, layoutDirection, this)
                val shadowPath = Path().apply {
                    when (outline) {
                        is Outline.Rectangle -> addRect(outline.rect)
                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                        is Outline.Generic -> addPath(outline.path)
                    }
                }
                drawIntoCanvas { canvas ->
                    canvas.drawPath(shadowPath, shadowPaint)
                }
            }
            .clip(capsuleShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = hazeStyle,
                    ) {
                        inputScale = HazeInputScale.Fixed(SettingsDimensions.HazeInputScaleValue)
                        this.blurEnabled = blurEnabled
                    }
                } else {
                    Modifier.background(containerColor)
                },
            )
            .then(
                if (showBorder) {
                    Modifier.glassStroke(
                        shape = capsuleShape,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                        topAlpha = SettingsDimensions.GlassBorderTopAlpha,
                        bottomAlpha = SettingsDimensions.GlassBorderBottomAlpha,
                        topColor = Color.White,
                        bottomColor = Color.Black,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NavigationBarInnerPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { screen ->
                val selected = isSelected(screen)
                NavigationTabItem(
                    screen = screen,
                    selected = selected,
                    pureBlack = pureBlack,
                    onClick = remember(screen, selected, onItemClick) {
                        { onItemClick(screen, selected) }
                    },
                    onDoubleClick = remember(screen, onSearchItemDoubleClick) {
                        if (screen == Screens.Search) onSearchItemDoubleClick else null
                    },
                )
            }

            if (hasOverflow) {
                Box(
                    modifier = Modifier
                        .padding(
                            start = NavigationBarOverflowPaddingHorizontal,
                            end = NavigationBarOverflowPaddingHorizontal,
                        )
                        .wrapContentSize(),
                ) {
                    ToolbarOverflowMenu(
                        pureBlack = pureBlack,
                        onShuffleClick = onShuffleClick,
                        shuffleIconRes = shuffleIconRes,
                        shuffleContentDescription = shuffleContentDescription,
                        onMusicRecognitionClick = onMusicRecognitionClick,
                        musicRecognitionContentDescription = musicRecognitionContentDescription,
                        onMusicTogetherClick = onMusicTogetherClick,
                    )
                }
            }
        }
    }
}