/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions
import moe.rukamori.archivetune.R

class ArchiveTuneCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val receiverApplicationId =
            context
                .getString(R.string.cast_receiver_application_id)
                .takeIf(String::isNotBlank)
                ?: CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID

        val notificationOptions =
            NotificationOptions.Builder()
                .setActions(
                    listOf(
                        MediaIntentReceiver.ACTION_SKIP_PREV,
                        MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                        MediaIntentReceiver.ACTION_SKIP_NEXT,
                        MediaIntentReceiver.ACTION_STOP_CASTING,
                    ),
                    intArrayOf(1, 2),
                )
                .build()

        val mediaOptions =
            CastMediaOptions.Builder()
                .setNotificationOptions(notificationOptions)
                .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(receiverApplicationId)
            .setCastMediaOptions(mediaOptions)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
