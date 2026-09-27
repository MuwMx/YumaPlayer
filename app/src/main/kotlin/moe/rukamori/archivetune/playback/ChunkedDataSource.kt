package moe.rukamori.archivetune.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

internal const val STREAM_CHUNK_BYTES: Long = 1024 * 1024L

@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long = STREAM_CHUNK_BYTES,
) : DataSource {

    private var baseSpec: DataSpec? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var chunkRemaining = 0L
    private var chunkOpen = false
    private var rangeBytes = chunkBytes
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeChunk()
        baseSpec = dataSpec
        position = dataSpec.position
        passthrough = false

        val host = dataSpec.uri.host
        val isGoogleVideo = host != null && (
            host.equals("googlevideo.com", ignoreCase = true) ||
                host.endsWith(".googlevideo.com", ignoreCase = true)
        )
        val total = if (isGoogleVideo) {
            dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        } else {
            null
        }

        if (total == null || total <= 0L) {
            passthrough = true
            val length = upstream.open(dataSpec)
            chunkOpen = true
            return length
        }

        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            total
        } else {
            minOf(total, position + dataSpec.length)
        }
        bytesRemaining = (end - position).coerceAtLeast(0L)
        rangeBytes = chunkBytes
        if (bytesRemaining > 0L) {
            openChunk()
        }
        return bytesRemaining
    }

    private fun openChunk() {
        val length = minOf(rangeBytes, bytesRemaining)
        val spec = requireNotNull(baseSpec).buildUpon()
            .setPosition(position)
            .setLength(length)
            .build()
        upstream.open(spec)
        chunkRemaining = length
        chunkOpen = true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passthrough) return upstream.read(buffer, offset, length)
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        repeat(MAX_EMPTY_RANGES) {
            if (chunkRemaining == 0L) {
                closeChunk()
                openChunk()
            }
            val bytesToRead = minOf(length.toLong(), chunkRemaining).toInt()
            val read = upstream.read(buffer, offset, bytesToRead)
            if (read != C.RESULT_END_OF_INPUT) {
                position += read
                chunkRemaining -= read
                bytesRemaining -= read
                return read
            }
            chunkRemaining = 0L
        }
        return C.RESULT_END_OF_INPUT
    }

    private fun closeChunk() {
        if (chunkOpen) {
            try {
                upstream.close()
            } finally {
                chunkOpen = false
            }
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: baseSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeChunk()
        baseSpec = null
        bytesRemaining = 0L
        chunkRemaining = 0L
        passthrough = false
    }

    private companion object {
        const val MAX_EMPTY_RANGES = 3
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkBytes: Long = STREAM_CHUNK_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstream.createDataSource(), chunkBytes)
    }
}
