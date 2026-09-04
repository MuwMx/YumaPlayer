package moe.rukamori.archivetune.flaccore.qbdlx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.flaccore.FlacConfig
import moe.rukamori.archivetune.flaccore.FlacLogger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Outcome of a getFileUrl call, classified from the JSON body (spec §2). */
sealed interface QbdlxResolveResult {
    data class Ok(val url: String, val codec: String, val bitDepth: Int, val sampleRateHz: Int) : QbdlxResolveResult
    /** Token is dead/unauthenticated (preview/sample/fmt5). Caller marks it dead + rotates. */
    object TokenDead : QbdlxResolveResult
    /** Track unavailable for this token's region/rights. Caller tries other tokens. */
    object RegionLocked : QbdlxResolveResult
}

/** Thrown on an HTTP 401 (auth) — distinct so the source can markDead + rotate. */
class QbdlxAuthException(val status: Int, message: String? = null) : RuntimeException(message)
/** Thrown on any other non-2xx / network failure — transient, do NOT mark dead. */
class QbdlxApiException(val status: Int, message: String? = null) : RuntimeException(message)

class QbdlxApiClient(
    private val config: FlacConfig,
    sharedClient: OkHttpClient,
    private val signer: QbdlxSigner,
    private val signingResolver: QbdlxSigningResolver,
) {
    internal var appId: String = ""
    internal var httpClient: OkHttpClient = sharedClient  // direct www.qobuz.com; no interceptor
    internal var baseUrl: String = ORIGIN
    internal var json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val logger = FlacLogger(TAG)

    /** Search the Qobuz catalog. Throws [QbdlxAuthException] on 401, [QbdlxApiException] otherwise. */
    suspend fun search(query: String, token: String, limit: Int = 10): List<QbdlxTrack> =
        withContext(Dispatchers.IO) {
            if (appId.isEmpty()) appId = config.qbdlxAppId()
            val url = "$baseUrl/api.json/0.2/catalog/search".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("type", "tracks")
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("app_id", appId)
                .build()
            val body = get(url.toString(), token)
            runCatching { json.decodeFromString<QbdlxSearchResponse>(body).tracks.items }.getOrDefault(emptyList())
        }

    /** Resolve a track id to a signed FLAC URL, classified. */
    suspend fun getFileUrl(trackId: Long, formatId: Int, token: String): QbdlxResolveResult =
        withContext(Dispatchers.IO) {
            if (appId.isEmpty()) appId = config.qbdlxAppId()
            // Sign with THIS token's own (app_id, app_secret): the pool spans more
            // than one app_id and a connected account carries its own pair. Sign
            // with the wrong secret and Qobuz returns a 30-second preview, not FLAC.
            val signing = signingResolver.signingFor(token)
            // ts and sig MUST be one atomic read: take ts once, sign with it, send the same ts.
            val ts = signer.requestTs()
            val sig = signer.signGetFileUrl(ts = ts, trackId = trackId, formatId = formatId, appSecret = signing.appSecret)
            val url = "$baseUrl/api.json/0.2/track/getFileUrl".toHttpUrl().newBuilder()
                .addQueryParameter("track_id", trackId.toString())
                .addQueryParameter("format_id", formatId.toString())
                .addQueryParameter("app_id", signing.appId)
                .addQueryParameter("request_ts", ts.toString())
                .addQueryParameter("request_sig", sig)
                .addQueryParameter("intent", "stream")
                .build()
            val raw = get(url.toString(), token, appIdHeader = signing.appId)
            val result = classify(json.decodeFromString<QbdlxFileUrl>(raw))
            if (result is QbdlxResolveResult.TokenDead) {
                logger.w("getFileUrl classified TokenDead for track=$trackId fmt=$formatId; raw=${raw.take(300)}")
            }
            result
        }

    private fun classify(f: QbdlxFileUrl): QbdlxResolveResult {
        val dead = f.sample || f.formatId == 5 ||
            f.restrictions.any { it.code.equals("UserUnauthenticated", ignoreCase = true) }
        if (dead) return QbdlxResolveResult.TokenDead
        if (f.url.isNullOrBlank() || f.formatId < 6) return QbdlxResolveResult.RegionLocked
        // formatId >= 6 here (5 already returned TokenDead) → always FLAC.
        return QbdlxResolveResult.Ok(f.url, "flac", f.bitDepth, (f.samplingRate * 1000f).toInt())
    }

    /**
     * Qobuz binds a `user_auth_token` to the app_id it was minted under: send a
     * different app's id and the SAME token answers 401. The pool mixes tokens from
     * two apps, so the id must come from the TOKEN, never from a client-wide
     * constant.
     *
     * Resolved here rather than at each endpoint because the endpoints that forgot
     * are exactly how this went unnoticed: [getFileUrl] was migrated to per-token
     * signing, the eight catalog calls were not, and since `search` runs first in
     * every resolve those tokens 401'd and were marked dead before signing was ever
     * reached. Measured on the live pool 2026-08-15: 2 of 18 tokens authenticated
     * with the primary app_id, 12 of 18 with their own — the pool was never "dead".
     */
    private suspend fun get(url: String, token: String, appIdHeader: String? = null): String {
        val tokenAppId = appIdHeader ?: signingResolver.signingFor(token).appId
        val req = Request.Builder().url(
            url.toHttpUrl().newBuilder().setQueryParameter("app_id", tokenAppId).build(),
        )
            .header("X-App-Id", tokenAppId)
            .header("X-User-Auth-Token", token)
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .get().build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                logger.w("auth 401 on ${url.substringBefore('?').substringAfterLast('/')}: ${body.take(160)}")
                throw QbdlxAuthException(401, body.take(120))
            }
            // A banned account is a DEAD TOKEN, not a service failure.
            //
            // Qobuz answers a blocked account with 403 USER_BLOCKED, which used to
            // fall through to the generic branch below: reported as a health
            // failure, never marked dead, never rotated away from. Because the
            // active token is sticky, one banned account in the pool meant every
            // single resolve failed to it and dropped to lossy YouTube — with the
            // other live tokens sitting unused. Observed on-device 2026-08-02.
            //
            // Matched on the error_code rather than the bare status so a 403 that
            // genuinely means "service said no" (rate limit, geo) still counts as
            // a transient failure and doesn't burn a good token.
            if (resp.code == 403 && body.contains("USER_BLOCKED", ignoreCase = true)) {
                logger.w("token's account is blocked (403 USER_BLOCKED) — marking dead + rotating")
                throw QbdlxAuthException(403, body.take(120))
            }
            if (!resp.isSuccessful) {
                logger.w("HTTP ${resp.code} on ${url.substringBefore('?').substringAfterLast('/')}: ${body.take(160)}")
                throw QbdlxApiException(resp.code, body.take(120))
            }
            return body
        }
    }

    private companion object {
        const val TAG = "QbdlxApiClient"
        const val ORIGIN = "https://www.qobuz.com"
        const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
    }
}
