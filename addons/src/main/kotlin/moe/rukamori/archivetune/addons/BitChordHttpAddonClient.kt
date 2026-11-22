package moe.rukamori.archivetune.addons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class BitChordHttpAddonClient(
    private val httpClient: OkHttpClient = defaultClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(
        addon: AddonDescriptor,
        query: String,
        title: String = "",
        artist: String = "",
        quality: String,
    ): Result<List<AddonTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = addon.downloadUrl.ifBlank { addon.repositoryUrl }.trimEnd('/')
            val httpUrl = "$base/search".toHttpUrlOrNull()
                ?: throw IOException("Invalid search URL for addon ${addon.id}")
            val urlBuilder = httpUrl.newBuilder()
            addon.defaultSettings.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            urlBuilder.setQueryParameter("quality", quality)
            urlBuilder.setQueryParameter("q", query)
            if (title.isNotBlank()) urlBuilder.setQueryParameter("title", title)
            if (artist.isNotBlank()) urlBuilder.setQueryParameter("artist", artist)

            val request = Request.Builder().url(urlBuilder.build()).header("Accept", "application/json").build()
            val body = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from search on ${addon.id}")
                resp.body.string().ifEmpty { throw IOException("Empty search response body from ${addon.id}") }
            }
            AddonResponseParser.parseTracks(body)
        }
    }

    suspend fun stream(
        addon: AddonDescriptor,
        trackId: String,
        quality: String,
    ): Result<AddonStream?> = withContext(Dispatchers.IO) {
        runCatching {
            val base = addon.downloadUrl.ifBlank { addon.repositoryUrl }.trimEnd('/')
            val encodedId = URLEncoder.encode(trackId, "UTF-8").replace("+", "%20")
            val httpUrl = "$base/stream/$encodedId".toHttpUrlOrNull()
                ?: throw IOException("Invalid stream URL for addon ${addon.id}")
            val urlBuilder = httpUrl.newBuilder()
            addon.defaultSettings.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            urlBuilder.setQueryParameter("quality", quality)

            val request = Request.Builder().url(urlBuilder.build()).header("Accept", "application/json").build()
            val body = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from stream on ${addon.id}")
                resp.body.string()
            }
            if (body.isBlank()) return@runCatching null

            val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: return@runCatching AddonResponseParser.parseStream(body, addon.id)

            val errorMsg = obj["error"]?.jsonPrimitive?.content
            val url = obj["url"]?.jsonPrimitive?.content ?: obj["streamUrl"]?.jsonPrimitive?.content
            if (!errorMsg.isNullOrBlank() && url.isNullOrBlank()) {
                Timber.tag(TAG).w("Stream error for $trackId in ${addon.id}: $errorMsg")
                return@runCatching null
            }

            val encPrim = runCatching { obj["encrypted"]?.jsonPrimitive }.getOrNull()
            val isEncrypted = encPrim?.booleanOrNull == true || encPrim?.intOrNull == 1 ||
                encPrim?.content?.lowercase()?.let { it != "false" && it != "none" && it != "0" } == true
            if (isEncrypted) {
                Timber.tag(TAG).w("Encrypted DRM stream skipped for $trackId in ${addon.id}")
                return@runCatching null
            }
            if (url.isNullOrBlank()) return@runCatching null

            var sampleRate = obj["sampleRate"]?.jsonPrimitive?.longOrNull?.toInt()
                ?: obj["sampleRateHz"]?.jsonPrimitive?.longOrNull?.toInt()
            if (sampleRate != null && sampleRate < 1000) sampleRate *= 1000

            val bitDepth = obj["bitDepth"]?.jsonPrimitive?.longOrNull?.toInt()
                ?: obj["bitsPerSample"]?.jsonPrimitive?.longOrNull?.toInt()

            var bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull?.toInt()
                ?: obj["bitrateKbps"]?.jsonPrimitive?.longOrNull?.toInt()
            if (bitrate != null && bitrate > 3000) bitrate /= 1000

            val codec = obj["codec"]?.jsonPrimitive?.content
                ?: obj["fileCodec"]?.jsonPrimitive?.content
                ?: obj["container"]?.jsonPrimitive?.content
                ?: obj["containerFormat"]?.jsonPrimitive?.content

            val fallbackText = listOfNotNull(
                obj["quality"]?.jsonPrimitive?.content,
                obj["streamQuality"]?.jsonPrimitive?.content,
                obj["audioQuality"]?.jsonPrimitive?.content,
                obj["format"]?.jsonPrimitive?.content,
            ).joinToString(" ")

            AddonStream(
                url = url,
                codec = codec,
                sampleRateHz = sampleRate ?: extractKhz(fallbackText),
                bitsPerSample = bitDepth ?: extractBitDepth(fallbackText),
                bitrateKbps = bitrate ?: extractKbps(fallbackText),
                origin = addon.id,
            )
        }
    }

    fun resolveQualityParam(addon: AddonDescriptor, pickedQuality: String?, requestedQuality: Int): String {
        if (!pickedQuality.isNullOrBlank()) {
            if (addon.qualityOptions.isEmpty()) return pickedQuality
            val match = addon.qualityOptions.firstOrNull { it.value.equals(pickedQuality, ignoreCase = true) }
            if (match != null) return match.value
        }

        val tier = when {
            requestedQuality >= 3 -> "LOSSLESS"
            requestedQuality == 2 -> "HIGH"
            else -> "LOW"
        }
        if (addon.qualityOptions.isEmpty()) return tier

        val keywords = if (tier == "HIGH") listOf("high", "320", "normal", "standard") else listOf("low", "96", "128", "min")
        return if (tier == "LOSSLESS") {
            addon.qualityOptions.first().value
        } else {
            addon.qualityOptions.firstOrNull { opt ->
                keywords.any { k -> opt.value.contains(k, ignoreCase = true) || opt.label.contains(k, ignoreCase = true) }
            }?.value ?: addon.qualityOptions.first().value
        }
    }

    private fun extractKhz(text: String): Int? {
        val match = Regex("""(\d+(?:\.\d+)?)\s*kHz""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return (match.groupValues[1].toDoubleOrNull()?.let { it * 1000 })?.toInt()
    }

    private fun extractBitDepth(text: String): Int? =
        Regex("""(\d+)\s*bit""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractKbps(text: String): Int? =
        Regex("""(\d+)\s*kbps""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()

    companion object {
        private const val TAG = "BitChordHttpClient"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
