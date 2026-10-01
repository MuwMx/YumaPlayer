@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassStroke

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
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical

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

    var isDragging by remember { mutableStateOf(false) }
    var currentDragIndex by remember { mutableIntStateOf(-1) }
    val selectorXAnimatable = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val tabCount = items.size
    val tabWidthPx = if (tabCount > 0) rowWidthPx / tabCount else 0f
    fun tabCenterX(index: Int): Float = tabWidthPx * (index + 0.5f)
    val minCenterX = if (tabCount > 0) tabCenterX(0) else 0f
    val maxCenterX = if (tabCount > 0) tabCenterX(tabCount - 1) else 0f

    val finishDrag: () -> Unit = {
        if (tabCount > 0 && tabWidthPx > 0f) {
            val targetIndex = currentDragIndex.coerceIn(0, tabCount - 1)
            val nearestCenter = tabCenterX(targetIndex)
            coroutineScope.launch {
                selectorXAnimatable.animateTo(
                    targetValue = nearestCenter,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
                isDragging = false
            }
        } else {
            isDragging = false
        }
    }

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
                drawIntoCanvas { canvas ->
                    val outline = capsuleShape.createOutline(size, layoutDirection, this)
                    when (outline) {
                        is Outline.Rounded -> {
                            val path = Path().apply { addRoundRect(outline.roundRect) }
                            canvas.drawPath(path, shadowPaint)
                        }
                        is Outline.Generic -> {
                            canvas.drawPath(outline.path, shadowPaint)
                        }
                        is Outline.Rectangle -> {
                            canvas.drawRect(
                                left = 0f,
                                top = 0f,
                                right = size.width,
                                bottom = size.height,
                                paint = shadowPaint,
                            )
                        }
                    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NavigationBarInnerPaddingHorizontal),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isDragging && tabWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = NavigationTabSlotPaddingVertical)
                        .width(with(density) { tabWidthPx.toDp() })
                        .graphicsLayer {
                            translationX = selectorXAnimatable.value - tabWidthPx / 2f
                        }
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha),
                            shape = CircleShape,
                        ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rowWidthPx = it.width.toFloat() }
                    .pointerInput(items) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                if (tabCount <= 0 || tabWidthPx <= 0f) return@detectDragGesturesAfterLongPress
                                isDragging = true
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                val initialX = offset.x.coerceIn(minCenterX, maxCenterX)
                                coroutineScope.launch {
                                    selectorXAnimatable.snapTo(initialX)
                                }
                                val targetIndex = ((initialX / tabWidthPx).toInt()).coerceIn(0, tabCount - 1)
                                currentDragIndex = targetIndex
                                if (!isSelected(items[targetIndex])) {
                                    view.performClick()
                                    onItemClick(items[targetIndex], false)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (tabCount <= 0 || tabWidthPx <= 0f) return@detectDragGesturesAfterLongPress
                                val clampedFingerX = change.position.x.coerceIn(minCenterX, maxCenterX)
                                coroutineScope.launch {
                                    selectorXAnimatable.animateTo(
                                        targetValue = clampedFingerX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                                val targetIndex = ((clampedFingerX / tabWidthPx).toInt()).coerceIn(0, tabCount - 1)
                                if (targetIndex != currentDragIndex) {
                                    currentDragIndex = targetIndex
                                    view.performClick()
                                    onItemClick(items[targetIndex], false)
                                }
                            },
                            onDragEnd = finishDrag,
                            onDragCancel = finishDrag,
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { screen ->
                    val selected = isSelected(screen)
                    NavigationTabItem(
                        screen = screen,
                        selected = selected,
                        pureBlack = pureBlack,
                        drawSelector = !isDragging,
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