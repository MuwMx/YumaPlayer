package moe.rukamori.archivetune.ui.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

@Stable
internal class DeviceMusicVolumeController(
    private val audioManager: AudioManager,
) {
    private var minVolume by mutableIntStateOf(readMinVolume())
    private var maxVolume by mutableIntStateOf(readMaxVolume())
    var volumeFraction by mutableFloatStateOf(readVolumeFraction())
        private set

    fun refresh() {
        minVolume = readMinVolume()
        maxVolume = readMaxVolume()
        volumeFraction = readVolumeFraction()
    }

    @JvmName("setDeviceMusicVolumeFraction")
    fun setVolumeFraction(fraction: Float) {
        val safeFraction = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: volumeFraction
        val volumeRange = (maxVolume - minVolume).coerceAtLeast(1)
        val targetVolume =
            (minVolume + (safeFraction * volumeRange).roundToInt())
                .coerceIn(minVolume, maxVolume)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        refresh()
    }

    private fun readVolumeFraction(): Float {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeRange = (maxVolume - minVolume).coerceAtLeast(1)
        return ((currentVolume - minVolume).toFloat() / volumeRange.toFloat()).coerceIn(0f, 1f)
    }

    private fun readMaxVolume(): Int {
        val streamMinVolume = readMinVolume()
        return audioManager
            .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            .coerceAtLeast(streamMinVolume + 1)
    }

    private fun readMinVolume(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
}

@Composable
internal fun rememberDeviceMusicVolumeController(): DeviceMusicVolumeController {
    val context = LocalContext.current
    val audioManager =
        remember(context) {
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    val controller =
        remember(audioManager) {
            DeviceMusicVolumeController(audioManager)
        }

    DisposableEffect(context, controller) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    controller.refresh()
                }
            }
        val contentResolver = context.applicationContext.contentResolver
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        controller.refresh()
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return controller
}
