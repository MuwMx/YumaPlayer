package moe.rukamori.archivetune.ui

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.deeplink.ResolveAlbumBrowseIdUseCase
import moe.rukamori.archivetune.deeplink.ResolveQueueMediaItemUseCase
import moe.rukamori.archivetune.deeplink.ResolveWatchPlaylistEndpointUseCase
import moe.rukamori.archivetune.models.ParsedIntentAction
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.joinTogether
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import moe.rukamori.archivetune.ui.state.PlayerEvent

class DeepLinkHandler(
    private val coroutineScope: CoroutineScope,
    private val playerConnectionProvider: () -> PlayerConnection?,
    private val requestSheetCollapse: () -> Unit,
    private val sendEvent: suspend (PlayerEvent) -> Unit,
    private val resolveAlbumBrowseIdUseCase: ResolveAlbumBrowseIdUseCase?,
    private val resolveQueueMediaItemUseCase: ResolveQueueMediaItemUseCase?,
    private val resolveWatchPlaylistEndpointUseCase: ResolveWatchPlaylistEndpointUseCase?,
) {
    fun handleDeepLinkAction(action: ParsedIntentAction) {
        when (action) {
            is ParsedIntentAction.TogetherJoin -> {
                coroutineScope.launch {
                    val connection = playerConnectionProvider() ?: return@launch
                    val displayName = Build.MODEL ?: "Yuma Player"
                    connection.service.joinTogether(action.uri.toString(), displayName)
                }
            }
            is ParsedIntentAction.Login -> {
                coroutineScope.launch {
                    requestSheetCollapse()
                    sendEvent(PlayerEvent.Navigate(buildLoginRoute(action.loginUrl)))
                }
            }
            is ParsedIntentAction.YouTubePlaylist -> {
                val playlistId = action.playlistId
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch {
                        resolveAlbumBrowseIdUseCase?.invoke(playlistId)?.onSuccess { browseId ->
                            if (browseId != null) {
                                requestSheetCollapse()
                                sendEvent(PlayerEvent.Navigate("album/$browseId"))
                            }
                        }
                    }
                } else {
                    coroutineScope.launch {
                        requestSheetCollapse()
                        sendEvent(PlayerEvent.Navigate("online_playlist/$playlistId"))
                    }
                }
            }
            is ParsedIntentAction.YouTubeAlbum -> {
                coroutineScope.launch {
                    requestSheetCollapse()
                    sendEvent(PlayerEvent.Navigate("album/${action.browseId}"))
                }
            }
            is ParsedIntentAction.YouTubeArtist -> {
                coroutineScope.launch {
                    requestSheetCollapse()
                    sendEvent(PlayerEvent.Navigate("artist/${action.artistId}"))
                }
            }
            is ParsedIntentAction.YouTubeVideo -> {
                coroutineScope.launch {
                    resolveQueueMediaItemUseCase?.invoke(action.videoId, action.playlistId)?.onSuccess { mediaItem ->
                        withContext(Dispatchers.Main) {
                            playerConnectionProvider()?.playQueue(ListQueue(items = listOf(mediaItem)))
                        }
                    }
                }
            }
            is ParsedIntentAction.YouTubeWatchPlaylist -> {
                coroutineScope.launch {
                    resolveWatchPlaylistEndpointUseCase?.invoke(action.playlistId, action.shuffle)?.onSuccess { endpoint ->
                        withContext(Dispatchers.Main) {
                            if (endpoint != null) {
                                playerConnectionProvider()?.playQueue(YouTubeQueue.playlist(endpoint))
                            } else {
                                requestSheetCollapse()
                                sendEvent(PlayerEvent.Navigate("online_playlist/${action.playlistId}"))
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
