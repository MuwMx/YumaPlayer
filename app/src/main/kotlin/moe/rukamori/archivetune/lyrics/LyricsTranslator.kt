package moe.rukamori.archivetune.lyrics

import moe.rukamori.archivetune.ai.AiLyricsDocumentParser
import moe.rukamori.archivetune.ai.AiLyricsSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import java.util.UUID

object LyricsTranslator {
    private const val MaxTranslatorItemsPerBatch = 50
    private const val MaxTranslatorCharsPerBatch = 4000

    suspend fun translate(
        lyrics: String,
        language: Language,
    ): String = withContext(Dispatchers.IO) {
        val document = AiLyricsDocumentParser.parse(lyrics)
        if (document.segments.isEmpty()) return@withContext lyrics

        val translator = Translator()
        val translatedSegments = mutableMapOf<Int, String>()
        
        document.segments.chunkedForTranslator().forEach { batch ->
            val separator = uniqueTranslationSeparator(batch)
            val joined = batch.joinToString(separator = separator) { segment -> segment.text }
            val translatedJoined = translator.translateBlocking(joined, language).translatedText
            val parts = translatedJoined.split(separator)

            if (parts.size == batch.size) {
                batch.forEachIndexed { index, segment ->
                    translatedSegments[segment.id] = parts[index].trim()
                }
            } else {
                batch.forEach { segment ->
                    translatedSegments[segment.id] = translator.translateBlocking(segment.text, language).translatedText.trim()
                }
            }
        }

        document.rebuild(translatedSegments)
    }

    private fun List<AiLyricsSegment>.chunkedForTranslator(): List<List<AiLyricsSegment>> {
        val chunks = ArrayList<List<AiLyricsSegment>>()
        val current = ArrayList<AiLyricsSegment>()
        var currentChars = 0

        forEach { segment ->
            val nextSize = currentChars + segment.text.length
            if (current.isNotEmpty() && (current.size >= MaxTranslatorItemsPerBatch || nextSize > MaxTranslatorCharsPerBatch)) {
                chunks.add(current.toList())
                current.clear()
                currentChars = 0
            }
            current.add(segment)
            currentChars += segment.text.length
        }

        if (current.isNotEmpty()) chunks.add(current.toList())
        return chunks
    }

    private fun uniqueTranslationSeparator(segments: List<AiLyricsSegment>): String {
        var separator = "<<<SEP-${UUID.randomUUID()}>>>"
        while (segments.any { segment -> segment.text.contains(separator) }) {
            separator = "<<<SEP-${UUID.randomUUID()}>>>"
        }
        return separator
    }

    fun formatLyricsSyncOffset(offsetMs: Int): String {
        val sign = if (offsetMs >= 0) "+" else "-"
        val absOffset = Math.abs(offsetMs)
        val seconds = absOffset / 1000
        val millis = (absOffset % 1000) / 10
        return "%s%d.%02d s".format(sign, seconds, millis)
    }
}
