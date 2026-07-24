/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.obfuscator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal data class PlayerScript(
    val playerId: String,
    val url: String,
    val source: String,
)

internal enum class PlayerScriptFetchStage {
    DISCOVERING,
    DOWNLOADING,
}

internal class PlayerScriptClient(
    private val proxyProvider: () -> Proxy?,
) {
    suspend fun fetch(
        videoId: String?,
        onStageChanged: (PlayerScriptFetchStage) -> Unit,
    ): PlayerScript =
        withContext(Dispatchers.IO) {
            val client = createClient()
            try {
                onStageChanged(PlayerScriptFetchStage.DISCOVERING)
                val iframeBody = client.fetchText(IFRAME_API_URL, MAX_DISCOVERY_BYTES)
                val discoveredPath =
                    playerPathPatterns.firstNotNullOfOrNull { pattern ->
                        pattern.find(iframeBody)?.groupValues?.getOrNull(1)
                    } ?: playerIdPattern
                        .find(iframeBody)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let { "/s/player/$it/player_ias.vflset/en_US/base.js" }
                        ?: videoId
                            ?.takeIf(VIDEO_ID_PATTERN::matches)
                            ?.let { id ->
                                val embedBody = client.fetchText("$YOUTUBE_ORIGIN/embed/$id", MAX_DISCOVERY_BYTES)
                                playerPathPatterns.firstNotNullOfOrNull { pattern ->
                                    pattern.find(embedBody)?.groupValues?.getOrNull(1)
                                } ?: playerIdPattern
                                    .find(embedBody)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.let { "/s/player/$it/player_ias.vflset/en_US/base.js" }
                            } ?: throw MoriCipherException("YouTube player URL was not found")

                val unescapedPath = discoveredPath.replace("\\/", "/")
                val playerUrl =
                    when {
                        unescapedPath.startsWith("http") -> unescapedPath
                        unescapedPath.startsWith("//") -> "https:$unescapedPath"
                        else -> "$YOUTUBE_ORIGIN${if (unescapedPath.startsWith("/")) "" else "/"}$unescapedPath"
                    }

                val playerId =
                    PLAYER_ID_PATTERN
                        .find(playerUrl)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?: throw MoriCipherException("YouTube player identifier was invalid")

                onStageChanged(PlayerScriptFetchStage.DOWNLOADING)
                val source = client.fetchText(playerUrl, MAX_PLAYER_BYTES)
                PlayerScript(playerId = playerId, url = playerUrl, source = source)
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }

    private fun createClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .proxy(proxyProvider() ?: Proxy.NO_PROXY)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

    private fun OkHttpClient.fetchText(
        url: String,
        maximumBytes: Int,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/javascript,text/html;q=0.9,*/*;q=0.1")
                .build()
        return newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MoriCipherException("YouTube player request failed with HTTP ${response.code}")
            }
            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength > maximumBytes) {
                throw MoriCipherException("YouTube player response exceeded the size limit")
            }
            val initialCapacity =
                contentLength
                    .takeIf { it in 1..maximumBytes.toLong() }
                    ?.toInt()
                    ?: DEFAULT_BUFFER_SIZE
            val output = ByteArrayOutputStream(initialCapacity)
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    if (totalBytes > maximumBytes) {
                        throw MoriCipherException("YouTube player response exceeded the size limit")
                    }
                    output.write(buffer, 0, read)
                }
            }
            val bytes = output.toByteArray()
            if (bytes.isEmpty()) throw MoriCipherException("YouTube player response was empty")
            bytes.toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
        }
    }

    private companion object {
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        const val IFRAME_API_URL = "$YOUTUBE_ORIGIN/iframe_api"
        const val MAX_DISCOVERY_BYTES = 1_000_000
        const val MAX_PLAYER_BYTES = 5_000_000
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/137.0 Mobile Safari/537.36"
        val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
        val PLAYER_ID_PATTERN = Regex("/s/player/([A-Za-z0-9_-]+)/")

        val playerPathPatterns =
            listOf(
                Regex("""["']([^"']*(?:/|\\/)s(?:/|\\/)player(?:/|\\/)[A-Za-z0-9_-]+(?:/|\\/)[^"']*base\.js)["']"""),
                Regex("""(?:jsUrl|PLAYER_JS_URL|jsPath|script)["']?\s*:\s*["']([^"']+base\.js)["']"""),
                Regex("""src\s*=\s*["']([^"']+/s/player/[^"']+base\.js)["']"""),
            )
        val playerIdPattern = Regex("""\\?/s\\?/player\\?/([A-Za-z0-9_-]+)\\?/""")
    }
}
