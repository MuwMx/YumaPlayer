/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioPassFixedVolumeKey
import moe.rukamori.archivetune.constants.AudioPassMode
import moe.rukamori.archivetune.constants.AudioPassModeKey
import moe.rukamori.archivetune.constants.HiFiResamplerKey
import moe.rukamori.archivetune.constants.HiResOutputKey
import moe.rukamori.archivetune.constants.PreferUsbDacKey
import moe.rukamori.archivetune.constants.UsbPassthroughKey
import moe.rukamori.archivetune.playback.audio.AudioOutputs
import moe.rukamori.archivetune.playback.audio.BluetoothCodec
import moe.rukamori.archivetune.playback.audio.BluetoothCodecWatcher
import moe.rukamori.archivetune.playback.audio.LdacQuality
import moe.rukamori.archivetune.playback.audio.UsbDacDetails
import moe.rukamori.archivetune.playback.audio.UsbDacReader
import moe.rukamori.archivetune.playback.audio.pass.AudioPass
import moe.rukamori.archivetune.playback.audio.pass.PassBlocker
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Locale

private sealed interface Shown {
    data class Dac(val details: UsbDacDetails) : Shown
    data class Bluetooth(val state: BluetoothCodecWatcher.State) : Shown
    data object Wired : Shown
    data object Nothing : Shown
}

private enum class HeroTone {
    BitPerfect,
    Blocked,
    Normal,
}

@Composable
fun AudioPassScreen(navController: NavController) {
    val context = LocalContext.current
    val service = LocalPlayerConnection.current?.service
    val outputs = service?.audioOutputs
    val devices by remember(outputs) { outputs?.devices ?: MutableStateFlow(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val blockers by remember(service) { service?.passBlockers ?: MutableStateFlow(emptySet()) }.collectAsStateWithLifecycle(emptySet())
    val pass = service?.audioPass
    val status by remember(pass) { pass?.status ?: MutableStateFlow(AudioPass.Status.Off) }.collectAsStateWithLifecycle(AudioPass.Status.Off)
    val dacVolume by remember(pass) { pass?.dacVolume ?: MutableStateFlow(null) }.collectAsStateWithLifecycle(null)
    val usbPermitted by remember(pass) { pass?.usbPermitted ?: MutableStateFlow(null) }.collectAsStateWithLifecycle(null)
    val canRestore by remember(service) { service?.canRestorePassBlockers ?: MutableStateFlow(false) }.collectAsStateWithLifecycle(false)

    var dac by remember { mutableStateOf<UsbDacDetails?>(null) }
    var mixerRate by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(devices) {
        val (details, rate) = withContext(Dispatchers.IO) {
            UsbDacReader.read(context) to UsbDacReader.mixerRate()
        }
        dac = details
        mixerRate = rate
    }
    val bluetooth = rememberBluetoothCodec()
    val codecState by bluetooth.state.collectAsStateWithLifecycle()

    val activeKind = devices.firstOrNull { it.isActive }?.kind
    val plugged = dac
    val shown = when {
        activeKind == AudioOutputs.Kind.BLUETOOTH -> Shown.Bluetooth(codecState)
        plugged != null -> Shown.Dac(plugged)
        activeKind == AudioOutputs.Kind.WIRED -> Shown.Wired
        else -> Shown.Nothing
    }

    var showBlockersOffer by remember { mutableStateOf(false) }
    if (showBlockersOffer && blockers.isNotEmpty()) {
        PassBlockersOfferSheet(
            blockers = blockers,
            onTurnOff = {
                service?.clearPassBlockers()
                showBlockersOffer = false
            },
            onDismiss = { showBlockersOffer = false },
        )
    }

    val (preferUsb, onPreferUsb) = rememberPreference(PreferUsbDacKey, defaultValue = false)
    val (hiRes, onHiRes) = rememberPreference(HiResOutputKey, defaultValue = false)
    val (resampler, onResampler) = rememberPreference(HiFiResamplerKey, defaultValue = false)
    val (passthrough, onPassthrough) = rememberPreference(UsbPassthroughKey, defaultValue = false)
    val (mode, onMode) = rememberEnumPreference(AudioPassModeKey, defaultValue = AudioPassMode.Default)
    val offerToClear = {
        if (blockers.isNotEmpty()) showBlockersOffer = true
    }

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.audiopass_hub_title)) },
        onBackClick = { navController.navigateUp() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            SignalPathHeroCard(
                shown = shown,
                mixerRate = mixerRate,
                passthrough = passthrough,
                mode = mode,
                hiRes = hiRes,
                status = status,
                blockers = blockers,
                canRestore = canRestore,
                onClearBlockers = { service?.clearPassBlockers() },
                onRestoreBlockers = { service?.restorePassBlockers() },
                onPermission = bluetooth::refresh,
            )

            ProcessingSection(
                preferUsb = preferUsb,
                onPreferUsb = onPreferUsb,
                hiRes = hiRes,
                onHiRes = onHiRes,
                resampler = resampler,
                onResampler = onResampler,
                resamplerBypassed = passthrough && mode.exclusive,
                bypassMode = mode,
            )

            OutputModeSection(
                passthrough = passthrough,
                onPassthroughChange = { turnedOn ->
                    onPassthrough(turnedOn)
                    if (turnedOn && mode.exclusive) offerToClear()
                },
                mode = mode,
                onModeSelect = { picked ->
                    onMode(picked)
                    if (passthrough && picked.exclusive && !mode.exclusive) offerToClear()
                },
                status = status.takeIf { mode.exclusive },
            )

            val showUsbDacCard = (passthrough && mode == AudioPassMode.DIRECT_USB) ||
                (plugged != null && (usbPermitted != null || dacVolume != null))
            AnimatedVisibility(
                visible = showUsbDacCard,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                DirectUsbCard(
                    dacName = plugged?.name,
                    permitted = usbPermitted,
                    volume = dacVolume,
                    onAllow = { pass?.requestUsbPermission() },
                    onVolume = { db -> pass?.setDacVolume(db) },
                )
            }

            AnimatedVisibility(
                visible = passthrough && mode.exclusive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                BlockersCard(
                    active = blockers,
                    lossyToo = mode == AudioPassMode.DIRECT_USB,
                    canRestore = canRestore,
                    onClear = { service?.clearPassBlockers() },
                    onRestore = { service?.restorePassBlockers() },
                )
            }

            Spacer(Modifier.height(SettingsDimensions.ScreenBottomPadding))
        }
    }
}

