package moe.rukamori.archivetune.flaccore.qbdlx

import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.flaccore.FlacConfig
import moe.rukamori.archivetune.flaccore.FlacLogger
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import moe.rukamori.archivetune.flaccore.qobuz.QobuzCandidateMatcher
import moe.rukamori.archivetune.flaccore.ratelimit.AggregatorRateLimiter

data class QbdlxResolvedStream(
    val url: String,
    val codec: String,
    val bitDepth: Int,
    val sampleRateHz: Int,
    val confidence: Float,
    val coverArtUrl: String?,
    val sourceTrackId: String,
)

/**
 * Backed by the Qobuz catalog via the DIRECT Qobuz API
 * (MD5-signed requests + a rotating `X-User-Auth-Token` pool).
 * Searches, scores candidates with the shared [QobuzCandidateMatcher],
 * and resolves the best match to a signed Hi-Res FLAC URL.
 *
 * Token health is per-account (Qobuz bans accounts, not IPs), so this source
 * rotates across a pool ([QbdlxCredentialStore]) and persists dead tokens. The
 * [AggregatorRateLimiter] breaker is a per-source health signal — a dead token
 * (auth failure / `UserUnauthenticated` preview) must NOT trip it (it's a
 * credential problem, not a service-down problem), so those paths rotate
 * without reporting a failure.
 */
