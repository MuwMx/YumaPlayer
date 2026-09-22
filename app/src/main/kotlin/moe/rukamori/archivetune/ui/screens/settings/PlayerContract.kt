/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.AutoSkipNextOnErrorKey
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.DownloadLocationUriKey
import moe.rukamori.archivetune.constants.EnableLosslessKey
import moe.rukamori.archivetune.constants.FlacDownloadQualityKey
import moe.rukamori.archivetune.constants.FlacQuality
import moe.rukamori.archivetune.constants.FlacStreamingQualityKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.MemoryCacheToggleKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.QobuzAppIdKey
import moe.rukamori.archivetune.constants.QobuzAppSecretKey
import moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.WakelockKey

internal object PlayerContract {
    val PlaybackSource = PlaybackSourceKey
    val FlacStreamingQuality = FlacStreamingQualityKey
    val FlacDownloadQuality = FlacDownloadQualityKey
    val AudioQuality = AudioQualityKey
    val LowDataMode = LowDataModeKey
    val SkipSilence = SkipSilenceKey
    val AudioNormalization = AudioNormalizationKey
    val AutoSkipNextOnError = AutoSkipNextOnErrorKey
    val PauseOnDeviceMute = PauseOnDeviceMuteKey
    val AutoStartOnBluetooth = AutoStartOnBluetoothKey
    val StopMusicOnTaskClear = StopMusicOnTaskClearKey
    val Wakelock = WakelockKey
    val CrossfadeEnabled = CrossfadeEnabledKey
    val CrossfadeDuration = CrossfadeDurationKey
    val CrossfadeGapless = CrossfadeGaplessKey
    val EnableLossless = EnableLosslessKey
    val MemoryCacheToggle = MemoryCacheToggleKey
    val DownloadLocationUri = DownloadLocationUriKey
    val QobuzAppId = QobuzAppIdKey
    val QobuzAppSecret = QobuzAppSecretKey
    val QobuzUserAuthToken = QobuzUserAuthTokenKey
}

@Immutable
data class PlayerSettingsUiState(
    val playbackSource: PlaybackSource = PlaybackSource.YT_MUSIC,
    val flacStreamingQuality: FlacQuality = FlacQuality.CD,
    val flacDownloadQuality: FlacQuality = FlacQuality.HI_RES,
    val audioQuality: AudioQuality = AudioQuality.AUTO,
    val lowDataMode: Boolean = true,
    val skipSilence: Boolean = false,
    val audioNormalization: Boolean = true,
    val autoSkipNextOnError: Boolean = false,
    val pauseOnDeviceMute: Boolean = false,
    val autoStartOnBluetooth: Boolean = false,
    val stopMusicOnTaskClear: Boolean = false,
    val wakelockEnabled: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationSeconds: Float = 5f,
    val crossfadeGapless: Boolean = true,
    val memoryCacheToggle: Boolean = false,
    val downloadLocationUri: String = "",
    val flacFolderPath: String? = null,
    val qobuzAppId: String = "",
    val qobuzAppSecret: String = "",
    val qobuzUserAuthToken: String = "",
)

@Immutable
data class PlayerSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onPlaybackSourceChange: (PlaybackSource) -> Unit = {},
    val onEnableLosslessChange: (Boolean) -> Unit = {},
    val onFlacStreamingQualityChange: (FlacQuality) -> Unit = {},
    val onFlacDownloadQualityChange: (FlacQuality) -> Unit = {},
    val onAudioQualityChange: (AudioQuality) -> Unit = {},
    val onLowDataModeChange: (Boolean) -> Unit = {},
    val onSkipSilenceChange: (Boolean) -> Unit = {},
    val onAudioNormalizationChange: (Boolean) -> Unit = {},
    val onAutoSkipNextOnErrorChange: (Boolean) -> Unit = {},
    val onPauseOnDeviceMuteChange: (Boolean) -> Unit = {},
    val onAutoStartOnBluetoothChange: (Boolean) -> Unit = {},
    val onStopMusicOnTaskClearChange: (Boolean) -> Unit = {},
    val onWakelockChange: (Boolean) -> Unit = {},
    val onCrossfadeEnabledChange: (Boolean) -> Unit = {},
    val onCrossfadeDurationSecondsChange: (Float) -> Unit = {},
    val onCrossfadeGaplessChange: (Boolean) -> Unit = {},
    val onMemoryCacheToggleChange: (Boolean) -> Unit = {},
    val onSelectFlacDownloadFolder: () -> Unit = {},
    val onQobuzAppIdChange: (String) -> Unit = {},
    val onQobuzAppSecretChange: (String) -> Unit = {},
    val onQobuzUserAuthTokenChange: (String) -> Unit = {},
    val onEqualizerClick: () -> Unit = {},
)

typealias PlayerSettingsActions = PlayerSettingsUiActions
