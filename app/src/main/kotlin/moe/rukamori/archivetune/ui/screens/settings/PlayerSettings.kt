@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.GlassScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import moe.rukamori.archivetune.constants.MemoryCacheToggleKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.ui.component.CrossfadeSliderPreference
import moe.rukamori.archivetune.ui.component.EditTextPreference
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceGroupScope
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
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

    val flacFolderPath = remember(downloadLocationUri) {
        if (downloadLocationUri.isBlank()) {
            null
        } else {
            runCatching {
                val uri = Uri.parse(downloadLocationUri)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                val name = docFile?.name?.takeIf { it.isNotBlank() }
                val rawPath = uri.lastPathSegment?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
                name ?: rawPath ?: downloadLocationUri
            }.getOrNull() ?: downloadLocationUri
        }
    }

    val (qobuzAppId, onQobuzAppIdChange) = rememberPreference(moe.rukamori.archivetune.constants.QobuzAppIdKey, "")
    val (qobuzAppSecret, onQobuzAppSecretChange) = rememberPreference(moe.rukamori.archivetune.constants.QobuzAppSecretKey, "")
    val (qobuzUserAuthToken, onQobuzUserAuthTokenChange) = rememberPreference(moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey, "")

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

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_and_audio)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
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

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.lossless_integration)) {
                item {
                    PlaybackSourceSelector(
                        playbackSource = playbackSource,
                        onPlaybackSourceChange = onPlaybackSourceChange,
                        onEnableLosslessChange = onEnableLosslessChange
                    )
                }
                if (playbackSource == PlaybackSource.FLAC) {
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.flac_streaming_quality)) },
                            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                            selectedValue = flacStreamingQuality,
                            onValueSelected = onFlacStreamingQualityChange,
                            valueText = { quality ->
                                when (quality) {
                                    FlacQuality.CD -> stringResource(R.string.flac_quality_cd)
                                    FlacQuality.HI_RES -> stringResource(R.string.flac_quality_hi_res)
                                    FlacQuality.MAX -> stringResource(R.string.flac_quality_max)
                                }
                            },
                        )
                    }
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.flac_download_quality)) },
                            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                            selectedValue = flacDownloadQuality,
                            onValueSelected = onFlacDownloadQualityChange,
                            valueText = { quality ->
                                when (quality) {
                                    FlacQuality.CD -> stringResource(R.string.flac_quality_cd)
                                    FlacQuality.HI_RES -> stringResource(R.string.flac_quality_hi_res)
                                    FlacQuality.MAX -> stringResource(R.string.flac_quality_max)
                                }
                            },
                        )
                    }
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.memory_cache_toggle)) },
                            icon = { Icon(painterResource(R.drawable.cached), null) },
                            checked = memoryCacheToggle,
                            onCheckedChange = onMemoryCacheToggleChange,
                        )
                    }
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.select_flac_download_folder)) },
                            description = flacFolderPath,
                            icon = { Icon(painterResource(R.drawable.snippet_folder), null) },
                            onClick = { folderPickerLauncher.launch(null) },
                        )
                    }
                    FlacTokenInputs(
                        qobuzAppId = qobuzAppId,
                        onQobuzAppIdChange = onQobuzAppIdChange,
                        qobuzAppSecret = qobuzAppSecret,
                        onQobuzAppSecretChange = onQobuzAppSecretChange,
                        qobuzUserAuthToken = qobuzUserAuthToken,
                        onQobuzUserAuthTokenChange = onQobuzUserAuthTokenChange
                    )
                } else {
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.audio_quality)) },
                            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                            selectedValue = audioQuality,
                            onValueSelected = onAudioQualityChange,
                            valueText = { quality ->
                                when (quality) {
                                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                                    AudioQuality.HIGHEST -> stringResource(R.string.audio_quality_highest)
                                }
                            },
                        )
                    }
                }
            }

            PreferenceGroup(title = stringResource(R.string.player_and_audio)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.skip_silence)) },
                        icon = { Icon(painterResource(R.drawable.skip_next), null) },
                        checked = skipSilence,
                        onCheckedChange = onSkipSilenceChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.audio_normalization)) },
                        icon = { Icon(painterResource(R.drawable.volume_up), null) },
                        checked = audioNormalization,
                        onCheckedChange = onAudioNormalizationChange,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.equalizer)) },
                        icon = { Icon(painterResource(R.drawable.equalizer), null) },
                        onClick = {
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
                        },
                        showChevron = true,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.audio_crossfade_title)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.audio_crossfade_title)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        checked = crossfadeEnabled,
                        onCheckedChange = onCrossfadeEnabledChange,
                    )
                }
                item {
                    CrossfadeSliderPreference(
                        valueSeconds = crossfadeDurationSeconds,
                        onValueChange = onCrossfadeDurationSecondsChange,
                        isEnabled = crossfadeEnabled,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.crossfade_gapless_title)) },
                        description = stringResource(R.string.crossfade_gapless_description),
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        checked = crossfadeGapless,
                        onCheckedChange = onCrossfadeGaplessChange,
                        isEnabled = crossfadeEnabled,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.player)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_start_on_bluetooth)) },
                        icon = { Icon(painterResource(R.drawable.bluetooth), null) },
                        checked = autoStartOnBluetooth,
                        onCheckedChange = onAutoStartOnBluetoothChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_on_device_mute)) },
                        icon = { Icon(painterResource(R.drawable.volume_off), null) },
                        checked = pauseOnDeviceMute,
                        onCheckedChange = onPauseOnDeviceMuteChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                        icon = { Icon(painterResource(R.drawable.skip_next), null) },
                        checked = autoSkipNextOnError,
                        onCheckedChange = onAutoSkipNextOnErrorChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.wakelock)) },
                        description = stringResource(R.string.wakelock_desc),
                        icon = { Icon(painterResource(R.drawable.lock), null) },
                        checked = wakelockEnabled,
                        onCheckedChange = onWakelockChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.low_data_mode_title)) },
                        description = stringResource(R.string.low_data_mode_description),
                        icon = { Icon(painterResource(R.drawable.android_cell), null) },
                        checked = lowDataMode,
                        onCheckedChange = onLowDataModeChange,
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun PlayerSettingsPreview() {
    TestThemeWrapper {
        PlayerSettings(navController = rememberNavController())
    }
}

