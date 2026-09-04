package moe.rukamori.archivetune.flaccore.qbdlx

import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.flaccore.FlacConfig
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class QbdlxApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: QbdlxApiClient

    private val fakeConfig = object : FlacConfig {
        override suspend fun qbdlxEnabled() = true
        override suspend fun qbdlxAppId() = "798273057"
        override suspend fun qbdlxAppSecret() = "secret"
        override suspend fun qbdlxTokenPool() = ""
    }

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        client = QbdlxApiClient(
            config = fakeConfig,
            sharedClient = OkHttpClient(),
            signer = QbdlxSigner { 1000L },
            // Every token in these tests signs under the same test pair; getFileUrl
            // reads app_id/secret from here (the real store resolves per-token).
            signingResolver = object : QbdlxSigningResolver {
                override suspend fun signingFor(token: String) = QbdlxSigning(appId = "798273057", appSecret = "secret")
            }
        ).also {
            it.baseUrl = server.url("/").toString().trimEnd('/')
            it.appId = "798273057"   // appId is an internal var (reads FlacConfig in prod), set here for the test
        }
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `search parses track items`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tracks":{"items":[{"id":42,"title":"Murderers","isrc":"USWB10003085","duration":160,"performer":{"name":"John Frusciante"},"maximum_bit_depth":16,"maximum_sampling_rate":44.1}]}}"""))
        val items = client.search("John Frusciante Murderers", token = "tok")
        assertEquals(1, items.size)
        assertEquals(42L, items[0].id)
        assertEquals("tok", server.takeRequest().getHeader("X-User-Auth-Token"))
    }

    /**
     * REGRESSION (live-probed 2026-08-15): the shared pool mixes tokens minted under
     * two Qobuz apps, and a token authenticates ONLY against its own app_id. Every
     * catalog call used to send the client-wide primary, so the 312369995 tokens —
     * two thirds of the pool — 401'd on `search` and were marked dead before signing
     * ever ran. 2 of 18 tokens worked that way; 12 of 18 work with their own app_id.
     */
    @Test fun `catalog calls send the token's own app_id, not the client default`() = runTest {
        val client = QbdlxApiClient(
            config = fakeConfig,
            sharedClient = OkHttpClient(),
            signer = QbdlxSigner { 1000L },
            signingResolver = object : QbdlxSigningResolver {
                override suspend fun signingFor(token: String) = QbdlxSigning(appId = "312369995", appSecret = "other-secret")
            }
        ).also {
            it.baseUrl = server.url("/").toString().trimEnd('/')
            it.appId = "798273057"
        }
        server.enqueue(MockResponse().setBody("""{"tracks":{"items":[]}}"""))

        client.search("anything", token = "second-app-token")

        val req = server.takeRequest()
        assertEquals("312369995", req.getHeader("X-App-Id"))
        assertTrue(req.path!!.contains("app_id=312369995"))
        assertTrue(!req.path!!.contains("798273057"))
    }

    @Test fun `getFileUrl Ok when url present and not restricted`() = runTest {
        server.enqueue(MockResponse().setBody("""{"url":"https://cdn/file?fmt=6","format_id":6,"bit_depth":16,"sampling_rate":44.1,"sample":false,"restrictions":[]}"""))
        val r = client.getFileUrl(trackId = 42, formatId = 27, token = "tok")
        assertTrue(r is QbdlxResolveResult.Ok)
        val ok = r as QbdlxResolveResult.Ok
        assertTrue(ok.url.contains("cdn/file"))
        assertEquals(16, ok.bitDepth)
        val req = server.takeRequest()
        assertEquals("798273057", req.getHeader("X-App-Id"))
        assertTrue(req.path!!.contains("request_sig="))
    }

    @Test fun `getFileUrl TokenDead on UserUnauthenticated preview`() = runTest {
        server.enqueue(MockResponse().setBody("""{"url":"https://cdn/file?fmt=5&range=20-30","format_id":5,"sample":true,"restrictions":[{"code":"UserUnauthenticated"}]}"""))
        val r = client.getFileUrl(trackId = 42, formatId = 27, token = "tok")
        assertTrue(r is QbdlxResolveResult.TokenDead)
    }

    @Test fun `getFileUrl RegionLocked when restricted with no usable url`() = runTest {
        server.enqueue(MockResponse().setBody("""{"format_id":6,"restrictions":[{"code":"TrackRestrictedByRights"}]}"""))
        val r = client.getFileUrl(trackId = 42, formatId = 27, token = "tok")
        assertTrue(r is QbdlxResolveResult.RegionLocked)
    }

    @Test fun `getFileUrl accepts format-downgrade to CD FLAC`() = runTest {
        server.enqueue(MockResponse().setBody("""{"url":"https://cdn/file?fmt=6","format_id":6,"restrictions":[{"code":"FormatRestrictedByFormatAvailability"}]}"""))
        val r = client.getFileUrl(trackId = 42, formatId = 27, token = "tok")
        assertTrue(r is QbdlxResolveResult.Ok)  // fmt6 is still lossless
    }

    /**
     * A banned Qobuz account answers 403 USER_BLOCKED. That is a dead TOKEN, not a
     * sick service: it must surface as QbdlxAuthException so the source marks it
     * dead and rotates. Treated as a generic failure it never rotates, and because
     * the active token is sticky one banned account silently downgrades every play
     * to lossy YouTube while live tokens sit unused.
     */
    @Test fun `403 USER_BLOCKED is a dead token, not a service failure`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"status":"error","code":403,"message":"Account is blocked","error_code":"USER_BLOCKED"}"""
            )
        )
        try {
            client.search("x", token = "tok")
            fail("Expected QbdlxAuthException")
        } catch (e: QbdlxAuthException) {
            assertEquals(403, e.status)
        }
    }

    /** A 403 that is NOT a ban stays transient — don't burn a good token on it. */
    @Test fun `other 403s remain transient api errors`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"status":"error","code":403,"message":"Rate limited"}""")
        )
        try {
            client.search("x", token = "tok")
            fail("Expected QbdlxApiException")
        } catch (e: QbdlxApiException) {
            assertEquals(403, e.status)
        }
    }

    @Test fun `search 401 throws TokenDead-signalling exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"status":"error","code":401}"""))
        try { client.search("x", token = "tok"); fail("Expected QbdlxAuthException") }
        catch (e: QbdlxAuthException) { assertEquals(401, e.status) }
    }
}
