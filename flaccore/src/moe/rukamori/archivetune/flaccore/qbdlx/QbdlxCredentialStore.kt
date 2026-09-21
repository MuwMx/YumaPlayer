package moe.rukamori.archivetune.flaccore.qbdlx

import java.util.concurrent.ConcurrentHashMap
import moe.rukamori.archivetune.flaccore.FlacConfig
import moe.rukamori.archivetune.flaccore.FlacKvStore
import moe.rukamori.archivetune.flaccore.FlacLogger

/** A parsed pool member: token, ISO-2 country, and the app_id it must be signed under. */
private data class PoolEntry(val token: String, val country: String, val appId: String)

/**
 * Manages the qbdlx Qobuz token pool: a bundled set of `user_auth_token:country`
 * pairs plus an optional user-pasted token that takes priority.
 *
 * Responsibilities:
 *  - [activeToken]: the token to use now — sticky, not round-robin. Priority:
 *    pasted (if live) > pinned pool token (if live and still a pool member) >
 *    the sticky primary (if still live) > else the first live token in canonical
 *    order, which becomes the new sticky primary. One token carries load until it
 *    dies, then we advance. Null when nothing is live.
 *  - [tokensForRegion]: ordered live tokens for a region-locked retry,
 *    country-matched first, bounded at [MAX_REGION_TRIES] so one locked track
 *    can't fan out across every account.
 *  - [markDead]/[recordAlive]: persist a token as dead (auth-failed) / clear it,
 *    so a cold start doesn't re-probe dead tokens.
 *  - [allDead]: true when there's no usable token (none configured, or all
 *    currently dead) — drives the Settings "paste a token" surface and gates
 *    the source off.
 *
 * Also the signing authority ([QbdlxSigningResolver]): each pool token is tagged
 * with the app_id it was minted under, and [signingFor] hands the client the right
 * (app_id, app_secret) so a token is never signed with a mismatched secret (which
 * silently downgrades it to a 30-second preview).
 */
