package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import moe.rukamori.archivetune.spotify.net.SpotifyGqlPayloadBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.Base64

class SpotifyHashRefresherTest {
    private val liveHash = "1".repeat(64)
    private val otherHash = "2".repeat(64)
    private lateinit var store: File
    private var clock = 1_000_000L
    private var fetches = 0
    private var offline = false

    @Before
    fun setUp() {
        store = File.createTempFile("gql-hashes", ".json").also { it.delete() }
    }

    @After
    fun tearDown() {
        store.delete()
        restoreShippedHashes()
    }

    private fun restoreShippedHashes() {
        SpotifyHashProvider.updateHashes(
            SpotifyHashProvider.getAll().mapValues { (operation, _) ->
                SpotifyHashProvider.RemoteHashEntry(hash = SpotifyHashProvider.hardcodedHash(operation)!!)
            },
            SpotifyHashProvider.HashSource.HARDCODED,
        )
    }

    private fun page(vararg operations: Pair<String, String>): suspend (String) -> String =
        { url ->
            fetches++
            if (offline) throw IOException("offline")
            when (url) {
                SpotifyHashDiscovery.WEB_PLAYER_URL -> {
                    val config = Base64.getEncoder().encodeToString("""{"buildVersion":"b1"}""".toByteArray())
                    """<script id="appServerConfig">$config</script><script src="https://open.spotifycdn.com/cdn/build/web-player/m.js"></script>"""
                }
                else -> operations.joinToString(";") { (name, hash) -> """new d.Ob("$name","query","$hash",null)""" }
            }
        }

    private fun refresher(vararg operations: Pair<String, String>) = SpotifyHashRefresher(SpotifyHashDiscovery(page(*operations)), store) { clock }

    @Test
    fun returnsTheHashTheWebPlayerUsesToday() =
        runBlocking {
            val refresher = refresher("fetchPlaylist" to liveHash)

            assertEquals(liveHash, refresher.liveHash("fetchPlaylist", rejected = setOf("old")))
        }

    @Test
    fun doesNotReturnAHashThatWasAlreadyRejected() =
        runBlocking {
            val refresher = refresher("fetchPlaylist" to liveHash)

            assertNull(refresher.liveHash("fetchPlaylist", rejected = setOf(liveHash)))
        }

    @Test
    fun anOperationTheWebPlayerDoesNotKnowGivesNothing() =
        runBlocking {
            assertNull(refresher("fetchPlaylist" to liveHash).liveHash("libraryV3", rejected = emptySet()))
        }

    @Test
    fun readsTheWebPlayerOnceForSeveralLookups() =
        runBlocking {
            val refresher = refresher("fetchPlaylist" to liveHash, "libraryV3" to otherHash)

            refresher.liveHash("fetchPlaylist", emptySet())
            val afterFirst = fetches
            refresher.liveHash("libraryV3", emptySet())

            assertEquals(afterFirst, fetches)
        }

    @Test
    fun readsTheWebPlayerAgainOnceTheTableIsStale() =
        runBlocking {
            val refresher = refresher("fetchPlaylist" to liveHash)
            refresher.liveHash("fetchPlaylist", emptySet())
            val afterFirst = fetches

            clock += 31 * 60 * 1000L
            refresher.liveHash("fetchPlaylist", emptySet())

            assertEquals(afterFirst * 2, fetches)
        }

    @Test
    fun doesNotHammerTheWebPlayerWhileItCannotBeReached() =
        runBlocking {
            offline = true
            val refresher = refresher("fetchPlaylist" to liveHash)

            assertNull(refresher.liveHash("fetchPlaylist", emptySet()))
            val afterFirst = fetches
            assertNull(refresher.liveHash("fetchPlaylist", emptySet()))
            assertEquals(afterFirst, fetches)

            clock += 6 * 60 * 1000L
            offline = false
            assertEquals(liveHash, refresher.liveHash("fetchPlaylist", emptySet()))
        }

    @Test
    fun aLearnedHashIsAppliedOnTheNextStart() {
        val shipped = SpotifyHashProvider.hardcodedHash("fetchPlaylist")!!
        refresher().learn("fetchPlaylist", liveHash)

        refresher().applyLearned()

        val entry = SpotifyHashProvider.getAll().getValue("fetchPlaylist")
        assertEquals(liveHash, entry.hash)
        // The hash that shipped with the app stays available as the fallback.
        assertEquals(shipped, entry.previousHash)
        assertEquals(listOf(liveHash, shipped), SpotifyGqlPayloadBuilder.resolveHashCandidates("fetchPlaylist"))
    }

    @Test
    fun aLearnedHashIsDroppedWhenTheAppShipsADifferentOneNow() {
        val shipped = SpotifyHashProvider.hardcodedHash("fetchPlaylist")!!
        store.writeText("""{"fetchPlaylist":{"hash":"$liveHash","base":"${"9".repeat(64)}"}}""")

        refresher().applyLearned()

        assertEquals(shipped, SpotifyHashProvider.getHash("fetchPlaylist"))
    }

    @Test
    fun aDamagedStoreIsIgnored() {
        val shipped = SpotifyHashProvider.hardcodedHash("fetchPlaylist")!!
        store.writeText("not json")

        refresher().applyLearned()

        assertEquals(shipped, SpotifyHashProvider.getHash("fetchPlaylist"))
    }

    @Test
    fun learningOneOperationKeepsTheOthers() {
        refresher().learn("fetchPlaylist", liveHash)
        refresher().learn("libraryV3", otherHash)

        refresher().applyLearned()

        assertEquals(liveHash, SpotifyHashProvider.getHash("fetchPlaylist"))
        assertEquals(otherHash, SpotifyHashProvider.getHash("libraryV3"))
    }

    @Test
    fun learnedHashesAreAppliedOnceBeforeTheFirstRequest() =
        runBlocking {
            refresher().learn("fetchPlaylist", liveHash)
            val refresher = refresher()

            refresher.applyLearnedOnce()
            // a later change to the store is not read again within the session
            store.writeText("not json")
            refresher.applyLearnedOnce()

            assertEquals(liveHash, SpotifyHashProvider.getHash("fetchPlaylist"))
        }

    @Test
    fun onlyAnAnswerWithDataProvesAHashWorks() {
        val json = { text: String -> Json.parseToJsonElement(text).jsonObject }

        assertTrue(SpotifyHashRefresher.isWorkingAnswer(json("""{"data":{"playlistV2":{}}}""")))
        assertFalse(SpotifyHashRefresher.isWorkingAnswer(null))
        assertFalse(SpotifyHashRefresher.isWorkingAnswer(json("""{}""")))
        assertFalse(SpotifyHashRefresher.isWorkingAnswer(json("""{"data":null}""")))
        assertFalse(SpotifyHashRefresher.isWorkingAnswer(json("""{"data":{},"errors":[{"message":"Something broke"}]}""")))
    }
}
