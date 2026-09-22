/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.pages

import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.obfuscator.MoriCipherRuntime
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

private class NewPipeDownloaderImpl(
    proxy: Proxy?,
) : Downloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy ?: Proxy.NO_PROXY)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(httpMethod, dataToSend?.toRequestBody())
                .url(url)

        var hasUserAgent = false
        headers.forEach { (headerName, headerValueList) ->
            if (headerName.equals("User-Agent", ignoreCase = true) && headerValueList.isNotEmpty()) {
                hasUserAgent = true
            }

            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        if (!hasUserAgent) {
            requestBuilder.header("User-Agent", YouTubeClient.USER_AGENT_WEB)
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body.string()
        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}

object NewPipeUtils {
    init {
        NewPipe.init(NewPipeDownloaderImpl(YouTube.streamProxy))
    }

    suspend fun getSignatureTimestamp(videoId: String): Result<Int> {
        MoriCipherRuntime.signatureTimestamp(videoId).getOrNull()?.let {
            return Result.success(it)
        }
        return runCatching {
            withJavaScriptPlayerCacheRecovery {
                YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
            }
        }
    }

    suspend fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<String> {
        try {
            val directUrl = format.url
            if (directUrl != null) {
                val resolvedDirectUrl =
                    if (directUrl.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true) {
                        val nResult = MoriCipherRuntime.transformNParameter(videoId, directUrl)
                        val nResolved = nResult.getOrNull()
                        if (nResolved != null) {
                            nResolved
                        } else {
                            val exception = nResult.exceptionOrNull()
                            System.err.println("NewPipeUtils: MoriCipherRuntime.transformNParameter failed for $videoId: ${exception?.message}")
                            exception?.printStackTrace()
                            getUrlWithThrottlingParameterDeobfuscated(videoId, directUrl)
                        }
                    } else {
                        directUrl
                    }

                return Result.success(
                    YouTube.appendGvsPoToken(
                        url = resolvedDirectUrl,
                        client = client,
                        authState = authState,
                    ),
                )
            }

            val cipherString =
                format.signatureCipher ?: format.cipher
                    ?: return Result.failure(ParsingException("Could not find format url"))

            val mResult = MoriCipherRuntime.resolveStreamUrl(videoId, cipherString)
            val resolved = mResult.getOrNull()
            if (resolved != null) {
                return Result.success(
                    YouTube.appendGvsPoToken(
                        url = resolved,
                        client = client,
                        authState = authState,
                    ),
                )
            } else {
                val exception = mResult.exceptionOrNull()
                System.err.println("NewPipeUtils: MoriCipherRuntime.resolveStreamUrl failed for $videoId: ${exception?.message}")
                exception?.printStackTrace()
            }

            val params = parseQueryString(cipherString)
            val obfuscatedSignature = params["s"] ?: throw ParsingException("Could not parse cipher signature")
            val signatureParam = params["sp"]?.takeIf { it.isNotBlank() } ?: "signature"
            val urlString = params["url"] ?: throw ParsingException("Could not parse cipher url")

            val urlBuilder = URLBuilder(urlString)

            val deobfuscatedSig =
                withJavaScriptPlayerCacheRecovery {
                    YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
                }

            urlBuilder.parameters[signatureParam] = deobfuscatedSig

            val resolvedUrl = getUrlWithThrottlingParameterDeobfuscated(videoId, urlBuilder.buildString())

            return Result.success(
                YouTube.appendGvsPoToken(
                    url = resolvedUrl,
                    client = client,
                    authState = authState,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return Result.failure(error)
        }
    }

    private fun getUrlWithThrottlingParameterDeobfuscated(
        videoId: String,
        url: String,
    ): String =
        withJavaScriptPlayerCacheRecovery {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        }

    private inline fun <T> withJavaScriptPlayerCacheRecovery(block: () -> T): T =
        try {
            block()
        } catch (parsingFailure: ParsingException) {
            throw parsingFailure
        } catch (error: Exception) {
            runCatching { YoutubeJavaScriptPlayerManager.clearAllCaches() }
            throw error
        }
}
