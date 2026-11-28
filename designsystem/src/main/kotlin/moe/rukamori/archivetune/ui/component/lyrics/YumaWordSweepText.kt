/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

internal class AlphaTransState {
    private var fromAlpha: Float = -1f
    private var toAlpha: Float = -1f
    private var startWallSec: Double = 0.0

    fun current(
        targetAlpha: Float,
        nowWallSec: Double,
        durationSec: Float,
        ease: YumaCubicBezier,
    ): Float {
        if (fromAlpha < 0f) {
            fromAlpha = targetAlpha
            toAlpha = targetAlpha
            return targetAlpha
        }
        val dur = durationSec.coerceAtLeast(0.001f)
        if (abs(targetAlpha - toAlpha) > 1e-4f) {
            fromAlpha = sample(nowWallSec, dur, ease)
            toAlpha = targetAlpha
            startWallSec = nowWallSec
        }
        return sample(nowWallSec, dur, ease)
    }

    private fun sample(nowWallSec: Double, dur: Float, ease: YumaCubicBezier): Float {
        if (abs(toAlpha - fromAlpha) <= 1e-4f) return toAlpha
        val p = ((nowWallSec - startWallSec) / dur).toFloat().coerceIn(0f, 1f)
        if (p >= 1f) {
            fromAlpha = toAlpha
            return toAlpha
        }
        return lerpFloat(fromAlpha, toAlpha, ease.at(p))
    }
}

internal class LinePlaybackTracker {
    var selected: Boolean = false
    var exitStartWallSec: Double? = null
    var exitSongSec: Double = 0.0
    var decoBornWallSec: Double? = null
    var lastLineIndex: Int = -1
    val alphaTrans: AlphaTransState = AlphaTransState()
}