@Composable
private fun rememberBluetoothCodec(): BluetoothCodecWatcher {
    val context = LocalContext.current
    val watcher = remember { BluetoothCodecWatcher(context) }
    DisposableEffect(watcher) {
        watcher.start()
        onDispose { watcher.stop() }
    }
    return watcher
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalPathHeroCard(
    shown: Shown,
    mixerRate: Int?,
    passthrough: Boolean,
    mode: AudioPassMode,
    hiRes: Boolean,
    status: AudioPass.Status,
    blockers: Set<PassBlocker>,
    canRestore: Boolean,
    onClearBlockers: () -> Unit,
    onRestoreBlockers: () -> Unit,
    onPermission: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val yumaColors = LocalYumaColors.current
    val tone = when {
        passthrough && mode.exclusive && blockers.isNotEmpty() -> HeroTone.Blocked
        passthrough && mode.exclusive && blockers.isEmpty() && status is AudioPass.Status.Playing && status.exact -> HeroTone.BitPerfect
        else -> HeroTone.Normal
    }

    val dotColor by animateColorAsState(
        targetValue = when (tone) {
            HeroTone.BitPerfect -> MaterialTheme.colorScheme.primary
            HeroTone.Blocked -> MaterialTheme.colorScheme.error
            HeroTone.Normal -> if (shown is Shown.Dac) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        animationSpec = if (animationsDisabled) snap() else tween(240),
        label = "signalHeroDot",
    )
    val borderColor by animateColorAsState(
        targetValue = when (tone) {
            HeroTone.BitPerfect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            HeroTone.Blocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            HeroTone.Normal -> yumaColors.glassBorder
        },
        animationSpec = if (animationsDisabled) snap() else tween(240),
        label = "signalHeroBorder",
    )
    val badgeBg by animateColorAsState(
        targetValue = when (tone) {
            HeroTone.BitPerfect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
            HeroTone.Blocked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
            HeroTone.Normal -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        },
        animationSpec = if (animationsDisabled) snap() else tween(240),
        label = "signalHeroBadgeBg",
    )
    val badgeFg = when (tone) {
        HeroTone.BitPerfect -> MaterialTheme.colorScheme.primary
        HeroTone.Blocked -> MaterialTheme.colorScheme.onErrorContainer
        HeroTone.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusTitleRes = when (shown) {
        is Shown.Dac -> if (shown.details.androidOutput) R.string.audiopass_status_connected else R.string.audiopass_status_driven
        is Shown.Bluetooth -> R.string.audiopass_status_bluetooth
        Shown.Wired -> R.string.audiopass_status_wired
        Shown.Nothing -> R.string.audiopass_status_none
    }

    val sourceNode = when (status) {
        is AudioPass.Status.Playing -> "${khz(status.sampleRate)} kHz · ${status.bits}-bit"
        is AudioPass.Status.Fell -> "${khz(status.sampleRate)} kHz · ${status.sourceBits}-bit"
        else -> when {
            hiRes -> "32-bit"
            mixerRate != null -> "${khz(mixerRate)} kHz"
            else -> "PCM"
        }
    }
    val modeNode = if (passthrough) {
        stringResource(mode.title)
    } else {
        stringResource(R.string.audiopass_info_mixer)
    }
    val deviceNode = when (shown) {
        is Shown.Dac -> shown.details.name
        is Shown.Bluetooth -> when (val bt = shown.state) {
            is BluetoothCodecWatcher.State.Known -> bt.device
            is BluetoothCodecWatcher.State.NeedsPermission -> bt.device
            is BluetoothCodecWatcher.State.Unknown -> bt.device
            is BluetoothCodecWatcher.State.LeAudio -> bt.device
            BluetoothCodecWatcher.State.None -> stringResource(R.string.audiopass_status_bluetooth)
        }
        Shown.Wired -> stringResource(R.string.audiopass_value_jack)
        Shown.Nothing -> stringResource(R.string.audiopass_kind_speaker)
    }

    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .yumaGlassCard(
                shape = cardShape,
                borderColor = borderColor,
            )
            .clip(cardShape)
            .padding(SettingsDimensions.SegmentedItemPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(SettingsDimensions.BadgeSize)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.audiopass_status_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SettingsDimensions.SegmentedBadgeCornerPercent))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = when (tone) {
                        HeroTone.BitPerfect -> stringResource(R.string.audiopass_mode_bit_perfect)
                        HeroTone.Blocked -> "${blockers.size}"
                        HeroTone.Normal -> modeNode
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeFg,
                )
            }
        }

        Text(
            text = stringResource(statusTitleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SignalNodePill(
                text = sourceNode,
                highlighted = tone == HeroTone.BitPerfect,
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignalNodePill(
                text = modeNode,
                highlighted = tone == HeroTone.BitPerfect,
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignalNodePill(
                text = deviceNode,
                highlighted = tone == HeroTone.BitPerfect,
            )
        }

        if (passthrough && mode.exclusive) {
            statusLine(status)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (tone) {
                        HeroTone.BitPerfect -> MaterialTheme.colorScheme.primary
                        HeroTone.Blocked -> MaterialTheme.colorScheme.error
                        HeroTone.Normal -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        if (passthrough && mode.exclusive && blockers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.audiopass_blockers_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PillButton(
                    text = stringResource(R.string.audiopass_blockers_button),
                    onClick = onClearBlockers,
                )
            }
        } else if (canRestore) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                PillButton(
                    text = stringResource(R.string.audiopass_blockers_restore),
                    onClick = onRestoreBlockers,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.28f))
                .padding(14.dp),
        ) {
            when (shown) {
                is Shown.Dac -> InfoRows(dacRows(shown.details, mixerRate))
                is Shown.Bluetooth -> BluetoothDetails(shown.state, onPermission)
                Shown.Wired -> InfoRows(
                    listOfNotNull(
                        stringResource(R.string.audiopass_info_output) to stringResource(R.string.audiopass_value_jack),
                        mixerRate?.let { stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it)) },
                    ),
                )
                Shown.Nothing -> {
                    Text(
                        text = stringResource(R.string.audiopass_status_none_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    mixerRate?.let {
                        Spacer(Modifier.height(10.dp))
                        InfoRows(listOf(stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it))))
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalNodePill(
    text: String,
    highlighted: Boolean,
) {
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    }
    val fg = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SettingsDimensions.SegmentedBadgeCornerPercent))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            horizontal = 4.dp,
            vertical = SettingsDimensions.SectionHeaderBottomPadding,
        ),
    )
}

