package moe.rukamori.archivetune.addons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

sealed interface DetectedAddonSource {
    data class HttpAddon(val baseUrl: String, val descriptor: AddonDescriptor) : DetectedAddonSource
    data class JsModuleIndex(val indexUrl: String, val descriptors: List<AddonDescriptor>) : DetectedAddonSource
}

class AddonUrlDetector(
    private val httpClient: OkHttpClient = defaultClient,
    private val indexLoader: BitChordIndexLoader = BitChordIndexLoader(httpClient),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun identify(rawUrl: String): Result<DetectedAddonSource> = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || (scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
            return@withContext Result.failure(AddonFormatException("Enter a valid http:// or https:// URL"))
        }

        val base = normalizeBase(trimmed)
        val urlWithoutQuery = trimmed.substringBefore('?')
        val candidateUrls = if (urlWithoutQuery.endsWith(".json", ignoreCase = true)) {
            listOf(trimmed, "$base/manifest.json").distinct()
        } else {
            listOf("$base/manifest.json", trimmed).distinct()
        }

        var firstFormatError: AddonFormatException? = null
        var lastTransportError: Throwable? = null

        for (candidate in candidateUrls) {
            fetchJson(candidate).fold(
                onSuccess = { body ->
                    val detected = detect(body, candidate)
                    if (detected.isSuccess) return@withContext detected
                    val err = detected.exceptionOrNull()
                    if (err is AddonFormatException && firstFormatError == null) firstFormatError = err
                },
                onFailure = { err -> lastTransportError = err },
            )
        }

        val fallbackProbe = fetchJson("$base/search?q=music")
        if (fallbackProbe.isSuccess) {
            val probeBody = fallbackProbe.getOrNull().orEmpty()
            val probeElem = runCatching { json.parseToJsonElement(probeBody) as? JsonObject }.getOrNull()
            if (probeElem != null && probeElem["tracks"] is JsonArray) {
                val host = uri.host.orEmpty()
                val descriptor = AddonDescriptor(
                    id = base,
                    name = host.ifBlank { base },
                    downloadUrl = base,
                    repositoryUrl = base,
                    format = AddonFormat.BITCHORD_HTTP,
                )
                return@withContext Result.success(DetectedAddonSource.HttpAddon(baseUrl = base, descriptor = descriptor))
            }
        }
        Result.failure(firstFormatError ?: lastTransportError ?: AddonFormatException("Unrecognized addon URL"))
    }

    fun detect(body: String, url: String): Result<DetectedAddonSource> {
        val trimmed = body.trim()
        if (trimmed.startsWith("<") || !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return Result.failure(AddonFormatException("This URL returned HTML or text instead of JSON"))
        }
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrElse {
            return Result.failure(AddonFormatException("This URL returned HTML or text instead of JSON"))
        }
        val obj = element as? JsonObject
            ?: return Result.failure(AddonFormatException("Unsupported JSON format: expected an object"))

        if (obj.keys.any { it.startsWith("category:") }) {
            val descriptors = indexLoader.parseIndex(trimmed, url)
            return if (descriptors.isEmpty()) {
                Result.failure(AddonFormatException("Module index contains no usable modules"))
            } else {
                Result.success(DetectedAddonSource.JsModuleIndex(indexUrl = url, descriptors = descriptors))
            }
        }

        val hasResources = obj["resources"] is JsonArray
        val looksLikeManifest = hasResources || (obj.containsKey("id") && (obj.containsKey("name") || obj.containsKey("version")))
        if (!looksLikeManifest) return Result.failure(AddonFormatException("Unrecognized JSON format"))

        if (isTruthy(obj["bitchordAuth"])) {
            return Result.failure(
                AddonFormatException("This addon requires BitChord app attestation (bitchordAuth) and cannot be used in third-party players"),
            )
        }

        val resources = (obj["resources"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content.lowercase() }.orEmpty()
        if (resources.isNotEmpty() && "search" !in resources) {
            return Result.failure(AddonFormatException("This addon does not support search"))
        }

        val defaultSettings = mutableMapOf<String, String>()
        val qualityOptions = mutableListOf<AddonQualityOption>()
        (obj["settings"] as? JsonArray)?.forEach { settingElem ->
            val sObj = settingElem as? JsonObject ?: return@forEach
            val key = sObj["key"]?.jsonPrimitive?.content ?: return@forEach
            sObj["default"]?.jsonPrimitive?.content?.let { defaultSettings[key] = it }
            if (key == "quality") {
                val opts = (sObj["options"] as? JsonArray)?.mapNotNull { optElem ->
                    when (optElem) {
                        is JsonObject -> {
                            val v = optElem["value"]?.jsonPrimitive?.content ?: optElem["id"]?.jsonPrimitive?.content
                            val l = optElem["label"]?.jsonPrimitive?.content ?: optElem["name"]?.jsonPrimitive?.content ?: v
                            if (!v.isNullOrBlank()) AddonQualityOption(value = v, label = l ?: v) else null
                        }
                        else -> {
                            val v = optElem.jsonPrimitive.content
                            if (v.isNotBlank()) AddonQualityOption(value = v, label = v) else null
                        }
                    }
                }?.distinctBy { it.value }.orEmpty()
                if (opts.size > 1) qualityOptions.addAll(opts)
            }
        }

        val baseUrl = normalizeBase(url)
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        val id = obj["id"]?.jsonPrimitive?.content?.ifBlank { null } ?: baseUrl
        val name = obj["name"]?.jsonPrimitive?.content?.ifBlank { null } ?: host.ifBlank { id }

        val descriptor = AddonDescriptor(
            id = id,
            name = name,
            author = obj["author"]?.jsonPrimitive?.content.orEmpty(),
            version = obj["version"]?.jsonPrimitive?.content.orEmpty(),
            description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
            downloadUrl = baseUrl,
            repositoryUrl = baseUrl,
            icon = obj["icon"]?.jsonPrimitive?.content ?: obj["logo"]?.jsonPrimitive?.content,
            format = AddonFormat.BITCHORD_HTTP,
            qualityOptions = qualityOptions,
            defaultSettings = defaultSettings,
        )
        return Result.success(DetectedAddonSource.HttpAddon(baseUrl = baseUrl, descriptor = descriptor))
    }

    private fun fetchJson(url: String): Result<String> = runCatching {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = when (resp.code) {
                    404 -> "HTTP 404 — not found"
                    in 500..599 -> "HTTP ${resp.code} — server unavailable"
                    else -> "HTTP ${resp.code}"
                }
                throw IOException(msg)
            }
            resp.body.string().ifEmpty { throw IOException("Empty response body") }
        }
    }

    private fun isTruthy(element: JsonElement?): Boolean {
        val prim = runCatching { element?.jsonPrimitive }.getOrNull() ?: return false
        prim.booleanOrNull?.let { return it }
        prim.intOrNull?.let { return it == 1 }
        val s = prim.content.trim().lowercase()
        return s == "true" || s == "1"
    }

    companion object {
        fun normalizeBase(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.contains("://")) {
                val pathSegment = s.substringAfterLast('/')
                if (pathSegment.endsWith(".json", ignoreCase = true)) {
                    s = s.substringBeforeLast('/').trimEnd('/')
                }
            }
            return s
        }

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
