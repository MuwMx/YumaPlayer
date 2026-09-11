/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

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

    fun parseDocument(ttml: String): Result<TtmlDocument> =
        runCatching {
            val source = ttml.removePrefix("\uFEFF").trimStart()
            if (UNSAFE_XML_REGEX.containsMatchIn(source)) {
                throw TtmlParseException(TtmlParseFailure.UNSAFE_XML)
            }

            val document = TtmlXmlUtils.parseXml(TtmlXmlUtils.declareMissingNamespaces(source))
            val root = document.documentElement ?: throw TtmlParseException(TtmlParseFailure.MALFORMED_XML)
            if (!root.hasLocalName("tt")) throw TtmlParseException(TtmlParseFailure.MALFORMED_XML)

            val timingContext = TtmlTimeParser.readTimingContext(root)
            val body = root.descendantElements().firstOrNull { it.hasLocalName("body") }
            val bodyStartMs = body?.attribute("begin")?.let { TtmlTimeParser.parseTime(it, timingContext) } ?: 0L
            val bodyEndMs =
                body?.attribute("end")?.let { TtmlTimeParser.parseTime(it, timingContext) }
                    ?: body?.attribute("dur")?.let { TtmlTimeParser.parseTime(it, timingContext) }?.let(bodyStartMs::plus)
            val declaredAgents = TtmlSpanHandler.parseAgents(root)
            val headRomanizations = TtmlSpanHandler.parseHeadTracks(root, "transliteration", timingContext)
            val headTranslations = TtmlSpanHandler.parseHeadTracks(root, "translation", timingContext)
            val rawLines = TtmlSpanHandler.parseRawLines(root, timingContext, headTranslations, headRomanizations)
            if (rawLines.isEmpty()) throw TtmlParseException(TtmlParseFailure.MISSING_LINES)

            val agents = TtmlSpanHandler.resolveUnknownAgents(declaredAgents, rawLines)
            val lines = TtmlSpanHandler.resolveLines(rawLines, bodyEndMs, agents.associateBy(TtmlAgent::id))
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
                                startTime = timing.startMs / TtmlTimeParser.MILLIS_PER_SECOND,
                                endTime = timing.endMs / TtmlTimeParser.MILLIS_PER_SECOND,
                            )
                        }
                    }
                val backgroundWords =
                    line.backgrounds.flatMap { track ->
                        track.segments.mapNotNull { segment ->
                            segment.timing?.let { timing ->
                                ParsedWord(
                                    text = segment.text,
                                    startTime = timing.startMs / TtmlTimeParser.MILLIS_PER_SECOND,
                                    endTime = timing.endMs / TtmlTimeParser.MILLIS_PER_SECOND,
                                    isBackground = true,
                                )
                            }
                        }
                    }
                val romanization = TtmlSpanHandler.chooseRomanization(line.romanizations)

                ParsedLine(
                    text = line.text,
                    startTime = line.timing.startMs / TtmlTimeParser.MILLIS_PER_SECOND,
                    endTime = line.timing.endMs / TtmlTimeParser.MILLIS_PER_SECOND,
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

    fun parse(ttml: String): List<ParsedLine> = parseTTML(ttml)
}
