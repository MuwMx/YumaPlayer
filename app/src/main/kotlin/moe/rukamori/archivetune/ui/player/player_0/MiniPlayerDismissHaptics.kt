package moe.rukamori.archivetune.ui.player.player_0

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.compose.ui.util.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

internal class MiniPlayerDismissHaptics(
    private val context: Context,
    private val hapticView: View,
    private val hapticFeedbackEnabled: Boolean
) {
    private var lastVibeTime: Long = 0L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrateContinuousFriction(dragFraction: Float) {
        if (!hapticFeedbackEnabled || vibrator?.hasVibrator() != true) return
        val now = System.currentTimeMillis()
        if (now - lastVibeTime < 20L) return
        lastVibeTime = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = lerp(70f, 140f, dragFraction).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(35L, amplitude)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator?.vibrate(effect, attributes)
            } else {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                vibrator?.vibrate(effect, audioAttributes)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25L)
        }
    }

    internal fun performHaptic(feedbackConstant: Int) {
        if (!hapticFeedbackEnabled) return
        ViewCompat.performHapticFeedback(hapticView, feedbackConstant)
    }

    internal fun triggerTension(dragFraction: Float) {
        vibrateContinuousFriction(dragFraction)
    }

    internal fun triggerRelease(feedbackConstant: Int = HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE) {
        cancel()
        performHaptic(feedbackConstant)
    }

    internal fun cancel() {
        lastVibeTime = 0L
        vibrator?.cancel()
    }
}
