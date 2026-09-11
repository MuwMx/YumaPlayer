/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import moe.rukamori.archivetune.paxsenix.models.AppleMusicLyricsResponse
import java.util.Locale

internal object PaxsenixParser {
    private val lyricsContentKeys =
        listOf(
            "lyrics",
            "lrc",
            "content",
            "lines",
            "text",
            "plainLyrics",
            "syncedLyrics",
            "line",
            "lyric",
        )

    fun cleanJsonLyrics(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val payload = runCatching { PaxsenixApi.json.parseToJsonElement(trimmed) }.getOrNull() ?: return trimmed
        return extractLyrics(payload)
    }

    fun parseLyrics(raw: String): String? {
        val timedLyrics = runCatching { PaxsenixApi.json.decodeFromString<AppleMusicLyricsResponse>(raw) }.getOrNull()
        return timedLyrics
            ?.takeIf { it.content.isNotEmpty() }
            ?.let(::convertAppleMusicToLrc)
            ?.takeIf(String::isNotBlank)
            ?: cleanJsonLyrics(raw)
    }

    private fun extractLyrics(element: JsonElement): String? =
        when (element) {
            JsonNull -> null
            is JsonPrimitive -> {
                if (!element.isString) {
                    null
                } else {
                    val value = element.content.trim()
                    if (value.isEmpty()) {
                        null
                    } else {
                        val nestedPayload = runCatching { PaxsenixApi.json.parseToJsonElement(value) }.getOrNull()
                        if (nestedPayload != null && nestedPayload !is JsonPrimitive) {
                            extractLyrics(nestedPayload)
                        } else {
                            value
                        }
                    }
                }
            }

            is JsonArray ->
                element
                    .mapNotNull(::extractLyrics)
                    .joinToString("\n")
                    .trim()
                    .takeIf(String::isNotEmpty)

            is JsonObject -> {
                if (element.isErrorPayload()) {
                    null
                } else {
                    lyricsContentKeys
                        .asSequence()
                        .mapNotNull { key -> element[key]?.let(::extractLyrics) }
                        .firstOrNull()
                        ?: (element["metadata"] as? JsonObject)?.let { metadata ->
                            lyricsContentKeys
                                .asSequence()
                                .mapNotNull { key -> metadata[key]?.let(::extractLyrics) }
                                .firstOrNull()
                        }
                        ?: element["words"]?.let { words ->
                            when (words) {
                                is JsonArray ->
                                    words
                                        .mapNotNull(::extractLyrics)
                                        .joinToString(" ")
                                        .trim()
                                        .takeIf(String::isNotEmpty)

                                else -> extractLyrics(words)
                            }
                        }
                }
            }
        }

    private fun JsonObject.isErrorPayload(): Boolean {
        if ((this["isError"] as? JsonPrimitive)?.booleanOrNull == true) return true
        if ((this["ok"] as? JsonPrimitive)?.booleanOrNull == false) return true

        return when (val error = this["error"]) {
            null, JsonNull -> false
            is JsonPrimitive -> error.booleanOrNull ?: error.content.trim().isNotEmpty()
            is JsonArray -> error.isNotEmpty()
            is JsonObject -> error.isNotEmpty()
        }
    }

    fun convertAppleMusicToLrc(response: AppleMusicLyricsResponse): String =
        response.content.joinToString("\n") { line ->
            val minutes = line.timestamp / 1_000 / 60
            val seconds = (line.timestamp / 1_000) % 60
            val hundredths = (line.timestamp % 1_000) / 10
            val time = String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
            val text = line.text.joinToString(" ") { it.text.trim() }
            "$time$text"
        }
}
