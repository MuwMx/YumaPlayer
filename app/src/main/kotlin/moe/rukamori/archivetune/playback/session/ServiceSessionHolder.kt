/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback.session

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.ArchiveTuneMediaNotificationProvider
import moe.rukamori.archivetune.playback.MediaLibrarySessionCallback
import moe.rukamori.archivetune.playback.MusicServiceNotification
import moe.rukamori.archivetune.utils.CoilBitmapLoader
import moe.rukamori.archivetune.utils.reportException

@UnstableApi
class ServiceSessionHolder {
    lateinit var mediaSession: MediaLibrarySession
    var sessionToken: SessionToken? = null

    fun buildMediaLibrarySession(
        service: MediaLibraryService,
        player: Player,
        callback: MediaLibrarySessionCallback,
        scope: CoroutineScope,
    ): MediaLibrarySession {
        mediaSession =
            MediaLibrarySession
                .Builder(service, player, callback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        service,
                        0,
                        Intent(service, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(service, scope))
                .build()
        return mediaSession
    }

    fun createNotificationProvider(context: Context): MediaNotification.Provider =
        ArchiveTuneMediaNotificationProvider(
            context = context,
            smallIconResId = R.drawable.small_icon,
        )

    fun initSessionTokenAndController(
        service: Service,
        serviceClass: Class<*>,
    ): SessionToken {
        val token = SessionToken(service, ComponentName(service, serviceClass))
        sessionToken = token
        val controllerFuture = MediaController.Builder(service, token).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())
        return token
    }

    fun updateNotification(
        context: Context,
        player: Player,
        mediaMetadata: MediaMetadata?,
        hasMetadata: Boolean,
        isLiked: Boolean,
    ) {
        MusicServiceNotification.updateNotification(
            context = context,
            mediaSession = mediaSession,
            player = player,
            mediaMetadata = mediaMetadata,
            hasMetadata = hasMetadata,
            isLiked = isLiked,
        )
    }

    fun refreshPlaybackNotification(
        context: Context,
        player: Player,
        mediaMetadata: MediaMetadata?,
        hasMetadata: Boolean,
        isLiked: Boolean,
        onUpdateNotification: (MediaSession, Boolean) -> Unit,
    ) {
        MusicServiceNotification.refreshPlaybackNotification(
            context = context,
            mediaSession = mediaSession,
            player = player,
            mediaMetadata = mediaMetadata,
            hasMetadata = hasMetadata,
            isLiked = isLiked,
            onUpdateNotification = onUpdateNotification,
        )
    }

    fun showTogetherParticipantNotification(
        context: Context,
        participantName: String,
        joined: Boolean,
    ) {
        MusicServiceNotification.showTogetherParticipantNotification(
            context = context,
            notificationManager = context.getSystemService(NotificationManager::class.java),
            participantName = participantName,
            joined = joined,
        )
    }

    fun startForegroundPlaybackService(service: Service): Boolean {
        val notification =
            try {
                MusicServiceNotification.buildForegroundNotification(service)
            } catch (e: Exception) {
                reportException(e)
                return false
            }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(
                    MusicServiceNotification.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                service.startForeground(MusicServiceNotification.NOTIFICATION_ID, notification)
            }
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            reportException(e)
            false
        } catch (e: IllegalStateException) {
            reportException(e)
            false
        } catch (e: Exception) {
            reportException(e)
            false
        }
    }

    fun stopForegroundService(service: Service) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                service.stopForeground(true)
            }
        }
    }

    fun releaseSession() {
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
    }
}
