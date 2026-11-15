package moe.rukamori.archivetune.spotify.mapping

import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyEntityMapperTest {

    @Test
    fun getBestImageRanksByExplicitDimensionsWhenPresent() {
        val small = SpotifyImage(url = "https://example.com/small.jpg", width = 64, height = 64)
        val medium = SpotifyImage(url = "https://example.com/medium.jpg", width = 300, height = 300)
        val large = SpotifyImage(url = "https://example.com/large.jpg", width = 640, height = 640)

        // Unordered list
        val images = listOf(medium, small, large)
        val best = SpotifyEntityMapper.getBestImage(images)

        assertEquals(large.url, best?.url)
    }

    @Test
    fun getBestImageInfersResolutionFromCdnTokensWhenDimensionsMissing() {
        val lowRes = SpotifyImage(url = "https://i.scdn.co/image/ab67616d00004851low")
        val midRes = SpotifyImage(url = "https://i.scdn.co/image/ab67616d00001e02mid")
        val highResB273 = SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b273high")
        val highRes0003 = SpotifyImage(url = "https://i.scdn.co/image/ab67616d00000003high2")

        val list1 = listOf(lowRes, highResB273, midRes)
        assertEquals(highResB273.url, SpotifyEntityMapper.getBestImage(list1)?.url)

        val list2 = listOf(lowRes, midRes, highRes0003)
        assertEquals(highRes0003.url, SpotifyEntityMapper.getBestImage(list2)?.url)
    }

    @Test
    fun getBestImagePrefersHighResTokenOverExplicitSmallDimension() {
        val explicitSmall = SpotifyImage(url = "https://example.com/explicit_small.jpg", width = 120, height = 120)
        val cdnLarge = SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b273token", width = null, height = null)

        val images = listOf(explicitSmall, cdnLarge)
        val best = SpotifyEntityMapper.getBestImage(images)

        assertEquals(cdnLarge.url, best?.url)
    }

    @Test
    fun getBestImageReturnsNullWhenEmptyOrBlank() {
        assertNull(SpotifyEntityMapper.getBestImage(emptyList()))
        assertNull(SpotifyEntityMapper.getBestImage(listOf(SpotifyImage(url = "   "))))
    }

    @Test
    fun getAlbumAndPlaylistThumbnailUseBestImage() {
        val small = SpotifyImage(url = "https://i.scdn.co/image/ab67616d00004851small")
        val large = SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b273large")

        val album = SpotifyAlbum(
            id = "album1",
            name = "Test Album",
            images = listOf(small, large),
        )
        val playlist = SpotifyPlaylist(
            id = "pl1",
            name = "Test Playlist",
            images = listOf(small, large),
        )
        val artist = moe.rukamori.archivetune.spotify.models.SpotifyArtist(
            id = "art1",
            name = "Test Artist",
            images = listOf(small, large),
        )

        assertEquals(large.url, SpotifyEntityMapper.getAlbumThumbnail(album))
        assertEquals(large.url, SpotifyEntityMapper.getPlaylistThumbnail(playlist))
        assertEquals(large.url, SpotifyEntityMapper.getArtistThumbnail(artist))
    }
}
