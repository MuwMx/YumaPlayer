/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.Base64

/**
 * Reads Spotify's current persisted-query hashes from its public web player.
 *
 * `open.spotify.com` names its current build and lists its JavaScript bundles, and each bundle
 * declares its GraphQL operations as `new X("operationName","query","<sha256>")`. Nothing here
 * needs an account or a token.
 */
class SpotifyHashDiscovery(
    private val fetch: suspend (url: String) -> String,
    private val parallelism: Int = DEFAULT_PARALLELISM,
) {
    /**
     * The operations found in one build of the web player. [complete] is false when some bundle
     * could not be downloaded, so an operation missing from [hashes] may exist in that bundle.
     */
    data class Table(
        val buildVersion: String?,
        val hashes: Map<String, String>,
        val complete: Boolean,
    )

    /** Returns null when the page does not list any bundle (for example an error page). */
    suspend fun discover(): Table? =
        withContext(Dispatchers.IO) {
            val html = fetch(WEB_PLAYER_URL)
            val bundleUrls = parseBundleUrls(html)
            if (bundleUrls.isEmpty()) return@withContext null

            val permits = Semaphore(parallelism)
            val bodies =
                coroutineScope {
                    bundleUrls
                        .map { url ->
                            async { permits.withPermit { fetchOrNull(url) } }
                        }.awaitAll()
                }
            val hashes = HashMap<String, String>()
            bodies.filterNotNull().forEach { hashes += parseOperations(it) }
            Table(
                buildVersion = parseBuildVersion(html),
                hashes = hashes,
                complete = bodies.none { it == null },
            )
        }

    private suspend fun fetchOrNull(url: String): String? =
        try {
            fetch(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        const val WEB_PLAYER_URL = "https://open.spotify.com/"
        const val DEFAULT_PARALLELISM = 6

        private val BUNDLE = Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/[^"']+?\.js""")
        private val OPERATION = Regex("""new [\w$]+(?:\.[\w$]+)?\("(\w+)","(?:query|mutation|subscription)","([0-9a-f]{64})"""")
        private val json = Json { ignoreUnknownKeys = true }

        /** The JavaScript bundles of the web player, from the page's links and its CDN file list. */
        internal fun parseBundleUrls(html: String): Set<String> {
            val urls = LinkedHashSet<String>()
            BUNDLE.findAll(html).forEach { urls += it.value }
            scriptJson(html, "__CDN_FILE_URLS__")?.values?.forEach { value ->
                val url = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
                if (url.endsWith(".js") && "/web-player/" in url) urls += url
            }
            return urls
        }

        internal fun parseBuildVersion(html: String): String? =
            (scriptJson(html, "appServerConfig")?.get("buildVersion") as? JsonPrimitive)?.content

        /** Operation name to hash for every operation declared in [bundle]. */
        internal fun parseOperations(bundle: String): Map<String, String> =
            OPERATION.findAll(bundle).associate { it.groupValues[1] to it.groupValues[2] }

        /** The content of `<script id="[id]">`, which the page writes as base64 JSON or plain JSON. */
        private fun scriptJson(
            html: String,
            id: String,
        ): JsonObject? {
            val raw = Regex("""<script id="$id"[^>]*>([^<]+)</script>""").find(html)?.groupValues?.get(1)?.trim() ?: return null
            val decoded = runCatching { String(Base64.getDecoder().decode(raw)) }.getOrDefault(raw)
            return runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull()
        }
    }
}
