/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lossless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.flaccore.monochrome.MonoHttpClient
import moe.rukamori.archivetune.flaccore.monochrome.MonoHttpResponse
import okhttp3.OkHttpClient
import okhttp3.Request

class MonoHttpClientImpl(
    private val client: OkHttpClient,
) : MonoHttpClient {

    override suspend fun get(url: String, headers: Map<String, String>): MonoHttpResponse =
        withContext(Dispatchers.IO) {
            val reqBuilder = Request.Builder().url(url)
            for ((k, v) in headers) {
                reqBuilder.addHeader(k, v)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                val responseHeaders = buildMap {
                    for (i in 0 until response.headers.size) {
                        put(response.headers.name(i), response.headers.value(i))
                    }
                }
                val bytes = response.body?.bytes()
                val body = bytes?.decodeToString().orEmpty()
                MonoHttpResponse(
                    code = code,
                    body = body,
                    headers = responseHeaders,
                    bytes = bytes,
                )
            }
        }
}
