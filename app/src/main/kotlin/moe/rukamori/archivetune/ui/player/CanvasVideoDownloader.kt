/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Proxy
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class CanvasVideoDownloader(
    private val index: CanvasCacheIndex,
) {
    companion object {
        private const val DOWNLOAD_BUFFER_SIZE_BYTES = 64 * 1024
        private const val DOWNLOAD_MAX_ATTEMPTS = 4
        private const val DOWNLOAD_RETRY_DELAY_MS = 750L
        private const val CanvasDownloadUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    }

    private val cacheJobs = LinkedHashMap<String, Job>()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        canvasClient()
    }

    private val directClient: OkHttpClient
        get() = client

    private val streamClient: OkHttpClient
        get() = client

    private fun canvasClient(proxy: Proxy = YouTube.streamOkHttpProxy): OkHttpClient =
        OkHttpClient
            .Builder()
            .dns(YouTube.dns)
            .proxy(proxy)
            .apply {
                val username = YouTube.proxyUsername
                val password = YouTube.proxyPassword
                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    proxyAuthenticator { _, response ->
                        val credential = Credentials.basic(username, password)
                        response.request
                            .newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }.connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val request = chain.request()
                if (!request.url.isYouTubeMediaHost()) {
                    return@addInterceptor chain.proceed(
                        request
                            .newBuilder()
                            .header("User-Agent", CanvasDownloadUserAgent)
                            .build(),
                    )
                }
                val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                chain.proceed(
                    StreamClientUtils
                        .applyRequestProfile(
                            request.newBuilder(),
                            requestProfile,
                        ).build(),
                )
            }.build()

    @Synchronized
    fun cancelAll() {
        cacheJobs.values.forEach { job -> job.cancel() }
        cacheJobs.clear()
    }

    @Synchronized
    fun cancel(mediaId: String) {
        cacheJobs.remove(mediaId)?.cancel()
    }

    fun downloadArtworkInBackground(
        directory: File,
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        synchronized(this) {
            if (cacheJobs[mediaId]?.isActive == true) return
            cacheJobs[mediaId] =
                downloadScope.launch {
                    try {
                        cacheArtworkVideos(
                            directory = directory,
                            mediaId = mediaId,
                            artwork = artwork,
                        )
                    } finally {
                        synchronized(this@CanvasVideoDownloader) {
                            cacheJobs.remove(mediaId)
                        }
                    }
                }
        }
    }

    private suspend fun cacheArtworkVideos(
        directory: File,
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        val current = index.getEntry(mediaId)
        val regularFileName =
            cacheCanvasVideo(
                directory = directory,
                mediaId = mediaId,
                variant = CanvasVideoVariant.Regular,
                url = artwork.downloadableRegularUrl(),
                currentFileName = current?.regularFileName,
            )
        if (regularFileName != null || current?.verticalFileName != null) {
            index.persistEntry(
                directory = directory,
                entry =
                    CanvasCacheEntry(
                        mediaId = mediaId,
                        artwork = artwork,
                        regularFileName = regularFileName,
                        verticalFileName = current?.verticalFileName,
                        createdAtMs = current?.createdAtMs ?: System.currentTimeMillis(),
                        lastAccessedAtMs = System.currentTimeMillis(),
                    ),
            )
        }
        val verticalFileName =
            cacheCanvasVideo(
                directory = directory,
                mediaId = mediaId,
                variant = CanvasVideoVariant.Vertical,
                url = artwork.downloadableVerticalUrl(),
                currentFileName = current?.verticalFileName,
            )

        if (regularFileName == null && verticalFileName == null) {
            Timber.tag(CanvasCacheLogTag).d("Canvas artwork resolved without downloadable video for %s", mediaId)
            return
        }

        val now = System.currentTimeMillis()
        val entry =
            CanvasCacheEntry(
                mediaId = mediaId,
                artwork = artwork,
                regularFileName = regularFileName,
                verticalFileName = verticalFileName,
                createdAtMs = current?.createdAtMs ?: now,
                lastAccessedAtMs = now,
            )

        index.persistEntry(directory = directory, entry = entry)
    }

    private suspend fun cacheCanvasVideo(
        directory: File,
        mediaId: String,
        variant: CanvasVideoVariant,
        url: String?,
        currentFileName: String?,
    ): String? {
        currentFileName
            ?.takeIf { fileName ->
                directory
                    .resolve(fileName)
                    .takeIf(File::isUsableFile)
                    ?.takeIf(File::isPlayableCanvasVideo) != null
            }?.let { return it }
        if (url.isNullOrBlank()) return null
        val fileName = canvasFileName(mediaId, variant, url)
        val target = directory.resolve(fileName)
        if (target.isUsableFile()) {
            if (target.isPlayableCanvasVideo()) return fileName
            runCatching { target.delete() }
        }

        val partial = directory.resolve("$fileName.part")
        return try {
            downloadToFile(url = url, target = partial)
            if (partial.length() <= 0L) throw IOException("Downloaded empty canvas video")
            if (!partial.isPlayableCanvasVideo()) throw IOException("Canvas video exceeds device decoder capabilities")
            if (target.exists() && !target.delete()) throw IOException("Failed to replace existing canvas video")
            if (!partial.renameTo(target)) throw IOException("Failed to commit canvas video")
            fileName
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Timber.w(error, "Failed to cache canvas video")
            runCatching { partial.delete() }
            currentFileName
                ?.takeIf { fileName ->
                    directory
                        .resolve(fileName)
                        .takeIf(File::isUsableFile)
                        ?.takeIf(File::isPlayableCanvasVideo) != null
                }
        }
    }

    private suspend fun downloadToFile(
        url: String,
        target: File,
    ) {
        currentCoroutineContext().ensureActive()
        target.parentFile?.mkdirs()
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < DOWNLOAD_MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            try {
                downloadToPartialFile(
                    url = url,
                    target = target,
                    existingBytes = target.takeIf { file -> file.isFile }?.length()?.coerceAtLeast(0L) ?: 0L,
                )
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                attempt += 1
                if (attempt >= DOWNLOAD_MAX_ATTEMPTS) break
                Timber.w(error, "Canvas download interrupted, retrying")
                delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
            }
        }
        throw IOException("Canvas download failed after $DOWNLOAD_MAX_ATTEMPTS attempts", lastError)
    }

    private suspend fun downloadToPartialFile(
        url: String,
        target: File,
        existingBytes: Long,
    ) {
        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .header("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        val request = requestBuilder.build()
        val callClient = if (request.url.isYouTubeMediaHost()) streamClient else directClient
        callClient.newCall(request).execute().use { response ->
            if (existingBytes > 0L && response.code == 416) return
            if (!response.isSuccessful) throw IOException("Canvas request failed: HTTP ${response.code}")
            val append = existingBytes > 0L && response.code == 206
            if (existingBytes > 0L && !append) {
                if (target.exists() && !target.delete()) throw IOException("Failed to restart canvas video download")
            }
            val body = response.body
            val contentType =
                body
                    .contentType()
                    ?.toString()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()
            if (
                contentType.contains("mpegurl") ||
                contentType.contains("m3u8") ||
                contentType.startsWith("text/") ||
                contentType.startsWith("image/") ||
                contentType.contains("json")
            ) {
                throw IOException("Canvas response is not a downloadable video: $contentType")
            }
            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun HttpUrl.isYouTubeMediaHost(): Boolean {
        val normalizedHost = host.lowercase(Locale.ROOT)
        return normalizedHost.endsWith("googlevideo.com") ||
            normalizedHost.endsWith("googleusercontent.com") ||
            normalizedHost.endsWith("youtube.com") ||
            normalizedHost.endsWith("youtube-nocookie.com") ||
            normalizedHost.endsWith("ytimg.com")
    }
}
