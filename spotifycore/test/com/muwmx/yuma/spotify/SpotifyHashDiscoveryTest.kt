package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SpotifyHashDiscoveryTest {
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)
    private val hashC = "c".repeat(64)

    private fun base64(json: String) = Base64.getEncoder().encodeToString(json.toByteArray())

    private fun page(
        buildVersion: String = "web-player_2026-10-23_1",
        bundles: List<String> = emptyList(),
        cdnFiles: List<String> = emptyList(),
    ) = buildString {
        append("<html><head>")
        append("""<script id="appServerConfig" type="text/plain">${base64("""{"buildVersion":"$buildVersion"}""")}</script>""")
        if (cdnFiles.isNotEmpty()) {
            // The page lists its files as a map from a file name to its address.
            val files = cdnFiles.withIndex().joinToString(",") { (index, url) -> """"file$index":"$url"""" }
            append("""<script id="__CDN_FILE_URLS__" type="text/plain">${base64("{$files}")}</script>""")
        }
        bundles.forEach { append("""<script src="$it" defer></script>""") }
        append("</head></html>")
    }

    private fun bundleUrl(name: String) = "https://open.spotifycdn.com/cdn/build/web-player/$name.js"

    private fun bundleWith(vararg operations: Pair<String, String>) =
        operations.joinToString(";") { (name, hash) -> """var x=new d.Ob("$name","query","$hash",null)""" }

    @Test
    fun readsTheBuildVersionFromTheBase64Config() {
        assertEquals("web-player_2026-10-23_1", SpotifyHashDiscovery.parseBuildVersion(page()))
    }

    @Test
    fun readsTheBuildVersionFromPlainJsonToo() {
        val html = """<script id="appServerConfig">{"buildVersion":"plain-1"}</script>"""

        assertEquals("plain-1", SpotifyHashDiscovery.parseBuildVersion(html))
    }

    @Test
    fun buildVersionIsNullWhenThePageHasNoConfig() {
        assertNull(SpotifyHashDiscovery.parseBuildVersion("<html></html>"))
    }

    @Test
    fun findsBundlesInLinksAndInTheCdnFileList() {
        val linked = bundleUrl("main")
        val listed = bundleUrl("lazy")
        val html = page(bundles = listOf(linked), cdnFiles = listOf(listed, "https://open.spotifycdn.com/other/ignored.js", "$linked.css"))

        assertEquals(setOf(linked, listed), SpotifyHashDiscovery.parseBundleUrls(html))
    }

    @Test
    fun readsOperationsOfAnyKind() {
        val bundle =
            """
            var a=new e.Ob("fetchPlaylist","query","$hashA",null);
            var b=new Ob("addToPlaylist","mutation","$hashB",null);
            var c=new X("notAHash","query","tooshort",null);
            var d=new X("mismatch","other","$hashC",null);
            """.trimIndent()

        assertEquals(mapOf("fetchPlaylist" to hashA, "addToPlaylist" to hashB), SpotifyHashDiscovery.parseOperations(bundle))
    }

    @Test
    fun collectsTheOperationsOfEveryBundle() =
        runBlocking {
            val pages =
                mapOf(
                    SpotifyHashDiscovery.WEB_PLAYER_URL to page(bundles = listOf(bundleUrl("one"), bundleUrl("two"))),
                    bundleUrl("one") to bundleWith("fetchPlaylist" to hashA),
                    bundleUrl("two") to bundleWith("libraryV3" to hashB),
                )

            val table = SpotifyHashDiscovery({ url -> pages.getValue(url) }).discover()

            assertEquals(mapOf("fetchPlaylist" to hashA, "libraryV3" to hashB), table?.hashes)
            assertEquals("web-player_2026-10-23_1", table?.buildVersion)
            assertTrue(table?.complete == true)
        }

    @Test
    fun aBundleThatCannotBeDownloadedMakesTheTableIncompleteButKeepsTheRest() =
        runBlocking {
            val pages =
                mapOf(
                    SpotifyHashDiscovery.WEB_PLAYER_URL to page(bundles = listOf(bundleUrl("one"), bundleUrl("two"))),
                    bundleUrl("one") to bundleWith("fetchPlaylist" to hashA),
                )

            val table =
                SpotifyHashDiscovery({ url -> pages[url] ?: throw java.io.IOException("offline") }).discover()

            assertEquals(mapOf("fetchPlaylist" to hashA), table?.hashes)
            assertFalse(table?.complete == true)
        }

    @Test
    fun aPageWithoutBundlesGivesNothing() =
        runBlocking {
            assertNull(SpotifyHashDiscovery({ "<html>Access denied</html>" }).discover())
        }
}
