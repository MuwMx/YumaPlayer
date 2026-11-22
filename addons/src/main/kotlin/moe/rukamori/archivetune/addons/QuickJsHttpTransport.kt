package moe.rukamori.archivetune.addons

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class QuickJsHttpTransport(
    private val httpClient: OkHttpClient,
) {
    fun executeFetch(reqJson: String, fetchBase: String): String {
        val reqObj = JSONObject(reqJson)
        var url = reqObj.optString("url")
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true) && fetchBase.isNotEmpty()) {
            url = if (url.startsWith("/")) {
                val baseRoot = fetchBase.substringBefore("://") + "://" + fetchBase.substringAfter("://").substringBefore("/")
                "$baseRoot$url"
            } else {
                "${fetchBase.trimEnd('/')}/$url"
            }
        }
        val method = reqObj.optString("method", "GET").uppercase()
        val headersObj = reqObj.optJSONObject("headers")
        val bodyStr = if (reqObj.has("body") && !reqObj.isNull("body")) reqObj.optString("body") else null

        val reqBuilder = Request.Builder().url(url)
        var hasUa = false
        if (headersObj != null) {
            for (k in headersObj.keys()) {
                val v = headersObj.optString(k)
                reqBuilder.header(k, v)
                if (k.equals("user-agent", ignoreCase = true)) hasUa = true
            }
        }
        if (!hasUa) reqBuilder.header("User-Agent", "Mozilla/5.0 YumaPlayer/Addon")

        when (method) {
            "POST" -> reqBuilder.post((bodyStr ?: "").toRequestBody("application/json".toMediaType()))
            "PUT" -> reqBuilder.put((bodyStr ?: "").toRequestBody("application/json".toMediaType()))
            "DELETE" -> reqBuilder.delete()
            else -> reqBuilder.get()
        }

        return httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val out = JSONObject()
            out.put("status", resp.code)
            out.put("ok", resp.isSuccessful)
            out.put("body", resp.body?.string().orEmpty())
            out.toString()
        }
    }
}
