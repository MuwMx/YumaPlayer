/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.proxy.RotatingProxySelector
import okhttp3.Dns
import java.net.Proxy
import java.util.Locale

/**
 * Facade providing access to InnerTube endpoints.
 * Dispatches to specialized network delegates.
 */
class InnerTube {
    internal val http = InnerTubeHttp()
    private val playback = InnerTubePlayback(http)
    private val browseDelegate = InnerTubeBrowse(http)
    private val library = InnerTubeLibrary(http)
    private val account = InnerTubeAccount(http)

    object TimingHook {
        var onRetry: ((attempt: Int, delayMs: Long) -> Unit)?
            get() = InnerTubeHttp.TimingHook.onRetry
            set(value) { InnerTubeHttp.TimingHook.onRetry = value }
    }

    var locale = YouTubeLocale(gl = Locale.getDefault().country, hl = Locale.getDefault().toLanguageTag())
    private val queueLocale = YouTubeLocale(gl = "US", hl = "en")

    @Volatile
    private var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    var visitorData: String?
        get() = authState.visitorData
        set(value) { authState = authState.copy(visitorData = value).normalized() }
    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) { authState = authState.copy(dataSyncId = value).normalized() }
    val delegatedSessionId: String? get() = authState.delegatedSessionId
    var poToken: String?
        get() = authState.poToken
        set(value) { authState = authState.copy(poToken = value).normalized() }
    var cookie: String?
        get() = authState.cookie
        set(value) { authState = authState.copy(cookie = value).normalized() }

    var proxy: Proxy?
        get() = http.proxy
        set(value) { http.proxy = value }
    var proxyUsername: String?
        get() = http.proxyUsername
        set(value) { http.proxyUsername = value }
    var proxyPassword: String?
        get() = http.proxyPassword
        set(value) { http.proxyPassword = value }
    internal var proxySelector: RotatingProxySelector?
        get() = http.proxySelector
        set(value) { http.proxySelector = value }
    var dns: Dns
        get() = http.dns
        set(value) { http.dns = value }

    var useLoginForBrowse: Boolean = false

    fun currentAuthState(): PlaybackAuthState = authState
    fun applyAuthState(value: PlaybackAuthState) { authState = value.normalized() }

    // Browse / Search / Queue
    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
        useAccountContext: Boolean = true,
    ) = browseDelegate.search(
        client = client,
        query = query,
        params = params,
        continuation = continuation,
        useAccountContext = useAccountContext,
        authState = currentAuthState(),
        locale = locale,
        useLoginForBrowse = useLoginForBrowse,
    )

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ) = browseDelegate.browse(
        client = client,
        browseId = browseId,
        params = params,
        continuation = continuation,
        setLogin = setLogin,
        authState = currentAuthState(),
        locale = locale,
        useLoginForBrowse = useLoginForBrowse,
    )

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = browseDelegate.next(
        client = client,
        videoId = videoId,
        playlistId = playlistId,
        playlistSetVideoId = playlistSetVideoId,
        index = index,
        params = params,
        continuation = continuation,
        authState = currentAuthState(),
        queueLocale = queueLocale,
    )

    suspend fun getSearchSuggestions(client: YouTubeClient, input: String) =
        browseDelegate.getSearchSuggestions(client, input, currentAuthState(), locale)

    suspend fun getQueue(client: YouTubeClient, videoIds: List<String>?, playlistId: String?) =
        browseDelegate.getQueue(client, videoIds, playlistId, currentAuthState(), locale)

    suspend fun getTranscript(client: YouTubeClient, videoId: String) =
        browseDelegate.getTranscript(client, videoId, currentAuthState(), locale)

    suspend fun getSwJsData() = browseDelegate.getSwJsData()

    // Playback & Telemetry
    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = currentAuthState(),
    ) = playback.player(
        client = client,
        videoId = videoId,
        playlistId = playlistId,
        signatureTimestamp = signatureTimestamp,
        poToken = poToken,
        setLogin = setLogin,
        authState = authState,
        locale = locale,
    )

    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String?,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState = currentAuthState(),
    ) = playback.registerPlayback(
        url = url,
        cpn = cpn,
        playlistId = playlistId,
        client = client,
        authState = authState,
        locale = locale,
    )

    suspend fun pingAtr(
        url: String,
        cpn: String,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState = currentAuthState(),
    ) = playback.pingAtr(
        url = url,
        cpn = cpn,
        client = client,
        authState = authState,
    )

    suspend fun pingWatchtime(
        url: String,
        cpn: String,
        seconds: Long,
        final: Boolean = false,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState = currentAuthState(),
    ) = playback.pingWatchtime(
        url = url,
        cpn = cpn,
        seconds = seconds,
        final = final,
        client = client,
        authState = authState,
        locale = locale,
    )

    // Account
    suspend fun accountMenu(client: YouTubeClient) =
        account.accountMenu(client, currentAuthState(), locale)

    suspend fun accountChannels(client: YouTubeClient) =
        account.accountChannels(client, currentAuthState(), locale)

    // Library
    suspend fun likeVideo(client: YouTubeClient, videoId: String) =
        library.likeVideo(client, videoId, currentAuthState(), locale)

    suspend fun unlikeVideo(client: YouTubeClient, videoId: String) =
        library.unlikeVideo(client, videoId, currentAuthState(), locale)

    suspend fun subscribeChannel(client: YouTubeClient, channelId: String) =
        library.subscribeChannel(client, channelId, currentAuthState(), locale)

    suspend fun unsubscribeChannel(client: YouTubeClient, channelId: String) =
        library.unsubscribeChannel(client, channelId, currentAuthState(), locale)

    suspend fun likePlaylist(client: YouTubeClient, playlistId: String) =
        library.likePlaylist(client, playlistId, currentAuthState(), locale)

    suspend fun unlikePlaylist(client: YouTubeClient, playlistId: String) =
        library.unlikePlaylist(client, playlistId, currentAuthState(), locale)

    suspend fun addToPlaylist(client: YouTubeClient, playlistId: String, videoId: String) =
        library.addToPlaylist(client, playlistId, videoId, currentAuthState(), locale)

    suspend fun addSongsToPlaylist(client: YouTubeClient, playlistId: String, videoIds: List<String>) =
        library.addSongsToPlaylist(client, playlistId, videoIds, currentAuthState(), locale)

    suspend fun addPlaylistToPlaylist(client: YouTubeClient, playlistId: String, addPlaylistId: String) =
        library.addPlaylistToPlaylist(client, playlistId, addPlaylistId, currentAuthState(), locale)

    suspend fun removeFromPlaylist(client: YouTubeClient, playlistId: String, videoId: String, setVideoId: String) =
        library.removeFromPlaylist(client, playlistId, videoId, setVideoId, currentAuthState(), locale)

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = library.moveSongPlaylist(client, playlistId, setVideoId, successorSetVideoId, currentAuthState(), locale)

    suspend fun createPlaylist(client: YouTubeClient, title: String, videoIds: List<String> = emptyList()) =
        library.createPlaylist(client, title, videoIds, currentAuthState(), locale)

    suspend fun renamePlaylist(client: YouTubeClient, playlistId: String, name: String) =
        library.renamePlaylist(client, playlistId, name, currentAuthState(), locale)

    suspend fun deletePlaylist(client: YouTubeClient, playlistId: String) =
        library.deletePlaylist(client, playlistId, currentAuthState(), locale)

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        MediaInfoResolver.getMediaInfo(this, videoId)
}