class QbdlxQobuzSource(
    private val apiClient: QbdlxApiClient,
    private val credentialStore: QbdlxCredentialStore,
    private val rateLimiter: AggregatorRateLimiter,
    private val config: FlacConfig,
) {

    private val logger = FlacLogger(TAG)

    val id: String = SOURCE_ID

    /**
     * Streaming-only gate: same toggle + pool check as [isEnabled] but
     * WITHOUT the breaker — a user stream tap bypasses the breaker (see
     * [resolveImmediate]), so gating enablement on it would be inconsistent.
     * A disabled toggle still blocks streaming.
     */
    suspend fun isEnabledForStreaming(): Boolean =
        config.qbdlxEnabled() && config.qobuzEnabled() && !credentialStore.allDead()

    /**
     * User-initiated immediate resolve for the streaming path. Skips the
     * token bucket AND the breaker.
     */
    suspend fun resolveImmediate(
        query: TrackQuery,
        requestedQuality: Int? = null,
    ): QbdlxResolvedStream? {
        if (!isEnabledForStreaming()) return null
        return resolveInternal(query, bypassRateLimit = true, requestedQuality = requestedQuality)
    }

    // ── Internals ───────────────────────────────────────────────────────

    private suspend fun resolveInternal(
        query: TrackQuery,
        bypassRateLimit: Boolean,
        requestedQuality: Int?,
    ): QbdlxResolvedStream? {
        val (track, conf, token) = search(query, bypassRateLimit) ?: return null
        val formatId = requestedQuality ?: 27 // Default to Hi-Res if not specified
        return resolveFile(track, conf, token, formatId, bypassRateLimit)
    }

    /**
     * Search + match. Returns the best candidate, its confidence, and the live
     * token that found it. Rotates on auth failure (dead token), bounded by the
     * live pool (each tried token is recorded; a repeat or an exhausted pool
     * ends the loop). Null when no token is live or nothing crosses threshold.
     */
    private suspend fun search(
        query: TrackQuery,
        bypassRateLimit: Boolean,
    ): Triple<QbdlxTrack, Float, String>? {
        var token = credentialStore.activeToken() ?: return null
        val tried = mutableSetOf<String>()
        var guard = 0
        while (guard++ < MAX_TOKEN_ATTEMPTS) {
            tried += token
            try {
                for (term in query.searchTerms()) {
                    val candidates = callLimited(bypassRateLimit) {
                        apiClient.search(term, token)
                    } ?: continue // api error / 429 / acquire-denied (already reported)
                    val match = candidates
                        .map { it to confidence(query, it) }
                        .filter { it.second >= QobuzCandidateMatcher.MIN_CONFIDENCE }
                        .maxByOrNull { it.second }
                    if (match != null) return Triple(match.first, match.second, token)
                }
                return null // searched all terms, no match (search itself succeeded)
            } catch (e: QbdlxAuthException) {
                // Dead token, not a health failure: mark + rotate, don't trip breaker.
                logger.w("search auth-failed (${e.status}); marking token dead + rotating")
                credentialStore.markDead(token)
                token = credentialStore.activeToken()?.takeUnless { it in tried } ?: return null
            }
        }
        return null
    }

    /**
     * Resolve [track] to a signed FLAC URL, rotating tokens on death/region
     * lock. TokenDead → markDead + next live token (sticky-advance); RegionLocked →
     * iterate [QbdlxCredentialStore.tokensForRegion] (bounded). Bounded by the
     * tried-set + [MAX_TOKEN_ATTEMPTS].
     */
    private suspend fun resolveFile(
        track: QbdlxTrack,
        conf: Float,
        startToken: String,
        formatId: Int,
        bypassRateLimit: Boolean,
    ): QbdlxResolvedStream? {
        val tried = mutableSetOf<String>()
        var token: String? = startToken
        var guard = 0
        while (token != null && guard++ < MAX_TOKEN_ATTEMPTS) {
            if (!tried.add(token)) {
                token = credentialStore.activeToken()?.takeUnless { it in tried }
                continue
            }
            val outcome = resolveOnce(track, token, formatId, bypassRateLimit)
            when (outcome) {
                is Outcome.Resolved -> {
                    credentialStore.recordAlive(token)
                    return build(track, conf, outcome.ok)
                }
                Outcome.Dead -> {
                    credentialStore.markDead(token)
                    token = credentialStore.activeToken()?.takeUnless { it in tried }
                }
                // RegionLocked: the token is fine, the track just isn't licensed
                // for it — try region-matched tokens, don't mark anything dead.
                Outcome.Region -> return resolveRegion(track, conf, tried, formatId, bypassRateLimit)
                Outcome.Abort -> return null // rate-limit/api error, already reported
            }
        }
        return null
    }

    private suspend fun resolveRegion(
        track: QbdlxTrack,
        conf: Float,
        tried: MutableSet<String>,
        formatId: Int,
        bypassRateLimit: Boolean,
    ): QbdlxResolvedStream? {
        // TrackQuery has no country today → null just yields bounded live tokens.
        for (rt in credentialStore.tokensForRegion(null)) {
            if (!tried.add(rt)) continue
            when (val outcome = resolveOnce(track, rt, formatId, bypassRateLimit)) {
                is Outcome.Resolved -> {
                    credentialStore.recordAlive(rt)
                    return build(track, conf, outcome.ok)
                }
                Outcome.Dead -> credentialStore.markDead(rt)
                Outcome.Region -> Unit // still locked on this token, try the next
                Outcome.Abort -> return null
            }
        }
        return null
    }

    /** One getFileUrl attempt, translating exceptions + classification into an [Outcome]. */
    private suspend fun resolveOnce(
        track: QbdlxTrack,
        token: String,
        formatId: Int,
        bypassRateLimit: Boolean,
    ): Outcome {
        val result = try {
            callLimited(bypassRateLimit) { apiClient.getFileUrl(track.id, formatId, token) }
        } catch (e: QbdlxAuthException) {
            // 401 on getFileUrl is the same signal as a UserUnauthenticated body.
            return Outcome.Dead
        } ?: return Outcome.Abort
        return when (result) {
            is QbdlxResolveResult.Ok -> Outcome.Resolved(result)
            QbdlxResolveResult.TokenDead -> Outcome.Dead
            QbdlxResolveResult.RegionLocked -> Outcome.Region
        }
    }

    private sealed interface Outcome {
        data class Resolved(val ok: QbdlxResolveResult.Ok) : Outcome
        object Dead : Outcome
        object Region : Outcome
        object Abort : Outcome
    }

    private fun build(track: QbdlxTrack, conf: Float, ok: QbdlxResolveResult.Ok): QbdlxResolvedStream {
        val img = track.album?.image
        val art = img?.large ?: img?.thumbnail ?: img?.small
        return QbdlxResolvedStream(
            url = ok.url,
            codec = ok.codec,
            bitDepth = ok.bitDepth,
            sampleRateHz = ok.sampleRateHz,
            confidence = conf,
            coverArtUrl = art,
            sourceTrackId = track.id.toString(),
        )
    }

    private fun confidence(query: TrackQuery, candidate: QbdlxTrack): Float =
        QobuzCandidateMatcher.confidence(
            query = query,
            candTitle = candidate.title,
            candArtist = candidate.performer?.name.orEmpty(),
            candIsrc = candidate.isrc,
            candDurationSec = candidate.duration,
            candStreamable = candidate.streamable,
        )

    /**
     * Wraps an API call with rate-limiter bookkeeping. Returns null on rate-limit denial / api
     * error (already reported). [QbdlxAuthException] is RETHROWN — token
     * rotation is the caller's concern and a dead token must not trip the
     * breaker.
     */
    private suspend fun <T> callLimited(
        bypassRateLimit: Boolean,
        block: suspend () -> T,
    ): T? {
        if (!bypassRateLimit && !rateLimiter.acquire(id)) return null
        return try {
            block().also { rateLimiter.reportSuccess(id) }
        } catch (e: QbdlxAuthException) {
            throw e // rotation concern; do NOT report (not a health failure)
        } catch (e: CancellationException) {
            throw e // never swallow cancellation as a failure
        } catch (e: QbdlxApiException) {
            if (e.status == 429) rateLimiter.reportRateLimited(id) else rateLimiter.reportFailure(id)
            logger.w("qbdlx api call failed status=${e.status}")
            null
        } catch (e: Exception) {
            rateLimiter.reportFailure(id)
            logger.w("qbdlx call threw ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    companion object {
        const val SOURCE_ID = "qbdlx_qobuz"
        private const val TAG = "QbdlxSource" // no "Qobuz" — keeps the source out of shared logcat diagnostics

        /**
         * Hard ceiling on token rotations per phase. The tried-set is the real
         * terminator (a finite pool exhausts); this just bounds a misbehaving
         * credential store from spinning the retry loop.
         * ponytail: fixed cap; tried-set already guarantees termination.
         */
        private const val MAX_TOKEN_ATTEMPTS = 6
    }
}
