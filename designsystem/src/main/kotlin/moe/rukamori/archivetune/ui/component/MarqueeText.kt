package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalMarqueeEnabled

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    isVisible: Boolean = true,
    // A scrolling text redraws every frame, so by default it scrolls through once and then rests.
    // The player passes more while it is open.
    iterations: Int = DEFAULT_MARQUEE_ITERATIONS,
) {
    var hasOverflow by remember(text) { mutableStateOf(false) }
    val marqueeEnabled = LocalMarqueeEnabled.current

    val marqueeModifier = if (hasOverflow && isVisible && marqueeEnabled) {
        Modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val leftFadePx = 4.dp.toPx()
                val rightFadePx = 24.dp.toPx()
                val leftFade = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = leftFadePx
                )
                val rightFade = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - rightFadePx,
                    endX = size.width
                )
                val canFade = size.width > leftFadePx + rightFadePx

                // Built here, not in onDrawWithContent: this block reruns only on a size
                // change, so a scrolling marquee stops allocating two brushes per frame.
                onDrawWithContent {
                    drawContent()
                    if (canFade) {
                        drawRect(brush = leftFade, blendMode = BlendMode.DstIn)
                        drawRect(brush = rightFade, blendMode = BlendMode.DstIn)
                    }
                }
            }
            .basicMarquee(
                iterations = iterations,
                initialDelayMillis = 2000,
                spacing = MarqueeSpacing(32.dp),
                velocity = 30.dp
            )
    } else {
        Modifier
    }

    Text(
        text = text,
        modifier = modifier.then(marqueeModifier),
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = if (marqueeEnabled) overflow else TextOverflow.Ellipsis,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = {
            if (!hasOverflow && it.hasVisualOverflow) {
                hasOverflow = true
            }
            onTextLayout?.invoke(it)
        },
        style = style
    )
}

private const val DEFAULT_MARQUEE_ITERATIONS = 1
