/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab.quality

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.DownloadLocationUriKey
import moe.rukamori.archivetune.constants.EnableLosslessKey
import moe.rukamori.archivetune.constants.EnableMonochromeKey
import moe.rukamori.archivetune.constants.EnableQobuzKey
import moe.rukamori.archivetune.constants.FlacDownloadQualityKey
import moe.rukamori.archivetune.constants.FlacQuality
import moe.rukamori.archivetune.constants.FlacStreamingQualityKey
import moe.rukamori.archivetune.constants.LosslessProviderOrderKey
import moe.rukamori.archivetune.constants.MemoryCacheToggleKey
import moe.rukamori.archivetune.constants.MonochromeServersKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.QobuzAppIdKey
import moe.rukamori.archivetune.constants.QobuzAppSecretKey
import moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupTitle
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun AudioQualitySourcesScreen(navController: NavController) {
    val context = LocalContext.current
    val (enableLossless, onEnableLosslessChange) = rememberPreference(EnableLosslessKey, false)
    val (playbackSource, onPlaybackSourceChange) = rememberEnumPreference(PlaybackSourceKey, PlaybackSource.YT_MUSIC)
    val (enableMonochrome, onEnableMonochromeChange) = rememberPreference(EnableMonochromeKey, true)
    val (monochromeServers, onMonochromeServersChange) = rememberPreference(MonochromeServersKey, "")
    val (enableQobuz, onEnableQobuzChange) = rememberPreference(EnableQobuzKey, true)
    val (providerOrderRaw, onProviderOrderRawChange) = rememberPreference(LosslessProviderOrderKey, "monochrome,qbdlx")
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(AudioQualityKey, AudioQuality.AUTO)
    val (flacStreamingQuality, onFlacStreamingQualityChange) = rememberEnumPreference(FlacStreamingQualityKey, FlacQuality.CD)
    val (flacDownloadQuality, onFlacDownloadQualityChange) = rememberEnumPreference(FlacDownloadQualityKey, FlacQuality.HI_RES)
    val (memoryCacheToggle, onMemoryCacheToggleChange) = rememberPreference(MemoryCacheToggleKey, false)
    val (downloadLocationUri, onDownloadLocationUriChange) = rememberPreference(DownloadLocationUriKey, "")
    val (qobuzAppId, onQobuzAppIdChange) = rememberPreference(QobuzAppIdKey, "")
    val (qobuzAppSecret, onQobuzAppSecretChange) = rememberPreference(QobuzAppSecretKey, "")
    val (qobuzUserAuthToken, onQobuzUserAuthTokenChange) = rememberPreference(QobuzUserAuthTokenKey, "")
    var activeSheet by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            onDownloadLocationUriChange(it.toString())
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                Toast.makeText(context, context.getString(R.string.folder_persist_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val losslessEnabled = enableLossless || playbackSource == PlaybackSource.FLAC
    val providerOrder = remember(providerOrderRaw) { normalizeProviderOrder(providerOrderRaw) }
    val customRelayCount = remember(monochromeServers) { parseRelayServers(monochromeServers).size }
    val qobuzReady = qobuzAppId.isNotBlank() && qobuzAppSecret.isNotBlank() && qobuzUserAuthToken.isNotBlank()
    val flacFolderPath = remember(downloadLocationUri, context) { resolveFolderPath(context, downloadLocationUri) }

    val monochromeUi = ProviderItemUi(
        id = "monochrome",
        name = stringResource(R.string.provider_monochrome),
        status = when {
            !enableMonochrome -> stringResource(R.string.provider_status_monochrome_disabled)
            customRelayCount > 0 -> stringResource(R.string.provider_status_monochrome_relays, customRelayCount)
            else -> stringResource(R.string.provider_status_monochrome_default)
        },
        active = losslessEnabled && enableMonochrome,
    )
    val qobuzUi = ProviderItemUi(
        id = "qbdlx",
        name = stringResource(R.string.provider_qobuz),
        status = when {
            !enableQobuz -> stringResource(R.string.provider_status_qobuz_disabled)
            qobuzReady -> stringResource(R.string.provider_status_qobuz_ready)
            else -> stringResource(R.string.provider_status_qobuz_unconfigured)
        },
        active = losslessEnabled && enableQobuz && qobuzReady,
    )
    val ytMusicFallback = ProviderItemUi(
        id = "yt_music",
        name = stringResource(R.string.provider_yt_music),
        status = stringResource(R.string.provider_status_yt_fallback),
        active = true,
    )

    when (activeSheet) {
        "monochrome" -> MonochromeConfigSheet(
            enabled = enableMonochrome, onEnabledChange = onEnableMonochromeChange,
            serversRaw = monochromeServers, onServersRawChange = onMonochromeServersChange,
            onDismiss = { activeSheet = null },
        )
        "qbdlx" -> QobuzConfigSheet(
            enabled = enableQobuz, onEnabledChange = onEnableQobuzChange,
            appId = qobuzAppId, onAppIdChange = onQobuzAppIdChange,
            appSecret = qobuzAppSecret, onAppSecretChange = onQobuzAppSecretChange,
            userAuthToken = qobuzUserAuthToken, onUserAuthTokenChange = onQobuzUserAuthTokenChange,
            onDismiss = { activeSheet = null },
        )
    }

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.audio_quality_sources_title)) },
        onBackClick = { navController.navigateUp() },
    ) {
        PreferenceGroupTitle(title = stringResource(R.string.stream_priority_section))
        ProviderOrderCard(
            order = providerOrder,
            providerUi = { id -> if (id == "monochrome") monochromeUi else qobuzUi },
            fallbackItem = ytMusicFallback,
            onReorder = { updated -> onProviderOrderRawChange(updated.joinToString(",")) },
            onTap = { id -> activeSheet = id },
            modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        )
        Text(
            text = stringResource(R.string.stream_priority_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                vertical = SettingsDimensions.SectionHeaderBottomPadding,
            ),
        )
        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        PreferenceGroup(title = stringResource(R.string.quality_settings_section)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_lossless)) },
                    icon = { Icon(painterResource(R.drawable.album), null) },
                    checked = losslessEnabled,
                    onCheckedChange = { enabled ->
                        onEnableLosslessChange(enabled)
                        onPlaybackSourceChange(if (enabled) PlaybackSource.FLAC else PlaybackSource.YT_MUSIC)
                    },
                )
            }
            item {
                EnumListPreference(
                    title = { Text(stringResource(R.string.audio_quality_streaming)) },
                    icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                    selectedValue = audioQuality,
                    onValueSelected = onAudioQualityChange,
                    valueText = { q -> audioQualityLabel(q) },
                )
            }
            if (losslessEnabled) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.flac_streaming_quality)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = flacStreamingQuality,
                        onValueSelected = onFlacStreamingQualityChange,
                        valueText = { q -> flacQualityLabel(q) },
                    )
                }
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.flac_download_quality)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = flacDownloadQuality,
                        onValueSelected = onFlacDownloadQualityChange,
                        valueText = { q -> flacQualityLabel(q) },
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
            }
        }
    }
}

@Composable
private fun audioQualityLabel(quality: AudioQuality): String = stringResource(
    when (quality) {
        AudioQuality.AUTO -> R.string.audio_quality_auto
        AudioQuality.HIGH -> R.string.audio_quality_high
        AudioQuality.LOW -> R.string.audio_quality_low
        AudioQuality.HIGHEST -> R.string.audio_quality_highest
    }
)

@Composable
private fun flacQualityLabel(quality: FlacQuality): String = stringResource(
    when (quality) {
        FlacQuality.CD -> R.string.flac_quality_cd
        FlacQuality.HI_RES -> R.string.flac_quality_hi_res
        FlacQuality.MAX -> R.string.flac_quality_max
    }
)