@Composable
private fun ProcessingSection(
    preferUsb: Boolean,
    onPreferUsb: (Boolean) -> Unit,
    hiRes: Boolean,
    onHiRes: (Boolean) -> Unit,
    resampler: Boolean,
    onResampler: (Boolean) -> Unit,
    resamplerBypassed: Boolean,
    bypassMode: AudioPassMode,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.audiopass_options))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentGap),
        ) {
            YumaToggleRow(
                title = stringResource(R.string.audiopass_prefer_usb),
                checked = preferUsb,
                onCheckedChange = onPreferUsb,
                position = YumaSegmentPosition.First,
            )
            YumaToggleRow(
                title = stringResource(R.string.audiopass_hires),
                checked = hiRes,
                onCheckedChange = onHiRes,
                position = YumaSegmentPosition.Middle,
            )
            YumaToggleRow(
                title = stringResource(R.string.audiopass_resampler),
                description = stringResource(R.string.audiopass_resampler_desc),
                badge = if (resamplerBypassed) stringResource(bypassMode.title) else null,
                checked = resampler,
                onCheckedChange = onResampler,
                dimmed = resamplerBypassed,
                position = YumaSegmentPosition.Last,
            )
        }
    }
}

@Composable
private fun YumaToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: YumaSegmentPosition,
    description: String? = null,
    badge: String? = null,
    dimmed: Boolean = false,
) {
    val shape = segmentShape(position)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsDimensions.SegmentedItemMinHeight)
            .yumaClickable { onCheckedChange(!checked) }
            .yumaGlassCard(
                shape = shape,
                position = position,
            )
            .clip(shape)
            .alpha(if (dimmed) 0.55f else 1f)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (badge != null) {
                    CapabilityBadge(
                        label = badge,
                        highlighted = false,
                    )
                }
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        YumaSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun YumaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                },
                label = "audioPassSwitchThumb",
            ) { isChecked ->
                Icon(
                    painter = painterResource(
                        id = if (isChecked) R.drawable.check else R.drawable.close,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun OutputModeSection(
    passthrough: Boolean,
    onPassthroughChange: (Boolean) -> Unit,
    mode: AudioPassMode,
    onModeSelect: (AudioPassMode) -> Unit,
    status: AudioPass.Status?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.audiopass_exclusive))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentGap),
        ) {
            YumaToggleRow(
                title = stringResource(R.string.audiopass_passthrough),
                description = stringResource(R.string.audiopass_passthrough_desc),
                checked = passthrough,
                onCheckedChange = onPassthroughChange,
                position = if (passthrough) YumaSegmentPosition.First else YumaSegmentPosition.Single,
            )

            AnimatedVisibility(
                visible = passthrough,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentGap),
                ) {
                    val entries = AudioPassMode.entries
                    entries.forEachIndexed { index, option ->
                        val available = Build.VERSION.SDK_INT >= option.minSdk
                        val position = if (index == entries.lastIndex) {
                            YumaSegmentPosition.Last
                        } else {
                            YumaSegmentPosition.Middle
                        }
                        AudioPassModeRow(
                            title = stringResource(option.title),
                            description = if (available) {
                                stringResource(option.description)
                            } else {
                                stringResource(R.string.audiopass_mode_needs, androidName(option.minSdk))
                            },
                            badges = option.capabilityBadges,
                            selected = option == mode,
                            enabled = available,
                            position = position,
                            onClick = { onModeSelect(option) },
                        )
                    }
                }
            }
        }

        if (passthrough) {
            statusLine(status)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioPassModeRow(
    title: String,
    description: String,
    badges: List<String>,
    selected: Boolean,
    enabled: Boolean,
    position: YumaSegmentPosition,
    onClick: () -> Unit,
) {
    val yumaColors = LocalYumaColors.current
    val shape = segmentShape(position)
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        yumaColors.glassBackground
    }
    val border = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        yumaColors.glassBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yumaClickable(enabled = enabled, onClick = onClick)
            .yumaGlassCard(
                shape = shape,
                backgroundColor = bg,
                borderColor = border,
                position = position,
            )
            .clip(shape)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                badges.forEach { badge ->
                    CapabilityBadge(
                        label = badge,
                        highlighted = selected,
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadge(
    label: String,
    highlighted: Boolean,
) {
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    }
    val fg = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SettingsDimensions.SegmentedBadgeCornerPercent))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

private fun segmentShape(position: YumaSegmentPosition): Shape = when (position) {
    YumaSegmentPosition.Single -> RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    YumaSegmentPosition.First -> RoundedCornerShape(
        topStart = SettingsDimensions.SegmentedCornerLarge,
        topEnd = SettingsDimensions.SegmentedCornerLarge,
        bottomStart = SettingsDimensions.SegmentedCornerSmall,
        bottomEnd = SettingsDimensions.SegmentedCornerSmall,
    )
    YumaSegmentPosition.Middle -> RoundedCornerShape(SettingsDimensions.SegmentedCornerSmall)
    YumaSegmentPosition.Last -> RoundedCornerShape(
        topStart = SettingsDimensions.SegmentedCornerSmall,
        topEnd = SettingsDimensions.SegmentedCornerSmall,
        bottomStart = SettingsDimensions.SegmentedCornerLarge,
        bottomEnd = SettingsDimensions.SegmentedCornerLarge,
    )
}

@Composable
private fun dacRows(dac: UsbDacDetails, mixerRate: Int?): List<Pair<String, String>> {
    val takes = listOfNotNull(
        if (dac.rates.isEmpty()) stringResource(R.string.audiopass_value_any_rate) else stringResource(R.string.audiopass_value_khz, rateSpan(dac.rates)),
        dac.bitDepths.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.audiopass_value_bits, it.joinToString("/")) },
    ).joinToString(" · ")
    val bitPerfect = when {
        dac.bitPerfect == null -> stringResource(R.string.audiopass_value_needs_14)
        dac.bitPerfect.isEmpty() -> stringResource(R.string.audiopass_value_not_offered)
        else -> listOf(
            stringResource(R.string.audiopass_value_khz, rateSpan(dac.bitPerfect.map { it.sampleRate })),
            stringResource(R.string.audiopass_value_bits, dac.bitPerfect.map { it.bits }.distinct().sorted().joinToString("/")),
        ).joinToString(" · ")
    }
    val id = if (dac.vendorId != null && dac.productId != null) String.format(Locale.ROOT, "%04x:%04x", dac.vendorId, dac.productId) else null
    return listOfNotNull(
        stringResource(R.string.audiopass_info_dac) to dac.name,
        dac.maker?.let { stringResource(R.string.audiopass_info_maker) to it },
        dac.uacVersion?.let {
            stringResource(R.string.audiopass_info_usb) to
                if (dac.asyncClock == true) stringResource(R.string.audiopass_value_uac_async, it) else stringResource(R.string.audiopass_value_uac, it)
        },
        stringResource(R.string.audiopass_info_takes) to takes,
        stringResource(R.string.audiopass_info_bitperfect) to bitPerfect,
        mixerRate?.takeIf { dac.androidOutput }?.let { stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it)) },
        id?.let { stringResource(R.string.audiopass_info_id) to (dac.firmware?.let { fw -> stringResource(R.string.audiopass_value_firmware, id, fw) } ?: id) },
        dac.maxPowerMa?.takeIf { it > 0 }?.let { stringResource(R.string.audiopass_info_power) to stringResource(R.string.audiopass_value_power, it) },
        stringResource(R.string.audiopass_info_mic) to stringResource(if (dac.hasMic) R.string.audiopass_value_yes else R.string.audiopass_value_no),
    )
}

