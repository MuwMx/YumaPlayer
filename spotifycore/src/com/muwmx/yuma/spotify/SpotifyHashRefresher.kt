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

/**
 * Recovers from a persisted-query hash that Spotify has stopped accepting.
 *
 * The hashes shipped in [SpotifyHashProvider] keep working until Spotify retires them. When a
 * request is rejected with every hash we know, [liveHash] looks up what the web player uses today.
 * A hash that then works is remembered with [learn], kept in [store], and applied again on the
 * next start by [applyLearnedOnce], so the app heals without a new release.
 */
class SpotifyHashRefresher(
    private val discovery: SpotifyHashDiscovery,
    private val store: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var table: SpotifyHashDiscovery.Table? = null
    private var tableAt = 0L
    private var failedAt = 0L

    private val learnedMutex = Mutex()

    @Volatile
    private var learnedApplied = false

    /**
     * Applies the remembered hashes the first time it is called, reading [store] on the IO dispatcher. The
     * client calls this before every request, so the app's start does not wait on the disk and no request
     * goes out before the remembered hashes are in place.
     */
    suspend fun applyLearnedOnce() {
        if (learnedApplied) return
        learnedMutex.withLock {
            if (learnedApplied) return
            withContext(Dispatchers.IO) { applyLearned() }
            learnedApplied = true
        }
    }

    /**
     * The hash the web player uses for [operationName] today, or null when it is unknown, equals
     * one of the [rejected] hashes, or the web player cannot be read right now.
     */
    suspend fun liveHash(
        operationName: String,
        rejected: Set<String>,
    ): String? =
        mutex.withLock {
            val current = currentTable() ?: return@withLock null
            current.hashes[operationName]?.takeIf { it !in rejected }
        }

    /** Remembers [hash], which Spotify accepted, so the next start does not have to look it up again. */
    @Synchronized
    fun learn(
        operationName: String,
        hash: String,
    ) {
        val learned = readStore().toMutableMap()
        learned[operationName] = LearnedHash(hash = hash, base = SpotifyHashProvider.hardcodedHash(operationName))
        writeStore(learned)
    }

    /**
     * Applies the remembered hashes to [SpotifyHashProvider]. One is skipped when the app's own hash
     * for that operation has changed since it was learned, because a newer release supersedes it.
     */
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
        table?.takeIf { time - tableAt < TABLE_TTL_MS }?.let { return it }
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
        // The web player changes a few times a month, and reading it costs about 13 MB.
        private const val TABLE_TTL_MS = 30 * 60 * 1000L
        private const val RETRY_AFTER_FAILURE_MS = 5 * 60 * 1000L

        /**
         * True only for a response that proves a hash works: it carries data and no errors. Anything else
         * (no body, an error, an empty answer) must not get a hash remembered, or every later start would
         * begin with a broken one.
         */
        internal fun isWorkingAnswer(response: JsonObject?): Boolean =
            response != null &&
                response["data"] is JsonObject &&
                (response["errors"] as? JsonArray).isNullOrEmpty()
    }
}
