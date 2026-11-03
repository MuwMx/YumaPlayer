/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.MaxCanvasCacheSizeKey
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber

object CanvasArtworkPlaybackCache {
    const val DEFAULT_MAX_SIZE_MEGABYTES = 256

    private val index = CanvasCacheIndex()
    private val downloader = CanvasVideoDownloader(index)

    @Volatile private var initJob: Job? = null
    @Volatile private var customSizeSet = false
    private val initLock = Any()
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (initJob != null) return
        synchronized(initLock) {
            if (initJob != null) return
            val appContext = context.applicationContext
            initJob =
                initScope.launch {
                    try {
                        val limitMb =
                            appContext.dataStore.data.first()[MaxCanvasCacheSizeKey]
                                ?: DEFAULT_MAX_SIZE_MEGABYTES
                        synchronized(initLock) {
                            if (!customSizeSet) {
                                index.setMaxSize(limitMb)
                            }
                            val directory = StorageLocationRepository.cacheDirectory(appContext, StorageFolderKind.CANVAS_CACHE)
                            index.init(directory)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(CanvasCacheLogTag).e(
                            e,
                            "Failed to initialize canvas cache from DataStore; leaving uninitialized to prevent destructive trim",
                        )
                    }
                }
        }
    }

    suspend fun get(
        mediaId: String,
        preferCachedOnly: Boolean = false,
        source: moe.rukamori.archivetune.canvas.CanvasSource = moe.rukamori.archivetune.canvas.CanvasSource.ALL,
    ): CanvasArtwork? =
        withContext(Dispatchers.IO) {
            initJob?.join()
            index.get(mediaId, preferCachedOnly, source)
        }

    fun cacheArtworkInBackground(
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        if (index.isCacheDisabled() || mediaId.isBlank()) return
        val directory = index.cacheDirectory ?: return
        directory.mkdirs()
        downloader.downloadArtworkInBackground(
            directory = directory,
            mediaId = mediaId,
            artwork = artwork,
        )
    }

    suspend fun put(
        mediaId: String,
        artwork: CanvasArtwork,
    ): CanvasArtwork =
        withContext(Dispatchers.IO) {
            initJob?.join()
            if (index.isCacheDisabled() || mediaId.isBlank()) return@withContext artwork
            downloader.cancelIfDifferentSource(mediaId, artwork.source)
            val current = index.getCurrentPlayable(mediaId, requiredSource = artwork.source)
            if (current == null) {
                index.removeIfDifferentSource(mediaId, artwork.source)
            }
            cacheArtworkInBackground(
                mediaId = mediaId,
                artwork = artwork,
            )

            current ?: artwork
        }

    fun remove(mediaId: String): CanvasArtwork? {
        downloader.cancel(mediaId)
        return index.remove(mediaId)
    }

    fun allKeys(): Set<String> = index.allKeys()

    fun byteSize(): Long = index.byteSize()

    fun clear() {
        downloader.cancelAll()
        index.clear()
    }

    fun clearAndPersist(): Boolean {
        downloader.cancelAll()
        return index.clearAndPersist()
    }

    fun setMaxSize(value: Int) {
        synchronized(initLock) {
            customSizeSet = true
            if (value <= 0) {
                downloader.cancelAll()
            }
            index.setMaxSize(value)
        }
    }
}
