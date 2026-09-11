/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.roundToLong

object TTMLParser {
    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val words: List<ParsedWord>,
        val isBackground: Boolean = false,
        val agent: String? = null,
        val providerRomanizedText: String? = null,
        val providerRomanizedWords: List<String>? = null,
        val providerRomanizedLanguage: String? = null,
        val providerTranslationText: String? = null,
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val isBackground: Boolean = false,
    )

    private data class TimingContext(
        val tickRate: Double,
        val frameRate: Double,
    )

    private data class RawSegment(
        var text: String,
        val beginMs: Long?,
        val endMs: Long?,
        val durationMs: Long?,
    )

    private data class RawTrack(
        val text: String,
        val language: String?,
        val segments: List<RawSegment>,
        val beginMs: Long? = null,
        val endMs: Long? = null,
        val durationMs: Long? = null,
    )

    private data class RawLine(
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

    private data class ParagraphContent(
        val main: RawTrack,
        val backgrounds: List<RawTrack>,
        val translations: List<RawTrack>,
        val romanizations: List<RawTrack>,
    )

    private class TrackBuilder(
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

    fun parseDocument(ttml: String): Result<TtmlDocument> =
        runCatching {
            val source = ttml.removePrefix("\uFEFF").trimStart()
            if (UNSAFE_XML_REGEX.containsMatchIn(source)) {
                throw TtmlParseException(TtmlParseFailure.UNSAFE_XML)
            }

            val document = parseXml(declareMissingNamespaces(source))
            val root = document.documentElement ?: throw TtmlParseException(TtmlParseFailure.MALFORMED_XML)
            if (!root.hasLocalName("tt")) throw TtmlParseException(TtmlParseFailure.MALFORMED_XML)

            val timingContext = readTimingContext(root)
            val body = root.descendantElements().firstOrNull { it.hasLocalName("body") }
            val bodyStartMs = body?.attribute("begin")?.let { parseTime(it, timingContext) } ?: 0L
            val bodyEndMs =
                body?.attribute("end")?.let { parseTime(it, timingContext) }
                    ?: body?.attribute("dur")?.let { parseTime(it, timingContext) }?.let(bodyStartMs::plus)
            val declaredAgents = parseAgents(root)
            val headRomanizations = parseHeadTracks(root, "transliteration", timingContext)
            val headTranslations = parseHeadTracks(root, "translation", timingContext)
            val rawLines = parseRawLines(root, timingContext, headTranslations, headRomanizations)
            if (rawLines.isEmpty()) throw TtmlParseException(TtmlParseFailure.MISSING_LINES)

            val agents = resolveUnknownAgents(declaredAgents, rawLines)
            val lines = resolveLines(rawLines, bodyEndMs, agents.associateBy(TtmlAgent::id))
            if (lines.isEmpty()) throw TtmlParseException(TtmlParseFailure.INVALID_TIMING)

            val hasTimedSegments =
                lines.any { line ->
                    line.main.segments.any { it.timing != null } ||
                        line.backgrounds.any { track -> track.segments.any { it.timing != null } }
                }

            TtmlDocument(
                language = root.attribute("lang"),
                timingMode = if (hasTimedSegments) TtmlTimingMode.WORD else TtmlTimingMode.LINE,
                agents = agents,
                lines = lines.sortedWith(compareBy<TtmlLine> { it.timing.startMs }.thenBy { it.sourceOrder }),
            )
        }.recoverCatching { throwable ->
            if (throwable is TtmlParseException) throw throwable
            throw TtmlParseException(TtmlParseFailure.MALFORMED_XML, throwable)
        }

    fun parseTTML(ttml: String): List<ParsedLine> =
        parseDocument(ttml)
            .getOrNull()
            ?.lines
            .orEmpty()
            .map { line ->
                val mainWords =
                    line.main.segments.mapNotNull { segment ->
                        segment.timing?.let { timing ->
                            ParsedWord(
                                text = segment.text,
                                startTime = timing.startMs / MILLIS_PER_SECOND,
                                endTime = timing.endMs / MILLIS_PER_SECOND,
                            )
                        }
                    }
                val backgroundWords =
                    line.backgrounds.flatMap { track ->
                        track.segments.mapNotNull { segment ->
                            segment.timing?.let { timing ->
                                ParsedWord(
                                    text = segment.text,
                                    startTime = timing.startMs / MILLIS_PER_SECOND,
                                    endTime = timing.endMs / MILLIS_PER_SECOND,
                                    isBackground = true,
                                )
                            }
                        }
                    }
                val romanization = chooseRomanization(line.romanizations)

                ParsedLine(
                    text = line.text,
                    startTime = line.timing.startMs / MILLIS_PER_SECOND,
                    endTime = line.timing.endMs / MILLIS_PER_SECOND,
                    words = mainWords + backgroundWords,
                    agent = line.agent?.id,
                    providerRomanizedText = romanization?.text?.normalizedText(),
                    providerRomanizedWords =
                        romanization
                            ?.segments
                            ?.map { it.text.normalizedText() }
                            ?.filter(String::isNotEmpty)
                            ?.takeIf { words -> words.isNotEmpty() },
                    providerRomanizedLanguage = romanization?.language,
                    providerTranslationText = line.translations.firstOrNull()?.text?.normalizedText(),
                )
            }

    private fun parseXml(source: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        try {
            factory.isXIncludeAware = false
        } catch (_: UnsupportedOperationException) {
        }
        factory.setExpandEntityReferences(false)
        factory.setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setAttributeIfSupported(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttributeIfSupported(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")

        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("External XML entities are not supported") }
        return builder.parse(InputSource(StringReader(source)))
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(
        name: String,
        value: Boolean,
    ) {
        try {
            setFeature(name, value)
        } catch (_: ParserConfigurationException) {
        }
    }

    private fun DocumentBuilderFactory.setAttributeIfSupported(
        name: String,
        value: String,
    ) {
        try {
            setAttribute(name, value)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun declareMissingNamespaces(source: String): String {
        val rootMatch = ROOT_TAG_REGEX.find(source) ?: return source
        val rootTag = rootMatch.value
        val declaredPrefixes =
            NAMESPACE_DECLARATION_REGEX
                .findAll(rootTag)
                .map { it.groupValues[1].lowercase() }
                .toSet()
        val missing =
            KNOWN_NAMESPACES.filterKeys { prefix ->
                prefix !in declaredPrefixes && Regex("""\b${Regex.escape(prefix)}:[A-Za-z_][\w.-]*""").containsMatchIn(source)
            }
        if (missing.isEmpty()) return source

        val declarations = missing.entries.joinToString(separator = "") { (prefix, uri) -> " xmlns:$prefix=\"$uri\"" }
        val insertionOffset = rootMatch.range.last + 1 - if (rootTag.endsWith("/>")) 2 else 1
        return source.substring(0, insertionOffset) + declarations + source.substring(insertionOffset)
    }

    private fun readTimingContext(root: Element): TimingContext {
        val baseFrameRate = root.attribute("frameRate")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 30.0
        val multiplier =
            root.attribute("frameRateMultiplier")
                ?.split(WHITESPACE_REGEX)
                ?.mapNotNull(String::toDoubleOrNull)
                ?.takeIf { it.size == 2 && it[0] > 0.0 && it[1] > 0.0 }
                ?.let { it[0] / it[1] }
                ?: 1.0
        val frameRate = (baseFrameRate * multiplier).coerceAtLeast(1.0)
        val tickRate = root.attribute("tickRate")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: frameRate
        return TimingContext(tickRate = tickRate, frameRate = frameRate)
    }

    private fun parseAgents(root: Element): List<TtmlAgent> =
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

    private fun parseHeadTracks(
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

    private fun parseRawLines(
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
                    beginMs = paragraph.attribute("begin")?.let { parseTime(it, timingContext) },
                    endMs = paragraph.attribute("end")?.let { parseTime(it, timingContext) },
                    durationMs = paragraph.attribute("dur")?.let { parseTime(it, timingContext) },
                    agentId = paragraph.inheritedAttribute("agent")?.removePrefix("#"),
                    main = content.main.copy(text = mainText),
                    backgrounds = content.backgrounds,
                    translations = content.translations + key?.let(headTranslations::get).orEmpty(),
                    romanizations = content.romanizations + key?.let(headRomanizations::get).orEmpty(),
                )
            }.toList()

    private fun parseParagraphContent(
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
        val beginMs = element.attribute("begin")?.let { parseTime(it, timingContext) }
        val endMs = element.attribute("end")?.let { parseTime(it, timingContext) }
        val durationMs = element.attribute("dur")?.let { parseTime(it, timingContext) }

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

    private fun parseGenericTrack(
        element: Element,
        inheritedLanguage: String?,
        timingContext: TimingContext,
    ): RawTrack {
        val language = element.attribute("lang") ?: inheritedLanguage
        val builder = TrackBuilder(language)
        parseGenericNodes(element, builder, timingContext, includeElementTiming = true)
        return builder.build(
            beginMs = element.attribute("begin")?.let { parseTime(it, timingContext) },
            endMs = element.attribute("end")?.let { parseTime(it, timingContext) },
            durationMs = element.attribute("dur")?.let { parseTime(it, timingContext) },
        )
    }

    private fun parseGenericNodes(
        element: Element,
        builder: TrackBuilder,
        timingContext: TimingContext,
        includeElementTiming: Boolean,
    ) {
        val spanChildren = element.directChildElements().filter { it.hasLocalName("span") }
        val beginMs = element.attribute("begin")?.let { parseTime(it, timingContext) }
        val endMs = element.attribute("end")?.let { parseTime(it, timingContext) }
        val durationMs = element.attribute("dur")?.let { parseTime(it, timingContext) }
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

    private fun resolveUnknownAgents(
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

    private fun resolveLines(
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
                    ?: lineStart.saturatedPlus(DEFAULT_FINAL_LINE_DURATION_MS)
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
                    if (start != null && end != null && end > start && start >= 0L) {
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

    private fun parseTime(
        rawValue: String,
        context: TimingContext,
    ): Long? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null

        OFFSET_TIME_REGEX.matchEntire(value)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
            val seconds =
                when (match.groupValues[2].lowercase()) {
                    "h" -> amount * 3600.0
                    "m" -> amount * 60.0
                    "s" -> amount
                    "ms" -> amount / 1000.0
                    "f" -> amount / context.frameRate
                    "t" -> amount / context.tickRate
                    else -> return null
                }
            return seconds.toMillisecondsOrNull()
        }

        val parts = value.replace(';', ':').split(':')
        if (parts.size !in 1..4) return null
        val numeric = parts.map { it.toDoubleOrNull()?.takeIf { number -> number.isFinite() && number >= 0.0 } ?: return null }
        if (numeric.size >= 2 && numeric.drop(1).dropLast(if (numeric.size == 4) 1 else 0).any { it >= 60.0 }) return null
        val seconds =
            when (numeric.size) {
                1 -> numeric[0]
                2 -> numeric[0] * 60.0 + numeric[1]
                3 -> numeric[0] * 3600.0 + numeric[1] * 60.0 + numeric[2]
                4 -> numeric[0] * 3600.0 + numeric[1] * 60.0 + numeric[2] + numeric[3] / context.frameRate
                else -> return null
            }
        return seconds.toMillisecondsOrNull()
    }

    private fun Double.toMillisecondsOrNull(): Long? {
        val milliseconds = this * 1000.0
        if (!milliseconds.isFinite() || milliseconds < 0.0 || milliseconds > Long.MAX_VALUE.toDouble()) return null
        return milliseconds.roundToLong()
    }

    private fun Long.saturatedPlus(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value

    private fun Element.attribute(localName: String): String? {
        val attributes = attributes ?: return null
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index) ?: continue
            val candidate = attribute.localName ?: attribute.nodeName.substringAfter(':')
            if (candidate.equals(localName, ignoreCase = true)) {
                return attribute.nodeValue?.trim()?.takeIf(String::isNotEmpty)
            }
        }
        return null
    }

    private fun Element.inheritedAttribute(localName: String): String? {
        var element: Element? = this
        while (element != null) {
            element.attribute(localName)?.let { return it }
            element = element.parentNode as? Element
        }
        return null
    }

    private fun Element.hasLocalName(expected: String): Boolean =
        (localName ?: tagName.substringAfter(':')).equals(expected, ignoreCase = true)

    private fun Element.hasSupplementaryRole(): Boolean =
        attribute("role")?.lowercase() in SUPPLEMENTARY_ROLES

    private fun Element.directText(): String {
        val result = StringBuilder()
        val children = childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                result.append(node.nodeValue.orEmpty())
            }
        }
        return result.toString()
    }

    private fun Element.directChildElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (index in 0 until children.length) {
            (children.item(index) as? Element)?.let(result::add)
        }
        return result
    }

    private fun Element.descendantElements(): Sequence<Element> = sequence {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            yield(child)
            yieldAll(child.descendantElements())
        }
    }

    private fun chooseRomanization(tracks: List<TtmlTrack>): TtmlTrack? =
        tracks.firstOrNull { it.language?.contains("Latn", ignoreCase = true) == true }
            ?: tracks.firstOrNull()

    private fun String.normalizedText(): String = replace(WHITESPACE_REGEX, " ").trim()

    private fun String.normalizedEdgeWhitespace(): String = trim { it.isWhitespace() || it == '\u00A0' }

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val OFFSET_TIME_REGEX = Regex("""^([0-9]+(?:\.[0-9]+)?)(ms|h|m|s|f|t)$""", RegexOption.IGNORE_CASE)
    private val UNSAFE_XML_REGEX = Regex("""<!\s*(?:DOCTYPE|ENTITY)\b""", RegexOption.IGNORE_CASE)
    private val ROOT_TAG_REGEX = Regex("""<(?:[A-Za-z_][\w.-]*:)?tt\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val NAMESPACE_DECLARATION_REGEX = Regex("""\bxmlns:([A-Za-z_][\w.-]*)\s*=""", RegexOption.IGNORE_CASE)
    private val KNOWN_NAMESPACES =
        linkedMapOf(
            "ttm" to "http://www.w3.org/ns/ttml#metadata",
            "tts" to "http://www.w3.org/ns/ttml#styling",
            "ttp" to "http://www.w3.org/ns/ttml#parameter",
            "itunes" to "http://music.apple.com/lyric-ttml-internal",
            "amll" to "http://www.example.com/ns/amll",
            "composer" to "http://composer.boidu.dev/ttml",
        )
    private val SUPPLEMENTARY_ROLES = setOf("x-bg", "x-translation", "x-roman")
    private const val MILLIS_PER_SECOND = 1000.0
    private const val DEFAULT_FINAL_LINE_DURATION_MS = 4000L
}
