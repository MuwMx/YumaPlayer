/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback.engine

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.Locale

open class SchemeRoutingDataSource(
    private val cachedFactory: DataSource.Factory,
    private val directFactory: DataSource.Factory,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val normalizedScheme = dataSpec.uri.scheme?.lowercase(Locale.US)
        val selectedFactory =
            if (
                normalizedScheme == "content" ||
                normalizedScheme == "file" ||
                normalizedScheme == "android.resource"
            ) {
                directFactory
            } else {
                cachedFactory
            }
        val selectedDataSource = selectedFactory.createDataSource()
        transferListeners.forEach(selectedDataSource::addTransferListener)
        delegate = selectedDataSource
        return selectedDataSource.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(delegate).read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
    }
}

open class ResolvedUrlRoutingDataSource(
    private val defaultFactory: DataSource.Factory,
    private val extractorFactory: DataSource.Factory,
    private val shouldUseExtractorFactory: (Uri) -> Boolean,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val selectedFactory =
            if (shouldUseExtractorFactory(dataSpec.uri)) {
                extractorFactory
            } else {
                defaultFactory
            }
        val selectedDataSource = selectedFactory.createDataSource()
        transferListeners.forEach(selectedDataSource::addTransferListener)
        delegate = selectedDataSource
        return selectedDataSource.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(delegate).read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
    }
}
