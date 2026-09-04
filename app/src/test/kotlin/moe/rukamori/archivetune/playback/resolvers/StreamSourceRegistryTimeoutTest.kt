package moe.rukamori.archivetune.playback.resolvers

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.flaccore.streaming.FlacStreamRegistry
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSourceRegistryTimeoutTest {

    @Test
    fun `registry returns null when qbdlx times out`() = runBlocking {
        val registry = FlacStreamRegistry(
            qbdlx = { _, _ -> 
                delay(40_000) // FlacStreamRegistry has 35s timeout
                null 
            }
        ).apply { timeoutMs = 100L }

        val query = TrackQuery(artist = "A", title = "T", durationMs = 1000L)
        val result = registry.resolve(query, 7)

        assertNull(result)
    }
}