@Composable
fun PlaybackSourceSelector(
    playbackSource: PlaybackSource,
    onPlaybackSourceChange: (PlaybackSource) -> Unit,
    onEnableLosslessChange: (Boolean) -> Unit
) {
    EnumListPreference(
        title = { Text(stringResource(R.string.playback_source)) },
        icon = { Icon(painterResource(R.drawable.album), null) },
        selectedValue = playbackSource,
        onValueSelected = { 
            onPlaybackSourceChange(it)
            onEnableLosslessChange(it == PlaybackSource.FLAC)
        },
        valueText = { source ->
            when (source) {
                PlaybackSource.YT_MUSIC -> stringResource(R.string.source_yt_music)
                PlaybackSource.FLAC -> stringResource(R.string.source_flac)
            }
        },
    )
}


fun PreferenceGroupScope.FlacTokenInputs(
    qobuzAppId: String,
    onQobuzAppIdChange: (String) -> Unit,
    qobuzAppSecret: String,
    onQobuzAppSecretChange: (String) -> Unit,
    qobuzUserAuthToken: String,
    onQobuzUserAuthTokenChange: (String) -> Unit
) {
    item {
        EditTextPreference(
            title = { Text(stringResource(R.string.qobuz_app_id)) },
            icon = { Icon(painterResource(R.drawable.lock), null) },
            value = qobuzAppId,
            onValueChange = onQobuzAppIdChange,
        )
    }
    item {
        EditTextPreference(
            title = { Text(stringResource(R.string.qobuz_app_secret)) },
            icon = { Icon(painterResource(R.drawable.lock), null) },
            value = qobuzAppSecret,
            onValueChange = onQobuzAppSecretChange,
        )
    }
    item {
        EditTextPreference(
            title = { Text(stringResource(R.string.qobuz_user_auth_token)) },
            icon = { Icon(painterResource(R.drawable.lock), null) },
            value = qobuzUserAuthToken,
            onValueChange = onQobuzUserAuthTokenChange,
        )
    }
}
