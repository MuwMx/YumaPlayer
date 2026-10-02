/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune

import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.MaxImageCacheSizeKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.io.File

internal fun App.createAppImageLoader(context: PlatformContext): ImageLoader {
    val smartTrimmer = dataStore[SmartTrimmerKey] ?: false
    val imageCacheConfig = resolveImageDiskCacheConfig(dataStore[MaxImageCacheSizeKey])

    val diskCache =
        DiskCache
            .Builder()
            .directory(StorageLocationRepository.cacheDirectory(this, StorageFolderKind.IMAGE_CACHE))
            .maxSizeBytes(imageCacheConfig.maxSizeBytes)
            .build()

    if (smartTrimmer && imageCacheConfig.policy == CachePolicy.ENABLED && didRunImageCacheTrim.compareAndSet(false, true)) {
        applicationScope.launch(Dispatchers.IO) { trimImageDiskCache(diskCache) }
    }

    return ImageLoader
        .Builder(this)
        .crossfade(true)
        .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        .diskCache(diskCache)
        .diskCachePolicy(imageCacheConfig.policy)
        .build()
}

internal fun trimImageDiskCache(diskCache: DiskCache) {
    try {
        val limitBytes = diskCache.maxSize
        if (limitBytes <= 0L || limitBytes == Long.MAX_VALUE) return

        val dir = File(diskCache.directory.toString())
        if (!dir.exists()) return

        val files =
            dir
                .walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.lastModified() }
                .toList()
        var currentSize = files.sumOf { it.length() }
        if (currentSize <= limitBytes) return

        for (file in files) {
            if (currentSize <= limitBytes) break
            val size = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) currentSize -= size
        }
    } catch (_: Exception) {
    }
}

internal data class ImageDiskCacheConfig(
    val policy: CachePolicy,
    val maxSizeBytes: Long,
)

internal fun resolveImageDiskCacheConfig(maxImageCacheSizeMb: Int?): ImageDiskCacheConfig {
    val sizeMb = maxImageCacheSizeMb ?: 512
    if (sizeMb == 0) return ImageDiskCacheConfig(policy = CachePolicy.DISABLED, maxSizeBytes = 1L)
    if (sizeMb < 0) return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = Long.MAX_VALUE)
    val bytesPerMb = 1024L * 1024L
    val safeSizeMb = sizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
    return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = safeSizeMb * bytesPerMb)
}
