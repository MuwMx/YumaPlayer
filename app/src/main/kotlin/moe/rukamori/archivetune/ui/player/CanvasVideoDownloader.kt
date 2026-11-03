/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
import okhttp3.Authenticator
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

internal fun isYouTubeMediaHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val normalizedHost = host.lowercase(Locale.ROOT)
    return normalizedHost == "googlevideo.com" || normalizedHost.endsWith(".googlevideo.com") ||
        normalizedHost == "googleusercontent.com" || normalizedHost.endsWith(".googleusercontent.com") ||
        normalizedHost == "youtube.com" || normalizedHost.endsWith(".youtube.com") ||
        normalizedHost == "youtube-nocookie.com" || normalizedHost.endsWith(".youtube-nocookie.com") ||
        normalizedHost == "ytimg.com" || normalizedHost.endsWith(".ytimg.com")
}

internal fun HttpUrl.isYouTubeMediaHost(): Boolean = isYouTubeMediaHost(host)

internal class CanvasVideoDownloader(
    private val index: CanvasCacheIndex,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val DOWNLOAD_BUFFER_SIZE_BYTES = 64 * 1024
        private const val DOWNLOAD_MAX_ATTEMPTS = 4
        private const val DOWNLOAD_RETRY_DELAY_MS = 750L
        private const val CanvasDownloadUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    }

    private val cacheJobs = LinkedHashMap<String, Job>()
    private val jobSources = LinkedHashMap<String, moe.rukamori.archivetune.canvas.CanvasSource?>()
    private val downloadScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val directClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .dns(YouTube.dns)
            .proxy(Proxy.NO_PROXY)
            .proxyAuthenticator(Authenticator.NONE)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    request
                        .newBuilder()
                        .header("User-Agent", CanvasDownloadUserAgent)
                        .build(),
                )
            }.build()
    }

    @Volatile
    private var streamClientPair: Pair<Proxy, OkHttpClient>? = null

    private val streamClient: OkHttpClient
        get() {
            val currentProxy = YouTube.streamOkHttpProxy ?: Proxy.NO_PROXY
            streamClientPair?.let { (proxy, client) ->
                if (proxy == currentProxy) return client
            }
            val client = createStreamClient(currentProxy)
            streamClientPair = currentProxy to client
            return client
        }

    private fun createStreamClient(proxy: Proxy): OkHttpClient =
        OkHttpClient
            .Builder()
            .dns(YouTube.dns)
            .proxy(proxy)
            .apply {
                val username = YouTube.proxyUsername
                val password = YouTube.proxyPassword
                if (proxy != Proxy.NO_PROXY && !username.isNullOrBlank() && !password.isNullOrBlank()) {
                    proxyAuthenticator { route, response ->
                        val host = route?.address?.url?.host ?: response.request.url.host
                        if (!isYouTubeMediaHost(host)) {
                            return@proxyAuthenticator null
                        }
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
        jobSources.clear()
    }

    @Synchronized
    fun cancel(mediaId: String) {
        cacheJobs.remove(mediaId)?.cancel()
        jobSources.remove(mediaId)
    }

    @Synchronized
    fun cancelIfDifferentSource(
        mediaId: String,
        newSource: moe.rukamori.archivetune.canvas.CanvasSource?,
    ) {
        val inFlightSource = jobSources[mediaId]
        if (inFlightSource != newSource) {
            cancel(mediaId)
        }
    }

    @Synchronized
    internal fun isJobCurrent(
        mediaId: String,
        job: Job,
        expectedSource: moe.rukamori.archivetune.canvas.CanvasSource?,
    ): Boolean = cacheJobs[mediaId] === job && jobSources[mediaId] == expectedSource

    @Synchronized
    internal fun getJob(mediaId: String): Job? = cacheJobs[mediaId]

    @Synchronized
    internal fun getSource(mediaId: String): moe.rukamori.archivetune.canvas.CanvasSource? = jobSources[mediaId]

    fun downloadArtworkInBackground(
        directory: File,
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        synchronized(this) {
            val existingJob = cacheJobs[mediaId]
            val inFlightSource = jobSources[mediaId]
            if (existingJob?.isActive == true) {
                if (inFlightSource != artwork.source) {
                    existingJob.cancel()
                    jobSources.remove(mediaId)
                } else {
                    return
                }
            }
            jobSources[mediaId] = artwork.source
            var job: Job? = null
            job =
                downloadScope.launch {
                    val currentJob = currentCoroutineContext()[Job] ?: return@launch
                    try {
                        cacheArtworkVideos(
                            directory = directory,
                            mediaId = mediaId,
                            artwork = artwork,
                            job = currentJob,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(CanvasCacheLogTag).w(e, "Canvas background download failed for %s", mediaId)
                    } finally {
                        synchronized(this@CanvasVideoDownloader) {
                            if (cacheJobs[mediaId] === currentJob) {
                                cacheJobs.remove(mediaId)
                                jobSources.remove(mediaId)
                            }
                        }
                    }
                }
            cacheJobs[mediaId] = job
        }
    }

    fun cacheArtworkInBackground(
        directory: File,
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        downloadArtworkInBackground(directory, mediaId, artwork)
    }

    internal fun commitFileAtomically(
        mediaId: String,
        partial: File,
        target: File,
        fileName: String,
        job: Job,
        expectedSource: moe.rukamori.archivetune.canvas.CanvasSource?,
    ): String? =
        synchronized(this) {
            if (!job.isActive || cacheJobs[mediaId] !== job || jobSources[mediaId] != expectedSource) {
                partial.delete()
                return null
            }
            if (target.exists() && !target.delete()) {
                partial.delete()
                return null
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                return null
            }
            return fileName
        }

    internal fun commitEntryAtomically(
        mediaId: String,
        entry: CanvasCacheEntry,
        job: Job,
        expectedSource: moe.rukamori.archivetune.canvas.CanvasSource?,
        directory: File,
    ): Boolean =
        synchronized(this) {
            if (!job.isActive || cacheJobs[mediaId] !== job || jobSources[mediaId] != expectedSource) {
                return false
            }
            index.persistEntry(directory = directory, entry = entry)
            return true
        }

    private suspend fun cacheArtworkVideos(
        directory: File,
        mediaId: String,
        artwork: CanvasArtwork,
        job: Job,
    ) {
        if (!isJobCurrent(mediaId, job, artwork.source)) return

        val current = index.getEntry(mediaId)?.takeIf { it.artwork.source == artwork.source }
        val regularFileName =
            cacheCanvasVideo(
                directory = directory,
                mediaId = mediaId,
                variant = CanvasVideoVariant.Regular,
                url = artwork.downloadableRegularUrl(),
                currentFileName = current?.regularFileName,
                job = job,
                expectedSource = artwork.source,
            )

        currentCoroutineContext().ensureActive()
        if (!isJobCurrent(mediaId, job, artwork.source)) return

        val verticalFileName =
            cacheCanvasVideo(
                directory = directory,
                mediaId = mediaId,
                variant = CanvasVideoVariant.Vertical,
                url = artwork.downloadableVerticalUrl(),
                currentFileName = current?.verticalFileName,
                job = job,
                expectedSource = artwork.source,
            )

        currentCoroutineContext().ensureActive()
        if (!isJobCurrent(mediaId, job, artwork.source)) return

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

        commitEntryAtomically(
            mediaId = mediaId,
            entry = entry,
            job = job,
            expectedSource = artwork.source,
            directory = directory,
        )
    }

    private suspend fun cacheCanvasVideo(
        directory: File,
        mediaId: String,
        variant: CanvasVideoVariant,
        url: String?,
        currentFileName: String?,
        job: Job,
        expectedSource: moe.rukamori.archivetune.canvas.CanvasSource?,
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

        val partial = File(directory, "$fileName.${java.util.UUID.randomUUID()}.part")
        return try {
            downloadToFile(url = url, target = partial)
            currentCoroutineContext().ensureActive()
            if (partial.length() <= 0L) throw IOException("Downloaded empty canvas video")
            if (!partial.isPlayableCanvasVideo()) throw IOException("Canvas video exceeds device decoder capabilities")

            commitFileAtomically(
                mediaId = mediaId,
                partial = partial,
                target = target,
                fileName = fileName,
                job = job,
                expectedSource = expectedSource,
            )
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            Timber.tag(CanvasCacheLogTag).w(e, "Failed to download canvas video: %s", url)
            null
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
}