@Composable
private fun ColumnScope.BluetoothDetails(state: BluetoothCodecWatcher.State, onPermission: () -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermission() }
    when (state) {
        is BluetoothCodecWatcher.State.Known -> {
            InfoRows(bluetoothRows(state.device, state.codec))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(if (state.codec.lossless) R.string.audiopass_bt_lossless else R.string.audiopass_bt_reencoded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val better = state.codec.better
            val ldac = state.codec.ldac?.takeIf { it != LdacQuality.HIGH }
            if (better != null || ldac != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (better != null) stringResource(R.string.audiopass_bt_better, better, state.codec.name)
                    else stringResource(R.string.audiopass_bt_ldac_low, ldacLabel(ldac!!)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                val developerOn = remember { developerOptionsOn(context) }
                if (developerOn) {
                    PillButton(stringResource(R.string.audiopass_bt_open_developer)) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.audiopass_bt_developer_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is BluetoothCodecWatcher.State.NeedsPermission -> {
            InfoRows(listOf(stringResource(R.string.audiopass_info_headphones) to state.device))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.audiopass_bt_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            PillButton(stringResource(R.string.audiopass_bt_permission)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        is BluetoothCodecWatcher.State.Unknown -> {
            InfoRows(
                listOf(
                    stringResource(R.string.audiopass_info_headphones) to state.device,
                    stringResource(R.string.audiopass_info_codec) to stringResource(R.string.audiopass_value_unknown),
                ),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.audiopass_bt_unknown_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is BluetoothCodecWatcher.State.LeAudio -> {
            InfoRows(
                listOf(
                    stringResource(R.string.audiopass_info_headphones) to state.device,
                    stringResource(R.string.audiopass_info_codec) to stringResource(R.string.audiopass_value_le_audio),
                ),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.audiopass_bt_reencoded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BluetoothCodecWatcher.State.None -> {
            Text(
                text = stringResource(R.string.audiopass_bt_reencoded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun bluetoothRows(device: String, codec: BluetoothCodec): List<Pair<String, String>> = listOfNotNull(
    stringResource(R.string.audiopass_info_headphones) to device,
    stringResource(R.string.audiopass_info_codec) to listOfNotNull(
        codec.name,
        codec.sampleRate?.let { stringResource(R.string.audiopass_value_khz, khz(it)) },
        codec.bits?.let { stringResource(R.string.audiopass_value_bits, it.toString()) },
    ).joinToString(" · "),
    codec.ldac?.let { stringResource(R.string.audiopass_info_quality) to ldacLabel(it) },
    codec.alternatives.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.audiopass_info_also) to it.joinToString(", ") },
)

@Composable
private fun ldacLabel(quality: LdacQuality): String =
    quality.kbps?.let { stringResource(R.string.audiopass_value_kbps, it) } ?: stringResource(R.string.audiopass_value_adaptive)

private fun developerOptionsOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { (key, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = key.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(112.dp),
                )
                Text(
                    text = value,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .yumaClickable(enabled = enabled, onClick = onClick)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

@Composable
internal fun statusLine(status: AudioPass.Status?): String? = when (status) {
    null, AudioPass.Status.Off -> null
    AudioPass.Status.Waiting -> stringResource(R.string.audiopass_now_waiting)
    is AudioPass.Status.Fell -> stringResource(R.string.audiopass_now_fell, khz(status.sampleRate), status.sourceBits.toString())
    is AudioPass.Status.Playing -> when {
        !status.exact -> stringResource(R.string.audiopass_now_inexact, status.device)
        status.lossless -> stringResource(R.string.audiopass_now_bitperfect, status.device, khz(status.sampleRate), status.bits.toString())
        else -> stringResource(R.string.audiopass_now_untouched, status.device, khz(status.sampleRate), status.bits.toString())
    }
}

@Composable
private fun DirectUsbCard(
    dacName: String?,
    permitted: Boolean?,
    volume: AudioPass.DacVolume?,
    onAllow: () -> Unit,
    onVolume: (Double) -> Unit,
) {
    val (fixedVolume, onFixedVolume) = rememberPreference(AudioPassFixedVolumeKey, defaultValue = false)

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.audiopass_usb_title))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentGap),
        ) {
            val topShape = segmentShape(YumaSegmentPosition.First)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .yumaGlassCard(
                        shape = topShape,
                        position = YumaSegmentPosition.First,
                    )
                    .clip(topShape)
                    .padding(SettingsDimensions.SegmentedItemPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (dacName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = dacName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (permitted == true) {
                            CapabilityBadge(label = "USB", highlighted = true)
                        }
                    }
                }

                when (permitted) {
                    null -> Text(
                        text = stringResource(R.string.audiopass_usb_no_dac),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    false -> {
                        Text(
                            text = stringResource(R.string.audiopass_usb_permission_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PillButton(
                            text = stringResource(R.string.audiopass_usb_permission),
                            onClick = onAllow,
                        )
                    }
                    true -> Unit
                }

                if (volume != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.audiopass_dac_volume),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.audiopass_value_db, String.format(Locale.ROOT, "%.1f", volume.currentDb)),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = AudioPass.levelOf(volume),
                            onValueChange = { level -> onVolume(AudioPass.dbAt(volume, level)) },
                            valueRange = 0f..1f,
                        )
                        Text(
                            text = stringResource(R.string.audiopass_dac_volume_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            YumaToggleRow(
                title = stringResource(R.string.audiopass_fixed_volume),
                description = stringResource(R.string.audiopass_fixed_volume_desc),
                checked = fixedVolume,
                onCheckedChange = onFixedVolume,
                position = YumaSegmentPosition.Last,
            )
        }
    }
}

private val AudioPassMode.capabilityBadges: List<String>
    get() = when (this) {
        AudioPassMode.SONG_RATE -> listOf("API 34")
        AudioPassMode.BIT_PERFECT -> listOf("API 34")
        AudioPassMode.DIRECT_USB -> listOf("USB")
        AudioPassMode.EXCLUSIVE_USB -> listOf("USB", "AAudio")
        AudioPassMode.EXCLUSIVE_DAC -> listOf("AAudio")
    }

private val AudioPassMode.title: Int
    @StringRes get() = when (this) {
        AudioPassMode.SONG_RATE -> R.string.audiopass_mode_song_rate
        AudioPassMode.BIT_PERFECT -> R.string.audiopass_mode_bit_perfect
        AudioPassMode.DIRECT_USB -> R.string.audiopass_mode_direct_usb
        AudioPassMode.EXCLUSIVE_USB -> R.string.audiopass_mode_exclusive_usb
        AudioPassMode.EXCLUSIVE_DAC -> R.string.audiopass_mode_exclusive_dac
    }

private val AudioPassMode.description: Int
    @StringRes get() = when (this) {
        AudioPassMode.SONG_RATE -> R.string.audiopass_mode_song_rate_desc
        AudioPassMode.BIT_PERFECT -> R.string.audiopass_mode_bit_perfect_desc
        AudioPassMode.DIRECT_USB -> R.string.audiopass_mode_direct_usb_desc
        AudioPassMode.EXCLUSIVE_USB -> R.string.audiopass_mode_exclusive_usb_desc
        AudioPassMode.EXCLUSIVE_DAC -> R.string.audiopass_mode_exclusive_dac_desc
    }

internal fun androidName(sdk: Int): String = when (sdk) {
    27 -> "8.1"
    34 -> "14"
    else -> "7"
}

@Composable
private fun BlockersCard(
    active: Set<PassBlocker>,
    lossyToo: Boolean,
    canRestore: Boolean,
    onClear: () -> Unit,
    onRestore: () -> Unit,
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .yumaGlassCard(shape = cardShape)
            .clip(cardShape)
            .padding(SettingsDimensions.SegmentedItemPaddingHorizontal),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (active.isEmpty()) R.string.audiopass_blockers_clear_title else R.string.audiopass_blockers_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PillButton(
                text = stringResource(R.string.audiopass_blockers_button),
                enabled = active.isNotEmpty(),
                onClick = onClear,
            )
        }
        Spacer(Modifier.height(12.dp))
        PassBlocker.entries.forEach { blocker ->
            val on = blocker in active
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(blocker.label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(if (lossyToo) R.string.audiopass_blocker_lossy_direct else R.string.audiopass_blocker_lossy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canRestore) {
            Spacer(Modifier.height(14.dp))
            PillButton(
                text = stringResource(R.string.audiopass_blockers_restore),
                onClick = onRestore,
            )
        }
    }
}

internal val PassBlocker.label: Int
    @StringRes get() = when (this) {
        PassBlocker.EQUALIZER -> R.string.audiopass_blocker_equalizer
        PassBlocker.AUDIO_FX -> R.string.audiopass_blocker_fx
        PassBlocker.VOLUME -> R.string.audiopass_blocker_volume
        PassBlocker.NORMALIZATION -> R.string.audiopass_blocker_normalization
        PassBlocker.SPEED -> R.string.audiopass_blocker_speed
        PassBlocker.TRANSITIONS -> R.string.audiopass_blocker_transitions
        PassBlocker.SILENCE_OR_HAPTICS -> R.string.audiopass_blocker_silence
    }

internal fun khz(rate: Int): String =
    if (rate % 1000 == 0) "${rate / 1000}" else String.format(Locale.ROOT, "%.1f", rate / 1000.0)

private fun rateSpan(rates: List<Int>): String {
    val low = rates.minOrNull() ?: return ""
    val high = rates.max()
    return if (low == high) khz(low) else "${khz(low)}–${khz(high)}"
}
