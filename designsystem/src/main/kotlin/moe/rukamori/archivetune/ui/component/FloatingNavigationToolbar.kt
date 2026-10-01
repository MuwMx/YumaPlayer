@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import moe.rukamori.archivetune.designsystem.R
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassStroke
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable

private val BarHeight = 56.dp
private val BarMaxWidth = 344.dp
private val CornerRadius = 28.dp
private val InnerPadding = 12.dp
private val IconSize = 24.dp

private object NavBarColors {
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

    val capsuleShape = remember { RoundedCornerShape(CornerRadius) }
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
    val shadowDyPx = remember(density) { with(density) { 0.85.dp.toPx() } }
    val shadowBlurPx = remember(density) { with(density) { 2.67.dp.toPx() } }
    val cornerRadiusPx = remember(density) { with(density) { CornerRadius.toPx() } }
    val shadowPaint = remember(shadowDyPx, shadowBlurPx) {
        Paint().apply {
            nativePaint.apply {
                isAntiAlias = true
                color = android.graphics.Color.argb((0.15f * 255).toInt(), 0, 0, 0)
                setShadowLayer(
                    shadowBlurPx,
                    0f,
                    shadowDyPx,
                    android.graphics.Color.argb((0.20f * 255).toInt(), 0, 0, 0),
                )
            }
        }
    }

    val animatedVisibilityFactor by animateFloatAsState(
        targetValue = visibilityFactor.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 380,
            easing = EaseOut,
        ),
        label = "FloatingToolbarVisibility",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = 40.dp.toPx() * (1f - animatedVisibilityFactor)
                val scale = lerp(0.85f, 1.0f, animatedVisibilityFactor)
                scaleX = scale
                scaleY = scale
                alpha = animatedVisibilityFactor
            }
            .widthIn(max = BarMaxWidth)
            .fillMaxWidth()
            .height(BarHeight)
            .drawBehind {
                drawIntoCanvas { canvas ->
                    canvas.drawRoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = cornerRadiusPx,
                        radiusY = cornerRadiusPx,
                        paint = shadowPaint,
                    )
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
                .padding(horizontal = InnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                FluidTabsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onSearchItemDoubleClick = onSearchItemDoubleClick,
                )
            }

            if (hasOverflow) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
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

@Composable
private fun FluidTabsContainer(
    items: List<Screens>,
    pureBlack: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, screen ->
            val selected = isSelected(screen)

            val selectionFactor by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 320,
                    easing = LinearOutSlowInEasing,
                ),
                label = "SelectionFactor_$index",
            )

            val iconTint by animateColorAsState(
                targetValue = if (selected) {
                    NavBarColors.iconActive(pureBlack)
                } else {
                    NavBarColors.iconInactive(pureBlack)
                },
                animationSpec = tween(250),
                label = "IconTint_$index",
            )

            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "IconScale_$index",
            )

            val onClickLambda = remember(screen, selected, onItemClick) {
                { onItemClick(screen, selected) }
            }

            val onDoubleClickLambda = remember(screen, onSearchItemDoubleClick) {
                if (screen == Screens.Search) onSearchItemDoubleClick else null
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .yumaCombinedClickable(
                        pressedScale = 0.93f,
                        onClick = onClickLambda,
                        onDoubleClick = onDoubleClickLambda,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selectionFactor > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = lerp(0.6f, 1.0f, selectionFactor)
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.09f * selectionFactor,
                                ),
                                shape = CircleShape,
                            ),
                    )
                }

                Icon(
                    painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                    contentDescription = stringResource(screen.titleId),
                    tint = iconTint,
                    modifier = Modifier
                        .size(IconSize)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }
    }
}

@Composable
private fun ToolbarOverflowMenu(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
    onMusicTogetherClick: (() -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (pureBlack) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_recognition)) },
                onClick = {
                    expanded = false
                    onMusicRecognitionClick?.invoke()
                },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.mic), contentDescription = musicRecognitionContentDescription)
                },
                enabled = onMusicRecognitionClick != null,
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_together)) },
                onClick = {
                    expanded = false
                    onMusicTogetherClick?.invoke()
                },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.multi_user), contentDescription = null)
                },
                enabled = onMusicTogetherClick != null,
            )

            if (onShuffleClick != null && shuffleIconRes != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    onClick = {
                        expanded = false
                        onShuffleClick()
                    },
                    leadingIcon = {
                        Icon(painter = painterResource(shuffleIconRes), contentDescription = shuffleContentDescription)
                    },
                )
            }
        }
    }
}
