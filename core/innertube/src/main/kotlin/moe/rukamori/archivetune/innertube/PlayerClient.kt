/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import moe.rukamori.archivetune.innertube.client.MediaInfoResolver
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.innertube.pages.RelatedPage
import moe.rukamori.archivetune.innertube.playback.PlayerMetadataOps
import moe.rukamori.archivetune.innertube.playback.PlayerTrackingOps
import moe.rukamori.archivetune.innertube.playback.PlayerWatchNextOps
import moe.rukamori.archivetune.innertube.playback.YouTubePlayerFacade

object PlayerClient : YouTubePlayerFacade {
    const val MAX_GET_QUEUE_SIZE = PlayerWatchNextOps.MAX_GET_QUEUE_SIZE

    private inline val innerTube: InnerTube get() = YouTube.innerTube

    internal fun currentPlaybackAuthState(): PlaybackAuthState = YouTube.currentPlaybackAuthState()

    internal fun resolvePlayerPoToken(
        client: YouTubeClient,
        explicitPoToken: String?,
        authState: PlaybackAuthState,
    ): String? = authState.resolvePlayerPoToken(
        client = client,
        explicitPoToken = explicitPoToken,
    )

    internal fun resolveGvsPoToken(authState: PlaybackAuthState = currentPlaybackAuthState()): String? =
        authState.resolveGvsPoToken()

    internal fun appendGvsPoToken(
        url: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): String {
        val token = authState.resolveGvsPoToken(client) ?: return url
        if (url.contains("pot=")) return url

        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}pot=$token"
    }

    override suspend fun player(
        videoId: String,
        playlistId: String?,
        client: YouTubeClient,
        signatureTimestamp: Int?,
        poToken: String?,
        setLogin: Boolean,
        authState: PlaybackAuthState,
    ): Result<PlayerResponse> = runCatching {
        val resolvedPoToken = resolvePlayerPoToken(client, poToken, authState)
        innerTube
            .player(
                client = client,
                videoId = videoId,
                playlistId = playlistId,
                signatureTimestamp = signatureTimestamp,
                poToken = resolvedPoToken,
                setLogin = setLogin,
                authState = authState,
            ).body<PlayerResponse>()
    }

    fun newCpn(): String = PlayerTrackingOps.newCpn()

    override suspend fun registerPlayback(
        playlistId: String?,
        playbackTracking: String,
        cpn: String,
        authState: PlaybackAuthState,
    ): Result<String> = PlayerTrackingOps.registerPlayback(
        innerTube = innerTube,
        playlistId = playlistId,
        playbackTracking = playbackTracking,
        cpn = cpn,
        authState = authState,
    )

    override suspend fun pingAtr(
        atrUrl: String,
        cpn: String,
        authState: PlaybackAuthState,
    ): Result<HttpResponse> = PlayerTrackingOps.pingAtr(
        innerTube = innerTube,
        atrUrl = atrUrl,
        cpn = cpn,
        authState = authState,
    )

    override suspend fun pingWatchtime(
        watchtimeUrl: String,
        cpn: String,
        seconds: Long,
        final: Boolean,
        authState: PlaybackAuthState,
    ): Result<HttpResponse> = PlayerTrackingOps.pingWatchtime(
        innerTube = innerTube,
        watchtimeUrl = watchtimeUrl,
        cpn = cpn,
        seconds = seconds,
        final = final,
        authState = authState,
    )

    override suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String?,
        followAutomixPreview: Boolean,
    ): Result<NextResult> = PlayerWatchNextOps.next(
        innerTube = innerTube,
        endpoint = endpoint,
        continuation = continuation,
        followAutomixPreview = followAutomixPreview,
    )

    override suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> =
        PlayerMetadataOps.lyrics(innerTube, endpoint)

    override suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> =
        PlayerMetadataOps.related(innerTube, endpoint)

    override suspend fun queue(
        videoIds: List<String>?,
        playlistId: String?,
    ): Result<List<SongItem>> = PlayerWatchNextOps.queue(innerTube, videoIds, playlistId)

    override suspend fun transcript(videoId: String): Result<String> =
        PlayerMetadataOps.transcript(innerTube, videoId)

    override suspend fun visitorData(): Result<String> =
        PlayerTrackingOps.visitorData(innerTube)

    override suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        MediaInfoResolver.getMediaInfo(innerTube, videoId)
}
