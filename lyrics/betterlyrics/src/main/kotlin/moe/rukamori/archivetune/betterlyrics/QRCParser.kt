/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

object QRCParser {
    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val words: List<ParsedWord>,
        val agent: String? = null,
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
    )

    private data class TimedWord(
        val text: String,
        val startTimeMs: Long,
        val durationMs: Long,
    )

    private data class MutableParsedLine(
        var text: String,
        var startTimeMs: Long,
        var durationMs: Long,
        var words: MutableList<TimedWord>,
        var agent: String? = null,
    )

    private data class TrackMetadata(
        val title: String?,
        val artist: String?,
    )

    private data class AgentContext(
        val aliases: MutableMap<String, String> = mutableMapOf(),
        var nextVoiceId: Int = 1,
        var currentSinger: String? = null,
    )

    private val lineTimingRegex = Regex("""^\[(\d{1,8}),(\d{1,8})](.*)$""")
    private val wordTimingRegex = Regex("""(.*?)\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)""")
    private val metadataRegex = Regex("""^\[([A-Za-z]+):(.*)]$""")
    private val lyricContentAttributeRegex = Regex("""LyricContent\s*=\s*\"""", RegexOption.IGNORE_CASE)
    private val metadataPrefixes =
        setOf(
            "词",
            "作词",
            "填词",
            "曲",
            "作曲",
            "谱曲",
            "编曲",
            "和声",
            "和声编写",
            "混音",
            "混音师",
            "母带",
            "母带处理",
            "母带工程",
            "吉他",
            "贝斯",
            "鼓",
            "钢琴",
            "键盘",
            "弦乐",
            "制作",
            "制作人",
            "配唱",
            "配唱制作人",
            "执行制作",
            "监制",
            "统筹",
            "演唱",
            "原唱",
            "翻唱",
            "歌手",
            "后期",
            "和音",
            "录音",
            "录音师",
            "录音室",
            "策划",
            "企划",
            "宣传",
            "文案",
            "伴奏",
            "美工",
            "海报",
            "封面",
            "旁白",
            "翻译",
            "译词",
            "校对",
            "鸣谢",
            "特别鸣谢",
            "出品",
            "出品人",
            "发行",
            "版权",
            "op",
            "sp",
            "opsp",
            "writtenby",
            "producedby",
            "composedby",
            "arrangedby",
            "lyricsby",
            "musicby",
            "mixedby",
            "masteredby",
            "recordedby",
            "performedby",
            "mixing",
            "mastering",
            "recording",
            "arrangement",
            "production",
            "vocal",
            "vocals",
            "backingvocals",
            "guitar",
            "bass",
            "drums",
            "piano",
            "keys",
            "keyboards",
            "strings",
            "producer",
            "executiveproducer",
            "lyricist",
            "lyrics",
            "lyric",
            "music",
            "composer",
            "arranger",
            "engineer",
            "translator",
            "translation",
            "publisher",
            "copyright",
        )
    private val colonlessCreditPrefixes =
        listOf(
            "作词",
            "作曲",
            "编曲",
            "填词",
            "谱曲",
            "制作人",
            "演唱",
            "原唱",
            "混音",
            "母带",
            "written by",
            "composed by",
            "produced by",
            "arranged by",
            "lyrics by",
            "music by",
            "mixed by",
            "mastered by",
            "recorded by",
            "performed by",
            "lyricist",
            "composer",
            "producer",
            "arranger",
        )

    fun isQrc(content: String): Boolean {
        if (content.contains("<QrcInfos", ignoreCase = true) || content.contains("LyricContent=", ignoreCase = true)) {
            return true
        }
        return content.lineSequence().any { line ->
            val match = lineTimingRegex.matchEntire(line.trim()) ?: return@any false
            wordTimingRegex.containsMatchIn(match.groupValues[3])
        }
    }

    fun hasWordTimings(content: String): Boolean =
        extractLyricContent(content).lineSequence().any { line ->
            val match = lineTimingRegex.matchEntire(line.trim()) ?: return@any false
            wordTimingRegex.containsMatchIn(match.groupValues[3])
        }

    fun parseQrc(content: String): List<ParsedLine> {
        val lyricContent = extractLyricContent(content)
        val metadata = readMetadata(lyricContent)
        val context = AgentContext()
        val parsedLines = mutableListOf<MutableParsedLine>()

        lyricContent.lineSequence().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty() || metadataRegex.matches(trimmedLine)) return@forEach

            val lineMatch = lineTimingRegex.matchEntire(trimmedLine) ?: return@forEach
            val words =
                wordTimingRegex
                    .findAll(lineMatch.groupValues[3])
                    .map { match ->
                        TimedWord(
                            text = match.groupValues[1],
                            startTimeMs = match.groupValues[2].toLong(),
                            durationMs = match.groupValues[3].toLong(),
                        )
                    }.filter { it.text.isNotEmpty() }
                    .toMutableList()

            if (words.isEmpty()) return@forEach

            val parsedLine =
                MutableParsedLine(
                    text = words.joinToString(separator = "") { it.text },
                    startTimeMs = lineMatch.groupValues[1].toLong(),
                    durationMs = lineMatch.groupValues[2].toLong(),
                    words = words,
                )

            if (parsedLines.isEmpty() && isCreditLineWithoutColon(parsedLine.text)) return@forEach

            if (extractSinger(parsedLine, context, parsedLines.size < 5, metadata)) {
                parsedLines += parsedLine
            }
        }

        while (parsedLines.isNotEmpty() && isTrailingCreditOrMetadata(parsedLines.last(), metadata)) {
            parsedLines.removeAt(parsedLines.lastIndex)
        }

        return parsedLines.mapIndexed { index, line ->
            val nextLineStartMs = parsedLines.getOrNull(index + 1)?.startTimeMs
            val fallbackLineEndMs =
                line.words.lastOrNull()?.let { it.startTimeMs + it.durationMs }
                    ?: nextLineStartMs
                    ?: line.startTimeMs
            val lineEndMs =
                when {
                    line.durationMs > 0L -> line.startTimeMs + line.durationMs
                    nextLineStartMs != null -> nextLineStartMs
                    else -> fallbackLineEndMs
                }.coerceAtLeast(line.startTimeMs)
            val parsedWords =
                line.words.mapIndexed { wordIndex, word ->
                    val nextWordStartMs = line.words.getOrNull(wordIndex + 1)?.startTimeMs
                    val wordEndMs =
                        when {
                            word.durationMs > 0L -> word.startTimeMs + word.durationMs
                            nextWordStartMs != null -> nextWordStartMs
                            else -> lineEndMs
                        }.coerceAtLeast(word.startTimeMs)
                    ParsedWord(
                        text = word.text,
                        startTime = word.startTimeMs / 1000.0,
                        endTime = wordEndMs / 1000.0,
                    )
                }

            ParsedLine(
                text = line.text.trim(),
                startTime = line.startTimeMs / 1000.0,
                endTime = lineEndMs / 1000.0,
                words = parsedWords,
                agent = line.agent,
            )
        }
    }

    private fun extractLyricContent(content: String): String {
        val attributeMatch = lyricContentAttributeRegex.find(content) ?: return content
        val valueStart = attributeMatch.range.last + 1
        val valueEnd = content.indexOf('"', startIndex = valueStart)
        if (valueEnd < 0) return content
        return unescapeXml(content.substring(valueStart, valueEnd))
    }

    private fun unescapeXml(value: String): String =
        value
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    private fun readMetadata(content: String): TrackMetadata {
        var title: String? = null
        var artist: String? = null
        content.lineSequence().forEach { line ->
            val match = metadataRegex.matchEntire(line.trim()) ?: return@forEach
            when (match.groupValues[1].lowercase()) {
                "ti" -> title = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
                "ar" -> artist = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
            }
        }
        return TrackMetadata(title = title, artist = artist)
    }

    private fun extractSinger(
        line: MutableParsedLine,
        context: AgentContext,
        isFirstFewLines: Boolean,
        metadata: TrackMetadata,
    ): Boolean {
        if (isFirstFewLines && isMetadataLine(line, metadata)) return false

        val colonIndex = line.text.indexOfFirst { it == ':' || it == '：' }
        if (colonIndex < 0) {
            line.agent = context.currentSinger
            return true
        }

        val singerName = line.text.substring(0, colonIndex).trim()
        if (isMetadataPrefix(singerName)) return false
        if (singerName.isEmpty() || singerName.length > 30) {
            line.agent = context.currentSinger
            return true
        }

        val agent =
            context.aliases.getOrPut(singerName) {
                val normalizedName = singerName.uppercase()
                if (normalizedName == "合" || normalizedName == "ALL" || normalizedName == "合唱") {
                    "v1000"
                } else {
                    "v${context.nextVoiceId++}"
                }
            }
        context.currentSinger = agent

        removeCharacterPrefix(line.words, colonIndex + 1)
        line.text = line.words.joinToString(separator = "") { it.text }.trim()
        if (line.text.isEmpty() || line.words.isEmpty()) return false

        line.agent = agent
        updateLineTiming(line)
        return true
    }

    private fun isMetadataLine(
        line: MutableParsedLine,
        metadata: TrackMetadata,
    ): Boolean {
        val normalizedText = normalizeForComparison(line.text)
        val normalizedTitle = metadata.title?.let(::normalizeForComparison).orEmpty()
        val normalizedArtist = metadata.artist?.let(::normalizeForComparison).orEmpty()

        if (normalizedTitle.isNotEmpty() && normalizedText.contains(normalizedTitle)) {
            if (normalizedArtist.isEmpty() || normalizedText.contains(normalizedArtist)) return true
        }
        if (normalizedArtist.isNotEmpty() && normalizedText == normalizedArtist) return true
        return line.words.size > 2 &&
            line.words.all { word ->
                kotlin.math.abs(word.durationMs - line.words.first().durationMs) < 10L
            }
    }

    private fun isTrailingCreditOrMetadata(
        line: MutableParsedLine,
        metadata: TrackMetadata,
    ): Boolean = isCreditLineWithoutColon(line.text) || isMetadataLine(line, metadata)

    private fun isCreditLineWithoutColon(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase()
        return colonlessCreditPrefixes.any { prefix ->
            lower == prefix ||
                (lower.startsWith(prefix) && lower.getOrNull(prefix.length)?.isWhitespace() == true)
        }
    }

    private fun isMetadataPrefix(value: String): Boolean {
        val normalized = normalizeForComparison(value)
        return normalized in metadataPrefixes ||
            normalized.endsWith("词") ||
            normalized.endsWith("曲") ||
            normalized.endsWith("声") ||
            normalized.endsWith("音")
    }

    private fun normalizeForComparison(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)

    private fun removeCharacterPrefix(
        words: MutableList<TimedWord>,
        characterCount: Int,
    ) {
        var remaining = characterCount
        val retained = mutableListOf<TimedWord>()
        words.forEach { word ->
            when {
                remaining >= word.text.length -> remaining -= word.text.length
                remaining > 0 -> {
                    val retainedText = word.text.drop(remaining).trimStart()
                    remaining = 0
                    if (retainedText.isNotEmpty()) retained += word.copy(text = retainedText)
                }
                else -> retained += word
            }
        }
        while (retained.firstOrNull()?.text?.isBlank() == true) {
            retained.removeAt(0)
        }
        retained.firstOrNull()?.let { firstWord ->
            retained[0] = firstWord.copy(text = firstWord.text.trimStart())
        }
        words.clear()
        words += retained
    }

    private fun updateLineTiming(line: MutableParsedLine) {
        val firstWord = line.words.firstOrNull() ?: return
        val lastWord = line.words.last()
        line.startTimeMs = firstWord.startTimeMs
        line.durationMs = (lastWord.startTimeMs + lastWord.durationMs - firstWord.startTimeMs).coerceAtLeast(0L)
    }
}
