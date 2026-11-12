/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.network.AuthSessionStore
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState
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
import moe.rukamori.archivetune.innertube.playback.YouTubePlayerFacade
import moe.rukamori.archivetune.innertube.proxy.RotatingProxyClient
import moe.rukamori.archivetune.innertube.search.YouTubeSearchFacade
import okhttp3.Dns
import java.net.Proxy

/**
 * Unified facade for InnerTube operations delegating to dedicated domain clients.
 */
object YouTube : YouTubeSearchFacade by SearchClient, YouTubePlayerFacade by PlayerClient {
    internal val innerTube: InnerTube get() = AuthSessionStore.innerTube

    val authStateFlow: StateFlow<PlaybackAuthState> get() = AuthSessionStore.authStateFlow
    val historySyncEvent: SharedFlow<Unit> get() = AuthSessionStore.historySyncEvent
    val ipRotationActiveCount: StateFlow<Int> get() = AuthSessionStore.ipRotationActiveCount

    fun notifyHistorySynced() = AuthSessionStore.notifyHistorySynced()

    var authState: PlaybackAuthState
        get() = AuthSessionStore.authState; set(value) { AuthSessionStore.authState = value }
    var locale: YouTubeLocale
        get() = AuthSessionStore.locale; set(value) { AuthSessionStore.locale = value }
    var visitorData: String?
        get() = AuthSessionStore.visitorData; set(value) { AuthSessionStore.visitorData = value }
    var dataSyncId: String?
        get() = AuthSessionStore.dataSyncId; set(value) { AuthSessionStore.dataSyncId = value }
    var cookie: String?
        get() = AuthSessionStore.cookie; set(value) { AuthSessionStore.cookie = value }
    var poToken: String?
        get() = AuthSessionStore.poToken; set(value) { AuthSessionStore.poToken = value }
    var webClientPoTokenEnabled: Boolean
        get() = AuthSessionStore.webClientPoTokenEnabled; set(value) { AuthSessionStore.webClientPoTokenEnabled = value }
    var poTokenGvs: String?
        get() = AuthSessionStore.poTokenGvs; set(value) { AuthSessionStore.poTokenGvs = value }
    var poTokenPlayer: String?
        get() = AuthSessionStore.poTokenPlayer; set(value) { AuthSessionStore.poTokenPlayer = value }
    var proxy: Proxy?
        get() = AuthSessionStore.proxy; set(value) { AuthSessionStore.proxy = value }
    var proxyUsername: String?
        get() = AuthSessionStore.proxyUsername; set(value) { AuthSessionStore.proxyUsername = value }
    var proxyPassword: String?
        get() = AuthSessionStore.proxyPassword; set(value) { AuthSessionStore.proxyPassword = value }
    var dns: Dns
        get() = AuthSessionStore.dns; set(value) { AuthSessionStore.dns = value }
    var streamBypassProxy: Boolean
        get() = AuthSessionStore.streamBypassProxy; set(value) { AuthSessionStore.streamBypassProxy = value }

    val streamProxy: Proxy? get() = AuthSessionStore.streamProxy
    val streamOkHttpProxy: Proxy get() = AuthSessionStore.streamOkHttpProxy

    var useLoginForBrowse: Boolean
        get() = AuthSessionStore.useLoginForBrowse; set(value) { AuthSessionStore.useLoginForBrowse = value }

    val rotatingProxyClient: RotatingProxyClient get() = AuthSessionStore.rotatingProxyClient

    suspend fun enableIpRotation() = AuthSessionStore.enableIpRotation()
    suspend fun refreshIpRotation() = AuthSessionStore.refreshIpRotation()
    fun disableIpRotation() = AuthSessionStore.disableIpRotation()
    fun currentPlaybackAuthState(): PlaybackAuthState = AuthSessionStore.currentPlaybackAuthState()
    fun createDnsOverHttps(url: String): Dns = AuthSessionStore.createDnsOverHttps(url)
    fun hasLoginCookie(): Boolean = AuthSessionStore.hasLoginCookie()
    fun hasPlaybackLoginContext(): Boolean = AuthSessionStore.hasPlaybackLoginContext()

    internal fun resolveGvsPoToken(authState: PlaybackAuthState = currentPlaybackAuthState()): String? =
        PlayerClient.resolveGvsPoToken(authState)

