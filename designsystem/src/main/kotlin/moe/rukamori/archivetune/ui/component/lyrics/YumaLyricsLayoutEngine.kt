/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
internal data class PreparedWord(
    val text: String,
    val textLayout: TextLayoutResult,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSizePx: Float,
    val startSec: Double,
    val durSec: Double,
    val isLineSynced: Boolean,
    val isBackground: Boolean,
    val isLong: Boolean,
    val groupIndex: Int,
    val useLetters: Boolean,
    val isRtl: Boolean,
    val glyphBoxes: List<Rect>,
    val letterLayouts: List<TextLayoutResult>,
    val letterStartXs: List<Float>,
)

@Immutable
internal data class PreparedGroup(
    val firstWordIndex: Int,
    val wordRange: IntRange,
    val wobbles: Boolean,
    val centerX: Float,
)

@Immutable
internal data class PreparedDeco(
    val text: String,
    val textLayout: TextLayoutResult,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSizePx: Float,
    val isPill: Boolean,
    val isRomanization: Boolean,
    val pillRect: Rect?,
)

@Immutable
internal data class PreparedLyricsLine(
    val lineIndex: Int,
    val startSec: Double,
    val durSec: Double,
    val isInstrumental: Boolean,
    val resolvedAlignment: YumaLyricsAlignment,
    val isRtl: Boolean,
    val fullWidthPx: Float,
    val contentWidthPx: Float,
    val totalHeightPx: Float,
    val words: List<PreparedWord>,
    val groups: List<PreparedGroup>,
    val decos: List<PreparedDeco>,
    val instrumentalX: Float,
    val instrumentalCenterY: Float,
    val instrumentalSizePx: Float,
)

internal fun resolveEffectiveAlignment(
    styleAlignment: YumaLyricsAlignment,
    agent: String?,
    isRtl: Boolean = false,
): YumaLyricsAlignment {
    val base = when (styleAlignment) {
        YumaLyricsAlignment.AutoByAgent -> when {
            agent.equals("v2", ignoreCase = true) || agent.equals("v3", ignoreCase = true) -> YumaLyricsAlignment.End
            agent.equals("v1000", ignoreCase = true) -> YumaLyricsAlignment.Center
            else -> YumaLyricsAlignment.Start
        }
        else -> styleAlignment
    }
    return if (!isRtl) {
        base
    } else {
        when (base) {
            YumaLyricsAlignment.Start -> YumaLyricsAlignment.End
            YumaLyricsAlignment.End -> YumaLyricsAlignment.Start
            YumaLyricsAlignment.Center, YumaLyricsAlignment.AutoByAgent -> YumaLyricsAlignment.Center
        }
    }
}

internal data class FlowBoxInput(
    val token: YumaToken,
    val width: Float,
    val height: Float,
)

internal data class FlowBoxOutput(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val groupIndex: Int,
    val wobbles: Boolean,
    val isRtl: Boolean,
)

internal fun computeAlignedRowShift(
    alignment: YumaLyricsAlignment,
    containerWidth: Float,
    rowWidth: Float,
): Float = when (alignment) {
    YumaLyricsAlignment.End -> (containerWidth - rowWidth).coerceAtLeast(0f)
    YumaLyricsAlignment.Center -> ((containerWidth - rowWidth) * 0.5f).coerceAtLeast(0f)
    else -> 0f
}

internal fun computeContentInset(
    alignment: YumaLyricsAlignment,
    fullWidth: Float,
    contentWidth: Float,
): Float = when (alignment) {
    YumaLyricsAlignment.End -> (fullWidth - contentWidth).coerceAtLeast(0f)
    YumaLyricsAlignment.Center -> ((fullWidth - contentWidth) * 0.5f).coerceAtLeast(0f)
    else -> 0f
}

private sealed interface GroupOrSpace {
    data object Space : GroupOrSpace
    data class Group(val entries: List<FlowBoxInput>) : GroupOrSpace
}

private class MutableWordPos(
    val input: FlowBoxInput,
    var x: Float,
    val y: Float,
    val groupIndex: Int,
    val wobbles: Boolean,
    val isRtl: Boolean,
)

