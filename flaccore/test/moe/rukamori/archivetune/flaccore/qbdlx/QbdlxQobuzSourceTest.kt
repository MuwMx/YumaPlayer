package moe.rukamori.archivetune.flaccore.qbdlx

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.flaccore.FlacConfig
import moe.rukamori.archivetune.flaccore.model.RateLimitState
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import moe.rukamori.archivetune.flaccore.ratelimit.AggregatorRateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QbdlxQobuzSourceTest {

    private val apiClient: QbdlxApiClient = mockk()
    private val credentialStore: QbdlxCredentialStore = mockk(relaxUnitFun = true)
    private val rateLimiter: AggregatorRateLimiter = mockk(relaxUnitFun = true)
    private val config: FlacConfig = mockk()

    private fun source() = QbdlxQobuzSource(apiClient, credentialStore, rateLimiter, config)

    private val sid = QbdlxQobuzSource.SOURCE_ID

    private val notBroken =
        RateLimitState(2.0, 0L, isCircuitBroken = false, msUntilUnblock = 0L, recentFailures = 0)

    private val query = TrackQuery(
        artist = "John Frusciante",
        title = "Murderers",
        isrc = "USWB10003085",
        durationMs = 160_000,
    )

    private fun candidate(id: Long = 42) = QbdlxTrack(
        id = id,
        title = "Murderers",
        isrc = "USWB10003085",
        duration = 160,
        streamable = true,
        performer = QbdlxPerformer("John Frusciante"),
        maximumBitDepth = 16,
        maximumSamplingRate = 44.1f,
        album = QbdlxAlbum(QbdlxImage(large = "https://art/large.jpg")),
    )

    private fun ok(url: String = "https://cdn/file?fmt=27") =
        QbdlxResolveResult.Ok(url, codec = "flac", bitDepth = 24, sampleRateHz = 96_000)

    private fun enabledAndAcquired() {
        coEvery { config.qbdlxEnabled() } returns true
        coEvery { config.qobuzEnabled() } returns true
        coEvery { credentialStore.allDead() } returns false
        coEvery { rateLimiter.stateOf(sid) } returns notBroken
        coEvery { rateLimiter.acquire(sid) } returns true
    }

    @Test
    fun `match yields QbdlxResolvedStream with response format`() = runTest {
        enabledAndAcquired()
        coEvery { credentialStore.activeToken() } returns "tok1"
        coEvery { apiClient.search(any(), "tok1") } returns listOf(candidate())
        coEvery { apiClient.getFileUrl(42, 27, "tok1") } returns ok()

        val r = source().resolveImmediate(query)

        assertNotNull(r)
        assertEquals("https://cdn/file?fmt=27", r!!.url)
        assertEquals(0.95f, r.confidence, 0.01f) // ISRC match
        assertEquals("flac", r.codec)
        assertEquals(24, r.bitDepth)
        assertEquals(96_000, r.sampleRateHz)
        assertEquals("42", r.sourceTrackId)
        assertEquals("https://art/large.jpg", r.coverArtUrl)
        coVerify { credentialStore.recordAlive("tok1") }
        coVerify { rateLimiter.reportSuccess(sid) }
    }

    @Test
    fun `TokenDead marks token dead rotates and succeeds on second token`() = runTest {
        enabledAndAcquired()
        coEvery { credentialStore.activeToken() } returnsMany listOf("tok1", "tok2")
        coEvery { apiClient.search(any(), any()) } returns listOf(candidate())
        coEvery { apiClient.getFileUrl(42, 27, "tok1") } returns QbdlxResolveResult.TokenDead
        coEvery { apiClient.getFileUrl(42, 27, "tok2") } returns ok()

        val r = source().resolveImmediate(query)

        assertNotNull(r)
        coVerify { credentialStore.markDead("tok1") }
        coVerify { credentialStore.recordAlive("tok2") }
    }

    @Test
    fun `RegionLocked tries another region token`() = runTest {
        enabledAndAcquired()
        coEvery { credentialStore.activeToken() } returns "tok1"
        coEvery { credentialStore.tokensForRegion(null) } returns listOf("tok1", "tok2")
        coEvery { apiClient.search(any(), any()) } returns listOf(candidate())
        coEvery { apiClient.getFileUrl(42, 27, "tok1") } returns QbdlxResolveResult.RegionLocked
        coEvery { apiClient.getFileUrl(42, 27, "tok2") } returns ok()

        val r = source().resolveImmediate(query)

        assertNotNull(r)
        // RegionLocked is NOT a token-death — tok1 must not be marked dead.
        coVerify(exactly = 0) { credentialStore.markDead("tok1") }
        coVerify { credentialStore.recordAlive("tok2") }
    }

    @Test
    fun `search auth failure marks token dead and rotates without tripping breaker`() = runTest {
        enabledAndAcquired()
        coEvery { credentialStore.activeToken() } returnsMany listOf("tok1", "tok2")
        coEvery { apiClient.search(any(), "tok1") } throws QbdlxAuthException(401)
        coEvery { apiClient.search(any(), "tok2") } returns listOf(candidate())
        coEvery { apiClient.getFileUrl(42, 27, "tok2") } returns ok()

        val r = source().resolveImmediate(query)

        assertNotNull(r)
        coVerify { credentialStore.markDead("tok1") }
        // A dead token is not a source-health failure.
        coVerify(exactly = 0) { rateLimiter.reportFailure(sid) }
    }

    @Test
    fun `whole pool dead disables source and resolve returns null`() = runTest {
        coEvery { config.qbdlxEnabled() } returns true
        coEvery { config.qobuzEnabled() } returns true
        coEvery { credentialStore.allDead() } returns true
        coEvery { rateLimiter.stateOf(sid) } returns notBroken

        assertFalse(source().isEnabledForStreaming())
        assertNull(source().resolveImmediate(query))
        coVerify(exactly = 0) { apiClient.search(any(), any()) }
    }

    @Test
    fun `qobuz disabled disables source and resolve returns null`() = runTest {
        coEvery { config.qbdlxEnabled() } returns true
        coEvery { config.qobuzEnabled() } returns false
        coEvery { credentialStore.allDead() } returns false

        assertFalse(source().isEnabledForStreaming())
        assertNull(source().resolveImmediate(query))
        coVerify(exactly = 0) { apiClient.search(any(), any()) }
    }

    @Test
    fun `resolveImmediate succeeds even when circuit broken and bypasses acquire`() = runTest {
        coEvery { config.qbdlxEnabled() } returns true
        coEvery { config.qobuzEnabled() } returns true
        coEvery { credentialStore.allDead() } returns false
        coEvery { rateLimiter.stateOf(sid) } returns
            RateLimitState(0.0, 0L, isCircuitBroken = true, msUntilUnblock = 60_000L, recentFailures = 5)
        coEvery { credentialStore.activeToken() } returns "tok1"
        coEvery { apiClient.search(any(), "tok1") } returns listOf(candidate())
        coEvery { apiClient.getFileUrl(42, 27, "tok1") } returns ok()

        val r = source().resolveImmediate(query)

        assertNotNull(r)
        coVerify(exactly = 0) { rateLimiter.acquire(any()) }
        coVerify { rateLimiter.reportSuccess(sid) }
    }

    @Test
    fun `disabled toggle blocks streaming gate`() = runTest {
        coEvery { config.qbdlxEnabled() } returns false
        coEvery { credentialStore.allDead() } returns false
        coEvery { rateLimiter.stateOf(sid) } returns notBroken

        assertFalse(source().isEnabledForStreaming())
    }

    @Test
    fun `429 reports rate limited not failure`() = runTest {
        enabledAndAcquired()
        coEvery { credentialStore.activeToken() } returns "tok1"
        coEvery { apiClient.search(any(), "tok1") } throws QbdlxApiException(429, "Too Many Requests")

        val r = source().resolveImmediate(query)

        assertNull(r)
        coVerify { rateLimiter.reportRateLimited(sid) }
        coVerify(exactly = 0) { rateLimiter.reportFailure(sid) }
    }

    @Test
    fun `cancellation propagates and is not swallowed as a failure`() = runTest {
        enabledAndAcquired()
        coEvery { credentialStore.activeToken() } returns "tok1"
        coEvery { apiClient.search(any(), "tok1") } throws kotlinx.coroutines.CancellationException("cancelled")

        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { source().resolveImmediate(query) }
        }
        coVerify(exactly = 0) { rateLimiter.reportFailure(sid) }
    }
}
