/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

import org.w3c.dom.Element
import org.w3c.dom.Node

internal data class RawSegment(
    var text: String,
    val beginMs: Long?,
    val endMs: Long?,
    val durationMs: Long?,
)

internal data class RawTrack(
    val text: String,
    val language: String?,
    val segments: List<RawSegment>,
    val beginMs: Long? = null,
    val endMs: Long? = null,
    val durationMs: Long? = null,
)

internal data class RawLine(
    val key: String?,
    val sourceOrder: Int,
    val beginMs: Long?,
    val endMs: Long?,
    val durationMs: Long?,
    val agentId: String?,
    val main: RawTrack,
    val backgrounds: List<RawTrack>,
    val translations: List<RawTrack>,
    val romanizations: List<RawTrack>,
)

internal data class ParagraphContent(
    val main: RawTrack,
    val backgrounds: List<RawTrack>,
    val translations: List<RawTrack>,
    val romanizations: List<RawTrack>,
)

internal class TrackBuilder(
    val language: String?,
) {
    val text = StringBuilder()
    val segments = mutableListOf<RawSegment>()
    private val pendingPrefix = StringBuilder()

    fun appendSegment(segment: RawSegment) {
        val originalText = segment.text
        if (pendingPrefix.isNotEmpty()) {
            segment.text = pendingPrefix.toString() + segment.text
            pendingPrefix.clear()
        }
        segments += segment
        text.append(originalText)
    }

    fun appendInterSpanText(value: String) {
        val content =
            if (value.contains('\n') || value.contains('\r')) {
                value.trim().takeIf(String::isNotEmpty) ?: return
            } else {
                value.takeIf(String::isNotEmpty) ?: return
            }
        val previous = segments.lastOrNull()
        if (previous != null) {
            previous.text += content
        } else {
            pendingPrefix.append(content)
        }
        text.append(content)
    }

    fun build(
        beginMs: Long? = null,
        endMs: Long? = null,
        durationMs: Long? = null,
    ): RawTrack =
        RawTrack(
            text = text.toString(),
            language = language,
            segments = segments.toList(),
            beginMs = beginMs,
            endMs = endMs,
            durationMs = durationMs,
        )
}

internal object TtmlSpanHandler {
    fun parseAgents(root: Element): List<TtmlAgent> =
        root.descendantElements()
            .filter { it.hasLocalName("agent") && it.attribute("id") != null }
            .mapIndexed { index, element ->
                TtmlAgent(
                    id = element.attribute("id").orEmpty().removePrefix("#"),
                    name =
                        element.descendantElements()
                            .firstOrNull { it.hasLocalName("name") }
                            ?.textContent
                            ?.normalizedText()
                            ?.takeIf(String::isNotEmpty),
                    type =
                        when (element.attribute("type")?.lowercase()) {
                            "person" -> TtmlAgentType.PERSON
                            "character" -> TtmlAgentType.CHARACTER
                            "group" -> TtmlAgentType.GROUP
                            "organization" -> TtmlAgentType.ORGANIZATION
                            else -> TtmlAgentType.OTHER
                        },
                    order = index,
                )
            }.filter { it.id.isNotEmpty() }
            .distinctBy(TtmlAgent::id)
            .toList()

    fun parseHeadTracks(
        root: Element,
        containerName: String,
        timingContext: TimingContext,
    ): Map<String, List<RawTrack>> {
        val result = linkedMapOf<String, MutableList<RawTrack>>()
        root.descendantElements()
            .filter { it.hasLocalName(containerName) }
            .forEach { container ->
                val language = container.attribute("lang")
                container.descendantElements()
                    .filter { it.hasLocalName("text") }
                    .forEach textLoop@{ textElement ->
                        val lineKey = textElement.attribute("for")?.removePrefix("#") ?: return@textLoop
                        val track = parseGenericTrack(textElement, language, timingContext)
                        if (track.text.isNotBlank()) {
                            result.getOrPut(lineKey) { mutableListOf() } += track
                        }
                    }
            }
        return result.mapValues { it.value.toList() }
    }

