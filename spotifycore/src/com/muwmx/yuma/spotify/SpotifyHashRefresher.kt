/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

class SpotifyHashRefresher(
    private val discovery: SpotifyHashDiscovery,
    private val store: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var table: SpotifyHashDiscovery.Table? = null
    private var tableAt = 0L
    private var tableTtlMs = TABLE_TTL_MS
    private var failedAt = 0L

    private val learnedMutex = Mutex()

    @Volatile
    private var learnedApplied = false

    suspend fun applyLearnedOnce() {
        if (learnedApplied) return
        learnedMutex.withLock {
            if (learnedApplied) return
            withContext(Dispatchers.IO) { applyLearned() }
            learnedApplied = true
        }
    }

    suspend fun liveHash(
        operationName: String,
        rejected: Set<String>,
    ): String? =
        mutex.withLock {
            val current = currentTable() ?: return@withLock null
            current.hashes[operationName]?.takeIf { it !in rejected }
        }

    @Synchronized
    fun learn(
        operationName: String,
        hash: String,
    ) {
        val learned = readStore().toMutableMap()
        learned[operationName] = LearnedHash(hash = hash, base = SpotifyHashProvider.hardcodedHash(operationName))
        writeStore(learned)
    }

    fun applyLearned() {
        val usable =
            readStore().filter { (operation, learned) ->
                learned.base != null && learned.base == SpotifyHashProvider.hardcodedHash(operation)
            }
        if (usable.isEmpty()) return
        SpotifyHashProvider.updateHashes(
            remoteHashes =
                usable.mapValues { (operation, learned) ->
                    SpotifyHashProvider.RemoteHashEntry(hash = learned.hash, previousHash = SpotifyHashProvider.hardcodedHash(operation))
                },
            source = SpotifyHashProvider.HashSource.CACHED,
        )
    }

    private suspend fun currentTable(): SpotifyHashDiscovery.Table? {
        val time = now()
        table?.takeIf { time - tableAt < tableTtlMs }?.let { return it }
        if (time - failedAt < RETRY_AFTER_FAILURE_MS) return null
        val fresh =
            try {
                discovery.discover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        if (fresh == null) {
            failedAt = time
            return null
        }
        table = fresh
        tableAt = time
        tableTtlMs = if (fresh.complete) TABLE_TTL_MS else RETRY_AFTER_FAILURE_MS
        return fresh
    }

    private data class LearnedHash(
        val hash: String,
        val base: String?,
    )

    private fun readStore(): Map<String, LearnedHash> =
        runCatching {
            Json
                .parseToJsonElement(store.readText())
                .jsonObject
                .mapValues { (_, value) ->
                    val entry = value.jsonObject
                    LearnedHash(
                        hash = entry.getValue("hash").jsonPrimitive.content,
                        base = (entry["base"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                    )
                }
        }.getOrDefault(emptyMap())

    private fun writeStore(learned: Map<String, LearnedHash>) {
        val json: JsonObject =
            buildJsonObject {
                learned.forEach { (operation, entry) ->
                    putJsonObject(operation) {
                        put("hash", entry.hash)
                        entry.base?.let { put("base", it) }
                    }
                }
            }
        runCatching {
            store.parentFile?.mkdirs()
            store.writeText(json.toString())
        }
    }

    companion object {
        private const val TABLE_TTL_MS = 30 * 60 * 1000L
        private const val RETRY_AFTER_FAILURE_MS = 5 * 60 * 1000L

        internal fun isWorkingAnswer(response: JsonObject?): Boolean =
            response != null &&
                response["data"] is JsonObject &&
                (response["errors"] as? JsonArray).isNullOrEmpty()
    }
}
