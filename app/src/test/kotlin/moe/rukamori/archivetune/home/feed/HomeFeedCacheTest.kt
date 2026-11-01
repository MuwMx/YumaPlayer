/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.feed

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeFeedCacheTest {

    private val context: Context = mockk()
    private val sharedPrefs: SharedPreferences = mockk()
    private val editor: SharedPreferences.Editor = mockk()
    private val storage = mutableMapOf<String, Any?>()

    private lateinit var cache: HomeFeedCache

    @Before
    fun setUp() {
        storage.clear()

        every { context.getSharedPreferences("home_feed_cache", Context.MODE_PRIVATE) } returns sharedPrefs
        every { sharedPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            storage[key] as? String ?: default
        }
        every { sharedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            storage[key] = value
            editor
        }
        every { editor.remove(any()) } answers {
            val key = firstArg<String>()
            storage.remove(key)
            editor
        }
        every { editor.apply() } answers { }

        cache = HomeFeedCache(context)
    }

    private fun createSampleHomePage(): HomePage {
        val songItem = SongItem(
            id = "song_1",
            title = "Test Song",
            artists = listOf(Artist("Artist 1", "id_1")),
            album = Album("Album 1", "id_album"),
            duration = 180,
            thumbnail = "https://thumb/1",
            explicit = false,
            endpoint = WatchEndpoint(videoId = "song_1"),
        )
        val albumItem = AlbumItem(
            browseId = "browse_1",
            playlistId = "pl_1",
            id = "browse_1",
            title = "Test Album",
            artists = listOf(Artist("Artist 1", "id_1")),
            year = 2026,
            thumbnail = "https://thumb/album",
            explicit = false,
            releaseType = AlbumReleaseType.ALBUM,
        )
        val playlistItem = PlaylistItem(
            id = "pl_2",
            title = "Test Playlist",
            author = Artist("Curator", null),
            songCountText = "20 songs",
            thumbnail = "https://thumb/pl",
            playEndpoint = WatchEndpoint(playlistId = "pl_2"),
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        return HomePage(
            chips = listOf(
                HomePage.Chip(
                    title = "Relax",
                    endpoint = BrowseEndpoint(browseId = "FEbrowse", params = "param_relax"),
                    deselectEndPoint = null,
                ),
            ),
            sections = listOf(
                HomePage.Section(
                    title = "Mixed Shelf",
                    label = "Fresh",
                    thumbnail = null,
                    endpoint = null,
                    items = listOf(songItem, albumItem, playlistItem),
                ),
            ),
            continuation = "cont_token_123",
        )
    }

    @Test
    fun loadHomePage_whenEmpty_returnsNull() = runBlocking {
        val result = cache.loadHomePage()
        assertNull(result)
    }

    @Test
    fun saveHomePage_and_loadHomePage_roundTrip_preservesContent() = runBlocking {
        val sample = createSampleHomePage()
        cache.saveHomePage(sample)

        assertTrue(storage.containsKey("cached_home_page"))
        val restored = cache.loadHomePage()

        assertNotNull(restored)
        assertEquals(sample.chips?.size, restored?.chips?.size)
        assertEquals(sample.chips?.first()?.title, restored?.chips?.first()?.title)
        assertEquals(sample.sections.size, restored?.sections?.size)
        assertEquals(sample.sections.first().title, restored?.sections?.first()?.title)
        assertEquals(sample.sections.first().items.size, restored?.sections?.first()?.items?.size)
        assertEquals(sample.continuation, restored?.continuation)
    }

    @Test
    fun loadHomePage_corruptJson_clearsDamagedEntry_and_returnsNull() = runBlocking {
        storage["cached_home_page"] = "{ corrupt json payload: invalid "

        val result = cache.loadHomePage()

        assertNull(result)
        assertNull(storage["cached_home_page"])
    }

    @Test
    fun clear_removesCachedEntry() = runBlocking {
        val sample = createSampleHomePage()
        cache.saveHomePage(sample)
        assertTrue(storage.containsKey("cached_home_page"))

        cache.clear()

        assertNull(storage["cached_home_page"])
        assertNull(cache.loadHomePage())
    }
}
