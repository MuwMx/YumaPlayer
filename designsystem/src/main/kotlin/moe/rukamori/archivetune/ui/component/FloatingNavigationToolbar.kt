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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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

    val shadowPaint = rememberNavigationShadowPaint()

    val animatedVisibilityFactor by animateFloatAsState(
        targetValue = visibilityFactor.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = NavigationBarVisibilityDurationMs,
            easing = EaseOut,
        ),
        label = "FloatingToolbarVisibility",
    )

    val dragState = rememberNavigationTabDragState(
        items = items,
        isSelected = isSelected,
        onItemClick = onItemClick,
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
            .fillMaxWidth()
            .height(NavigationBarHeight)
            .navigationShapeShadow(capsuleShape, shadowPaint)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NavigationBarInnerPaddingHorizontal),
            contentAlignment = Alignment.CenterStart,
        ) {
            NavigationDragSelectorOverlay(dragState)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationTabDragGestures(dragState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { screen ->
                    val selected = isSelected(screen)
                    NavigationTabItem(
                        screen = screen,
                        selected = selected,
                        pureBlack = pureBlack,
                        drawSelector = !dragState.isDragging,
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
}