    internal fun appendGvsPoToken(
        url: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): String = PlayerClient.appendGvsPoToken(url, client, authState)

    // Browse operations
    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> =
        BrowseClient.album(browseId, withSongs)

    suspend fun albumSongs(playlistId: String, album: AlbumItem? = null): Result<List<SongItem>> =
        BrowseClient.albumSongs(playlistId, album)

    var traceLogger: ((String) -> Unit)?
        get() = BrowseClient.traceLogger; set(value) { BrowseClient.traceLogger = value }

    suspend fun artist(browseId: String): Result<ArtistPage> = BrowseClient.artist(browseId)

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> =
        BrowseClient.artistItems(endpoint)

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        BrowseClient.artistItemsContinuation(continuation)

    suspend fun playlist(playlistId: String): Result<PlaylistPage> =
        BrowseClient.playlist(playlistId)

    suspend fun playlistContinuation(
        continuation: String,
        playlistId: String? = null,
    ): Result<PlaylistContinuationPage> = BrowseClient.playlistContinuation(continuation, playlistId)

    suspend fun home(continuation: String? = null, params: String? = null): Result<HomePage> =
        BrowseClient.home(continuation, params)

    suspend fun explore(): Result<ExplorePage> = BrowseClient.explore()
    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = BrowseClient.newReleaseAlbums()
    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = BrowseClient.moodAndGenres()
    suspend fun browse(browseId: String, params: String?): Result<BrowseResult> =
        BrowseClient.browse(browseId, params)

    suspend fun library(browseId: String, tabIndex: Int = 0): Result<LibraryPage> =
        BrowseClient.library(browseId, tabIndex)

    suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> =
        BrowseClient.libraryContinuation(continuation)

    suspend fun libraryRecentActivity(): Result<LibraryPage> = BrowseClient.libraryRecentActivity()
    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> =
        BrowseClient.getChartsPage(continuation)

    suspend fun musicHistory(): Result<HistoryPage> = BrowseClient.musicHistory()

    // Playlist & Library mutation operations
    suspend fun likeVideo(videoId: String, like: Boolean) = PlaylistClient.likeVideo(videoId, like)
    suspend fun likePlaylist(playlistId: String, like: Boolean) = PlaylistClient.likePlaylist(playlistId, like)
    suspend fun subscribeChannel(channelId: String, subscribe: Boolean) =
        PlaylistClient.subscribeChannel(channelId, subscribe)

    suspend fun getChannelId(browseId: String): String = PlaylistClient.getChannelId(browseId)

    suspend fun addToPlaylist(playlistId: String, videoId: String) =
        PlaylistClient.addToPlaylist(playlistId, videoId)

    suspend fun addSongsToPlaylist(
        playlistId: String,
        videoIds: List<String>,
        batchSize: Int = PlaylistClient.DEFAULT_PLAYLIST_EDIT_BATCH_SIZE,
        onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
    ): Result<List<String?>> =
        PlaylistClient.addSongsToPlaylist(playlistId, videoIds, batchSize, onProgress)

    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String) =
        PlaylistClient.addPlaylistToPlaylist(playlistId, addPlaylistId)

    suspend fun playlistEntrySetVideoIds(playlistId: String, videoId: String) =
        PlaylistClient.playlistEntrySetVideoIds(playlistId, videoId)

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) =
        PlaylistClient.removeFromPlaylist(playlistId, videoId, setVideoId)

    suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String?) =
        PlaylistClient.moveSongPlaylist(playlistId, setVideoId, successorSetVideoId)

    suspend fun createPlaylist(title: String, videoIds: List<String> = emptyList()) =
        PlaylistClient.createPlaylist(title, videoIds)

    suspend fun renamePlaylist(playlistId: String, name: String) =
        PlaylistClient.renamePlaylist(playlistId, name)

    suspend fun deletePlaylist(playlistId: String) = PlaylistClient.deletePlaylist(playlistId)

    // Account operations
    suspend fun accountInfo(): Result<AccountInfo> = AuthSessionStore.accountInfo()
    suspend fun accountChannels(): Result<List<AccountChannel>> = AuthSessionStore.accountChannels()
    suspend fun accountDataSyncId(): Result<String> = AuthSessionStore.accountDataSyncId()

    typealias SearchFilter = moe.rukamori.archivetune.innertube.SearchFilter
    typealias LibraryFilter = moe.rukamori.archivetune.innertube.LibraryFilter
}
