package moe.rukamori.archivetune.playback.resolvers

import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.flaccore.streaming.FlacStreamRegistry
import moe.rukamori.archivetune.flaccore.streaming.FlacStreamUrl
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamSourceRegistryOrderTest {

    @Test
    fun `registry resolves qbdlx`() = runBlocking {
        val expectedUrl = FlacStreamUrl(
            url = "http://qbdlx.test",
            expiresAtMs = 0L,
            origin = "qbdlx"
        )

        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> expectedUrl }
        )

        val query = TrackQuery(artist = "A", title = "T", durationMs = 1000L)
        val result = registry.resolve(query, 7)

        assertEquals(expectedUrl, result)
    }
}
