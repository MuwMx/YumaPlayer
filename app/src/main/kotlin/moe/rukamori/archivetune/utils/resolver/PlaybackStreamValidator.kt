/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver

import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.ProxyAuth
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.reportException
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit

object PlaybackStreamValidator {
    private const val logTag = "PlaybackStreamValidator"
    internal const val STREAM_PROBE_CONNECT_TIMEOUT_SECONDS = 4L
    internal const val STREAM_PROBE_READ_TIMEOUT_SECONDS = 4L
    internal const val STREAM_PROBE_CALL_TIMEOUT_SECONDS = 6L

    @Volatile
    private var streamClientPair: Pair<Proxy, OkHttpClient>? = null

    internal fun currentStreamClient(): OkHttpClient {
        val current = YouTube.streamOkHttpProxy
        streamClientPair?.let { (proxy, client) ->
            if (proxy == current) return client
        }
        val client =
            OkHttpClient
                .Builder()
                .proxy(current)
                .proxyAuthenticator(ProxyAuth.proxyAuthenticator)
                .connectTimeout(STREAM_PROBE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(STREAM_PROBE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(STREAM_PROBE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        streamClientPair = current to client
        return client
    }

    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    fun validateStatus(url: String): Boolean {
        val start = android.os.SystemClock.elapsedRealtime()
        Timber.tag("PlaybackTiming").v("Validating stream URL status")
        try {
            val requestProfile = StreamClientUtils.resolveRequestProfile(url)
            val probeRanges = buildPlaybackProbeRanges()

            var sawReadableProbe = false
            for (range in probeRanges) {
                val rangeStart = android.os.SystemClock.elapsedRealtime()
                val rangeRequest =
                    StreamClientUtils
                        .applyRequestProfile(
                            okhttp3.Request
                                .Builder()
                                .get()
                                .header("Range", range)
                                .url(url),
                            requestProfile,
                        ).build()

                val probeValid =
                    currentStreamClient().newCall(rangeRequest).execute().use { response ->
                        val code = response.code
                        val rangeDuration = android.os.SystemClock.elapsedRealtime() - rangeStart
                        Timber.tag("PlaybackTiming").d("Stream probe range [$range] -> code $code in ${rangeDuration}ms")

                        if (code == 403) return@use false
                        if (code !in 200..399 && code != 416) return@use false
                        if (code == 416) return@use sawReadableProbe

                        val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.US)
                        if (
                            contentType.startsWith("text/html") ||
                            contentType.startsWith("text/plain") ||
                            contentType.startsWith("application/json") ||
                            contentType.startsWith("application/xml") ||
                            contentType.startsWith("text/xml")
                        ) {
                            Timber.tag("PlaybackTiming").w(
                                "Rejecting stream probe because it returned non-media content-type: %s",
                                contentType,
                            )
                            return@use false
                        }

                        val readable = response.body.source().request(1)
                        if (readable) {
                            sawReadableProbe = true
                        }
                        readable
                    }
                if (!probeValid) {
                    Timber.tag("PlaybackTiming").w("Stream probe failed on range $range (Total: ${android.os.SystemClock.elapsedRealtime() - start}ms)")
                    return false
                }
            }
            Timber.tag("PlaybackTiming").i("Stream validation PASSED in ${android.os.SystemClock.elapsedRealtime() - start}ms")
            return true
        } catch (e: Exception) {
            Timber.tag("PlaybackTiming").e(e, "Stream validation EXCEPTION after ${android.os.SystemClock.elapsedRealtime() - start}ms")
            reportException(e)
        }
        return false
    }

    internal fun buildPlaybackProbeRanges(): List<String> =
        listOf(
            "bytes=0-1",
        )
}
