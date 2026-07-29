/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.ui.component.CrossfadeSliderPreference
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun PlayerSettings(navController: NavController) {
    val context = LocalContext.current

    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(
            AudioQualityKey,
            defaultValue = AudioQuality.AUTO,
        )
    val (skipSilence, onSkipSilenceChange) =
        rememberPreference(
            SkipSilenceKey,
            defaultValue = false,
        )
    val (audioNormalization, onAudioNormalizationChange) =
        rememberPreference(
            AudioNormalizationKey,
            defaultValue = true,
        )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) =
        rememberPreference(
            AutoSkipNextOnErrorKey,
            defaultValue = false,
        )
    val (pauseOnDeviceMute, onPauseOnDeviceMuteChange) =
        rememberPreference(
            PauseOnDeviceMuteKey,
            defaultValue = false,
        )
    val (autoStartOnBluetooth, onAutoStartOnBluetoothChange) =
        rememberPreference(
            AutoStartOnBluetoothKey,
            defaultValue = false,
        )
    val (wakelockEnabled, onWakelockChange) =
        rememberPreference(
            WakelockKey,
            defaultValue = false,
        )

    val (crossfadeEnabled, onCrossfadeEnabledChange) =
        rememberPreference(
            CrossfadeEnabledKey,
            defaultValue = false,
        )
    val (crossfadeDurationSeconds, onCrossfadeDurationSecondsChange) =
        rememberPreference(
            CrossfadeDurationKey,
            defaultValue = 5f,
        )
    val (crossfadeGapless, onCrossfadeGaplessChange) =
        rememberPreference(
            CrossfadeGaplessKey,
            defaultValue = true,
        )

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.player_and_audio)) },
        onBackClick = navController::navigateUp,
        onBackLongClick = navController::backToMain,
    ) {

        // Группа 1: Аудио и Звук (4 элемента)
        PreferenceGroup(title = stringResource(R.string.player_and_audio)) {
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
                            AudioQuality.HIGHEST -> stringResource(R.string.audio_quality_high)
                        }
                    },
                )
            }

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
                )
            }
        }

        // Группа 2: Кроссфейд и переходы (3 элемента)
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

        // Группа 3: Воспроизведение и автопаузы (4 элемента)
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
        }
    }
}
