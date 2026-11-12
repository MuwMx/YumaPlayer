/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.body.Action
import moe.rukamori.archivetune.innertube.models.body.CreatePlaylistBody
import moe.rukamori.archivetune.innertube.models.body.EditPlaylistBody
import moe.rukamori.archivetune.innertube.models.body.LikeBody
import moe.rukamori.archivetune.innertube.models.body.PlaylistDeleteBody
import moe.rukamori.archivetune.innertube.models.body.SubscribeBody

internal class InnerTubeLibrary(private val http: InnerTubeHttp) {
    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        val response =
            http.client.post("like/like") {
                with(http) {
                    ytClient(client, setLogin = true, authState = authState)
                }
                setBody(
                    LikeBody(
                        context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                        target = LikeBody.Target.VideoTarget(videoId),
                    ),
                )
            }
        with(http) {
            response.ensureNoInnertubeError("likeVideo($videoId)")
        }
    }

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        val response =
            http.client.post("like/removelike") {
                with(http) {
                    ytClient(client, setLogin = true, authState = authState)
                }
                setBody(
                    LikeBody(
                        context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                        target = LikeBody.Target.VideoTarget(videoId),
                    ),
                )
            }
        with(http) {
            response.ensureNoInnertubeError("unlikeVideo($videoId)")
        }
    }

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("subscription/subscribe") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    channelIds = listOf(channelId),
                ),
            )
        }
    }

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("subscription/unsubscribe") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    channelIds = listOf(channelId),
                ),
            )
        }
    }

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("like/like") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                LikeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId),
                ),
            )
        }
    }

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("like/removelike") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                LikeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId),
                ),
            )
        }
    }

    suspend fun addToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("browse/edit_playlist") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        listOf(
                            Action.AddVideoAction(addedVideoId = videoId),
                        ),
                ),
            )
        }
    }

    suspend fun addSongsToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoIds: List<String>,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("browse/edit_playlist") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        videoIds.map { videoId ->
                            Action.AddVideoAction(addedVideoId = videoId)
                        },
                ),
            )
        }
    }

    suspend fun addPlaylistToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        addPlaylistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("browse/edit_playlist") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        listOf(
                            Action.AddPlaylistAction(addedFullListId = addPlaylistId),
                        ),
                ),
            )
        }
    }

    suspend fun removeFromPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        setVideoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("browse/edit_playlist") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        listOf(
                            Action.RemoveVideoAction(
                                removedVideoId = videoId,
                                setVideoId = setVideoId,
                            ),
                        ),
                ),
            )
        }
    }

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("browse/edit_playlist") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    playlistId = playlistId,
                    actions =
                        listOf(
                            Action.MoveVideoAction(
                                movedSetVideoIdSuccessor = successorSetVideoId,
                                setVideoId = setVideoId,
                            ),
                        ),
                ),
            )
        }
    }

    suspend fun createPlaylist(
        client: YouTubeClient,
        title: String,
        videoIds: List<String> = emptyList(),
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("playlist/create") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                CreatePlaylistBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    title = title,
                    videoIds = videoIds,
                ),
            )
        }
    }

    suspend fun renamePlaylist(
        client: YouTubeClient,
        playlistId: String,
        name: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("browse/edit_playlist") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    playlistId = playlistId,
                    actions =
                        listOf(
                            Action.RenamePlaylistAction(
                                playlistName = name,
                            ),
                        ),
                ),
            )
        }
    }

    suspend fun deletePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("playlist/delete") {
            println("deleting $playlistId")
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                PlaylistDeleteBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    playlistId = playlistId,
                ),
            )
        }
    }
}
