/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.pages

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePageSerializationTest {
    @Test
    fun realisticHomePage_roundTripSerialization_preservesAllFieldsAndPolymorphicTypes() {
        val songItem =
            SongItem(
                id = "song_id_1",
                title = "Song Title",
                artists = listOf(Artist(name = "Artist Name", id = "artist_id_1")),
                album = Album(name = "Album Name", id = "album_id_1"),
                duration = 215,
                chartPosition = 1,
                chartChange = "UP",
                thumbnail = "https://example.com/song.jpg",
                explicit = true,
                endpoint =
                    WatchEndpoint(
                        videoId = "song_id_1",
                        playlistId = "playlist_1",
                        playlistSetVideoId = "set_vid_1",
                        params = "watch_params",
                        index = 0,
                        watchEndpointMusicSupportedConfigs =
                            WatchEndpoint.WatchEndpointMusicSupportedConfigs(
                                WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig(
                                    musicVideoType =
                                        WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_OMV,
                                ),
                            ),
                    ),
                setVideoId = "set_video_id_1",
                viewCountText = "1.2M views",
                viewCount = 1200000L,
            )

        val albumItem =
            AlbumItem(
                browseId = "album_browse_1",
                playlistId = "album_playlist_1",
                id = "album_browse_1",
                title = "Album Title",
                artists = listOf(Artist(name = "Artist Name", id = "artist_id_1")),
                year = 2026,
                thumbnail = "https://example.com/album.jpg",
                explicit = false,
                releaseType = AlbumReleaseType.EP,
            )

        val playlistItem =
            PlaylistItem(
                id = "playlist_id_1",
                title = "Playlist Title",
                author = Artist(name = "Curator", id = null),
                songCountText = "50 songs",
                thumbnail = "https://example.com/playlist.jpg",
                playEndpoint = WatchEndpoint(playlistId = "playlist_id_1"),
                shuffleEndpoint = WatchEndpoint(playlistId = "playlist_id_1", params = "shuffle"),
                radioEndpoint = WatchEndpoint(playlistId = "playlist_id_1", params = "radio"),
                isEditable = true,
                description = "A curated playlist description",
            )

        val artistItem =
            ArtistItem(
                id = "artist_id_2",
                title = "Artist Title",
                thumbnail = "https://example.com/artist.jpg",
                channelId = "UCartist2",
                playEndpoint = WatchEndpoint(playlistId = "artist_radio"),
                shuffleEndpoint = WatchEndpoint(playlistId = "artist_shuffle"),
                radioEndpoint = WatchEndpoint(playlistId = "artist_radio"),
                subscriberCountText = "500K subscribers",
                monthlyListenerCountText = "1M listeners",
            )

        val featuredCard =
            HomePage.Section.FeaturedCard(
                id = "card_1",
                title = "Featured Card",
                subtitle = "Card subtitle",
                thumbnail = "https://example.com/card.jpg",
                endpoint =
                    BrowseEndpoint(
                        browseId = "VLcard_1",
                        params = "card_params",
                        browseEndpointContextSupportedConfigs =
                            BrowseEndpoint.BrowseEndpointContextSupportedConfigs(
                                BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig(
                                    pageType =
                                        BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.MUSIC_PAGE_TYPE_PLAYLIST,
                                ),
                            ),
                    ),
                playEndpoint = WatchEndpoint(playlistId = "card_play"),
                shuffleEndpoint = WatchEndpoint(playlistId = "card_shuffle"),
                radioEndpoint = WatchEndpoint(playlistId = "card_radio"),
                itemIds = listOf("song_id_1", "song_id_2"),
            )

        val chip =
            HomePage.Chip(
                title = "Relax",
                endpoint =
                    BrowseEndpoint(
                        browseId = "FEmusic_home",
                        params = "chip_params",
                        browseEndpointContextSupportedConfigs =
                            BrowseEndpoint.BrowseEndpointContextSupportedConfigs(
                                BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig(
                                    pageType =
                                        BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.MUSIC_PAGE_TYPE_ARTIST,
                                ),
                            ),
                    ),
                deselectEndPoint = BrowseEndpoint(browseId = "FEmusic_home"),
            )

        val section =
            HomePage.Section(
                title = "Quick Picks",
                label = "START RADIO FROM A SONG",
                thumbnail = "https://example.com/section.jpg",
                endpoint = BrowseEndpoint(browseId = "VLquick_picks"),
                items = listOf(songItem, albumItem, playlistItem, artistItem),
                playEndpoint = WatchEndpoint(videoId = "video1", playlistId = "playlist1"),
                featuredCards = listOf(featuredCard),
            )

        val originalHomePage =
            HomePage(
                chips = listOf(chip),
                sections = listOf(section),
                continuation = "continuation_token_123",
            )

        val json = Json.encodeToString(originalHomePage)

        assertTrue(json.contains("\"type\":\"song\""))
        assertTrue(json.contains("\"type\":\"album\""))
        assertTrue(json.contains("\"type\":\"playlist\""))
        assertTrue(json.contains("\"type\":\"artist\""))

        assertFalse(json.contains("moe.rukamori.archivetune.innertube.models.SongItem"))
        assertFalse(json.contains("moe.rukamori.archivetune.innertube.models.AlbumItem"))
        assertFalse(json.contains("moe.rukamori.archivetune.innertube.models.PlaylistItem"))
        assertFalse(json.contains("moe.rukamori.archivetune.innertube.models.ArtistItem"))

        val decodedHomePage = Json.decodeFromString<HomePage>(json)
        assertEquals(originalHomePage, decodedHomePage)
    }

    @Test
    fun nullableAndDefaultCases_roundTripSerialization_matchesOriginal() {
        val minimalSong =
            SongItem(
                id = "min_song",
                title = "Min Title",
                artists = emptyList(),
                thumbnail = "https://example.com/min.jpg",
            )

        val minimalAlbum =
            AlbumItem(
                browseId = "min_album",
                playlistId = "min_playlist",
                title = "Min Album",
                artists = null,
                thumbnail = "https://example.com/min_album.jpg",
            )

        val minimalPlaylist =
            PlaylistItem(
                id = "min_playlist",
                title = "Min Playlist",
                author = null,
                songCountText = null,
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )

        val minimalArtist =
            ArtistItem(
                id = "min_artist",
                title = "Min Artist",
                thumbnail = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )

        val minimalSection =
            HomePage.Section(
                title = "Minimal Section",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = listOf(minimalSong, minimalAlbum, minimalPlaylist, minimalArtist),
            )

        val minimalHomePage =
            HomePage(
                chips = null,
                sections = listOf(minimalSection),
                continuation = null,
            )

        val json = Json.encodeToString(minimalHomePage)
        val decoded = Json.decodeFromString<HomePage>(json)

        assertEquals(minimalHomePage, decoded)
    }

    @Test
    fun ignoreUnknownKeys_decodesSuccessfullyWhenExtraFieldsPresent() {
        val jsonConfig = Json { ignoreUnknownKeys = true }
        val payloadWithUnknowns =
            """
            {
                "chips": null,
                "sections": [],
                "continuation": null,
                "unknownField": "ignoredValue"
            }
            """.trimIndent()

        val decoded = jsonConfig.decodeFromString<HomePage>(payloadWithUnknowns)
        assertEquals(HomePage(chips = null, sections = emptyList(), continuation = null), decoded)
    }
}