@Composable
internal fun YumaLyricsLineCanvas(
    prepared: PreparedLyricsLine,
    nextLineStartSec: Double,
    positionMsProvider: () -> Long,
    lineAlphaProvider: () -> Float,
    style: YumaLyricsStyle,
    modifier: Modifier = Modifier,
    animFrameProvider: () -> Long = { 0L },
) {
    val density = LocalDensity.current
    val tracker = remember(prepared) { LinePlaybackTracker() }
    val heightDp = with(density) { prepared.totalHeightPx.toDp() }
    val layerPaint = remember { Paint() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.coerceAtLeast(1.dp)),
    ) {
        val frameNanos = animFrameProvider()
        val posMs = positionMsProvider()
        val tSec = posMs / 1000.0
        val nowWallSec = (if (frameNanos != 0L) frameNanos else System.nanoTime()) / 1_000_000_000.0
        val tokens = style.tokens
        val timing = tokens.timing
        val motion = tokens.motion
        val visual = tokens.visual
        val targetOpacity = lineAlphaProvider().coerceIn(0f, 1f)
        val lineOpacity = tracker.alphaTrans.current(
            targetAlpha = targetOpacity,
            nowWallSec = nowWallSec,
            durationSec = motion.opacityTransitionSecs,
            ease = motion.ease,
        ).coerceIn(0f, 1f)
        if (lineOpacity <= 0.001f) return@Canvas

        if (tracker.lastLineIndex != prepared.lineIndex) {
            tracker.lastLineIndex = prepared.lineIndex
            tracker.selected = false
            tracker.exitStartWallSec = null
            tracker.decoBornWallSec = null
        }

        val lineEndSec = max(nextLineStartSec, prepared.startSec + prepared.durSec + 0.05)
        val isSelected = tSec + 2.0 >= prepared.startSec && tSec < lineEndSec
        if (isSelected && !tracker.selected) {
            tracker.selected = true
            tracker.exitStartWallSec = null
        } else if (!isSelected && tracker.selected) {
            tracker.selected = false
            val staleSec = lineEndSec + timing.exitFadeSec + 0.05
            tracker.exitStartWallSec = if (tSec <= staleSec) nowWallSec else null
            tracker.exitSongSec = tSec
        }
        val activeExitWall = tracker.exitStartWallSec
        if (activeExitWall != null && nowWallSec - activeExitWall > timing.exitFadeSec) {
            tracker.exitStartWallSec = null
        }

        val isLive = tracker.selected && tracker.exitStartWallSec == null && tSec >= prepared.startSec

        if (prepared.isInstrumental) {
            drawInstrumentalBreak(
                prepared = prepared,
                tSec = tSec,
                nowWallSec = nowWallSec,
                tracker = tracker,
                style = style,
                lineOpacity = lineOpacity,
            )
            return@Canvas
        }

        val words = prepared.words
        val groups = prepared.groups
        val dimColor = style.textColor.copy(alpha = visual.dimAlpha * lineOpacity)
        val bgDimColor = style.textColor.copy(alpha = visual.dimAlpha * visual.backgroundAlpha * lineOpacity)

        val groupWobbles = ArrayList<Triple<Float, Float, Float>?>(groups.size)
        for (gr in groups) {
            if (motion.wobble.enabled && gr.wobbles && isLive && tracker.exitStartWallSec == null) {
                val firstWord = words[gr.firstWordIndex]
                val (txEm, sx) = wobble(tSec - firstWord.startSec, motion.wobble, motion.ease, motion.easeInOut)
                if (abs(sx - 1f) > 1e-4f || abs(txEm) > 1e-4f) {
                    groupWobbles.add(Triple(gr.centerX, txEm * firstWord.fontSizePx, sx))
                } else {
                    groupWobbles.add(null)
                }
            } else {
                groupWobbles.add(null)
            }
        }

        // Pass 1: dim layer
        for (word in words) {
            val wobble = groupWobbles.getOrNull(word.groupIndex)
            val pass1Color = if (word.isBackground) bgDimColor else dimColor
            drawWordPass(
                word = word,
                wobble = wobble,
                tSec = tSec,
                isLive = isLive,
                isExiting = tracker.exitStartWallSec != null,
                tokens = tokens,
                color = pass1Color,
                brush = null,
                shadow = null,
            )
        }

        // Pass 2: lit layer + glyph-contour glow
        val glowFromPx = visual.baseFontSizeSp * style.textScale * visual.glowRemRatio * density.density
        for (word in words) {
            val look = computeWordLook(
                wordStartSec = word.startSec,
                wordDurSec = word.durSec,
                isLineSynced = word.isLineSynced,
                isLong = word.isLong,
                tSec = tSec,
                isLive = isLive,
                exitSinceWallSec = tracker.exitStartWallSec?.let { nowWallSec - it },
                exitSongSec = tracker.exitSongSec,
                glowFromPx = glowFromPx,
                tokens = tokens,
            )
            if (look.opacity <= 0f || look.end <= 0f) continue

            val wobble = groupWobbles.getOrNull(word.groupIndex)
            val litAlpha = look.opacity * lineOpacity * (if (word.isBackground) visual.backgroundAlpha else visual.activeAlpha)
            val litColor = style.textColor.copy(alpha = litAlpha.coerceIn(0f, 1f))
            val isFullyLit = look.start >= 1f

            val shouldDrawGlow = look.glowRadiusPx > 0.05f && look.glowAlpha >= visual.glowInvisibleAlpha
            if (shouldDrawGlow) {
                val glowAlpha = (look.glowAlpha * look.opacity * lineOpacity).coerceIn(0f, 1f)
                val glowColor = style.textColor.copy(alpha = glowAlpha)
                val sigma = look.glowRadiusPx * 0.5f
                val pad = 3f * sigma
                val wordBox = transformedWordRect(word, wobble)
                val layerRect = Rect(
                    left = wordBox.left - pad,
                    top = wordBox.top - pad,
                    right = wordBox.right + pad,
                    bottom = wordBox.bottom + pad,
                )
                if (isFullyLit) {
                    drawWordPass(
                        word = word,
                        wobble = wobble,
                        tSec = tSec,
                        isLive = isLive,
                        isExiting = tracker.exitStartWallSec != null,
                        tokens = tokens,
                        color = glowColor,
                        brush = null,
                        shadow = Shadow(color = glowColor, offset = Offset.Zero, blurRadius = look.glowRadiusPx),
                    )
                } else {
                    drawContext.canvas.saveLayer(layerRect, layerPaint)
                    drawWordPass(
                        word = word,
                        wobble = wobble,
                        tSec = tSec,
                        isLive = isLive,
                        isExiting = tracker.exitStartWallSec != null,
                        tokens = tokens,
                        color = glowColor,
                        brush = null,
                        shadow = Shadow(color = glowColor, offset = Offset.Zero, blurRadius = look.glowRadiusPx),
                    )
                    val maskBrush = buildWordSweepBrush(
                        word = word,
                        wordBox = wordBox,
                        startFrac = look.start,
                        endFrac = look.end,
                        color = Color.White,
                        originX = wordBox.left,
                    )
                    if (maskBrush != null) {
                        drawRect(
                            brush = maskBrush,
                            topLeft = layerRect.topLeft,
                            size = layerRect.size,
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    drawContext.canvas.restore()
                }
            }

            if (isFullyLit) {
                drawWordPass(
                    word = word,
                    wobble = wobble,
                    tSec = tSec,
                    isLive = isLive,
                    isExiting = tracker.exitStartWallSec != null,
                    tokens = tokens,
                    color = litColor,
                    brush = null,
                    shadow = null,
                )
            } else {
                drawWordSweepPass(
                    word = word,
                    wobble = wobble,
                    tSec = tSec,
                    isLive = isLive,
                    isExiting = tracker.exitStartWallSec != null,
                    tokens = tokens,
                    look = look,
                    litColor = litColor,
                )
            }
        }

        // Decorations (romanization pill + translation)
        if (prepared.decos.isNotEmpty()) {
            val born = tracker.decoBornWallSec ?: nowWallSec.also { tracker.decoBornWallSec = it }
            val enterP = ((nowWallSec - born) / timing.decoEntrySec.coerceAtLeast(0.01f)).toFloat().coerceIn(0f, 1f)
            val e = motion.decoEase.at(enterP)
            val decoVis = e * lineOpacity

            for (deco in prepared.decos) {
                val dy = -0.4f * deco.fontSizePx * (1f - e)
                if (deco.isPill && deco.pillRect != null) {
                    val r = deco.pillRect.translate(0f, dy)
                    val corner = CornerRadius(deco.fontSizePx * 0.6f, deco.fontSizePx * 0.6f)
                    drawRoundRect(
                        color = style.textColor.copy(alpha = visual.romanizationPillFillAlpha * decoVis),
                        topLeft = r.topLeft,
                        size = r.size,
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = style.textColor.copy(alpha = visual.romanizationPillStrokeAlpha * decoVis),
                        topLeft = r.topLeft,
                        size = r.size,
                        cornerRadius = corner,
                        style = Stroke(width = 1f),
                    )
                }
                val baseAlpha = if (deco.isRomanization) visual.romanizationAlpha else visual.translationAlpha
                drawText(
                    textLayoutResult = deco.textLayout,
                    color = style.textColor.copy(alpha = (baseAlpha * decoVis).coerceIn(0f, 1f)),
                    topLeft = Offset(deco.x, deco.y + dy),
                )
            }
        }
    }
}

private fun DrawScope.drawInstrumentalBreak(
    prepared: PreparedLyricsLine,
    tSec: Double,
    nowWallSec: Double,
    tracker: LinePlaybackTracker,
    style: YumaLyricsStyle,
    lineOpacity: Float,
) {
    val visual = style.tokens.visual
    val timing = style.tokens.timing
    val motion = style.tokens.motion
    val ease = motion.ease
    val e = tSec - prepared.startSec
    val d = max(prepared.durSec, 0.001)
    val p = (e / d).toFloat().coerceIn(0f, 1f)

    val lit = when {
        tracker.exitStartWallSec != null -> {
            val since = (nowWallSec - (tracker.exitStartWallSec ?: nowWallSec)).toFloat()
            1f - ease.at((since / timing.exitFadeSec).coerceIn(0f, 1f))
        }
        tracker.selected && e >= 0.0 -> ease.at((e / 0.15).toFloat().coerceIn(0f, 1f))
        else -> 0f
    }

    val em = prepared.instrumentalSizePx
    val baseR = em * 0.14f
    val spacing = 2.5f
    val dim = visual.dimAlpha * lineOpacity
    val periodSec = visual.instrumentalDotWavePeriodSec.toDouble().coerceAtLeast(0.1)
    val leadSec = timing.instrumentalCollapseLeadSec.toDouble().coerceAtLeast(0.001)
    val (collapseScale, collapseDy, collapseAlpha) = if (d > leadSec && e > d - leadSec) {
        val cp = ((e - (d - leadSec)) / leadSec).toFloat().coerceIn(0f, 1f)
        val c = motion.easeIn.at(cp)
        Triple((1f - c).coerceAtLeast(0f), em * visual.instrumentalCollapseDropEm * c, (1f - c).coerceIn(0f, 1f))
    } else {
        Triple(1f, 0f, 1f)
    }

    for (i in 0 until 3) {
        val phase = 2.0 * PI * max(e, 0.0) / periodSec + i * visual.instrumentalDotPhaseOffsetRad
        val wave = sin(phase).toFloat()
        val cx = prepared.instrumentalX + baseR * (1f + spacing * i)
        val floatDy = if (lit > 0f) wave * em * visual.instrumentalDotFloatEm * lit else 0f
        val cy = prepared.instrumentalCenterY + floatDy + collapseDy
        val pulseR = baseR * (1f + visual.instrumentalDotPulseScale * wave * lit)
        val r = pulseR * collapseScale
        val fill = ((p * 3f - i).coerceIn(0f, 1f) * lit).coerceAtLeast(0f)
        val alpha = ((dim + (lineOpacity - dim) * fill) * collapseAlpha).coerceIn(0f, 1f)
        if (r > 0.05f && alpha > 0.001f) {
            drawCircle(
                color = style.textColor.copy(alpha = alpha),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}

private fun transformedWordRect(
    word: PreparedWord,
    wobble: Triple<Float, Float, Float>?,
): Rect {
    if (wobble == null) {
        return Rect(word.x, word.y, word.x + word.width, word.y + word.height)
    }
    val (cx, tx, sx) = wobble
    val left = cx + (word.x - cx) * sx + tx
    val right = cx + (word.x + word.width - cx) * sx + tx
    return Rect(minOf(left, right), word.y, maxOf(left, right), word.y + word.height)
}

internal data class SweepGradientBounds(
    val startX: Float,
    val endX: Float,
    val isRtl: Boolean,
)

internal fun computeSweepGradientBounds(
    width: Float,
    isRtl: Boolean,
    startFrac: Float,
    endFrac: Float,
    originX: Float = 0f,
): SweepGradientBounds? {
    val w = width.coerceAtLeast(1f)
    return if (isRtl) {
        val xs = originX + w * (1f - startFrac)
        val xe = originX + w * (1f - endFrac) - 1f
        if (abs(xs - xe) < 0.5f) return null
        SweepGradientBounds(startX = xe, endX = xs, isRtl = true)
    } else {
        val xs = originX + w * startFrac
        val xe = originX + w * endFrac + 1f
        if (abs(xe - xs) < 0.5f) return null
        SweepGradientBounds(startX = xs, endX = xe, isRtl = false)
    }
}

private fun buildWordSweepBrush(
    word: PreparedWord,
    wordBox: Rect,
    startFrac: Float,
    endFrac: Float,
    color: Color,
    originX: Float,
): Brush? {
    val bounds = computeSweepGradientBounds(
        width = wordBox.width,
        isRtl = word.isRtl,
        startFrac = startFrac,
        endFrac = endFrac,
        originX = originX,
    ) ?: return null
    val transparent = color.copy(alpha = 0f)
    return Brush.horizontalGradient(
        colors = if (bounds.isRtl) listOf(transparent, color) else listOf(color, transparent),
        startX = bounds.startX,
        endX = bounds.endX,
    )
}

private fun DrawScope.drawWordPass(
    word: PreparedWord,
    wobble: Triple<Float, Float, Float>?,
    tSec: Double,
    isLive: Boolean,
    isExiting: Boolean,
    tokens: YumaLyricsTokens,
    color: Color,
    brush: Brush?,
    shadow: Shadow?,
) {
    val motion = tokens.motion
    val bounce = motion.wordBounce?.takeIf { isLive && !isExiting && !word.isLineSynced }
        ?.let { wordBounce(tSec - word.startSec, word.durSec, it) }

    val canUseLetters = word.useLetters && word.letterLayouts.size == word.text.length && isLive && !isExiting
    withWobbleAndBounce(word, wobble, bounce) {
        if (!canUseLetters) {
            if (brush != null) {
                drawText(word.textLayout, brush = brush, topLeft = Offset(word.x, word.y), shadow = shadow)
            } else {
                drawText(word.textLayout, color = color, topLeft = Offset(word.x, word.y), shadow = shadow)
            }
        } else {
            val waves = letterWaves(
                letterCount = word.text.length,
                wordDurSec = word.durSec,
                floatSec = motion.letterWave.secs.toDouble(),
                timing = tokens.timing,
                crestFraction = motion.letterWave.crestFraction,
            )
            val elapsed = tSec - word.startSec
            for (idx in word.text.indices) {
                val charLayout = word.letterLayouts[idx]
                val charX = word.x + word.letterStartXs[idx]
                val charY = word.y
                val (liftEm, scale) = letterFloat(
                    elapsedSec = elapsed,
                    wave = waves[idx],
                    isLong = word.isLong,
                    tokens = motion.letterWave,
                    easeInOut = motion.easeInOut,
                    easeOut = motion.easeOut,
                )
                val liftPx = -liftEm * word.fontSizePx
                val pivot = Offset(
                    x = charX + charLayout.size.width * 0.5f,
                    y = charY + charLayout.size.height * 0.5f,
                )
                withTransform({
                    if (liftPx != 0f) translate(top = liftPx)
                    if (abs(scale - 1f) > 1e-4f) scale(scale, scale, pivot = pivot)
                }) {
                    if (brush != null) {
                        drawText(charLayout, brush = brush, topLeft = Offset(charX, charY), shadow = shadow)
                    } else {
                        drawText(charLayout, color = color, topLeft = Offset(charX, charY), shadow = shadow)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawWordSweepPass(
    word: PreparedWord,
    wobble: Triple<Float, Float, Float>?,
    tSec: Double,
    isLive: Boolean,
    isExiting: Boolean,
    tokens: YumaLyricsTokens,
    look: WordLook,
    litColor: Color,
) {
    val motion = tokens.motion
    val bounce = motion.wordBounce?.takeIf { isLive && !isExiting && !word.isLineSynced }
        ?.let { wordBounce(tSec - word.startSec, word.durSec, it) }

    val canUseLetters = word.useLetters && word.letterLayouts.size == word.text.length && isLive && !isExiting
    if (!canUseLetters) {
        val wordBox = transformedWordRect(word, wobble)
        val brush = buildWordSweepBrush(word, wordBox, look.start, look.end, litColor, originX = 0f) ?: return
        withWobbleAndBounce(word, wobble, bounce) {
            drawText(word.textLayout, brush = brush, topLeft = Offset(word.x, word.y))
        }
        return
    }

    val waves = letterWaves(
        letterCount = word.text.length,
        wordDurSec = word.durSec,
        floatSec = motion.letterWave.secs.toDouble(),
        timing = tokens.timing,
        crestFraction = motion.letterWave.crestFraction,
    )
    val n = word.text.length.coerceAtLeast(1).toFloat()
    val elapsed = tSec - word.startSec
    val transparent = litColor.copy(alpha = 0f)

    withWobbleAndBounce(word, wobble, bounce) {
        for (idx in word.text.indices) {
            val charLayout = word.letterLayouts[idx]
            val charX = word.x + word.letterStartXs[idx]
            val charY = word.y
            val charW = charLayout.size.width.toFloat().coerceAtLeast(1f)
            val xs = (look.start * n - idx) * charW
            val xe = (look.end * n - idx) * charW + 1f
            if (xe <= 0f) continue

            val (liftEm, scale) = letterFloat(
                elapsedSec = elapsed,
                wave = waves[idx],
                isLong = word.isLong,
                tokens = motion.letterWave,
                easeInOut = motion.easeInOut,
                easeOut = motion.easeOut,
            )
            val liftPx = -liftEm * word.fontSizePx
            val pivot = Offset(
                x = charX + charW * 0.5f,
                y = charY + charLayout.size.height * 0.5f,
            )
            withTransform({
                if (liftPx != 0f) translate(top = liftPx)
                if (abs(scale - 1f) > 1e-4f) scale(scale, scale, pivot = pivot)
            }) {
                if (xs >= charW) {
                    drawText(charLayout, color = litColor, topLeft = Offset(charX, charY))
                } else {
                    val brush = Brush.horizontalGradient(
                        colors = listOf(litColor, transparent),
                        startX = xs,
                        endX = xe,
                    )
                    drawText(charLayout, brush = brush, topLeft = Offset(charX, charY))
                }
            }
        }
    }
}

private inline fun DrawScope.withWobbleAndBounce(
    word: PreparedWord,
    wobble: Triple<Float, Float, Float>?,
    bounce: Pair<Float, Float>?,
    block: DrawScope.() -> Unit,
) {
    if (wobble == null && bounce == null) {
        block()
        return
    }
    withTransform({
        if (wobble != null) {
            val (cx, tx, sx) = wobble
            if (tx != 0f) translate(left = tx)
            if (abs(sx - 1f) > 1e-4f) scale(sx, 1f, pivot = Offset(cx, word.y + word.height * 0.5f))
        }
        if (bounce != null) {
            val (liftEm, scale) = bounce
            val liftPx = -liftEm * word.fontSizePx
            if (liftPx != 0f) translate(top = liftPx)
            if (abs(scale - 1f) > 1e-4f) {
                scale(scale, scale, pivot = Offset(word.x + word.width * 0.5f, word.y + word.height * 0.5f))
            }
        }
    }) {
        block()
    }
}
