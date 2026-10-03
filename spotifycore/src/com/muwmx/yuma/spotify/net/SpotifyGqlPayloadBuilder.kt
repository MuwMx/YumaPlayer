/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import moe.rukamori.archivetune.spotify.SpotifyHashProvider

object SpotifyGqlPayloadBuilder {
    fun resolveHashCandidates(operationName: String): List<String> {
        val primaryHash = SpotifyHashProvider.getHash(operationName)
        return buildList {
            add(primaryHash)
            SpotifyHashProvider.getPreviousHash(operationName)?.let { prev ->
                if (prev != primaryHash) add(prev)
            }
        }
    }

    fun buildGqlBody(
        operationName: String,
        sha256Hash: String,
        variables: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("variables", variables)
            put("operationName", operationName)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", sha256Hash)
                }
            }
        }
}
