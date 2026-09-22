/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.AddItemYouTubePlaylistResponse
import moe.rukamori.archivetune.innertube.models.response.CreatePlaylistResponse

object PlaylistClient {
    const val DEFAULT_PLAYLIST_EDIT_BATCH_SIZE = 50

    private inline val innerTube: InnerTube get() = YouTube.innerTube
    private inline var authState: PlaybackAuthState
        get() = YouTube.authState
        set(value) {
            YouTube.authState = value
        }

    private suspend inline fun artist(browseId: String) = YouTube.artist(browseId)
    private suspend inline fun playlist(playlistId: String) = YouTube.playlist(playlistId)
    private suspend inline fun playlistContinuation(continuation: String, playlistId: String? = null) =
        YouTube.playlistContinuation(continuation, playlistId)

    private suspend fun <T> withPlaylistMutationAuthRecovery(block: suspend () -> T): T {
        val initialAuthState = authState
        return try {
            block()
        } catch (e: Throwable) {
            if (!shouldRetryPlaylistMutationWithoutDelegatedContext(initialAuthState, e)) throw e
            authState = authState.copy(dataSyncId = null)
            block()
        }
    }

    private fun shouldRetryPlaylistMutationWithoutDelegatedContext(
        initialAuthState: PlaybackAuthState,
        failure: Throwable,
    ): Boolean {
        val exception = failure as? ClientRequestException ?: return false
        if (exception.response.status != HttpStatusCode.Forbidden) return false

        val currentAuthState = authState
        return initialAuthState.hasPlaybackLoginContext &&
            currentAuthState.hasPlaybackLoginContext &&
            currentAuthState.cookie == initialAuthState.cookie &&
            currentAuthState.dataSyncId == initialAuthState.dataSyncId
    }

    suspend fun likeVideo(
        videoId: String,
        like: Boolean,
    ) = runCatching {
        if (like) {
            innerTube.likeVideo(WEB_REMIX, videoId)
        } else {
            innerTube.unlikeVideo(WEB_REMIX, videoId)
        }
    }

    suspend fun likePlaylist(
        playlistId: String,
        like: Boolean,
    ) = runCatching {
        if (like) {
            innerTube.likePlaylist(WEB_REMIX, playlistId)
        } else {
            innerTube.unlikePlaylist(WEB_REMIX, playlistId)
        }
    }

    suspend fun subscribeChannel(
        channelId: String,
        subscribe: Boolean,
    ) = runCatching {
        if (subscribe) {
            innerTube.subscribeChannel(WEB_REMIX, channelId)
        } else {
            innerTube.unsubscribeChannel(WEB_REMIX, channelId)
        }
    }

    suspend fun getChannelId(browseId: String): String {
        artist(browseId).onSuccess {
            return it.artist.channelId ?: ""
        }
        return ""
    }

    suspend fun addToPlaylist(
        playlistId: String,
        videoId: String,
    ) = runCatching {
        val result =
            withPlaylistMutationAuthRecovery {
                innerTube
                    .addToPlaylist(WEB_REMIX, playlistId, videoId)
                    .body<AddItemYouTubePlaylistResponse>()
            }.playlistEditResults
                .firstOrNull { result ->
                    result.playlistEditVideoAddedResultData.videoId == videoId
                }?.playlistEditVideoAddedResultData
        require(result?.setVideoId?.isNotBlank() == true) {
            "Playlist edit did not confirm added video $videoId"
        }
        result.setVideoId
    }

    suspend fun addSongsToPlaylist(
        playlistId: String,
        videoIds: List<String>,
        batchSize: Int = DEFAULT_PLAYLIST_EDIT_BATCH_SIZE,
        onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
    ): Result<List<String?>> =
        runCatching {
            require(batchSize > 0) { "batchSize must be positive" }
            if (videoIds.isEmpty()) return@runCatching emptyList()

            val setVideoIds = ArrayList<String?>(videoIds.size)
            val totalSongs = videoIds.size
            var completedSongs = 0
            onProgress(completedSongs, totalSongs)

            videoIds.chunked(batchSize).forEach { batch ->
                val batchResponse =
                    runCatching {
                        withPlaylistMutationAuthRecovery {
                            innerTube
                                .addSongsToPlaylist(WEB_REMIX, playlistId, batch)
                                .body<AddItemYouTubePlaylistResponse>()
                        }
                    }

                if (batchResponse.isSuccess) {
                    val resultByVideoId =
                        batchResponse
                            .getOrThrow()
                            .playlistEditResults
                            .map { it.playlistEditVideoAddedResultData }
                            .filter { result -> result.setVideoId.isNotBlank() }
                            .associateBy { it.videoId }

                    batch.forEach { videoId ->
                        val setVideoId =
                            resultByVideoId[videoId]?.setVideoId
                                ?: throw IllegalStateException("Playlist edit did not confirm added video $videoId")
                        setVideoIds += setVideoId
                        completedSongs += 1
                    }
                } else if (batch.size == 1) {
                    throw batchResponse.exceptionOrNull() ?: IllegalStateException("Playlist edit failed")
                } else {
                    batch.forEach { videoId ->
                        val setVideoId = addToPlaylist(playlistId, videoId).getOrThrow()
                        setVideoIds += setVideoId
                        completedSongs += 1
                    }
                }
                onProgress(completedSongs, totalSongs)
            }

            setVideoIds
        }

    suspend fun addPlaylistToPlaylist(
        playlistId: String,
        addPlaylistId: String,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
        }
    }

    suspend fun playlistEntrySetVideoIds(
        playlistId: String,
        videoId: String,
    ) = runCatching {
        val setVideoIds = mutableListOf<String>()

        fun collectSetVideoIds(songs: List<SongItem>) {
            setVideoIds +=
                songs
                    .asSequence()
                    .filter { song -> song.id == videoId }
                    .mapNotNull(SongItem::setVideoId)
                    .toList()
        }

        val playlistPage = playlist(playlistId).getOrThrow()
        collectSetVideoIds(playlistPage.songs)

        var continuation =
            playlistPage.songsContinuation?.takeUnless(String::isBlank)
                ?: playlistPage.continuation?.takeUnless(String::isBlank)
        while (continuation != null) {
            val continuationPage = playlistContinuation(continuation, playlistId).getOrThrow()
            collectSetVideoIds(continuationPage.songs)
            continuation = continuationPage.continuation?.takeUnless(String::isBlank)
        }

        setVideoIds.distinct()
    }

    suspend fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
        }
    }

    suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
        }
    }

    suspend fun createPlaylist(
        title: String,
        videoIds: List<String> = emptyList(),
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.createPlaylist(WEB_REMIX, title, videoIds).body<CreatePlaylistResponse>()
        }.playlistId
    }

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
        }
    }

    suspend fun deletePlaylist(playlistId: String) =
        runCatching {
            withPlaylistMutationAuthRecovery {
                innerTube.deletePlaylist(WEB_REMIX, playlistId)
            }
        }
}
