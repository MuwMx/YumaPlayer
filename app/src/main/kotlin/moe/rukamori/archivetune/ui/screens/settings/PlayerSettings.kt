/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
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
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.GlassScaffold
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun PlayerSettings(navController: NavController) {
    val context = LocalContext.current

    val (playbackSource, onPlaybackSourceChange) = rememberEnumPreference(
        PlaybackSourceKey,
        defaultValue = PlaybackSource.YT_MUSIC,
    )
    val (flacStreamingQuality, onFlacStreamingQualityChange) = rememberEnumPreference(
        FlacStreamingQualityKey,
        defaultValue = FlacQuality.CD,
    )
    val (flacDownloadQuality, onFlacDownloadQualityChange) = rememberEnumPreference(
        FlacDownloadQualityKey,
        defaultValue = FlacQuality.HI_RES,
    )
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO,
    )
    val (lowDataMode, onLowDataModeChange) = rememberPreference(
        LowDataModeKey,
        defaultValue = true,
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false,
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true,
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false,
    )
    val (pauseOnDeviceMute, onPauseOnDeviceMuteChange) = rememberPreference(
        PauseOnDeviceMuteKey,
        defaultValue = false,
    )
    val (autoStartOnBluetooth, onAutoStartOnBluetoothChange) = rememberPreference(
        AutoStartOnBluetoothKey,
        defaultValue = false,
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false,
    )
    val (wakelockEnabled, onWakelockChange) = rememberPreference(
        WakelockKey,
        defaultValue = false,
    )

    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false,
    )
    val (crossfadeDurationSeconds, onCrossfadeDurationSecondsChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f,
    )
    val (crossfadeGapless, onCrossfadeGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true,
    )

    val (_, onEnableLosslessChange) = rememberPreference(EnableLosslessKey, false)
    val (memoryCacheToggle, onMemoryCacheToggleChange) = rememberPreference(MemoryCacheToggleKey, false)
    val (downloadLocationUri, onDownloadLocationUriChange) = rememberPreference(DownloadLocationUriKey, "")

    val flacFolderPath = remember(downloadLocationUri, context) {
        resolveFlacFolderPath(context, downloadLocationUri)
    }
    val (qobuzAppId, onQobuzAppIdChange) = rememberPreference(QobuzAppIdKey, "")
    val (qobuzAppSecret, onQobuzAppSecretChange) = rememberPreference(QobuzAppSecretKey, "")
    val (qobuzUserAuthToken, onQobuzUserAuthTokenChange) = rememberPreference(QobuzUserAuthTokenKey, "")

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                onDownloadLocationUriChange(it.toString())
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Toast.makeText(context, context.getString(R.string.folder_persist_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val onEqualizerClick = remember(context) {
        {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.equalizer),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val state = PlayerSettingsUiState(
        playbackSource = playbackSource,
        flacStreamingQuality = flacStreamingQuality,
        flacDownloadQuality = flacDownloadQuality,
        audioQuality = audioQuality,
        lowDataMode = lowDataMode,
        skipSilence = skipSilence,
        audioNormalization = audioNormalization,
        autoSkipNextOnError = autoSkipNextOnError,
        pauseOnDeviceMute = pauseOnDeviceMute,
        autoStartOnBluetooth = autoStartOnBluetooth,
        stopMusicOnTaskClear = stopMusicOnTaskClear,
        wakelockEnabled = wakelockEnabled,
        crossfadeEnabled = crossfadeEnabled,
        crossfadeDurationSeconds = crossfadeDurationSeconds,
        crossfadeGapless = crossfadeGapless,
        memoryCacheToggle = memoryCacheToggle,
        downloadLocationUri = downloadLocationUri,
        flacFolderPath = flacFolderPath,
        qobuzAppId = qobuzAppId,
        qobuzAppSecret = qobuzAppSecret,
        qobuzUserAuthToken = qobuzUserAuthToken,
    )

    val actions = remember(navController, folderPickerLauncher, onEqualizerClick) {
        PlayerSettingsUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onPlaybackSourceChange = onPlaybackSourceChange,
            onEnableLosslessChange = onEnableLosslessChange,
            onFlacStreamingQualityChange = onFlacStreamingQualityChange,
            onFlacDownloadQualityChange = onFlacDownloadQualityChange,
            onAudioQualityChange = onAudioQualityChange,
            onLowDataModeChange = onLowDataModeChange,
            onSkipSilenceChange = onSkipSilenceChange,
            onAudioNormalizationChange = onAudioNormalizationChange,
            onAutoSkipNextOnErrorChange = onAutoSkipNextOnErrorChange,
            onPauseOnDeviceMuteChange = onPauseOnDeviceMuteChange,
            onAutoStartOnBluetoothChange = onAutoStartOnBluetoothChange,
            onStopMusicOnTaskClearChange = onStopMusicOnTaskClearChange,
            onWakelockChange = onWakelockChange,
            onCrossfadeEnabledChange = onCrossfadeEnabledChange,
            onCrossfadeDurationSecondsChange = onCrossfadeDurationSecondsChange,
            onCrossfadeGaplessChange = onCrossfadeGaplessChange,
            onMemoryCacheToggleChange = onMemoryCacheToggleChange,
            onSelectFlacDownloadFolder = { folderPickerLauncher.launch(null) },
            onQobuzAppIdChange = onQobuzAppIdChange,
            onQobuzAppSecretChange = onQobuzAppSecretChange,
            onQobuzUserAuthTokenChange = onQobuzUserAuthTokenChange,
            onEqualizerClick = onEqualizerClick,
        )
    }

    PlayerSettingsScreen(
        state = state,
        actions = actions,
    )
}

@Composable
internal fun PlayerSettingsScreen(
    state: PlayerSettingsUiState,
    actions: PlayerSettingsUiActions,
) {
    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_and_audio)) },
                navigationIcon = {
                    IconButton(
                        onClick = actions.onNavigateUp,
                        onLongClick = actions.onNavigateHome,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = GlassDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        PlayerSettingsContent(
            state = state,
            actions = actions,
            modifier = Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
        )
    }
}

@ThemePreviews
@Composable
private fun PlayerSettingsPreview() {
    TestThemeWrapper {
        PlayerSettings(navController = rememberNavController())
    }
}

private fun resolveFlacFolderPath(context: Context, downloadLocationUri: String): String? {
    if (downloadLocationUri.isBlank()) {
        return null
    }
    return runCatching {
        val uri = Uri.parse(downloadLocationUri)
        val docFile = DocumentFile.fromTreeUri(context, uri)
        val name = docFile?.name?.takeIf { it.isNotBlank() }
        val rawPath = uri.lastPathSegment?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
        name ?: rawPath ?: downloadLocationUri
    }.getOrNull() ?: downloadLocationUri
}
