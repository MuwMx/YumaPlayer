package moe.rukamori.archivetune

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.models.ParsedIntentAction
import moe.rukamori.archivetune.musicrecognition.openMusicRecognition
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.joinTogether
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.IntentParser
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException

class MainIntentRouter(
    private val activity: MainActivity,
    private val serviceBinding: MusicServiceBinding,
) {
    var pendingIntent: Intent? = null
    var pendingDeepLinkQueue: Queue? = null
        private set
    var pendingVoiceSearchQuery: String? = null
        private set
    var pendingAodModeRequest = false
        private set
    private var pendingAodModeJob: Job? = null
    var aodModeLaunchRequestCount by mutableIntStateOf(0)
    var pendingTogetherJoinLink: String? = null
        private set
    var pendingBackupRestoreUri by mutableStateOf<Uri?>(null)

    private val playerConnection: PlayerConnection?
        get() = serviceBinding.playerConnection

    fun toExternalAudioMediaItem(uri: Uri): MediaItem {
        val mediaId = uri.toString()
        val title = IntentParser.resolveExternalAudioTitle(activity, uri)
        val metadata = moe.rukamori.archivetune.models.MediaMetadata(
            id = mediaId,
            title = title,
            artists = emptyList(),
            duration = -1,
        )
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setTag(metadata)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .setMediaType(MEDIA_TYPE_MUSIC)
                    .build()
            ).build()
    }

    fun playPendingDeepLinkQueueIfReady() {
        val pending = pendingDeepLinkQueue ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkQueue = null
        connection.playQueue(pending)
    }

    fun playPendingVoiceSearchIfReady() {
        val query = pendingVoiceSearchQuery ?: return
        val connection = playerConnection ?: return
        pendingVoiceSearchQuery = null
        connection.playFromVoiceSearch(query)
    }

    fun requestAodMode() {
        pendingAodModeRequest = true
        startMusicServiceSafely()
        openPendingAodModeIfReady()
    }

    fun openPendingAodModeIfReady() {
        if (!pendingAodModeRequest) return
        val connection = playerConnection ?: return
        pendingAodModeRequest = false
        pendingAodModeJob?.cancel()
        pendingAodModeJob =
            activity.lifecycleScope.launch {
                connection.queueRestoreCompleted.first { it }
                if (awaitRestorablePlayback(connection)) {
                    aodModeLaunchRequestCount++
                }
            }
    }

    fun joinPendingTogetherIfReady() {
        val pending = pendingTogetherJoinLink ?: return
        val connection = playerConnection ?: return
        pendingTogetherJoinLink = null
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val displayName =
                runCatching { activity.dataStore.data.first()[moe.rukamori.archivetune.constants.TogetherDisplayNameKey] }
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Build.MODEL ?: activity.getString(R.string.app_name) }
            withContext(Dispatchers.Main) {
                connection.service.joinTogether(pending, displayName)
            }
        }
    }

    fun onServiceConnected(connection: PlayerConnection) {
        playPendingDeepLinkQueueIfReady()
        playPendingVoiceSearchIfReady()
        openPendingAodModeIfReady()
        joinPendingTogetherIfReady()
    }

    fun onServiceDisconnected() {
        pendingAodModeJob?.cancel()
        pendingAodModeJob = null
    }

    fun onStart() {
        playPendingDeepLinkQueueIfReady()
        openPendingAodModeIfReady()
    }

    fun startMusicServiceSafely() {
        runCatching { activity.startService(Intent(activity, MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        if (intent == null) return

        intent.getStringExtra("navigate_to")?.takeIf { it.isNotBlank() }?.let { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
            intent.removeExtra("navigate_to")
            return
        }

        val action = IntentParser.parse(intent, activity) ?: return

        when (action) {
            is ParsedIntentAction.BackupRestore -> {
                pendingBackupRestoreUri = action.uri
            }
            is ParsedIntentAction.MusicRecognition -> {
                navController.openMusicRecognition()
            }
            is ParsedIntentAction.AodMode -> {
                requestAodMode()
            }
            is ParsedIntentAction.VoiceSearch -> {
                pendingVoiceSearchQuery = action.query
                startMusicServiceSafely()
                playPendingVoiceSearchIfReady()
            }
            is ParsedIntentAction.ExternalAudio -> {
                val mediaItems = action.uris.map { uri -> toExternalAudioMediaItem(uri) }
                pendingDeepLinkQueue = ListQueue(items = mediaItems)
                startMusicServiceSafely()
                playPendingDeepLinkQueueIfReady()
            }
            is ParsedIntentAction.TogetherJoin,
            is ParsedIntentAction.Login,
            is ParsedIntentAction.YouTubePlaylist,
            is ParsedIntentAction.YouTubeAlbum,
            is ParsedIntentAction.YouTubeArtist,
            is ParsedIntentAction.YouTubeVideo,
            is ParsedIntentAction.YouTubeWatchPlaylist -> {
                startMusicServiceSafely()
                activity.playerViewModel.handleDeepLinkAction(action)
            }
        }
    }
}

internal suspend fun awaitRestorablePlayback(connection: PlayerConnection): Boolean {
    repeat(15) {
        if (
            connection.player.currentMediaItem != null ||
            connection.player.mediaItemCount > 0 ||
            connection.mediaMetadata.value != null
        ) {
            return true
        }
        delay(100)
    }

    return (
        connection.player.currentMediaItem != null ||
            connection.player.mediaItemCount > 0 ||
            connection.mediaMetadata.value != null
    )
}
