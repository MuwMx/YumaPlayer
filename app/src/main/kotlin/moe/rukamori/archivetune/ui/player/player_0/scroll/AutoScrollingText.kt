package moe.rukamori.archivetune.ui.player.player_0.scroll

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * Умная обертка: включает бегущую строку только при наличии реального оверфлоу
 * и полной развертке шторки (для плавности анимации свайпа).
 */
@Composable
fun AutoScrollingTextOnDemand(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    gradientEdgeColor: Color = Color.Black,
    modifier: Modifier = Modifier,
    expansionFractionProvider: () -> Float = { 1f }, // По умолчанию скроллинг разрешен всегда
    canScroll: Boolean = true
) {
    val disableAnimations by rememberPreference(DisableAnimationsKey, defaultValue = false)
    val effectiveCanScroll = canScroll && !disableAnimations

    var overflow by remember(text, style) { mutableStateOf(false) }
    val canStart by remember(text, style) {
        derivedStateOf { expansionFractionProvider() > 0.99f && overflow }
    }

    if (!overflow) {
        // Тестовый невидимый замерщик для обнаружения выхода за границы
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { res: TextLayoutResult -> overflow = res.hasVisualOverflow },
            modifier = modifier
        )
    } else {
        AutoScrollingText(
            text = text,
            style = style,
            textAlign = TextAlign.Start,
            gradientEdgeColor = gradientEdgeColor,
            modifier = modifier,
            canScroll = effectiveCanScroll && canStart
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    gradientEdgeColor: Color = Color.Black,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    gradientWidth: Dp = 24.dp,
    canScroll: Boolean = true
) {
    val disableAnimations by rememberPreference(DisableAnimationsKey, defaultValue = false)
    val effectiveCanScroll = canScroll && !disableAnimations

    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        // Измеряем реальный размер текста без ограничений по ширине экрана
        val textPlaceable = subcompose("text") {
            Text(text = text, style = style, maxLines = 1)
        }[0].measure(constraints.copy(maxWidth = Int.MAX_VALUE))

        val isOverflowing = textPlaceable.width > constraints.maxWidth

        val content = @Composable {
            if (isOverflowing && effectiveCanScroll) {
                val initialDelayMillis = 2000
                val fadeAnimationDuration = 500

                var isScrolling by remember(text, effectiveCanScroll) { mutableStateOf(false) }
                LaunchedEffect(text, effectiveCanScroll) {
                    isScrolling = false
                    delay(initialDelayMillis.toLong())
                    isScrolling = true
                }

                // Плавное появление левого затухания только после старта движения
                val animatedLeftGradientStartColor by animateColorAsState(
                    targetValue = if (isScrolling) Color.Transparent else gradientEdgeColor,
                    animationSpec = tween(durationMillis = fadeAnimationDuration),
                    label = "LeftGradientStartColor"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val gradientWidthPx = gradientWidth.toPx()

                            // Мягкое левое растворение букв при скролле
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(animatedLeftGradientStartColor, gradientEdgeColor),
                                    startX = 0f,
                                    endX = gradientWidthPx
                                ),
                                blendMode = BlendMode.DstIn
                            )
                            // Постоянное правое растворение на границе экрана
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(gradientEdgeColor, Color.Transparent),
                                    startX = size.width - gradientWidthPx,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Text(
                        text = text,
                        style = style,
                        textAlign = textAlign,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(gradientWidth + 6.dp),
                            velocity = 25.dp,
                            initialDelayMillis = initialDelayMillis
                        )
                    )
                }
            } else if (isOverflowing) {
                // Текст длинный, но скроллинг заблокирован (например, отключены анимации) — просто плавно тушим правый край
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val gradientWidthPx = gradientWidth.toPx()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(gradientEdgeColor, Color.Transparent),
                                    startX = size.width - gradientWidthPx,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Text(
                        text = text,
                        style = style,
                        textAlign = textAlign,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Текст короткий — рендерим стандартный легковесный Text
                Text(
                    text = text,
                    style = style,
                    textAlign = textAlign,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        val contentPlaceable = subcompose("content", content)[0].measure(constraints)
        val targetWidth = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: contentPlaceable.width

        layout(targetWidth, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}
