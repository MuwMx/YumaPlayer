package moe.rukamori.archivetune.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object AddonResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun jsString(value: String): String = JsonPrimitive(value).toString()

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
        val rawDur = obj["duration"]?.jsonPrimitive?.doubleOrNull ?: obj["durationMs"]?.jsonPrimitive?.doubleOrNull
        val durationMs = when {
            rawDur == null -> null
            rawDur < 100_000.0 -> (rawDur * 1000.0).toLong()
            else -> rawDur.toLong()
        }
        val isrc = obj["isrc"]?.jsonPrimitive?.content
        val streamUrl = obj["streamUrl"]?.jsonPrimitive?.content ?: obj["streamURL"]?.jsonPrimitive?.content
        return AddonTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            isrc = isrc,
            streamUrl = streamUrl,
        )
    }

    fun parseStream(raw: String, addonId: String): AddonStream? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return AddonStream(url = trimmed, origin = addonId)
        }
        val obj = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull() ?: return null
        val trackObj = obj["track"] as? JsonObject

        val url = obj["streamUrl"]?.jsonPrimitive?.content
            ?: obj["url"]?.jsonPrimitive?.content
            ?: trackObj?.get("streamUrl")?.jsonPrimitive?.content
            ?: trackObj?.get("url")?.jsonPrimitive?.content
            ?: return null

        val mimeType = obj["mimeType"]?.jsonPrimitive?.content
            ?: trackObj?.get("mimeType")?.jsonPrimitive?.content
        val audioQuality = obj["audioQuality"]?.jsonPrimitive?.content
            ?: trackObj?.get("audioQuality")?.jsonPrimitive?.content

        var sampleRate = obj["sampleRate"]?.jsonPrimitive?.longOrNull?.toInt()
            ?: obj["sampleRateHz"]?.jsonPrimitive?.longOrNull?.toInt()
            ?: trackObj?.get("sampleRate")?.jsonPrimitive?.longOrNull?.toInt()
        if (sampleRate != null && sampleRate < 1000) sampleRate *= 1000

        val bitDepth = obj["bitDepth"]?.jsonPrimitive?.longOrNull?.toInt()
            ?: obj["bitsPerSample"]?.jsonPrimitive?.longOrNull?.toInt()
            ?: trackObj?.get("bitDepth")?.jsonPrimitive?.longOrNull?.toInt()

        var bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull?.toInt()
            ?: obj["bitrateKbps"]?.jsonPrimitive?.longOrNull?.toInt()
            ?: trackObj?.get("bitrate")?.jsonPrimitive?.longOrNull?.toInt()
        if (bitrate != null && bitrate > 3000) bitrate /= 1000

        val expiresAtMs = obj["expiresAtMs"]?.jsonPrimitive?.longOrNull ?: 0L
        val coverArtUrl = obj["coverArtUrl"]?.jsonPrimitive?.content
            ?: obj["artworkUrl"]?.jsonPrimitive?.content

        val rawCodec = obj["codec"]?.jsonPrimitive?.content
            ?: trackObj?.get("codec")?.jsonPrimitive?.content
        val codec = inferCodec(rawCodec, mimeType, audioQuality, url)

        return AddonStream(
            url = url,
            expiresAtMs = expiresAtMs,
            codec = codec,
            bitsPerSample = bitDepth,
            sampleRateHz = sampleRate,
            bitrateKbps = bitrate,
            coverArtUrl = coverArtUrl,
            origin = addonId,
        )
    }

    fun buildContextArg(settings: Map<String, String>): String {
        val root = buildJsonObject {
            put("settings", buildJsonObject {
                for ((k, v) in settings) {
                    put(k, buildJsonObject {
                        put("value", JsonPrimitive(v))
                    })
                }
            })
        }
        return root.toString()
    }

    private fun inferCodec(
        rawCodec: String?,
        mimeType: String?,
        audioQuality: String?,
        url: String,
    ): String? {
        if (!rawCodec.isNullOrBlank()) return rawCodec
        if (mimeType != null) {
            val m = mimeType.lowercase()
            when {
                m.contains("flac") -> return "flac"
                m.contains("audio/mpeg") || m.contains("mp3") -> return "mp3"
                m.contains("aac") -> return "aac"
                m.contains("opus") -> return "opus"
                m.contains("ogg") -> return "ogg"
                m.contains("wav") -> return "wav"
            }
        }
        if (audioQuality != null) {
            val q = audioQuality.uppercase()
            if (q == "LOSSLESS" || q == "FLAC") return "flac"
        }
        val cleanUrl = url.substringBefore('?')
        val ext = cleanUrl.substringAfterLast('.', "").lowercase()
        return if (ext in supportedExtensions) ext else null
    }

    private val supportedExtensions = setOf("flac", "mp3", "aac", "m4a", "mp4", "opus", "ogg", "wav")
}
