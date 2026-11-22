package moe.rukamori.archivetune.addons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URI
import java.util.concurrent.TimeUnit

class BitChordIndexLoader(
    private val httpClient: OkHttpClient = defaultClient,
) {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RawSpineModule(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String,
        @SerialName("author") val author: String = "",
        @SerialName("version") val version: String = "",
        @SerialName("code") val code: Int = 0,
        @SerialName("type") val type: String = "MODULE",
        @SerialName("description") val description: String = "",
        @SerialName("tags") private val declaredTags: List<String> = emptyList(),
        @SerialName("labels") private val declaredLabels: List<String> = emptyList(),
        @SerialName("download") val download: String = "",
        @SerialName("logo") val logo: String? = null,
        @SerialName("icon") val icon: String? = null,
        @SerialName("lastUpdated") val lastUpdated: String = "",
    ) {
        val tags: List<String> get() = declaredTags.ifEmpty { declaredLabels }
    }

    suspend fun loadIndex(indexUrl: String): Result<List<AddonDescriptor>> = withContext(Dispatchers.IO) {
        runCatching {
            validateUrl(indexUrl)
            val request = Request.Builder().url(indexUrl).build()
            val body = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} from $indexUrl")
                resp.body?.string() ?: throw IllegalStateException("Empty body from $indexUrl")
            }
            parseIndex(body, indexUrl)
        }.onFailure {
            Timber.tag(TAG).e(it, "Failed to load index from $indexUrl")
        }
    }

    fun parseIndex(body: String, baseUrl: String = ""): List<AddonDescriptor> {
        val baseUri = if (baseUrl.isNotBlank()) URI(baseUrl) else null
        val obj = json.decodeFromString(JsonObject.serializer(), body)
        val listSerializer = ListSerializer(RawSpineModule.serializer())
        val rawModules = obj.entries
            .filter { it.key.startsWith("category:") && it.key !in excludedCategories }
            .flatMap { (_, value) ->
                runCatching { json.decodeFromJsonElement(listSerializer, value) }.getOrElse { emptyList() }
            }
            .distinctBy { it.id }

        return rawModules.mapNotNull { raw ->
            val resolvedDownload = resolveDownloadUrl(raw.download, baseUri) ?: return@mapNotNull null
            AddonDescriptor(
                id = raw.id,
                name = raw.name,
                author = raw.author,
                version = raw.version,
                code = raw.code,
                type = raw.type,
                description = raw.description,
                tags = raw.tags,
                downloadUrl = resolvedDownload,
                icon = raw.icon ?: raw.logo,
                lastUpdated = raw.lastUpdated,
                format = AddonFormat.BITCHORD_INDEX,
            )
        }
    }

    private fun resolveDownloadUrl(download: String, baseUri: URI?): String? {
        val trimmed = download.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val uri = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                URI(trimmed)
            } else {
                baseUri?.resolve(trimmed) ?: URI(trimmed)
            }
            validateUri(uri)
            uri.toASCIIString()
        }.getOrNull()
    }

    companion object {
        private const val TAG = "BitChordIndexLoader"
        private val excludedCategories = setOf("category:artworks", "category:testing")

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        fun validateUrl(url: String) {
            val uri = URI(url)
            validateUri(uri)
        }

        private fun validateUri(uri: URI) {
            val scheme = uri.scheme?.lowercase()
            require(scheme == "http" || scheme == "https") { "Invalid URL scheme: ${uri.scheme}" }
            require(!uri.host.isNullOrBlank()) { "URL must have a valid host: $uri" }
        }
    }
}
