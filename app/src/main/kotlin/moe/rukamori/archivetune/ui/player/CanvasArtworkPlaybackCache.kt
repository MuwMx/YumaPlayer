/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository

object CanvasArtworkPlaybackCache {
    private val index = CanvasCacheIndex()
    private val downloader = CanvasVideoDownloader(index)

    fun init(context: Context) {
        val directory = StorageLocationRepository.cacheDirectory(context, StorageFolderKind.CANVAS_CACHE)
        index.init(directory)
    }

    fun get(
        mediaId: String,
        preferCachedOnly: Boolean = false,
    ): CanvasArtwork? = index.get(mediaId, preferCachedOnly)

    suspend fun put(
        mediaId: String,
        artwork: CanvasArtwork,
    ): CanvasArtwork =
        withContext(Dispatchers.IO) {
            if (index.isCacheDisabled() || mediaId.isBlank()) return@withContext artwork
            val directory = index.cacheDirectory ?: return@withContext artwork
            directory.mkdirs()

            val current = index.getCurrentPlayable(mediaId)
            downloader.downloadArtworkInBackground(
                directory = directory,
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
        if (value <= 0) {
            downloader.cancelAll()
        }
        index.setMaxSize(value)
    }
}
