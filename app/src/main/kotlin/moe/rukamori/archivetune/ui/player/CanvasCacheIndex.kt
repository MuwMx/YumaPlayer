/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import timber.log.Timber
import java.io.File
import java.util.LinkedHashMap

internal class CanvasCacheIndex {
    companion object {
        private const val DEFAULT_MAX_SIZE_MEGABYTES = 256
        private const val PERSIST_FILE = "canvas_artwork_cache.json"
        private const val PERSIST_DEBOUNCE_MS = 2_000L
        private const val CACHE_SIZE_BYTES_PER_MEGABYTE = 1024L * 1024L
    }

    private val map = LinkedHashMap<String, CanvasCacheEntry>(DEFAULT_MAX_SIZE_MEGABYTES, 0.75f, true)

    @Volatile private var maxSizeBytes = DEFAULT_MAX_SIZE_MEGABYTES.toLong() * CACHE_SIZE_BYTES_PER_MEGABYTE

    @Volatile var cacheDirectory: File? = null
        private set

    @Volatile private var cacheFile: File? = null

    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    @Synchronized
    fun init(directory: File) {
        cacheDirectory = directory
        cacheFile = directory.resolve(PERSIST_FILE)
        loadFromDisk()
    }

    fun isCacheDisabled(): Boolean = maxSizeBytes == 0L

    @Synchronized
    fun get(
        mediaId: String,
        preferCachedOnly: Boolean = false,
    ): CanvasArtwork? {
        if (maxSizeBytes == 0L || mediaId.isBlank()) return null
        val directory = cacheDirectory ?: return null
        val entry = map[mediaId] ?: return null
        val playable =
            entry.toPlayableArtwork(
                directory = directory,
                preferCachedOnly = preferCachedOnly,
            )
        if (playable == null) {
            map.remove(mediaId)
            schedulePersist()
            return null
        }
        map[mediaId] = entry.copy(lastAccessedAtMs = System.currentTimeMillis())
        schedulePersist()
        return playable
    }

    @Synchronized
    fun getCurrentPlayable(mediaId: String): CanvasArtwork? {
        val directory = cacheDirectory ?: return null
        return map[mediaId]?.toPlayableArtwork(
            directory = directory,
            preferCachedOnly = false,
        )
    }

    @Synchronized
    fun getEntry(mediaId: String): CanvasCacheEntry? = map[mediaId]

    @Synchronized
    fun persistEntry(
        directory: File,
        entry: CanvasCacheEntry,
    ) {
        map[entry.mediaId] = entry
        trimLocked(directory)
        schedulePersist()
    }

    @Synchronized
    fun remove(mediaId: String): CanvasArtwork? {
        val entry = map.remove(mediaId) ?: return null
        val directory = cacheDirectory
        if (directory != null) {
            runCatching { entry.regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { entry.verticalFileName?.let { directory.resolve(it).delete() } }
        }
        schedulePersist()
        return entry.artwork
    }

    @Synchronized
    fun allKeys(): Set<String> = map.keys.toSet()

    @Synchronized
    fun byteSize(): Long {
        val directory = cacheDirectory ?: return 0L
        return map.values.sumOf { entry -> entry.byteSize(directory) }
    }

    @Synchronized
    fun clear() {
        clearFilesLocked()
        map.clear()
        schedulePersist()
    }

    @Synchronized
    fun clearAndPersist(): Boolean {
        clearFilesLocked()
        map.clear()
        persistJob?.cancel()
        return writeToDisk()
    }

    @Synchronized
    fun setMaxSize(value: Int) {
        maxSizeBytes = value.toCanvasCacheLimitBytes()
        val directory = cacheDirectory
        if (maxSizeBytes == 0L) {
            clearFilesLocked()
            map.clear()
            schedulePersist()
            return
        }
        if (directory != null) {
            trimLocked(directory)
        }
        schedulePersist()
    }

    private fun loadFromDisk() {
        val file = cacheFile ?: return
        if (!file.exists()) return
        try {
            val raw = file.readText()
            if (raw.isBlank()) return
            val restored = decodeEntries(raw)
            map.clear()
            restored
                .filter { entry -> entry.mediaId.isNotBlank() }
                .forEach { entry -> map[entry.mediaId] = entry }
            cacheDirectory?.let(::trimLocked)
            Timber.d("Canvas cache restored: ${map.size} entries from disk")
        } catch (error: Exception) {
            Timber.e(error, "Failed to restore canvas cache from disk")
            runCatching { file.delete() }
        }
    }

    private fun decodeEntries(raw: String): List<CanvasCacheEntry> =
        runCatching {
            json.decodeFromString(ListSerializer(CanvasCacheEntry.serializer()), raw)
        }.getOrElse {
            val legacy =
                json.decodeFromString(
                    MapSerializer(
                        String.serializer(),
                        CanvasArtwork.serializer(),
                    ),
                    raw,
                )
            val now = System.currentTimeMillis()
            legacy.map { (mediaId, artwork) ->
                CanvasCacheEntry(
                    mediaId = mediaId,
                    artwork = artwork,
                    regularFileName = null,
                    verticalFileName = null,
                    createdAtMs = now,
                    lastAccessedAtMs = now,
                )
            }
        }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob =
            persistScope.launch {
                delay(PERSIST_DEBOUNCE_MS)
                writeToDisk()
            }
    }

    private fun writeToDisk(): Boolean {
        val file = cacheFile ?: return true
        return try {
            val snapshot: List<CanvasCacheEntry>
            synchronized(this) {
                snapshot = map.values.toList()
            }
            val raw = json.encodeToString(ListSerializer(CanvasCacheEntry.serializer()), snapshot)
            file.parentFile?.mkdirs()
            file.writeText(raw)
            true
        } catch (error: Exception) {
            Timber.e(error, "Failed to persist canvas cache to disk")
            false
        }
    }

    private fun trimLocked(directory: File) {
        val activeFiles =
            map.values
                .flatMap { entry ->
                    listOfNotNull(entry.regularFileName, entry.verticalFileName)
                }.toSet()
        directory
            .listFiles()
            ?.filter { file -> file.isFile && file.name.endsWith(".mp4") && file.name !in activeFiles }
            ?.forEach { file -> runCatching { file.delete() } }
        trimToByteLimitLocked(directory)
    }

    private fun trimToByteLimitLocked(directory: File) {
        val limitBytes = maxSizeBytes
        if (limitBytes == Long.MAX_VALUE) return
        var totalBytes = map.values.sumOf { entry -> entry.byteSize(directory) }
        val iterator = map.entries.iterator()
        while (totalBytes > limitBytes && iterator.hasNext()) {
            val entry = iterator.next().value
            val entryBytes = entry.byteSize(directory)
            iterator.remove()
            runCatching { entry.regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { entry.verticalFileName?.let { directory.resolve(it).delete() } }
            totalBytes -= entryBytes
        }
    }

    private fun clearFilesLocked() {
        val directory = cacheDirectory ?: return
        map.values.forEach { entry ->
            runCatching { entry.regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { entry.verticalFileName?.let { directory.resolve(it).delete() } }
        }
        directory
            .listFiles()
            ?.filter { file -> file.isFile && (file.name.endsWith(".mp4") || file.name.endsWith(".part")) }
            ?.forEach { file -> runCatching { file.delete() } }
    }
}