    fun parseRawLines(
        root: Element,
        timingContext: TimingContext,
        headTranslations: Map<String, List<RawTrack>>,
        headRomanizations: Map<String, List<RawTrack>>,
    ): List<RawLine> =
        root.descendantElements()
            .filter { it.hasLocalName("p") }
            .mapIndexedNotNull { index, paragraph ->
                val content = parseParagraphContent(paragraph, timingContext)
                val key = (paragraph.attribute("key") ?: paragraph.attribute("id"))?.removePrefix("#")
                val mainText = content.main.text.normalizedEdgeWhitespace()
                val visibleText =
                    mainText.takeIf(String::isNotBlank)
                        ?: content.backgrounds.joinToString(separator = " ") { it.text.normalizedText() }
                if (visibleText.isBlank()) return@mapIndexedNotNull null

                RawLine(
                    key = key,
                    sourceOrder = index,
                    beginMs = paragraph.attribute("begin")?.let { TtmlTimeParser.parseTime(it, timingContext) },
                    endMs = paragraph.attribute("end")?.let { TtmlTimeParser.parseTime(it, timingContext) },
                    durationMs = paragraph.attribute("dur")?.let { TtmlTimeParser.parseTime(it, timingContext) },
                    agentId = paragraph.inheritedAttribute("agent")?.removePrefix("#"),
                    main = content.main.copy(text = mainText),
                    backgrounds = content.backgrounds,
                    translations = content.translations + key?.let(headTranslations::get).orEmpty(),
                    romanizations = content.romanizations + key?.let(headRomanizations::get).orEmpty(),
                )
            }.toList()

    fun parseParagraphContent(
        paragraph: Element,
        timingContext: TimingContext,
    ): ParagraphContent {
        val paragraphLanguage = paragraph.inheritedAttribute("lang")
        val main = TrackBuilder(paragraphLanguage)
        val backgrounds = mutableListOf<RawTrack>()
        val translations = mutableListOf<RawTrack>()
        val romanizations = mutableListOf<RawTrack>()
        val children = paragraph.childNodes

        for (index in 0 until children.length) {
            val node = children.item(index)
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> main.appendInterSpanText(node.nodeValue.orEmpty())
                Node.ELEMENT_NODE -> {
                    val element = node as Element
                    if (!element.hasLocalName("span")) continue
                    when (element.attribute("role")?.lowercase()) {
                        "x-bg" -> backgrounds += parseGenericTrack(element, paragraphLanguage, timingContext)
                        "x-translation" -> translations += parseGenericTrack(element, paragraphLanguage, timingContext)
                        "x-roman" -> romanizations += parseGenericTrack(element, paragraphLanguage, timingContext)
                        else -> parseMainSpan(element, main, backgrounds, translations, romanizations, timingContext)
                    }
                }
            }
        }

