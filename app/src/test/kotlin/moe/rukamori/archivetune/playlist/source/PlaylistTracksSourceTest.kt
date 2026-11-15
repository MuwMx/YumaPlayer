package moe.rukamori.archivetune.playlist.source

import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTracksSourceTest {
    @Test
    fun `room source exposes the full capability set`() {
        val caps = PlaylistCapabilities.Room
        assertTrue(caps.editable)
        assertTrue(caps.reorderable)
        assertTrue(caps.sortable)
        assertTrue(caps.swipeDelete)
        assertTrue(caps.supportsDownload)
        assertFalse(caps.paginated)
    }

    @Test
    fun `innertube source is paginated and downloadable but not editable`() {
        val caps = PlaylistCapabilities.InnerTube
        assertTrue(caps.paginated)
        assertTrue(caps.supportsDownload)
        assertFalse(caps.editable)
        assertFalse(caps.reorderable)
        assertFalse(caps.sortable)
        assertFalse(caps.swipeDelete)
    }

    @Test
    fun `spotify source is paginated only`() {
        val caps = PlaylistCapabilities.Spotify
        assertTrue(caps.paginated)
        assertFalse(caps.editable)
        assertFalse(caps.sortable)
        assertFalse(caps.supportsDownload)
    }

    @Test
    fun `auto source sorts and downloads but never reorders`() {
        val caps = PlaylistCapabilities.Auto
        assertTrue(caps.sortable)
        assertTrue(caps.supportsDownload)
        assertFalse(caps.reorderable)
        assertFalse(caps.swipeDelete)
        assertFalse(caps.paginated)
    }

    @Test
    fun `cache sources are fully read-only`() {
        val caps = PlaylistCapabilities.ReadOnly
        assertFalse(caps.editable)
        assertFalse(caps.reorderable)
        assertFalse(caps.sortable)
        assertFalse(caps.paginated)
        assertFalse(caps.swipeDelete)
        assertFalse(caps.supportsDownload)
    }

    @Test
    fun `top source is read-only but downloadable`() {
        val caps = PlaylistCapabilities.Top
        assertFalse(caps.editable)
        assertFalse(caps.reorderable)
        assertFalse(caps.sortable)
        assertFalse(caps.paginated)
        assertFalse(caps.swipeDelete)
        // The legacy Top hero ships a download button (sendAddMissingDownloads
        // over Room songs); the plan's original matrix missed it.
        assertTrue(caps.supportsDownload)
        // Rows carry their 1-based rank (TopPlaylistScreen.kt:415).
        assertTrue(caps.showsTrackNumbers)
    }

    @Test
    fun `playlistTrack wraps the origin item instead of flattening it`() {
        val songItem =
            SongItem(
                id = "v1",
                title = "Song",
                artists = listOf(Artist(name = " Artist ", id = null)),
                album = Album(name = "A", id = "alb"),
                duration = 61,
                thumbnail = "t.jpg",
                explicit = true,
            )
        val track = songItem.toPlaylistTrack()
        assertEquals("v1", track.id)
        assertEquals("Song", track.title)
        assertEquals(listOf(" Artist "), track.artists)
        assertEquals(61_000L, track.durationMs)
        assertEquals("t.jpg", track.thumbnailUrl)
        assertTrue(track.explicit)
        // The wrapper contract: origin keeps the typed item.
        assertSame(songItem, track.origin)
    }

    @Test
    fun `song mapper reads room fields`() {
        val song =
            Song(
                song =
                    SongEntity(
                        id = "s1",
                        title = "Title",
                        duration = 30,
                        thumbnailUrl = "u.jpg",
                        explicit = false,
                    ),
                artists = emptyList(),
            )
        val track = song.toPlaylistTrack()
        assertEquals("s1", track.id)
        assertEquals(30_000L, track.durationMs)
        assertSame(song, track.origin)
    }

    @Test
    fun `spotify mapper reads album art and clamps zero durations`() {
        val track =
            SpotifyTrack(
                id = "sp1",
                name = "Track",
                artists = listOf(SpotifySimpleArtist(name = "Artist")),
                album = SpotifySimpleAlbum(name = "Album"),
                durationMs = 0,
            ).toPlaylistTrack()
        assertEquals("sp1", track.id)
        // Zero duration means unknown in the Spotify model; never map it to 0ms.
        assertNull(track.durationMs)
        assertNull(track.thumbnailUrl) // SpotifySimpleAlbum with no images
    }

    @Test
    fun `unsupported operations on read-only sources fail instead of throwing`() =
        kotlinx.coroutines.test.runTest {
            val database: moe.rukamori.archivetune.db.MusicDatabase = io.mockk.mockk(relaxed = true)
            val source = TopPlaylistSource("10", database)
            assertTrue(source.reorder(0, 1).isFailure)
            assertTrue(source.removeSong(0).isFailure)
            assertTrue(source.setSort(PlaylistSongSortType.NAME, false).isFailure)
            // loadMore/refresh default to success no-ops.
            assertTrue(source.loadMore().isSuccess)
        }

    @Test
    fun `setSort on read-only sources never crashes and stays a failure`() =
        kotlinx.coroutines.test.runTest {
            val database: moe.rukamori.archivetune.db.MusicDatabase = io.mockk.mockk(relaxed = true)
            val cacheSource =
                CachePlaylistSource(
                    context = io.mockk.mockk(relaxed = true),
                    database = database,
                    downloadUtil = io.mockk.mockk(relaxed = true),
                    playerCache = io.mockk.mockk(relaxed = true),
                    downloadCache = io.mockk.mockk(relaxed = true),
                )
            assertTrue(cacheSource.setSort(PlaylistSongSortType.NAME, true).isFailure)
        }
}
