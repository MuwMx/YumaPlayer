/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.library

import io.ktor.client.statement.HttpResponse
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.network.InnerTubeHttp
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState

internal class InnerTubeLibrary(http: InnerTubeHttp) {
    private val ratings = InnerTubeLibraryRatings(http)
    private val playlists = InnerTubeLibraryPlaylists(http)

    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = ratings.likeVideo(client, videoId, authState, locale)

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = ratings.unlikeVideo(client, videoId, authState, locale)

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = ratings.subscribeChannel(client, channelId, authState, locale)

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = ratings.unsubscribeChannel(client, channelId, authState, locale)

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = ratings.likePlaylist(client, playlistId, authState, locale)

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = ratings.unlikePlaylist(client, playlistId, authState, locale)

    suspend fun addToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.addToPlaylist(client, playlistId, videoId, authState, locale)

    suspend fun addSongsToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoIds: List<String>,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.addSongsToPlaylist(client, playlistId, videoIds, authState, locale)

    suspend fun addPlaylistToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        addPlaylistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.addPlaylistToPlaylist(client, playlistId, addPlaylistId, authState, locale)

    suspend fun removeFromPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        setVideoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.removeFromPlaylist(client, playlistId, videoId, setVideoId, authState, locale)

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.moveSongPlaylist(client, playlistId, setVideoId, successorSetVideoId, authState, locale)

    suspend fun createPlaylist(
        client: YouTubeClient,
        title: String,
        videoIds: List<String> = emptyList(),
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.createPlaylist(client, title, videoIds, authState, locale)

    suspend fun renamePlaylist(
        client: YouTubeClient,
        playlistId: String,
        name: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.renamePlaylist(client, playlistId, name, authState, locale)

    suspend fun deletePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = playlists.deletePlaylist(client, playlistId, authState, locale)
}
