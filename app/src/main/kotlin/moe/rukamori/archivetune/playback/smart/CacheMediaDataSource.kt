package moe.rukamori.archivetune.playback.smart

import android.media.MediaDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import java.io.RandomAccessFile

internal class CacheMediaDataSource(
    private val cache: Cache,
    private val cacheKey: String,
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        if (position < 0L) return -1
        return runCatching {
            val spans = cache.getCachedSpans(cacheKey)
            val span = spans.firstOrNull { it.position <= position && position < (it.position + it.length) }
                ?: return -1
            val file = span.file
            if (file == null || !file.exists() || !file.canRead()) {
                return -1
            }
            val fileOffset = position - span.position
            val bytesAvailable = span.length - fileOffset
            val bytesToRead = minOf(size.toLong(), bytesAvailable).toInt()
            if (bytesToRead <= 0) return -1
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(fileOffset)
                raf.read(buffer, offset, bytesToRead)
            }
        }.getOrDefault(-1)
    }

    override fun getSize(): Long {
        val length = runCatching {
            cache.getContentMetadata(cacheKey).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrDefault(-1L)
        if (length > 0L) return length
        val spans = runCatching { cache.getCachedSpans(cacheKey) }.getOrNull().orEmpty()
        if (spans.isEmpty()) return -1L
        return spans.maxOf { it.position + it.length }
    }

    override fun close() {}
}