class QbdlxCredentialStore(
    private val config: FlacConfig,
    private val kvStore: FlacKvStore,
    private val poolProvider: QbdlxPoolProvider,
) : QbdlxSigningResolver {
    private val pastedTokenKey = "pasted_token"
    private val pinnedTokenKey = "pinned_token"

    // ── Signing credentials (app_id → app_secret) ───────────────────────────
    internal var primaryAppId: String = ""
    internal var primaryAppSecret: String = ""
    internal var appSecretsRaw: String = ""

    private suspend fun ensureConfigLoaded() {
        if (primaryAppId.isEmpty()) {
            primaryAppId = config.qbdlxAppId()
            primaryAppSecret = config.qbdlxAppSecret()
        }
    }

    /**
     * app_id → app_secret for every credential pair we can sign with. The primary
     * pair is always present (so a token with no explicit app_id, or one whose
     * app_id we don't have a secret for, still signs under the primary). Cheap to
     * rebuild each call — the map is a handful of entries.
     */
    private suspend fun appSecretMap(): Map<String, String> {
        ensureConfigLoaded()
        val map = LinkedHashMap<String, String>()
        map[primaryAppId] = primaryAppSecret
        appSecretsRaw.split(",").forEach { pair ->
            val i = pair.indexOf(':')
            if (i > 0) {
                val appId = pair.take(i).trim()
                val secret = pair.substring(i + 1).trim()
                if (appId.isNotEmpty() && secret.isNotEmpty()) map[appId] = secret
            }
        }
        return map
    }

    /**
     * The (app_id, app_secret) to sign [token]'s requests with:
     *  1. a pool token signs with the app_id it's tagged with → that app_id's
     *     secret (or the primary secret if we don't have that app_id's);
     *  2. anything unknown falls back to the primary pair.
     */
    override suspend fun signingFor(token: String): QbdlxSigning {
        ensureConfigLoaded()
        val appId = poolAppId(token) ?: primaryAppId
        // Use the tag's app_id only if we actually have its secret; otherwise fall
        // back to the full primary PAIR (never a tagged-app_id / primary-secret mix,
        // which is exactly the mismatch that yields previews).
        val secret = appSecretMap()[appId] ?: return QbdlxSigning(primaryAppId, primaryAppSecret)
        return QbdlxSigning(appId, secret)
    }

    private suspend fun poolAppId(token: String): String? = pool().firstOrNull { it.token == token }?.appId

    private var overriddenPoolRaw: String? = null
    /**
     * Test seam: the raw `token:country,token:country` pool. Defaults to the
     * decrypted BuildConfig blob (via [QbdlxPoolProvider]); tests override it.
     * Dynamic: each [pool] call reads fresh from [poolProvider] unless overridden.
     */
    internal var poolRaw: String?
        get() = overriddenPoolRaw
        set(value) { overriddenPoolRaw = value }

    internal suspend fun resolvePoolRaw(): String = overriddenPoolRaw ?: poolProvider.rawPool()

    /** Injectable clock (epoch ms) for the dead-token cooldown; overridable in tests. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /**
     * Token → epoch-ms until which it is considered dead. IN-MEMORY and
     * TIME-BOXED (circuit-breaker style), deliberately NOT persisted: a single
     * transient auth failure (a cold-start network blip, or a 401 from the same
     * shared token being used concurrently across apps/the website) must NOT
     * permanently disable a token. It's skipped for [DEAD_COOLDOWN_MS] then
     * auto-retried; a genuinely-dead token just re-marks. A process restart also
     * clears it. This replaces an earlier persisted, permanent dead-set that
     * left the whole pool stuck on one transient 401 ("token expired" forever).
     */
    private val deadUntil = ConcurrentHashMap<String, Long>()

    /**
     * Token → epoch-ms of its last auth failure. Drives selection order: a token we
     * have never probed sorts ahead of one we know failed, and among failed ones the
     * oldest failure goes first, so successive resolves work DOWN the pool.
     *
     * Selection used to be canonical order alone, which meant every resolve re-probed
     * the same head of the list: QbdlxQobuzSource only tries a bounded number of
     * tokens per resolve, and [DEAD_COOLDOWN_MS] expires between tracks, so anything
     * past that first handful was unreachable and a live token further down was never
     * found (device-verified 2026-08-15 against a 17-token pool).
     */
    private val lastFailedAt = ConcurrentHashMap<String, Long>()

    /**
     * Sticky primary: the token we keep using until it dies (replaces round-robin).
     * @Volatile for visibility only — two concurrent resolves both picking a live
     * token is benign (last write wins, no corruption). Nulled on markDead of the
     * primary so the next call advances. In-memory (per process).
     */
    @Volatile
    private var activePrimary: String? = null

    /**
     * Parsed pool. Each entry is `token:country[:appId]`:
     *  - `token`            → primary app_id, no country
     *  - `token:country`    → primary app_id (the legacy/bundled shape)
     *  - `token:country:appId` → tagged app_id (the remote pool, which spans
     *    more than one app_id — the tag is what lets us sign each token correctly
     *    instead of dropping the ones we couldn't sign).
     * Countries are ISO-2 and app_ids are 9-digit, so the tail is unambiguous;
     * the token (never containing ':') is whatever is left in front.
     */
    private suspend fun pool(): List<PoolEntry> {
        ensureConfigLoaded()
        return resolvePoolRaw().split(",")
            .mapNotNull { entry ->
                val e = entry.trim().ifEmpty { return@mapNotNull null }
                val parts = e.split(":")
                when (parts.size) {
                    1 -> PoolEntry(parts[0], "", primaryAppId)
                    2 -> PoolEntry(parts[0], parts[1], primaryAppId)
                    else -> PoolEntry(
                        token = parts.dropLast(2).joinToString(":"),
                        country = parts[parts.size - 2],
                        appId = parts.last(),
                    )
                }
            }
    }

    /** True when [token] is within its dead cooldown. Cleans up expired entries. */
    private fun isDead(token: String): Boolean {
        val until = deadUntil[token] ?: return false
        if (clock() < until) return true
        deadUntil.remove(token) // cooldown elapsed — give it another chance
        return false
    }

    private suspend fun pastedToken(): String? =
        kvStore.get(pastedTokenKey)?.takeIf { it.isNotBlank() }

    /** The picker-pinned pool token, or null for Auto. */
    suspend fun pinnedToken(): String? =
        kvStore.get(pinnedTokenKey)?.takeIf { it.isNotBlank() }

    /** Pin a pool token for the Settings picker, or clear (null) for Auto. */
    suspend fun setPinnedToken(token: String?) {
        val t = token?.trim()
        if (t.isNullOrEmpty()) kvStore.put(pinnedTokenKey, null) else kvStore.put(pinnedTokenKey, t)
    }

    /**
     * The token to use now (sticky, not round-robin):
     *   1. pasted token if live (the user's own / monthly-refresh path — wins);
     *   2. pinned pool token if live AND still a member of the current pool
     *      (a pin to a since-removed token is ignored → falls through to auto);
     *   3. the sticky [activePrimary] if still live;
     *   4. else the first live token in canonical order → pinned as the new primary.
     * Null when nothing is live.
     */
    suspend fun activeToken(): String? {
        pastedToken()?.let { if (!isDead(it)) return it }
        pinnedToken()?.let { p ->
            if (!isDead(p) && pool().any { it.token == p }) return p
        }
        activePrimary?.let { if (!isDead(it)) return it }
        // Never-probed tokens first (no [lastFailedAt] entry → 0), then oldest failure
        // first, with the historical canonical order as the tiebreak so a healthy pool
        // still picks the same token on every device.
        val next = pool().map { it.token }
            .filter { !isDead(it) }
            .sortedWith(compareBy({ lastFailedAt[it] ?: 0L }, { it.hashCode() }, { it }))
            .firstOrNull() ?: return null
        activePrimary = next
        return next
    }

    /**
     * Live tokens to try for a region-locked track: country-matched first, then
     * the rest, capped at [MAX_REGION_TRIES].
     */
    suspend fun tokensForRegion(country: String?): List<String> {
        val live = pool().filter { !isDead(it.token) }
        val sorted = if (country.isNullOrBlank()) {
            live
        } else {
            live.sortedByDescending { it.country.equals(country, ignoreCase = true) }
        }
        return sorted.map { it.token }.take(MAX_REGION_TRIES)
    }

    /** Mark [token] dead for the cooldown window (auth failure). Auto-retried after. */
    fun markDead(token: String) {
        val now = clock()
        deadUntil[token] = now + DEAD_COOLDOWN_MS
        lastFailedAt[token] = now
        if (token == activePrimary) activePrimary = null
    }

    /** Clear a token's dead flag (a successful call, or a fresh paste). */
    fun recordAlive(token: String) {
        deadUntil.remove(token)
        lastFailedAt.remove(token)
    }

    /**
     * Set (or clear, with null) the user-pasted token. Clears any dead flag on
     * the pasted value so pasting a token (the "expired — paste a fresh one"
     * recovery) always gives it a clean chance, even if that same string was
     * previously marked dead.
     */
    suspend fun setPastedToken(token: String?) {
        val t = token?.trim()
        if (!t.isNullOrEmpty()) recordAlive(t)
        if (t.isNullOrEmpty()) kvStore.put(pastedTokenKey, null) else kvStore.put(pastedTokenKey, t)
    }

    /**
     * True when there is NO usable token: none configured at all (no bundled
     * pool, no paste), or every configured one is currently dead. Drives the
     * Settings "paste a token" badge AND gates the source off entirely via
     * isEnabled/isEnabledForStreaming. A tokenless build MUST surface the paste
     * prompt and drop out of the chain.
     */
    suspend fun allDead(): Boolean {
        val pasted = pastedToken()
        val poolTokens = pool().map { it.token }
        if (poolTokens.isEmpty() && pasted == null) return true // no credentials at all
        pasted?.let { if (!isDead(it)) return false }
        return poolTokens.all { isDead(it) }
    }

    /** Test-only: wipe persisted pasted state + in-memory dead flags. */
    internal suspend fun clearPersistedForTest() {
        deadUntil.clear()
        kvStore.put(pastedTokenKey, null)
        kvStore.put(pinnedTokenKey, null)
    }

    companion object {
        private const val TAG = "QbdlxPool"

        const val MAX_REGION_TRIES = 3

        // Dead-token cooldown before a token is retried (circuit-breaker style).
        // 60s, deliberately SHORT: a dead token blacks out BOTH download and
        // streaming (isEnabled + isEnabledForStreaming gate on allDead), so a
        // TRANSIENT failure (a preview/522/timeout on the shared account under
        // the download burst) that trips a mark-dead must not kill qbdlx for
        // long. 60s recovers fast; a genuinely-dead token just re-marks, costing
        // one doomed attempt per minute (negligible). Was 10min — far too long a
        // total blackout for a transient ("completely dead" until it aged out).
        const val DEAD_COOLDOWN_MS = 60_000L
    }
}