internal fun layoutTokenBoxes(
    items: List<FlowBoxInput>,
    spaceWidth: Float,
    maxWidth: Float,
    lineHeight: Float,
    alignment: YumaLyricsAlignment,
    lineIsRtl: Boolean,
    longGroupMaxChars: Int,
): Pair<List<FlowBoxOutput>, Float> {
    val grouped = ArrayList<GroupOrSpace>()
    for (item in items) {
        if (item.token.isSpace) {
            grouped.add(GroupOrSpace.Space)
        } else {
            val prev = grouped.lastOrNull()
            if (prev is GroupOrSpace.Group && prev.entries.first().token.isBackground == item.token.isBackground) {
                grouped[grouped.lastIndex] = GroupOrSpace.Group(prev.entries + item)
            } else {
                grouped.add(GroupOrSpace.Group(listOf(item)))
            }
        }
    }

    val placed = ArrayList<MutableWordPos>()
    val rows = ArrayList<Pair<Int, Float>>()
    rows.add(0 to 0f)
    var curX = 0f
    var curRow = 0
    var pendingSpace = false
    var groupCounter = 0

    for (entry in grouped) {
        when (entry) {
            GroupOrSpace.Space -> {
                pendingSpace = placed.isNotEmpty()
            }
            is GroupOrSpace.Group -> {
                val groupText = entry.entries.joinToString("") { it.token.text }
                val isLongGroup = groupText.length > longGroupMaxChars
                val groupWidth = entry.entries.sumOf { it.width.toDouble() }.toFloat()
                val sp = if (pendingSpace) spaceWidth else 0f
                pendingSpace = false

                val breakable = isLongGroup || groupWidth > maxWidth
                if (curX > 0f && curX + sp + groupWidth > maxWidth && !breakable) {
                    curRow++
                    curX = 0f
                    rows.add(placed.size to 0f)
                } else {
                    curX += sp
                }

                val gi = groupCounter++
                for (box in entry.entries) {
                    if (breakable && curX > 0f && curX + box.width > maxWidth) {
                        curRow++
                        curX = 0f
                        rows.add(placed.size to 0f)
                    }
                    val y = curRow * lineHeight + (lineHeight - box.height) * 0.5f
                    placed.add(
                        MutableWordPos(
                            input = box,
                            x = curX,
                            y = y,
                            groupIndex = gi,
                            wobbles = !isLongGroup,
                            isRtl = hasRtl(box.token.text),
                        ),
                    )
                    curX += box.width
                    rows[curRow] = rows[curRow].first to curX
                }
            }
        }
    }

    for (r in rows.indices) {
        val first = rows[r].first
        val rowW = rows[r].second
        val end = rows.getOrNull(r + 1)?.first ?: placed.size
        if (first >= end) continue

        if (lineIsRtl) {
            for (k in first until end) {
                placed[k].x = rowW - placed[k].x - placed[k].input.width
            }
        }

        var i = first
        while (i < end) {
            var j = i
            while (j < end && placed[j].isRtl != lineIsRtl) {
                j++
            }
            if (j > i + 1) {
                var lo = Float.POSITIVE_INFINITY
                var hi = Float.NEGATIVE_INFINITY
                for (k in i until j) {
                    lo = minOf(lo, placed[k].x)
                    hi = maxOf(hi, placed[k].x + placed[k].input.width)
                }
                for (k in i until j) {
                    placed[k].x = lo + hi - placed[k].x - placed[k].input.width
                }
            }
            i = max(j, i + 1)
        }
    }

    for (r in rows.indices) {
        val first = rows[r].first
        val rowW = rows[r].second
        val end = rows.getOrNull(r + 1)?.first ?: placed.size
        val shift = computeAlignedRowShift(alignment, maxWidth, rowW)
        if (shift > 0f) {
            for (k in first until end) {
                placed[k].x += shift
            }
        }
    }

    val totalHeight = if (placed.isEmpty()) 0f else (curRow + 1) * lineHeight
    val outputs = placed.map {
        FlowBoxOutput(
            x = it.x,
            y = it.y,
            width = it.input.width,
            height = it.input.height,
            groupIndex = it.groupIndex,
            wobbles = it.wobbles,
            isRtl = it.isRtl,
        )
    }
    return outputs to totalHeight
}

