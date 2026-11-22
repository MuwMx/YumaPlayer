/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.monochrome

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import moe.rukamori.archivetune.flaccore.FlacKvStore
import java.net.URI

class MonochromeServers(
    private val kvStore: FlacKvStore,
    private val config: MonochromeConfig? = null,
) {
    enum class Kind { Streamer, Relay }

    data class Server(val url: String, val kind: Kind, val name: String)

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var added: List<Server> = emptyList()

    @Volatile
    private var initialized: Boolean = false

    suspend fun all(): List<Server> {
        ensureLoaded()
        return added + DefaultServer
    }

    suspend fun usable(): List<Server> {
        val list = all()
        val notDown = list.filter { downReason(it) == null }
        return notDown.ifEmpty { list }
    }

    suspend fun addServer(url: String, kind: Kind = Kind.Relay, name: String? = null): Boolean {
        ensureLoaded()
        val (base, _) = linkParts(url) ?: return false
        val serverName = name?.takeIf { it.isNotBlank() } ?: hostOf(base)
        if (all().any { it.url == base }) return false
        added = added + Server(base, kind, serverName)
        saveServers()
        clearDown(base)
        return true
    }

    suspend fun removeServer(url: String) {
        ensureLoaded()
        added = added.filterNot { it.url == url }
        saveServers()
        clearDown(url)
    }

    suspend fun markDown(url: String, reason: String) {
        kvStore.put("$DownKey$url", reason)
        kvStore.put("$DownAtKey$url", System.currentTimeMillis().toString())
    }

    suspend fun clearDown(url: String) {
        kvStore.put("$DownKey$url", null)
        kvStore.put("$DownAtKey$url", null)
    }

    suspend fun downReason(server: Server): String? {
        val reason = kvStore.get("$DownKey${server.url}") ?: return null
        val markedAt = kvStore.get("$DownAtKey${server.url}")?.toLongOrNull() ?: 0L
        return reason.takeIf { System.currentTimeMillis() - markedAt < UnreachableRetryMs }
    }

    private suspend fun ensureLoaded() {
        if (!initialized) {
            added = readServers()
            initialized = true
        }
    }

    private suspend fun readServers(): List<Server> {
        val storedJson = kvStore.get(ServersKey)
        val fromStore = if (!storedJson.isNullOrBlank()) {
            runCatching {
                val array = json.parseToJsonElement(storedJson) as? JsonArray ?: return@runCatching emptyList()
                array.mapNotNull { el ->
                    val obj = el as? JsonObject ?: return@mapNotNull null
                    val url = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val kindName = (obj["kind"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val kind = Kind.entries.firstOrNull { it.name == kindName } ?: Kind.Relay
                    val name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    Server(url, kind, name)
                }
            }.getOrDefault(emptyList())
        } else emptyList()

        val fromConfig = config?.customServers()?.mapNotNull { url ->
            val parts = linkParts(url) ?: return@mapNotNull null
            Server(parts.first, Kind.Relay, hostOf(parts.first))
        }.orEmpty()

        return (fromStore + fromConfig).distinctBy { it.url }
    }

    private suspend fun saveServers() {
        val list = buildJsonArray {
            added.forEach { server ->
                addJsonObject {
                    put("url", server.url)
                    put("kind", server.kind.name)
                    put("name", server.name)
                }
            }
        }
        kvStore.put(ServersKey, list.toString())
    }

    companion object {
        val DefaultServer = Server("https://tracks.monochrome.st", Kind.Streamer, "tracks.monochrome.st")
        private const val ServersKey = "mono_servers"
        private const val DownKey = "mono_down_"
        private const val DownAtKey = "mono_down_at_"
        const val UnreachableRetryMs = 10L * 60 * 1000

        fun linkParts(link: String): Pair<String, String>? {
            val text = link.trim().replace(" ", "%20").let { if ("://" in it) it else "https://$it" }
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) return null
            val origin = "${uri.scheme}://${uri.rawAuthority}"
            return (origin + uri.rawPath.orEmpty()).trimEnd('/') to origin
        }

        fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty()
    }
}
