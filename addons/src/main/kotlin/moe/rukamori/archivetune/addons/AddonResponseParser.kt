package moe.rukamori.archivetune.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.json.JSONObject

internal object AddonResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseTracks(raw: String): List<AddonTrack> {
        val elem = json.parseToJsonElement(raw)
        val array = when (elem) {
            is JsonArray -> elem
            is JsonObject -> elem["tracks"]?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { it.toAddonTrackOrNull() }
    }

    private fun JsonElement.toAddonTrackOrNull(): AddonTrack? {
        val obj = this as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val artist = obj["artist"]?.jsonPrimitive?.content.orEmpty()
        val album = obj["album"]?.jsonPrimitive?.content
        val rawDur = obj["duration"]?.jsonPrimitive?.longOrNull ?: obj["durationMs"]?.jsonPrimitive?.longOrNull
        val durationMs = if (rawDur != null && rawDur < 100_000L) rawDur * 1000L else rawDur
        val isrc = obj["isrc"]?.jsonPrimitive?.content
        return AddonTrack(id = id, title = title, artist = artist, album = album, durationMs = durationMs, isrc = isrc)
    }

    fun parseStream(raw: String, addonId: String): AddonStream? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return AddonStream(url = trimmed, origin = addonId)
        }
        val obj = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull() ?: return null
        val url = obj["streamUrl"]?.jsonPrimitive?.content ?: obj["url"]?.jsonPrimitive?.content ?: return null
        val codec = obj["codec"]?.jsonPrimitive?.content
        val bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull?.toInt()
        return AddonStream(url = url, codec = codec, bitrateKbps = bitrate, origin = addonId)
    }

    fun buildContextArg(settings: Map<String, String>): String =
        settings.entries.joinToString(prefix = "{settings:{", postfix = "}}") { (k, v) ->
            "\"$k\":{\"value\":${JSONObject.quote(v)}}"
        }
}
