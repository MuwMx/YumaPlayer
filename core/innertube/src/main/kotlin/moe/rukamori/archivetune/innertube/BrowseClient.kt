/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import moe.rukamori.archivetune.innertube.browse.BrowseAlbumOps
import moe.rukamori.archivetune.innertube.browse.BrowseArtistOps
import moe.rukamori.archivetune.innertube.browse.BrowseChartItemConverter
import moe.rukamori.archivetune.innertube.browse.BrowseChartsOps
import moe.rukamori.archivetune.innertube.browse.BrowseFeedOps
import moe.rukamori.archivetune.innertube.browse.BrowseGenericOps
import moe.rukamori.archivetune.innertube.browse.BrowseLibraryOps
import moe.rukamori.archivetune.innertube.browse.BrowsePlaylistOps
import moe.rukamori.archivetune.innertube.browse.BrowseTwoRowItemConverter
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsContinuationPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.ExplorePage
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.LibraryContinuationPage
import moe.rukamori.archivetune.innertube.pages.LibraryPage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.innertube.pages.PlaylistContinuationPage
import moe.rukamori.archivetune.innertube.pages.PlaylistPage

object BrowseClient {
    var traceLogger: ((String) -> Unit)? = null

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> =
        BrowseAlbumOps.album(browseId, withSongs)

    suspend fun albumSongs(playlistId: String, album: AlbumItem? = null): Result<List<SongItem>> =
        BrowseAlbumOps.albumSongs(playlistId, album)

    suspend fun artist(browseId: String): Result<ArtistPage> =
        BrowseArtistOps.artist(browseId)

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> =
        BrowseArtistOps.artistItems(endpoint)

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        BrowseArtistOps.artistItemsContinuation(continuation)

    suspend fun playlist(playlistId: String): Result<PlaylistPage> =
        BrowsePlaylistOps.playlist(playlistId)

    suspend fun playlistContinuation(continuation: String, playlistId: String? = null): Result<PlaylistContinuationPage> =
        BrowsePlaylistOps.playlistContinuation(continuation, playlistId)

    suspend fun home(continuation: String? = null, params: String? = null): Result<HomePage> =
        BrowseFeedOps.home(continuation, params)

    suspend fun explore(): Result<ExplorePage> = BrowseFeedOps.explore()

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = BrowseFeedOps.newReleaseAlbums()

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = BrowseFeedOps.moodAndGenres()

    suspend fun browse(browseId: String, params: String? = null): Result<BrowseResult> =
        BrowseGenericOps.browse(browseId, params)

    suspend fun library(browseId: String, tabIndex: Int = 0): Result<LibraryPage> =
        BrowseLibraryOps.library(browseId, tabIndex)

    suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> =
        BrowseLibraryOps.libraryContinuation(continuation)

    suspend fun libraryRecentActivity(): Result<LibraryPage> =
        BrowseLibraryOps.libraryRecentActivity()

    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> =
        BrowseChartsOps.getChartsPage(continuation)

    suspend fun musicHistory(): Result<HistoryPage> = BrowseLibraryOps.musicHistory()

    internal fun convertToChartItem(renderer: MusicResponsiveListItemRenderer): YTItem? =
        BrowseChartItemConverter.convertToChartItem(renderer)

    internal fun convertMusicTwoRowItem(renderer: MusicTwoRowItemRenderer): YTItem? =
        BrowseTwoRowItemConverter.convertMusicTwoRowItem(renderer)
}