internal fun prepareLyricsLine(
    line: YumaLyricsLine,
    lineIndex: Int,
    lines: List<YumaLyricsLine>,
    fullWidthPx: Float,
    style: YumaLyricsStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    layoutDirection: LayoutDirection,
): PreparedLyricsLine {
    val tokens = style.tokens
    val timing = tokens.timing
    val motion = tokens.motion
    val visual = tokens.visual

    val fontSizeSp = visual.baseFontSizeSp * style.textScale
    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    val lineHeightPx = fontSizePx * style.lineSpacingMultiplier
    val verticalPadPx = fontSizePx * visual.lineVerticalPaddingEm * style.lineSpacingMultiplier

    val effectiveAgent = if (line.isInstrumental) nearestAgent(lines, lineIndex) else line.agent
    val lineRtl = hasRtl(line.text)
    val resolvedAlign = resolveEffectiveAlignment(style.alignment, effectiveAgent, lineRtl)

    val safeFullWidth = fullWidthPx.coerceAtLeast(40f)
    val maxScale = max(motion.activeScale, 1.0f)
    val contentWidth = (safeFullWidth / maxScale).coerceAtLeast(40f)
    val inset = computeContentInset(resolvedAlign, safeFullWidth, contentWidth)

    val startSec = line.timeMs / 1000.0
    val durSec = (line.endTimeMs - line.timeMs).coerceAtLeast(0L) / 1000.0

    if (line.isInstrumental) {
        val instSize = fontSizePx * (3f * visual.instrumentalDotSizeEm + 2f * visual.instrumentalDotGapEm)
        val rawX = when (resolvedAlign) {
            YumaLyricsAlignment.End -> contentWidth - instSize
            YumaLyricsAlignment.Center -> (contentWidth - instSize) * 0.5f
            else -> 0f
        }
        return PreparedLyricsLine(
            lineIndex = lineIndex,
            startSec = startSec,
            durSec = durSec,
            isInstrumental = true,
            resolvedAlignment = resolvedAlign,
            isRtl = lineRtl,
            fullWidthPx = safeFullWidth,
            contentWidthPx = contentWidth,
            totalHeightPx = verticalPadPx * 2f + lineHeightPx,
            words = emptyList(),
            groups = emptyList(),
            decos = emptyList(),
            instrumentalX = rawX + inset,
            instrumentalCenterY = verticalPadPx + lineHeightPx * 0.5f,
            instrumentalSizePx = fontSizePx,
        )
    }

    val rawWords = line.words.ifEmpty {
        buildLineSyncedParts(line, timing.lineStaggerSec.toDouble())
    }
    val allToks = rawWords.flatMap { w ->
        splitPartIntoTokens(w.text, w.startMs, w.endMs, w.isBackground)
    }
    val mainToks = allToks.filter { !it.isBackground }
    val bgToks = allToks.filter { it.isBackground }

    val mainStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = style.fontFamily,
    )
    val bgFontSizeSp = fontSizeSp * visual.backgroundEm
    val bgFontSizePx = fontSizePx * visual.backgroundEm
    val bgStyle = TextStyle(
        fontSize = bgFontSizeSp.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = style.fontFamily,
    )

    val preparedWords = ArrayList<PreparedWord>()
    val preparedGroups = ArrayList<PreparedGroup>()
    var curY = verticalPadPx

    fun appendFlow(
        toks: List<YumaToken>,
        flowStyle: TextStyle,
        flowFontPx: Float,
        flowLineHeight: Float,
    ) {
        if (toks.none { !it.isSpace }) return

        val spaceLayout = textMeasurer.measure(
            text = " ",
            style = flowStyle,
            maxLines = 1,
            softWrap = false,
            layoutDirection = layoutDirection,
        )
        val spaceW = max(spaceLayout.size.width.toFloat(), flowFontPx * 0.25f)

        val expandedToks = toks.flatMap { t ->
            if (t.isSpace || isJoiningScript(t.text) || hasRtl(t.text) || t.text.length < 2) {
                listOf(t)
            } else {
                val measured = textMeasurer.measure(
                    text = t.text,
                    style = flowStyle,
                    maxLines = 1,
                    softWrap = false,
                    layoutDirection = layoutDirection,
                )
                if (measured.size.width <= contentWidth) {
                    listOf(t)
                } else {
                    val n = t.text.length
                    t.text.mapIndexed { idx, ch ->
                        YumaToken(
                            text = ch.toString(),
                            startSec = t.startSec + t.durSec * idx / n,
                            durSec = t.durSec / n,
                            isBackground = t.isBackground,
                            isSpace = false,
                        )
                    }
                }
            }
        }

        val wordLayouts = HashMap<Int, TextLayoutResult>()
        val boxInputs = expandedToks.mapIndexed { idx, tok ->
            if (tok.isSpace) {
                FlowBoxInput(tok, 0f, 0f)
            } else {
                val layout = textMeasurer.measure(
                    text = tok.text,
                    style = flowStyle,
                    maxLines = 1,
                    softWrap = false,
                    layoutDirection = layoutDirection,
                )
                wordLayouts[idx] = layout
                FlowBoxInput(tok, layout.size.width.toFloat(), layout.size.height.toFloat())
            }
        }

        val (outputs, flowH) = layoutTokenBoxes(
            items = boxInputs,
            spaceWidth = spaceW,
            maxWidth = contentWidth,
            lineHeight = flowLineHeight,
            alignment = resolvedAlign,
            lineIsRtl = lineRtl,
            longGroupMaxChars = visual.longGroupMaxChars,
        )

        val baseWordIdx = preparedWords.size
        val baseGroupIdx = preparedGroups.size
        val groupBuckets = LinkedHashMap<Int, MutableList<Int>>()

        var outIdx = 0
        for ((tokIdx, tok) in expandedToks.withIndex()) {
            if (tok.isSpace) continue
            val out = outputs[outIdx++]
            val layout = wordLayouts[tokIdx] ?: continue
            val globalGroup = baseGroupIdx + out.groupIndex
            val useLetters = motion.letterWave.enabled &&
                !isJoiningScript(tok.text) &&
                !out.isRtl
            val glyphBoxes = List(tok.text.length) { charIdx ->
                layout.getBoundingBox(charIdx)
            }
            val letterLayouts = if (useLetters && tok.text.length > 1) {
                List(tok.text.length) { charIdx ->
                    textMeasurer.measure(
                        text = tok.text.substring(charIdx, charIdx + 1),
                        style = flowStyle,
                        maxLines = 1,
                        softWrap = false,
                        layoutDirection = layoutDirection,
                    )
                }
            } else {
                emptyList()
            }
            val letterStartXs = if (useLetters && tok.text.length > 1) {
                List(tok.text.length) { charIdx ->
                    layout.getHorizontalPosition(charIdx, usePrimaryDirection = true)
                }
            } else {
                emptyList()
            }
            val wordGlobalIdx = preparedWords.size
            preparedWords.add(
                PreparedWord(
                    text = tok.text,
                    textLayout = layout,
                    x = out.x + inset,
                    y = curY + out.y,
                    width = out.width,
                    height = out.height,
                    fontSizePx = flowFontPx,
                    startSec = tok.startSec,
                    durSec = tok.durSec,
                    isLineSynced = tok.durSec <= 0.0,
                    isBackground = tok.isBackground,
                    isLong = tok.durSec > motion.letterWave.longWordSecs,
                    groupIndex = globalGroup,
                    useLetters = useLetters,
                    isRtl = out.isRtl,
                    glyphBoxes = glyphBoxes,
                    letterLayouts = letterLayouts,
                    letterStartXs = letterStartXs,
                ),
            )
            groupBuckets.getOrPut(out.groupIndex) { ArrayList() }.add(wordGlobalIdx)
        }

        for ((_, indices) in groupBuckets) {
            if (indices.isEmpty()) continue
            val firstIdx = indices.first()
            val lastIdx = indices.last()
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            for (wi in firstIdx..lastIdx) {
                val pw = preparedWords[wi]
                minX = minOf(minX, pw.x)
                maxX = maxOf(maxX, pw.x + pw.width)
            }
            val wobbles = outputs.getOrNull(firstIdx - baseWordIdx)?.wobbles ?: false
            preparedGroups.add(
                PreparedGroup(
                    firstWordIndex = firstIdx,
                    wordRange = firstIdx..lastIdx,
                    wobbles = wobbles,
                    centerX = (minX + maxX) * 0.5f,
                ),
            )
        }

        curY += flowH
    }

    appendFlow(mainToks, mainStyle, fontSizePx, lineHeightPx)

    if (bgToks.any { !it.isSpace }) {
        curY += fontSizePx * visual.backgroundGapEm
        appendFlow(bgToks, bgStyle, bgFontSizePx, lineHeightPx * visual.backgroundEm)
    }

    val decos = ArrayList<PreparedDeco>()
    val decoGapPx = fontSizePx * visual.decoGapEm

    if (style.showRomanization && !line.romanizedText.isNullOrBlank()) {
        curY += decoGapPx
        val romanFontSizeSp = fontSizeSp * visual.romanizationScale
        val romanFontSizePx = fontSizePx * visual.romanizationScale
        val romanStyle = TextStyle(
            fontSize = romanFontSizeSp.sp,
            lineHeight = (romanFontSizeSp * style.lineSpacingMultiplier).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = style.fontFamily,
            textAlign = when (resolvedAlign) {
                YumaLyricsAlignment.End -> TextAlign.End
                YumaLyricsAlignment.Center -> TextAlign.Center
                else -> TextAlign.Start
            },
        )
        val padX = if (visual.romanizationPill) fontSizePx * visual.romanizationPillPadXEm else 0f
        val padY = if (visual.romanizationPill) fontSizePx * visual.romanizationPillPadYEm else 0f
        val innerMaxW = (contentWidth - padX * 2f - 2f).roundToInt().coerceAtLeast(1)
        val layout = textMeasurer.measure(
            text = line.romanizedText,
            style = romanStyle,
            constraints = Constraints(maxWidth = innerMaxW),
            layoutDirection = layoutDirection,
        )
        val textW = layout.size.width.toFloat()
        val textH = layout.size.height.toFloat()
        val boxW = textW + padX * 2f + 2f
        val boxH = textH + padY * 2f + 2f
        val boxX = when (resolvedAlign) {
            YumaLyricsAlignment.End -> contentWidth - boxW
            YumaLyricsAlignment.Center -> (contentWidth - boxW) * 0.5f
            else -> 0f
        } + inset
        val pillRect = if (visual.romanizationPill) {
            Rect(boxX, curY, boxX + boxW, curY + boxH)
        } else {
            null
        }
        decos.add(
            PreparedDeco(
                text = line.romanizedText,
                textLayout = layout,
                x = boxX + padX + 1f,
                y = curY + padY + 1f,
                width = textW,
                height = textH,
                fontSizePx = romanFontSizePx,
                isPill = visual.romanizationPill,
                isRomanization = true,
                pillRect = pillRect,
            ),
        )
        curY += boxH
    }

    if (style.showTranslation && !line.translationText.isNullOrBlank()) {
        curY += decoGapPx
        val transFontSizeSp = fontSizeSp * visual.translationScale
        val transFontSizePx = fontSizePx * visual.translationScale
        val transStyle = TextStyle(
            fontSize = transFontSizeSp.sp,
            lineHeight = (transFontSizeSp * style.lineSpacingMultiplier).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = style.fontFamily,
            textAlign = when (resolvedAlign) {
                YumaLyricsAlignment.End -> TextAlign.End
                YumaLyricsAlignment.Center -> TextAlign.Center
                else -> TextAlign.Start
            },
        )
        val maxWInt = contentWidth.roundToInt().coerceAtLeast(1)
        val layout = textMeasurer.measure(
            text = line.translationText,
            style = transStyle,
            constraints = Constraints(maxWidth = maxWInt),
            layoutDirection = layoutDirection,
        )
        val textW = layout.size.width.toFloat()
        val textH = layout.size.height.toFloat()
        val boxX = when (resolvedAlign) {
            YumaLyricsAlignment.End -> contentWidth - textW
            YumaLyricsAlignment.Center -> (contentWidth - textW) * 0.5f
            else -> 0f
        } + inset
        decos.add(
            PreparedDeco(
                text = line.translationText,
                textLayout = layout,
                x = boxX,
                y = curY,
                width = textW,
                height = textH,
                fontSizePx = transFontSizePx,
                isPill = false,
                isRomanization = false,
                pillRect = null,
            ),
        )
        curY += textH
    }

    return PreparedLyricsLine(
        lineIndex = lineIndex,
        startSec = startSec,
        durSec = durSec,
        isInstrumental = false,
        resolvedAlignment = resolvedAlign,
        isRtl = lineRtl,
        fullWidthPx = safeFullWidth,
        contentWidthPx = contentWidth,
        totalHeightPx = curY + verticalPadPx,
        words = preparedWords,
        groups = preparedGroups,
        decos = decos,
        instrumentalX = 0f,
        instrumentalCenterY = 0f,
        instrumentalSizePx = 0f,
    )
}
