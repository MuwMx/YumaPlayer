package moe.rukamori.archivetune.innertube

import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.NavigationEndpoint
import moe.rukamori.archivetune.innertube.models.Run
import moe.rukamori.archivetune.innertube.models.Runs
import moe.rukamori.archivetune.innertube.models.Thumbnail
import moe.rukamori.archivetune.innertube.models.ThumbnailRenderer
import moe.rukamori.archivetune.innertube.models.Thumbnails
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseClientChartParserTest {
    private fun createThumbnail(url: String = "https://example.com/thumb.jpg") =
        ThumbnailRenderer(
            musicThumbnailRenderer =
                ThumbnailRenderer.MusicThumbnailRenderer(
                    thumbnail =
                        Thumbnails(
                            thumbnails = listOf(Thumbnail(url = url, width = 120, height = 120)),
                        ),
                    thumbnailCrop = null,
                    thumbnailScale = null,
                ),
            musicAnimatedThumbnailRenderer = null,
            croppedSquareThumbnailRenderer = null,
        )

    @Test
    fun convertToChartItem_twoColumnsWithIndex_producesSongItem() {
        val renderer =
            MusicResponsiveListItemRenderer(
                badges = null,
                fixedColumns = null,
                flexColumns =
                    listOf(
                        MusicResponsiveListItemRenderer.FlexColumn(
                            musicResponsiveListItemFlexColumnRenderer =
                                MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                    text = Runs(runs = listOf(Run(text = "Chart Song", navigationEndpoint = null))),
                                ),
                        ),
                        MusicResponsiveListItemRenderer.FlexColumn(
                            musicResponsiveListItemFlexColumnRenderer =
                                MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                    text =
                                        Runs(
                                            runs =
                                                listOf(
                                                    Run(
                                                        text = "Top Artist",
                                                        navigationEndpoint =
                                                            NavigationEndpoint(
                                                                browseEndpoint = BrowseEndpoint(browseId = "UCartist1"),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                ),
                        ),
                    ),
                thumbnail = createThumbnail(),
                menu = null,
                playlistItemData =
                    MusicResponsiveListItemRenderer.PlaylistItemData(
                        playlistSetVideoId = null,
                        videoId = "video123",
                    ),
                overlay = null,
                navigationEndpoint = null,
                customIndexColumn =
                    MusicResponsiveListItemRenderer.CustomIndexColumn(
                        musicCustomIndexColumnRenderer =
                            MusicResponsiveListItemRenderer.CustomIndexColumn.MusicCustomIndexColumnRenderer(
                                text = Runs(runs = listOf(Run(text = "1", navigationEndpoint = null))),
                            ),
                    ),
            )

        val item = BrowseClient.convertToChartItem(renderer)
        assertNotNull(item)
        assertTrue(item is SongItem)
        val song = item as SongItem
        assertEquals("video123", song.id)
        assertEquals("Chart Song", song.title)
        assertEquals(1, song.chartPosition)
        assertEquals(1, song.artists.size)
        assertEquals("Top Artist", song.artists[0].name)
    }

    @Test
    fun convertToChartItem_watchEndpointFallback_producesSongItem() {
        val renderer =
            MusicResponsiveListItemRenderer(
                badges = null,
                fixedColumns = null,
                flexColumns =
                    listOf(
                        MusicResponsiveListItemRenderer.FlexColumn(
                            musicResponsiveListItemFlexColumnRenderer =
                                MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                    text = Runs(runs = listOf(Run(text = "Fallback Song", navigationEndpoint = null))),
                                ),
                        ),
                        MusicResponsiveListItemRenderer.FlexColumn(
                            musicResponsiveListItemFlexColumnRenderer =
                                MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                    text = Runs(runs = listOf(Run(text = "Artist", navigationEndpoint = null))),
                                ),
                        ),
                    ),
                thumbnail = createThumbnail(),
                menu = null,
                playlistItemData = null,
                overlay = null,
                navigationEndpoint =
                    NavigationEndpoint(
                        watchEndpoint = WatchEndpoint(videoId = "vid_fallback"),
                    ),
                customIndexColumn = null,
            )

        val item = BrowseClient.convertToChartItem(renderer)
        assertNotNull(item)
        assertTrue(item is SongItem)
        assertEquals("vid_fallback", (item as SongItem).id)
    }

    @Test
    fun convertToChartItem_artistRenderer_producesArtistItem() {
        val renderer =
            MusicResponsiveListItemRenderer(
                badges = null,
                fixedColumns = null,
                flexColumns =
                    listOf(
                        MusicResponsiveListItemRenderer.FlexColumn(
                            musicResponsiveListItemFlexColumnRenderer =
                                MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                    text = Runs(runs = listOf(Run(text = "Popular Artist", navigationEndpoint = null))),
                                ),
                        ),
                        MusicResponsiveListItemRenderer.FlexColumn(
                            musicResponsiveListItemFlexColumnRenderer =
                                MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                    text = Runs(runs = listOf(Run(text = "1M subscribers", navigationEndpoint = null))),
                                ),
                        ),
                    ),
                thumbnail = createThumbnail(),
                menu = null,
                playlistItemData = null,
                overlay = null,
                navigationEndpoint =
                    NavigationEndpoint(
                        browseEndpoint =
                            BrowseEndpoint(
                                browseId = "UCartist_chart",
                                browseEndpointContextSupportedConfigs =
                                    BrowseEndpointContextSupportedConfigs(
                                        browseEndpointContextMusicConfig =
                                            BrowseEndpointContextMusicConfig(
                                                pageType = BrowseEndpointContextMusicConfig.MUSIC_PAGE_TYPE_ARTIST,
                                            ),
                                    ),
                            ),
                    ),
                customIndexColumn = null,
            )

        val item = BrowseClient.convertToChartItem(renderer)
        assertNotNull(item)
        assertTrue(item is ArtistItem)
        val artist = item as ArtistItem
        assertEquals("UCartist_chart", artist.id)
        assertEquals("Popular Artist", artist.title)
    }

    @Test
    fun convertMusicTwoRowItem_playlistRenderer_producesPlaylistItem() {
        val renderer =
            MusicTwoRowItemRenderer(
                thumbnailRenderer = createThumbnail("https://example.com/p.jpg"),
                title = Runs(runs = listOf(Run(text = "Trending 20", navigationEndpoint = null))),
                subtitle = Runs(runs = listOf(Run(text = "YouTube Charts", navigationEndpoint = null))),
                navigationEndpoint =
                    NavigationEndpoint(
                        browseEndpoint =
                            BrowseEndpoint(
                                browseId = "VLplaylist123",
                                browseEndpointContextSupportedConfigs =
                                    BrowseEndpointContextSupportedConfigs(
                                        browseEndpointContextMusicConfig =
                                            BrowseEndpointContextMusicConfig(
                                                pageType = BrowseEndpointContextMusicConfig.MUSIC_PAGE_TYPE_PLAYLIST,
                                            ),
                                    ),
                            ),
                    ),
                subtitleBadges = null,
                menu = null,
                thumbnailOverlay = null,
            )

        val item = BrowseClient.convertMusicTwoRowItem(renderer)
        assertNotNull(item)
        assertTrue(item is PlaylistItem)
        val playlist = item as PlaylistItem
        assertEquals("playlist123", playlist.id)
        assertEquals("Trending 20", playlist.title)
    }
}
