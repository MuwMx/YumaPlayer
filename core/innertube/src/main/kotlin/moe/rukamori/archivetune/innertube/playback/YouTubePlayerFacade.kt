/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.playback

import io.ktor.client.statement.HttpResponse
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.network.AuthSessionStore
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.innertube.pages.RelatedPage

interface YouTubePlayerFacade {
    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = AuthSessionStore.currentPlaybackAuthState(),
    ): Result<PlayerResponse>

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
        cpn: String = PlayerTrackingOps.newCpn(),
        authState: PlaybackAuthState = AuthSessionStore.currentPlaybackAuthState(),
    ): Result<String>

    suspend fun pingAtr(
        atrUrl: String,
        cpn: String,
        authState: PlaybackAuthState = AuthSessionStore.currentPlaybackAuthState(),
    ): Result<HttpResponse>

    suspend fun pingWatchtime(
        watchtimeUrl: String,
        cpn: String,
        seconds: Long,
        final: Boolean = false,
        authState: PlaybackAuthState = AuthSessionStore.currentPlaybackAuthState(),
    ): Result<HttpResponse>

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null,
        followAutomixPreview: Boolean = true,
    ): Result<NextResult>

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?>

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage>

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): Result<List<SongItem>>

    suspend fun transcript(videoId: String): Result<String>

    suspend fun visitorData(): Result<String>

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo>
}
