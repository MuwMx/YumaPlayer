/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.monochrome

import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.flaccore.FlacKvStore
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonochromeSourceTest {

    private class InMemoryKvStore : FlacKvStore {
        private val map = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    @Test
    fun `parse streamer search response`() {
        val json = """
            {
                "tracks": [
                    {
                        "trackId": "12345",
                        "title": "Starboy",
                        "artistNames": ["The Weeknd", "Daft Punk"],
                        "albumTitle": "Starboy",
                        "duration": 230000,
                        "isrc": "USUG11601000",
                        "playable": true
                    }
                ]
            }
        """.trimIndent()

        val kv = InMemoryKvStore()
        val servers = MonochromeServers(kv)
        val client = MonoHttpClient { _, _ -> MonoHttpResponse(200, "") }
        val source = MonochromeSource(servers, client)

        val tracks = source.streamerTracksIn(json)
        assertEquals(1, tracks.size)
        assertEquals("12345", tracks[0].id)
        assertEquals("Starboy", tracks[0].title)
        assertEquals("The Weeknd, Daft Punk", tracks[0].artist)
        assertEquals("USUG11601000", tracks[0].isrc)
    }

    @Test
    fun `parse relay search response`() {
        val json = """
            {
                "data": [
                    {
                        "id": "rel_99",
                        "title": "Levitating",
                        "artist": "Dua Lipa&DaBaby",
                        "album": "Future Nostalgia",
                        "duration": 203,
                        "isrc": "GBAHT2000244"
                    }
                ]
            }
        """.trimIndent()

        val kv = InMemoryKvStore()
        val servers = MonochromeServers(kv)
        val client = MonoHttpClient { _, _ -> MonoHttpResponse(200, "") }
        val source = MonochromeSource(servers, client)

        val tracks = source.relayTracksIn(json)
        assertEquals(1, tracks.size)
        assertEquals("rel_99", tracks[0].id)
        assertEquals("Levitating", tracks[0].title)
        assertEquals("Dua Lipa, DaBaby", tracks[0].artist)
        assertEquals(203000L, tracks[0].durationMs)
    }

    @Test
    fun `miss cache prevents repeated requests within TTL`() = runTest {
        val kv = InMemoryKvStore()
        val servers = MonochromeServers(kv)
        var callCount = 0
        val client = MonoHttpClient { _, _ ->
            callCount++
            MonoHttpResponse(200, """{"tracks":[]}""")
        }
        val source = MonochromeSource(servers, client)
        val query = TrackQuery(artist = "Nonexistent", title = "Nothing")

        val res1 = source.resolve(query)
        assertTrue(res1 is MonochromeResolveResult.NotFound)
        assertEquals(1, callCount)

        val res2 = source.resolve(query)
        assertTrue(res2 is MonochromeResolveResult.NotFound)
        assertEquals(1, callCount)
    }
}
