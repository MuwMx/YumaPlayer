/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.getItems
import moe.rukamori.archivetune.innertube.models.response.AccountMenuResponse
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.models.response.GetQueueResponse
import moe.rukamori.archivetune.innertube.models.response.GetTranscriptResponse
import moe.rukamori.archivetune.innertube.models.response.NextResponse
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
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
import moe.rukamori.archivetune.innertube.pages.NextPage
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.innertube.pages.PlaylistContinuationPage
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import moe.rukamori.archivetune.innertube.pages.RelatedPage
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import moe.rukamori.archivetune.innertube.proxy.RotatingProxyClient
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Proxy
import kotlin.random.Random

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    internal val innerTube = InnerTube()
    private val accountSwitcherClient = WEB.copy(loginSupported = true)
    private val mutableAuthState = MutableStateFlow(PlaybackAuthState.EMPTY)

    val authStateFlow: StateFlow<PlaybackAuthState> = mutableAuthState.asStateFlow()

    private val _historySyncEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val historySyncEvent: SharedFlow<Unit> = _historySyncEvent.asSharedFlow()

    fun notifyHistorySynced() {
        _historySyncEvent.tryEmit(Unit)
    }

    var authState: PlaybackAuthState
        get() = mutableAuthState.value
        set(value) {
            val normalized = value.normalized()
            mutableAuthState.value = normalized
            innerTube.applyAuthState(normalized)
        }

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = authState.visitorData
        set(value) {
            authState = authState.copy(visitorData = value)
        }
    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) {
            authState = authState.copy(dataSyncId = value)
        }
    var cookie: String?
        get() = authState.cookie
        set(value) {
            authState = authState.copy(cookie = value)
        }
    var poToken: String?
        get() = authState.poToken
        set(value) {
            authState = authState.copy(poToken = value)
        }
    var webClientPoTokenEnabled: Boolean
        get() = authState.webClientPoTokenEnabled
        set(value) {
            authState = authState.copy(webClientPoTokenEnabled = value)
        }
    var poTokenGvs: String?
        get() = authState.poTokenGvs
        set(value) {
            authState = authState.copy(poTokenGvs = value)
        }
    var poTokenPlayer: String?
        get() = authState.poTokenPlayer
        set(value) {
            authState = authState.copy(poTokenPlayer = value)
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }
    var proxyUsername: String?
        get() = innerTube.proxyUsername
        set(value) {
            innerTube.proxyUsername = value
        }
    var proxyPassword: String?
        get() = innerTube.proxyPassword
        set(value) {
            innerTube.proxyPassword = value
        }
    var dns: Dns
        get() = innerTube.dns
        set(value) {
            innerTube.dns = value
        }
    var streamBypassProxy: Boolean = false
    val streamProxy: Proxy?
        get() = if (streamBypassProxy) null else proxy
    val streamOkHttpProxy: Proxy
        get() = streamProxy ?: Proxy.NO_PROXY
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    val rotatingProxyClient = RotatingProxyClient()
    private val _ipRotationActiveCount = MutableStateFlow(0)
    val ipRotationActiveCount: StateFlow<Int> = _ipRotationActiveCount.asStateFlow()

    suspend fun enableIpRotation() {
        withContext(Dispatchers.IO) {
            rotatingProxyClient.fetchAndLoad()
            innerTube.proxySelector = rotatingProxyClient.selector()
            _ipRotationActiveCount.value = rotatingProxyClient.activeCount()
        }
    }

    suspend fun refreshIpRotation() {
        withContext(Dispatchers.IO) {
            if (rotatingProxyClient.activeCount() <= 1) {
                rotatingProxyClient.fetchAndLoad()
            } else {
                rotatingProxyClient.rotate()
            }
            innerTube.proxySelector = rotatingProxyClient.selector()
            _ipRotationActiveCount.value = rotatingProxyClient.activeCount()
        }
    }

    fun disableIpRotation() {
        innerTube.proxySelector = null
        _ipRotationActiveCount.value = 0
    }

    fun currentPlaybackAuthState(): PlaybackAuthState = authState

    fun createDnsOverHttps(url: String): Dns {
        val bootstrapClient = OkHttpClient.Builder().build()
        return DnsOverHttps
            .Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .build()
    }

    private fun resolvePlayerPoToken(
        client: YouTubeClient,
        explicitPoToken: String?,
        authState: PlaybackAuthState,
    ): String? =
        PlayerClient.resolvePlayerPoToken(
            client = client,
            explicitPoToken = explicitPoToken,
            authState = authState,
        )

    fun hasLoginCookie(): Boolean = authState.hasLoginCookie

    fun hasPlaybackLoginContext(): Boolean = authState.hasPlaybackLoginContext

    internal fun resolveGvsPoToken(authState: PlaybackAuthState = currentPlaybackAuthState()): String? =
        PlayerClient.resolveGvsPoToken(authState)

    internal fun appendGvsPoToken(
        url: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): String = PlayerClient.appendGvsPoToken(url, client, authState)

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> =
        SearchClient.searchSuggestions(query)

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> =
        SearchClient.searchSummary(query)

    suspend fun search(
        query: String,
        filter: SearchFilter,
        useAccountContext: Boolean = true,
    ): Result<SearchResult> =
        SearchClient.search(query, filter, useAccountContext)

    suspend fun searchContinuation(
        continuation: String,
        useAccountContext: Boolean = true,
    ): Result<SearchResult> =
        SearchClient.searchContinuation(continuation, useAccountContext)

    suspend fun album(
        browseId: String,
        withSongs: Boolean = true,
    ): Result<AlbumPage> =
        BrowseClient.album(browseId, withSongs)

    suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem? = null,
    ): Result<List<SongItem>> =
        BrowseClient.albumSongs(playlistId, album)

    suspend fun artist(browseId: String): Result<ArtistPage> =
        BrowseClient.artist(browseId)

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> =
        BrowseClient.artistItems(endpoint)

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        BrowseClient.artistItemsContinuation(continuation)

    suspend fun playlist(playlistId: String): Result<PlaylistPage> =
        BrowseClient.playlist(playlistId)

    suspend fun playlistContinuation(
        continuation: String,
        playlistId: String? = null,
    ): Result<PlaylistContinuationPage> =
        BrowseClient.playlistContinuation(continuation, playlistId)

    suspend fun home(
        continuation: String? = null,
        params: String? = null,
    ): Result<HomePage> =
        BrowseClient.home(continuation, params)

    suspend fun explore(): Result<ExplorePage> =
        BrowseClient.explore()

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> =
        BrowseClient.newReleaseAlbums()

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> =
        BrowseClient.moodAndGenres()

    suspend fun browse(
        browseId: String,
        params: String?,
    ): Result<BrowseResult> =
        BrowseClient.browse(browseId, params)

    suspend fun library(
        browseId: String,
        tabIndex: Int = 0,
    ): Result<LibraryPage> =
        BrowseClient.library(browseId, tabIndex)

    suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> =
        BrowseClient.libraryContinuation(continuation)

    suspend fun libraryRecentActivity(): Result<LibraryPage> =
        BrowseClient.libraryRecentActivity()

    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> =
        BrowseClient.getChartsPage(continuation)

    suspend fun musicHistory(): Result<HistoryPage> =
        BrowseClient.musicHistory()

    suspend fun likeVideo(
        videoId: String,
        like: Boolean,
    ) = PlaylistClient.likeVideo(videoId, like)

    suspend fun likePlaylist(
        playlistId: String,
        like: Boolean,
    ) = PlaylistClient.likePlaylist(playlistId, like)

    suspend fun subscribeChannel(
        channelId: String,
        subscribe: Boolean,
    ) = PlaylistClient.subscribeChannel(channelId, subscribe)

    suspend fun getChannelId(browseId: String): String =
        PlaylistClient.getChannelId(browseId)

    suspend fun addToPlaylist(
        playlistId: String,
        videoId: String,
    ) = PlaylistClient.addToPlaylist(playlistId, videoId)

    suspend fun addSongsToPlaylist(
        playlistId: String,
        videoIds: List<String>,
        batchSize: Int = DEFAULT_PLAYLIST_EDIT_BATCH_SIZE,
        onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
    ): Result<List<String?>> =
        PlaylistClient.addSongsToPlaylist(playlistId, videoIds, batchSize, onProgress)

    suspend fun addPlaylistToPlaylist(
        playlistId: String,
        addPlaylistId: String,
    ) = PlaylistClient.addPlaylistToPlaylist(playlistId, addPlaylistId)

    suspend fun playlistEntrySetVideoIds(
        playlistId: String,
        videoId: String,
    ) = PlaylistClient.playlistEntrySetVideoIds(playlistId, videoId)

    suspend fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = PlaylistClient.removeFromPlaylist(playlistId, videoId, setVideoId)

    suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = PlaylistClient.moveSongPlaylist(playlistId, setVideoId, successorSetVideoId)

    suspend fun createPlaylist(
        title: String,
        videoIds: List<String> = emptyList(),
    ) = PlaylistClient.createPlaylist(title, videoIds)

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ) = PlaylistClient.renamePlaylist(playlistId, name)

    suspend fun deletePlaylist(playlistId: String) =
        PlaylistClient.deletePlaylist(playlistId)

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): Result<PlayerResponse> =
        PlayerClient.player(
            videoId = videoId,
            playlistId = playlistId,
            client = client,
            signatureTimestamp = signatureTimestamp,
            poToken = poToken,
            setLogin = setLogin,
            authState = authState,
        )

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ) = PlayerClient.registerPlayback(
        playlistId = playlistId,
        playbackTracking = playbackTracking,
        authState = authState,
    )

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null,
        followAutomixPreview: Boolean = true,
    ): Result<NextResult> =
        PlayerClient.next(endpoint, continuation, followAutomixPreview)

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> =
        PlayerClient.lyrics(endpoint)

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> =
        PlayerClient.related(endpoint)

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): Result<List<SongItem>> =
        PlayerClient.queue(videoIds, playlistId)

    suspend fun transcript(videoId: String): Result<String> =
        PlayerClient.transcript(videoId)

    suspend fun visitorData(): Result<String> =
        PlayerClient.visitorData()

    suspend fun accountInfo(): Result<AccountInfo> =
        runCatching {
            val response = innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
            val accountInfo =
                response.actions
                    .firstOrNull()
                    ?.openPopupAction
                    ?.popup
                    ?.multiPageMenuRenderer
                    ?.header
                    ?.activeAccountHeaderRenderer
                    ?.toAccountInfo()
            accountInfo ?: throw IllegalStateException("Failed to get account info - user may not be logged in")
        }

    suspend fun accountChannels(): Result<List<AccountChannel>> =
        runCatching {
            val response =
                Json.parseToJsonElement(
                    innerTube.accountChannels(accountSwitcherClient).bodyAsText(),
                )

            response
                .objectsNamed("accountItemRenderer")
                .mapNotNull(::parseAccountChannel)
                .sortedByDescending(AccountChannel::isSelected)
                .distinctBy(AccountChannel::dataSyncId)
                .toList()
        }

    suspend fun accountDataSyncId(): Result<String> =
        runCatching {
            val response =
                Json.parseToJsonElement(
                    innerTube.accountChannels(accountSwitcherClient).bodyAsText(),
                )

            response.findMainAppWebDataSyncId()
                ?: response
                    .objectsNamed("accountItemRenderer")
                    .mapNotNull { renderer ->
                        val isDisabled = renderer.booleanValue("isDisabled") ?: false
                        val hasChannel = renderer.booleanValue("hasChannel") ?: true
                        if (isDisabled || !hasChannel) return@mapNotNull null

                        renderer.parseAccountChannelDataSyncId()?.let { dataSyncId ->
                            dataSyncId to (renderer.booleanValue("isSelected") ?: false)
                        }
                    }.sortedByDescending { (_, isSelected) -> isSelected }
                    .firstOrNull()
                    ?.first
                ?: throw IllegalStateException("Failed to get YouTube DataSyncId")
        }

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        PlayerClient.getMediaInfo(videoId)

    typealias SearchFilter = moe.rukamori.archivetune.innertube.SearchFilter
    typealias LibraryFilter = moe.rukamori.archivetune.innertube.LibraryFilter

    const val MAX_GET_QUEUE_SIZE = 1000
    private const val DEFAULT_PLAYLIST_EDIT_BATCH_SIZE = 50
}