        return ParagraphContent(
            main = main.build(),
            backgrounds = backgrounds.filter { it.text.isNotBlank() },
            translations = translations.filter { it.text.isNotBlank() },
            romanizations = romanizations.filter { it.text.isNotBlank() },
        )
    }

    private fun parseMainSpan(
        element: Element,
        main: TrackBuilder,
        backgrounds: MutableList<RawTrack>,
        translations: MutableList<RawTrack>,
        romanizations: MutableList<RawTrack>,
        timingContext: TimingContext,
    ) {
        val directSpanChildren = element.directChildElements().filter { it.hasLocalName("span") }
        directSpanChildren.forEach { child ->
            when (child.attribute("role")?.lowercase()) {
                "x-bg" -> backgrounds += parseGenericTrack(child, main.language, timingContext)
                "x-translation" -> translations += parseGenericTrack(child, main.language, timingContext)
                "x-roman" -> romanizations += parseGenericTrack(child, main.language, timingContext)
            }
        }
        val mainSpanChildren = directSpanChildren.filterNot { it.hasSupplementaryRole() }
        val beginMs = element.attribute("begin")?.let { TtmlTimeParser.parseTime(it, timingContext) }
        val endMs = element.attribute("end")?.let { TtmlTimeParser.parseTime(it, timingContext) }
        val durationMs = element.attribute("dur")?.let { TtmlTimeParser.parseTime(it, timingContext) }

        if (mainSpanChildren.isEmpty()) {
            val text =
                if (directSpanChildren.isEmpty()) {
                    element.textContent.orEmpty()
                } else {
                    element.directText()
                }
            if (text.isNotEmpty()) {
                main.appendSegment(RawSegment(text = text, beginMs = beginMs, endMs = endMs, durationMs = durationMs))
            }
            return
        }

        val children = element.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> main.appendInterSpanText(node.nodeValue.orEmpty())
                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    if (!child.hasLocalName("span")) continue
                    if (!child.hasSupplementaryRole()) {
                        parseMainSpan(child, main, backgrounds, translations, romanizations, timingContext)
                    }
                }
            }
        }
    }

    fun parseGenericTrack(
        element: Element,
        inheritedLanguage: String?,
        timingContext: TimingContext,
    ): RawTrack {
        val language = element.attribute("lang") ?: inheritedLanguage
        val builder = TrackBuilder(language)
        parseGenericNodes(element, builder, timingContext, includeElementTiming = true)
        return builder.build(
            beginMs = element.attribute("begin")?.let { TtmlTimeParser.parseTime(it, timingContext) },
            endMs = element.attribute("end")?.let { TtmlTimeParser.parseTime(it, timingContext) },
            durationMs = element.attribute("dur")?.let { TtmlTimeParser.parseTime(it, timingContext) },
        )
    }

    private fun parseGenericNodes(
        element: Element,
        builder: TrackBuilder,
        timingContext: TimingContext,
        includeElementTiming: Boolean,
    ) {
        val spanChildren = element.directChildElements().filter { it.hasLocalName("span") }
        val beginMs = element.attribute("begin")?.let { TtmlTimeParser.parseTime(it, timingContext) }
        val endMs = element.attribute("end")?.let { TtmlTimeParser.parseTime(it, timingContext) }
        val durationMs = element.attribute("dur")?.let { TtmlTimeParser.parseTime(it, timingContext) }
        if (includeElementTiming && spanChildren.isEmpty() && beginMs != null) {
            val text = element.textContent.orEmpty()
            if (text.isNotEmpty()) {
                builder.appendSegment(RawSegment(text, beginMs, endMs, durationMs))
            }
            return
        }

        val children = element.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> builder.appendInterSpanText(node.nodeValue.orEmpty())
                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    if (child.hasLocalName("span")) {
                        parseGenericNodes(child, builder, timingContext, includeElementTiming = true)
                    }
                }
            }
        }
    }

    fun resolveUnknownAgents(
        declared: List<TtmlAgent>,
        rawLines: List<RawLine>,
    ): List<TtmlAgent> {
        val agents = declared.toMutableList()
        val knownIds = declared.mapTo(mutableSetOf(), TtmlAgent::id)
        rawLines.mapNotNull(RawLine::agentId).forEach { id ->
            if (knownIds.add(id)) {
                agents += TtmlAgent(id = id, name = null, type = TtmlAgentType.PERSON, order = agents.size)
            }
        }
        return agents
    }

    fun resolveLines(
        rawLines: List<RawLine>,
        bodyEndMs: Long?,
        agentById: Map<String, TtmlAgent>,
    ): List<TtmlLine> {
        val preliminaryStarts = rawLines.map { line -> line.beginMs ?: line.minimumRawBegin() }
        return rawLines.mapIndexedNotNull { index, rawLine ->
            val lineStart = preliminaryStarts[index] ?: return@mapIndexedNotNull null
            val nextLineStart =
                preliminaryStarts
                    .asSequence()
                    .drop(index + 1)
                    .filterNotNull()
                    .firstOrNull { start -> start > lineStart }
            val declaredEnd = rawLine.endMs ?: rawLine.durationMs?.let(lineStart::plus)
            val preliminaryEnd =
                declaredEnd
                    ?: rawLine.maximumRawEnd(lineStart)
                    ?: nextLineStart
                    ?: bodyEndMs?.takeIf { end -> end > lineStart }
                    ?: lineStart.saturatedPlus(TtmlTimeParser.DEFAULT_FINAL_LINE_DURATION_MS)
            if (preliminaryEnd <= lineStart) return@mapIndexedNotNull null

            val preliminaryRange = TtmlTimeRange(lineStart, preliminaryEnd)
            val main = resolveTrack(rawLine.main, preliminaryRange)
            val backgrounds = rawLine.backgrounds.map { resolveTrack(it, preliminaryRange) }
            val translations = rawLine.translations.map { resolveTrack(it, preliminaryRange) }
            val romanizations = rawLine.romanizations.map { resolveTrack(it, preliminaryRange) }
            val allTracks = sequenceOf(main) + backgrounds.asSequence() + translations.asSequence() + romanizations.asSequence()
            val resolvedTimes = allTracks.flatMap { track -> track.segments.asSequence().mapNotNull(TtmlSegment::timing) }.toList()
            val effectiveStart = minOf(lineStart, resolvedTimes.minOfOrNull(TtmlTimeRange::startMs) ?: lineStart)
            val effectiveEnd = maxOf(preliminaryEnd, resolvedTimes.maxOfOrNull(TtmlTimeRange::endMs) ?: preliminaryEnd)
            if (effectiveEnd <= effectiveStart) return@mapIndexedNotNull null

            val visibleText =
                main.text.normalizedEdgeWhitespace().takeIf(String::isNotBlank)
                    ?: backgrounds.joinToString(separator = " ") { it.text.normalizedText() }

            TtmlLine(
                key = rawLine.key,
                sourceOrder = rawLine.sourceOrder,
                timing = TtmlTimeRange(effectiveStart, effectiveEnd),
                text = visibleText,
                main = main.copy(text = main.text.normalizedEdgeWhitespace()),
                backgrounds = backgrounds.filter { it.text.isNotBlank() },
                translations = translations.filter { it.text.isNotBlank() },
                romanizations = romanizations.filter { it.text.isNotBlank() },
                agent = rawLine.agentId?.let(agentById::get),
            )
        }
    }

    private fun resolveTrack(
        rawTrack: RawTrack,
        lineRange: TtmlTimeRange,
    ): TtmlTrack {
        val offset = determineTrackOffset(rawTrack, lineRange)
        val trackStart = rawTrack.beginMs?.plus(offset) ?: lineRange.startMs
        val trackEnd =
            rawTrack.endMs?.plus(offset)
                ?: rawTrack.durationMs?.let(trackStart::plus)
                ?: lineRange.endMs
        val starts = rawTrack.segments.map { segment -> segment.beginMs?.plus(offset) }
        val resolved =
            rawTrack.segments.mapIndexed { index, segment ->
                val start = starts[index]
                val explicitEnd = segment.endMs?.plus(offset) ?: segment.durationMs?.let { duration -> start?.plus(duration) }
                val nextStart = starts.drop(index + 1).firstOrNull { it != null }
                val end = explicitEnd ?: nextStart ?: trackEnd
                val timing =
                    if (start != null && end > start && start >= 0L) {
                        TtmlTimeRange(start, end)
                    } else {
                        null
                    }
                TtmlSegment(text = segment.text, timing = timing)
            }
        val normalizedSegments =
            if (
                resolved.isNotEmpty() &&
                resolved.none { segment -> segment.timing != null } &&
                rawTrack.beginMs != null &&
                (rawTrack.endMs != null || rawTrack.durationMs != null) &&
                trackEnd > trackStart
            ) {
                listOf(TtmlSegment(text = rawTrack.text, timing = TtmlTimeRange(trackStart, trackEnd)))
            } else {
                resolved
            }
        return TtmlTrack(
            text = rawTrack.text.normalizedEdgeWhitespace(),
            language = rawTrack.language,
            segments = normalizedSegments,
        )
    }

    private fun determineTrackOffset(
        track: RawTrack,
        lineRange: TtmlTimeRange,
    ): Long {
        val segmentRanges =
            track.segments.mapNotNull { segment ->
                val start = segment.beginMs ?: return@mapNotNull null
                val end = segment.endMs ?: segment.durationMs?.let(start::plus) ?: start
                start to end
            }
        val rawRanges =
            if (segmentRanges.isNotEmpty()) {
                segmentRanges
            } else {
                val start = track.beginMs ?: return 0L
                val end = track.endMs ?: track.durationMs?.let(start::plus) ?: start
                listOf(start to end)
            }
        if (rawRanges.isEmpty() || lineRange.startMs == 0L) return 0L
        val absoluteFits = rawRanges.all { (start, end) -> start >= lineRange.startMs && end <= lineRange.endMs }
        if (absoluteFits) return 0L
        val relativeFits =
            rawRanges.all { (start, end) ->
                start >= 0L &&
                    lineRange.startMs + start >= lineRange.startMs &&
                    lineRange.startMs + end <= lineRange.endMs
            }
        return if (relativeFits) lineRange.startMs else 0L
    }

    private fun RawLine.minimumRawBegin(): Long? =
        allTracks()
            .flatMap { track ->
                sequenceOf(track.beginMs) + track.segments.asSequence().map(RawSegment::beginMs)
            }.filterNotNull()
            .minOrNull()

    private fun RawLine.maximumRawEnd(lineStart: Long): Long? {
        val rawMaximum =
            allTracks()
                .flatMap { track ->
                    val trackEnd = track.endMs ?: track.beginMs?.let { begin -> track.durationMs?.let(begin::plus) }
                    sequenceOf(trackEnd) +
                        track.segments.asSequence().map { segment ->
                            segment.endMs ?: segment.beginMs?.let { begin -> segment.durationMs?.let(begin::plus) }
                        }
                }.filterNotNull()
                .maxOrNull()
                ?: return null
        return if (rawMaximum < lineStart) lineStart + rawMaximum else rawMaximum
    }

    private fun RawLine.allTracks(): Sequence<RawTrack> =
        sequenceOf(main) + backgrounds.asSequence() + translations.asSequence() + romanizations.asSequence()

    fun chooseRomanization(tracks: List<TtmlTrack>): TtmlTrack? =
        tracks.firstOrNull { it.language?.contains("Latn", ignoreCase = true) == true }
            ?: tracks.firstOrNull()
}
