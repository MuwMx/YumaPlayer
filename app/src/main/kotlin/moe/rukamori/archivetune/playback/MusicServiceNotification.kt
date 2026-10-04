/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleLike
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleRepeatMode
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleShuffle
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleStartRadio
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber

internal object MusicServiceNotification {
    const val CHANNEL_ID = "music_channel_01"
    const val ACTION_MEDIA_NOTIFICATION_DISMISSED =
        "moe.rukamori.archivetune.action.MEDIA_NOTIFICATION_DISMISSED"
    const val EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT =
        "moe.rukamori.archivetune.extra.MEDIA_NOTIFICATION_DELETE_INTENT"
    const val NOTIFICATION_ID = 888
    const val TOGETHER_NOTIFICATION_CHANNEL_ID = "together_room_events"
    const val TOGETHER_PARTICIPANT_NOTIFICATION_ID = 891
    private const val DISCORD_SYNC_TAG = "DiscordSync"

    fun createNotificationChannels(
        context: Context,
        notificationManager: NotificationManager?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.music_player),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    TOGETHER_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.music_together),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    fun buildForegroundNotification(context: Context): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(context.getString(R.string.music_player))
            .setContentText(context.getString(R.string.app_name))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun buildTogetherParticipantNotification(
        context: Context,
        participantName: String,
        joined: Boolean,
    ): Notification {
        val normalizedName = participantName.trim().ifBlank { context.getString(R.string.together_unknown_participant) }
        val contentText =
            context.getString(
                if (joined) {
                    R.string.together_participant_joined_notification
                } else {
                    R.string.together_participant_left_notification
                },
                normalizedName,
            )
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, TOGETHER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(context.getString(R.string.music_together))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
    }

    fun showTogetherParticipantNotification(
        context: Context,
        notificationManager: NotificationManager?,
        participantName: String,
        joined: Boolean,
    ) {
        val notification = buildTogetherParticipantNotification(context, participantName, joined)
        runCatching {
            notificationManager?.notify(TOGETHER_PARTICIPANT_NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Timber.tag("Together").v(error, "Unable to show participant notification")
        }
    }

    fun buildCustomLayout(
        context: Context,
        player: Player,
        hasMetadata: Boolean,
        isLiked: Boolean,
    ): List<CommandButton> {
        return listOf(
            CommandButton
                .Builder()
                .setDisplayName(
                    context.getString(
                        if (isLiked) {
                            R.string.action_remove_like
                        } else {
                            R.string.action_like
                        },
                    ),
                ).setIconResId(if (isLiked) R.drawable.favorite else R.drawable.favorite_border)
                .setSessionCommand(CommandToggleLike)
                .setEnabled(hasMetadata)
                .build(),
            CommandButton
                .Builder()
                .setDisplayName(
                    context.getString(
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_OFF -> R.string.repeat_mode_off
                            Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                            Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                            else -> R.string.repeat_mode_off
                        },
                    ),
                ).setIconResId(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> R.drawable.ic_repeat
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
                        else -> R.drawable.ic_repeat
                    },
                ).setSessionCommand(CommandToggleRepeatMode)
                .build(),
            CommandButton
                .Builder()
                .setDisplayName(
                    context.getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on),
                ).setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                .setSessionCommand(CommandToggleShuffle)
                .build(),
            CommandButton
                .Builder()
                .setDisplayName(context.getString(R.string.start_radio))
                .setIconResId(R.drawable.radio)
                .setSessionCommand(CommandToggleStartRadio)
                .setEnabled(hasMetadata)
                .build(),
        )
    }

    fun updateNotification(
        context: Context,
        mediaSession: MediaSession,
        player: Player,
        mediaMetadata: MediaMetadata?,
        hasMetadata: Boolean,
        isLiked: Boolean,
    ) {
        try {
            Timber.tag("MediaNotification").d("updateNotification: mediaId=${mediaMetadata?.id}, isLiked=$isLiked")
            val customLayout = buildCustomLayout(
                context = context,
                player = player,
                hasMetadata = hasMetadata,
                isLiked = isLiked,
            )
            mediaSession.setCustomLayout(customLayout)
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun hasResumablePlaybackNotification(player: Player): Boolean {
        val state = player.playbackState
        return player.mediaItemCount > 0 &&
            player.currentMediaItem != null &&
            state != Player.STATE_IDLE &&
            state != Player.STATE_ENDED
    }

    fun refreshPlaybackNotification(
        context: Context,
        mediaSession: MediaSession,
        player: Player,
        mediaMetadata: MediaMetadata?,
        hasMetadata: Boolean,
        isLiked: Boolean,
        onUpdateNotification: (MediaSession, Boolean) -> Unit,
    ) {
        updateNotification(
            context = context,
            mediaSession = mediaSession,
            player = player,
            mediaMetadata = mediaMetadata,
            hasMetadata = hasMetadata,
            isLiked = isLiked,
        )
        onUpdateNotification(mediaSession, hasResumablePlaybackNotification(player))
    }

    fun handleMediaNotificationDismissed(
        intent: Intent,
        isPlaying: Boolean,
        isForeground: Boolean,
        onDismissedWhilePausedInBackground: () -> Unit,
    ) {
        val originalDeleteIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT,
                    PendingIntent::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT)
            }

        if (!isPlaying && !isForeground) {
            onDismissedWhilePausedInBackground()
        } else if (!isPlaying) {
            Timber.tag(DISCORD_SYNC_TAG).d(
                "notification dismissed while paused but app is foreground; keeping paused RPC visible",
            )
        }

        runCatching {
            originalDeleteIntent?.send()
        }.onFailure {
            Timber.tag(DISCORD_SYNC_TAG).w(it, "failed to forward original notification delete intent")
        }
    }
}
