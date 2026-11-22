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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import moe.rukamori.archivetune.flaccore.match.CatalogTrack
import moe.rukamori.archivetune.flaccore.match.TrackMatcher
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

sealed interface MonochromeResolveResult {
    data class Found(
        val url: String,
        val flacInfo: FlacInfo,
        val matched: CatalogTrack,
    ) : MonochromeResolveResult

    data class NotFound(val reason: String) : MonochromeResolveResult
    data class Unavailable(val reason: String) : MonochromeResolveResult
}

class MonochromeSource(
    private val servers: MonochromeServers,
    private val httpClient: MonoHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, CatalogTrack>()
    private val misses = ConcurrentHashMap<String, Long>()

    suspend fun resolve(query: TrackQuery): MonochromeResolveResult {
        val usableServers = servers.usable()
        var lastResult: MonochromeResolveResult = MonochromeResolveResult.NotFound("Not on Monochrome")
        for (server in usableServers) {
            val res = resolveOn(server, query)
            if (res is MonochromeResolveResult.Found) return res
            lastResult = res
        }
        return lastResult
    }

    private suspend fun resolveOn(server: MonochromeServers.Server, query: TrackQuery): MonochromeResolveResult {
        val key = "${server.url}|${query.title}|${query.artist}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            return MonochromeResolveResult.NotFound("Not on ${server.name}")
        }

        val found = matches[key] ?: run {
            val primaryArtist = query.artist.substringBefore(",").trim()
            val searchQuery = "$primaryArtist ${TrackMatcher.core(query.title)}".trim()
            val searchReq = searchRequest(server, searchQuery, SearchLimit)
            val response = runCatching { httpClient.get(searchReq.first, searchReq.second) }.getOrElse {
                val reason = "${server.name} didn't answer: ${it.message}"
                servers.markDown(server.url, reason)
                return MonochromeResolveResult.Unavailable(reason)
            }
            if (!response.ok) {
                val reason = "${server.name} answered ${response.code}"
                servers.markDown(server.url, reason)
                return MonochromeResolveResult.Unavailable(reason)
            }
            servers.clearDown(server.url)

            val candidates = tracksIn(server.kind, response.body)
            val best = TrackMatcher.ranked(query, candidates).firstOrNull()
            if (best == null) {
                misses[key] = System.currentTimeMillis()
                return MonochromeResolveResult.NotFound("No matching recording on Monochrome")
            }
            matches[key] = best.track
            best.track
        }

        val opened = open(server, found.id)
            ?: return MonochromeResolveResult.Unavailable("${server.name} gave no stream")
        val info = FlacStreamInfo.parse(opened.head)
            ?: return MonochromeResolveResult.Unavailable("Monochrome's stream isn't FLAC")

        return MonochromeResolveResult.Found(
            url = opened.url,
            flacInfo = info,
            matched = found,
        )
    }

    private suspend fun open(server: MonochromeServers.Server, id: String): OpenedStream? = when (server.kind) {
        MonochromeServers.Kind.Streamer -> {
            val url = "${server.url}/track/$id"
            val resp = httpClient.get(url, streamHeaders + HeadRange)
            if (resp.ok && resp.bytes != null) OpenedStream(url, resp.bytes) else null
        }
        MonochromeServers.Kind.Relay -> {
            val resp = httpClient.get("${server.url}/s/${enc(id)}?q=flac", relayHeaders + HeadRange)
            val link = resp.header("X-Relay-Stream")?.takeIf { it.startsWith("${originOf(server.url)}/") }
            if (resp.ok && link != null && resp.bytes != null) OpenedStream(link, resp.bytes) else null
        }
    }

    private fun searchRequest(
        server: MonochromeServers.Server,
        query: String,
        limit: Int,
    ): Pair<String, Map<String, String>> = when (server.kind) {
        MonochromeServers.Kind.Streamer ->
            "${server.url}/search/tracks?q=${enc(query)}&limit=$limit" to headers
        MonochromeServers.Kind.Relay ->
            "${server.url}/search?q=${enc(query)}&n=$limit" to relayHeaders
    }

    private fun tracksIn(kind: MonochromeServers.Kind, text: String): List<CatalogTrack> = when (kind) {
        MonochromeServers.Kind.Streamer -> streamerTracksIn(text)
        MonochromeServers.Kind.Relay -> relayTracksIn(text)
    }

    fun streamerTracksIn(text: String): List<CatalogTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val tracks = root["tracks"] as? JsonArray ?: return emptyList()
        return tracks.mapNotNull { element ->
            val t = element as? JsonObject ?: return@mapNotNull null
            if ((t["playable"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
            val id = t.string("trackId") ?: t.string("id") ?: return@mapNotNull null
            val artists = (t["artistNames"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val duration = (t["duration"] as? JsonPrimitive)?.longOrNull?.let { if (it > 10_000) it else it * 1000 }
            CatalogTrack(
                id = id,
                title = t.string("title") ?: return@mapNotNull null,
                artist = artists.joinToString(", "),
                album = t.string("albumTitle") ?: t.string("releaseTitle"),
                durationMs = duration,
                isrc = t.string("isrc"),
            )
        }
    }

    fun relayTracksIn(text: String): List<CatalogTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val rows = root["data"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val t = element as? JsonObject ?: return@mapNotNull null
            CatalogTrack(
                id = t.string("id") ?: return@mapNotNull null,
                title = t.string("title") ?: return@mapNotNull null,
                artist = t.string("artist").orEmpty().split(RelayArtistJoin).joinToString(", ") { it.trim() },
                album = t.string("album")?.takeIf { it.isNotBlank() },
                durationMs = (t["duration"] as? JsonPrimitive)?.longOrNull?.let { if (it > 10_000) it else it * 1000 },
                isrc = t.string("isrc"),
            )
        }
    }

    private class OpenedStream(val url: String, val head: ByteArray)

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun originOf(url: String): String = URI(url).let { "${it.scheme}://${it.rawAuthority}" }

    companion object {
        const val MissMemoryMs = 30L * 60 * 1000
        private const val SearchLimit = 8
        private const val UserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val headers = mapOf("Accept" to "application/json", "User-Agent" to UserAgent)
        private val streamHeaders = mapOf("User-Agent" to UserAgent)
        private val relayHeaders = headers + ("X-Relay-Client" to "relay/2")
        private val HeadRange = "Range" to "bytes=0-41"
        private val RelayArtistJoin = Regex("""(?<! )&(?! )""")
    }
}
