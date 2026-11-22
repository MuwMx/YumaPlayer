/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.streaming

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlacStreamRegistryTest {

    private val query = TrackQuery(artist = "Artist", title = "Title", durationMs = 1000)
    private val quality = 27

    @Test
    fun `qbdlx resolves successfully`() = runTest {
        val expected = FlacStreamUrl("url", 0, origin = "qbdlx")

        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> expected },
        )

        val result = registry.resolve(query, quality)
        assertEquals(expected, result)
    }

    @Test
    fun `monochrome prioritized when providerOrder specifies monochrome first`() = runTest {
        val monoUrl = FlacStreamUrl("mono_url", 0, origin = "monochrome")
        val qbdlxUrl = FlacStreamUrl("qbdlx_url", 0, origin = "qbdlx")

        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> qbdlxUrl },
            monochrome = { _, _ -> monoUrl },
            providerOrder = { listOf("monochrome", "qbdlx") },
        )

        val result = registry.resolve(query, quality)
        assertEquals("monochrome", result?.origin)
    }

    @Test
    fun `qbdlx prioritized when providerOrder specifies qbdlx first`() = runTest {
        val monoUrl = FlacStreamUrl("mono_url", 0, origin = "monochrome")
        val qbdlxUrl = FlacStreamUrl("qbdlx_url", 0, origin = "qbdlx")

        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> qbdlxUrl },
            monochrome = { _, _ -> monoUrl },
            providerOrder = { listOf("qbdlx", "monochrome") },
        )

        val result = registry.resolve(query, quality)
        assertEquals("qbdlx", result?.origin)
    }

    @Test
    fun `falls back to second provider when first returns null`() = runTest {
        val qbdlxUrl = FlacStreamUrl("qbdlx_url", 0, origin = "qbdlx")

        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> qbdlxUrl },
            monochrome = { _, _ -> null },
            providerOrder = { listOf("monochrome", "qbdlx") },
        )

        val result = registry.resolve(query, quality)
        assertEquals("qbdlx", result?.origin)
    }

    @Test
    fun `returns null if all return null`() = runTest {
        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> null },
            monochrome = { _, _ -> null },
        )

        val result = registry.resolve(query, quality)
        assertNull(result)
    }

    @Test
    fun `returns null if qbdlx times out`() = runTest {
        val registry = FlacStreamRegistry(
            qbdlx = { _, _ ->
                delay(200)
                FlacStreamUrl("qbdlx", 0, origin = "qbdlx")
            },
        ).apply { timeoutMs = 100L }

        val result = registry.resolve(query, quality)
        assertNull(result)
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException is propagated`() = runTest {
        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> throw CancellationException("test") },
        )

        registry.resolve(query, quality)
    }
}
