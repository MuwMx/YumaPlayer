/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerCustomProfilesJsonKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.EqProfile
import moe.rukamori.archivetune.playback.EqProfilesPayload
import moe.rukamori.archivetune.playback.EqualizerJson
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.player.rememberDeviceMusicVolumeController
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerDialog(
    onDismiss: () -> Unit,
    openSystemEqualizer: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val eqCapabilities by playerConnection.service.eqCapabilities.collectAsStateWithLifecycle()

    val (eqEnabled, setEqEnabled) = rememberPreference(EqualizerEnabledKey, defaultValue = false)
    val (selectedProfileId, setSelectedProfileId) = rememberPreference(EqualizerSelectedProfileIdKey, defaultValue = "flat")
    val (bandLevelsRaw, setBandLevelsRaw) = rememberPreference(EqualizerBandLevelsMbKey, defaultValue = "")

    val (outputGainEnabled, setOutputGainEnabled) = rememberPreference(EqualizerOutputGainEnabledKey, defaultValue = false)
    val (outputGainMb, setOutputGainMb) = rememberPreference(EqualizerOutputGainMbKey, defaultValue = 0)

    val (bassBoostEnabled, setBassBoostEnabled) = rememberPreference(EqualizerBassBoostEnabledKey, defaultValue = false)
    val (bassBoostStrength, setBassBoostStrength) = rememberPreference(EqualizerBassBoostStrengthKey, defaultValue = 0)

    val (virtualizerEnabled, setVirtualizerEnabled) = rememberPreference(EqualizerVirtualizerEnabledKey, defaultValue = false)
    val (virtualizerStrength, setVirtualizerStrength) = rememberPreference(EqualizerVirtualizerStrengthKey, defaultValue = 0)

    val (customProfilesJson, setCustomProfilesJson) = rememberPreference(EqualizerCustomProfilesJsonKey, defaultValue = "")

    val caps = eqCapabilities
    val bandCount = caps?.bandCount ?: 0
    val minMb = caps?.minBandLevelMb ?: -1500
    val maxMb = caps?.maxBandLevelMb ?: 1500

    var outputGainLocal by rememberSaveable { mutableIntStateOf(outputGainMb) }
    LaunchedEffect(outputGainMb) { outputGainLocal = outputGainMb }

    var bassBoostStrengthLocal by rememberSaveable { mutableIntStateOf(bassBoostStrength) }
    LaunchedEffect(bassBoostStrength) { bassBoostStrengthLocal = bassBoostStrength }

    var virtualizerStrengthLocal by rememberSaveable { mutableIntStateOf(virtualizerStrength) }
    LaunchedEffect(virtualizerStrength) { virtualizerStrengthLocal = virtualizerStrength }

    var bandLevelsMb by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(bandLevelsRaw, bandCount) {
        bandLevelsMb = resampleLevelsByIndex(decodeBandLevelsMb(bandLevelsRaw), bandCount)
    }

    val profiles = remember(customProfilesJson) { decodeProfilesPayload(customProfilesJson).profiles }
    val activeProfileId = selectedProfileId.removePrefix("profile:").takeIf { selectedProfileId.startsWith("profile:") }
    val activeProfile = remember(profiles, activeProfileId) { profiles.firstOrNull { it.id == activeProfileId } }
    val selectedSystemPresetName =
        selectedProfileId
            .removePrefix("system:")
            .toIntOrNull()
            ?.let { index -> caps?.systemPresets?.getOrNull(index) }

    val currentProfileTitle =
        when {
            selectedProfileId == "flat" -> stringResource(R.string.eq_flat)
            selectedSystemPresetName != null -> selectedSystemPresetName
            activeProfile != null -> activeProfile.name
            else -> stringResource(R.string.eq_manual)
        }

    val currentProfileSupportingText =
        when {
            activeProfile != null -> stringResource(R.string.eq_custom_profile)
            selectedSystemPresetName != null -> stringResource(R.string.eq_system_preset)
            else -> stringResource(R.string.eq_profile_hint)
        }

    val coroutineScope = rememberCoroutineScope()
    var showSaveProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showManageProfilesDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExportProfileJson by rememberSaveable { mutableStateOf<String?>(null) }

    val exportFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            val raw = pendingExportProfileJson ?: return@rememberLauncherForActivityResult
            pendingExportProfileJson = null
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(raw.toByteArray(Charsets.UTF_8))
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.backup_create_success), Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val currentProfiles = profiles
            coroutineScope.launch(Dispatchers.IO) {
                val raw =
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytes().toString(Charsets.UTF_8)
                        }
                    }.getOrNull()

                if (raw.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.eq_import_failed), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val payload =
                    decodeProfilesPayload(raw).takeIf { it.profiles.isNotEmpty() }
                        ?: runCatching {
                            EqProfilesPayload(EqualizerJson.json.decodeFromString<List<EqProfile>>(raw))
                        }.getOrNull()
                        ?: EqProfilesPayload()

                if (payload.profiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.eq_import_failed), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val existingIds = currentProfiles.map { it.id }.toMutableSet()
                val normalizedImported =
                    payload.profiles.map { p ->
                        val baseName = p.name.trim().ifBlank { context.getString(R.string.eq_imported_profile) }
                        val incomingId = p.id.trim()
                        val finalId =
                            if (incomingId.isBlank() || !existingIds.add(incomingId)) {
                                generateSequence { UUID.randomUUID().toString() }.first { existingIds.add(it) }
                            } else {
                                incomingId
                            }
                        p.copy(id = finalId, name = baseName)
                    }

                val updatedPayload =
                    EqProfilesPayload(
                        profiles =
                            (currentProfiles + normalizedImported)
                                .distinctBy { it.id }
                                .sortedBy { it.name.lowercase() },
                    )

                val firstImported = normalizedImported.firstOrNull() ?: return@launch

                withContext(Dispatchers.Main) {
                    setEqEnabled(true)
                    setBandLevelsRaw(encodeBandLevelsMb(firstImported.bandLevelsMb))
                    setOutputGainMb(firstImported.outputGainMb)
                    setOutputGainEnabled(firstImported.outputGainMb != 0)
                    setBassBoostStrength(firstImported.bassBoostStrength)
                    setBassBoostEnabled(firstImported.bassBoostStrength != 0)
                    setVirtualizerStrength(firstImported.virtualizerStrength)
                    setVirtualizerEnabled(firstImported.virtualizerStrength != 0)
                    setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
                    setSelectedProfileId("profile:${firstImported.id}")
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.eq_import_success, normalizedImported.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }

    if (showSaveProfileDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.eq_save_profile)) },
            placeholder = { Text(text = stringResource(R.string.eq_profile_name)) },
            onDone = { name ->
                val trimmed = name.trim()
                if (trimmed.isNotBlank()) {
                    val newProfile =
                        EqProfile(
                            id = UUID.randomUUID().toString(),
                            name = trimmed,
                            bandCenterFreqHz = caps?.centerFreqHz.orEmpty(),
                            bandLevelsMb = bandLevelsMb,
                            outputGainMb = outputGainMb,
                            bassBoostStrength = bassBoostStrength,
                            virtualizerStrength = virtualizerStrength,
                        )

                    val updatedPayload =
                        EqProfilesPayload(
                            profiles =
                                (profiles + newProfile)
                                    .distinctBy { it.id }
                                    .sortedBy { it.name.lowercase() },
                        )

                    setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
                    setSelectedProfileId("profile:${newProfile.id}")
                    pendingExportProfileJson = encodeProfilesPayload(EqProfilesPayload(listOf(newProfile)))
                    val safeName = trimmed.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    exportFileLauncher.launch("$safeName-eq.json")
                }
            },
            onDismiss = { showSaveProfileDialog = false },
        )
    }

    if (showManageProfilesDialog) {
        ListDialog(
            onDismiss = { showManageProfilesDialog = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                items = profiles,
                key = { it.id },
                contentType = { "eq_profile" },
            ) { profile ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                setEqEnabled(true)
                                setBandLevelsRaw(encodeBandLevelsMb(profile.bandLevelsMb))
                                setOutputGainMb(profile.outputGainMb)
                                setOutputGainEnabled(profile.outputGainMb != 0)
                                setBassBoostStrength(profile.bassBoostStrength)
                                setBassBoostEnabled(profile.bassBoostStrength != 0)
                                setVirtualizerStrength(profile.virtualizerStrength)
                                setVirtualizerEnabled(profile.virtualizerStrength != 0)
                                setSelectedProfileId("profile:${profile.id}")
                                showManageProfilesDialog = false
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.eq_custom_profile),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = {
                            val updatedPayload =
                                EqProfilesPayload(
                                    profiles = profiles.filterNot { it.id == profile.id },
                                )
                            setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
                            if (selectedProfileId == "profile:${profile.id}") {
                                setSelectedProfileId("manual")
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.equalizer),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EqActivateRow(
                        enabled = eqEnabled,
                        onEnabledChange = {
                            setEqEnabled(it)
                            if (it && selectedProfileId.isBlank()) setSelectedProfileId("manual")
                        },
                    )

                    EqHeroCard(
                        enabled = eqEnabled,
                        profileTitle = currentProfileTitle,
                        profileSubtitle = currentProfileSupportingText,
                        onOpenSystemEqualizer = openSystemEqualizer,
                    )

                    if (caps == null || bandCount <= 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp),
                            ) {
                                CircularWavyProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.eq_waiting_for_audio_session),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        return@Column
                    }

                    EqSection(
                        title = stringResource(R.string.eq_presets),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedProfileId == "flat",
                                onClick = {
                                    playerConnection.service.applyEqFlatPreset()
                                    setSelectedProfileId("flat")
                                },
                                label = { Text(text = stringResource(R.string.eq_flat)) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                border = null,
                            )

                            caps.systemPresets.forEachIndexed { index, name ->
                                FilterChip(
                                    selected = selectedProfileId == "system:$index",
                                    onClick = {
                                        playerConnection.service.applySystemEqPreset(index)
                                        setSelectedProfileId("system:$index")
                                    },
                                    label = {
                                        Text(
                                            text = name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                    border = null,
                                )
                            }
                        }
                    }

                    EqSection(
                        title = stringResource(R.string.eq_profiles),
                        subtitle = stringResource(R.string.eq_profile_hint),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentProfileTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (eqEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ) {
                                    Text(
                                        text = stringResource(if (eqEnabled) R.string.enabled else R.string.disabled),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (eqEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = { showManageProfilesDialog = true }) {
                                Text(text = stringResource(R.string.eq_manage))
                            }
                            FilledTonalButton(onClick = { showSaveProfileDialog = true }) {
                                Text(text = stringResource(R.string.eq_save))
                            }
                            OutlinedButton(onClick = { importFileLauncher.launch(arrayOf("application/json")) }) {
                                Text(text = stringResource(R.string.eq_import))
                            }
                        }
                    }

                    EqSection(
                        title = stringResource(R.string.eq_bands),
                        trailing = {
                            TextButton(
                                onClick = {
                                    setSelectedProfileId("manual")
                                    setBandLevelsRaw(encodeBandLevelsMb(List(bandCount) { 0 }))
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(text = stringResource(R.string.reset))
                            }
                        },
                    ) {
                        caps.centerFreqHz.forEachIndexed { band, hz ->
                            val label = formatHz(hz)
                            val value = bandLevelsMb.getOrNull(band) ?: 0
                            val valueDb = (value / 100f).coerceIn(-24f, 24f)

                            EqBandSliderRow(
                                label = label,
                                value = value,
                                valueLabel = formatDb(valueDb),
                                minMb = minMb,
                                maxMb = maxMb,
                                onValueChange = { newValue ->
                                    val coerced = newValue.toInt().coerceIn(minMb, maxMb)
                                    bandLevelsMb =
                                        bandLevelsMb.toMutableList().apply {
                                            while (size < bandCount) add(0)
                                            set(band, coerced)
                                        }
                                },
                                onValueChangeFinished = {
                                    setSelectedProfileId("manual")
                                    setBandLevelsRaw(encodeBandLevelsMb(bandLevelsMb))
                                },
                            )

                            if (band != caps.centerFreqHz.lastIndex) {
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }

                    EqSection(title = stringResource(R.string.eq_output_gain)) {
                        EqToggleSliderRow(
                            enabled = outputGainEnabled,
                            onEnabledChange = {
                                setSelectedProfileId("manual")
                                setOutputGainEnabled(it)
                            },
                            value = outputGainLocal,
                            onValueChange = { outputGainLocal = it },
                            valueRange = -1500..1500,
                            formatValue = { formatDb(it / 100f) },
                            onValueChangeFinished = {
                                setSelectedProfileId("manual")
                                setOutputGainMb(outputGainLocal)
                            },
                        )
                    }

                    EqSection(title = stringResource(R.string.eq_bass_boost)) {
                        EqToggleSliderRow(
                            enabled = bassBoostEnabled,
                            onEnabledChange = {
                                setSelectedProfileId("manual")
                                setBassBoostEnabled(it)
                            },
                            value = bassBoostStrengthLocal,
                            onValueChange = { bassBoostStrengthLocal = it },
                            valueRange = 0..1000,
                            formatValue = { "${it / 10}%" },
                            onValueChangeFinished = {
                                setSelectedProfileId("manual")
                                setBassBoostStrength(bassBoostStrengthLocal)
                            },
                        )
                    }

                    EqSection(title = stringResource(R.string.eq_virtualizer)) {
                        EqToggleSliderRow(
                            enabled = virtualizerEnabled,
                            onEnabledChange = {
                                setSelectedProfileId("manual")
                                setVirtualizerEnabled(it)
                            },
                            value = virtualizerStrengthLocal,
                            onValueChange = { virtualizerStrengthLocal = it },
                            valueRange = 0..1000,
                            formatValue = { "${it / 10}%" },
                            onValueChangeFinished = {
                                setSelectedProfileId("manual")
                                setVirtualizerStrength(virtualizerStrengthLocal)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqSection(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun EqActivateRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "eqActivateContainer",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.tap_to_activate_equalizer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                thumbContent =
                    if (enabled) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun EqHeroCard(
    enabled: Boolean,
    profileTitle: String,
    profileSubtitle: String,
    onOpenSystemEqualizer: () -> Unit,
) {
    val heroAccentColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "eqHeroAccent",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        heroAccentColor.copy(alpha = 0.95f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 0.78f else 0.52f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                            ),
                    ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.graphic_eq),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (enabled) R.string.enabled else R.string.disabled),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = profileTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profileSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = onOpenSystemEqualizer,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.tune),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.eq_open_system_equalizer))
                }
            }
        }
    }
}

@Composable
private fun EqBandSliderRow(
    label: String,
    value: Int,
    valueLabel: String,
    minMb: Int,
    maxMb: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val normalizedLevel = abs(value).toFloat() / maxOf(abs(minMb), abs(maxMb), 1)
    val accentColor = if (value < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val containerColor by animateColorAsState(
        targetValue =
            when {
                value > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + (normalizedLevel * 0.25f))
                value < 0 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f + (normalizedLevel * 0.25f))
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "eqBandContainer",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(56.dp),
            )

            Spacer(Modifier.width(8.dp))

            Slider(
                value = value.toFloat().coerceIn(minMb.toFloat(), maxMb.toFloat()),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = minMb.toFloat()..maxMb.toFloat(),
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = accentColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EqToggleSliderRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    formatValue: (Int) -> String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val containerColor by animateColorAsState(
        targetValue =
            if (enabled) {
                MaterialTheme.colorScheme.secondaryContainer.copy(
                    alpha = 0.45f,
                )
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "eqToggleContainer",
    ) {
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                thumbContent = {
                    Icon(
                        painter = painterResource(id = if (enabled) R.drawable.check else R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )

            Spacer(Modifier.width(12.dp))

            Slider(
                value = value.toFloat().coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat()),
                onValueChange = { onValueChange(it.toInt().coerceIn(valueRange.first, valueRange.last)) },
                onValueChangeFinished = { onValueChangeFinished?.invoke() },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                enabled = enabled,
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun decodeBandLevelsMb(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
}

private fun encodeBandLevelsMb(levelsMb: List<Int>): String =
    runCatching {
        EqualizerJson.json.encodeToString(levelsMb)
    }.getOrNull().orEmpty()

private fun decodeProfilesPayload(raw: String?): EqProfilesPayload {
    if (raw.isNullOrBlank()) return EqProfilesPayload()
    return runCatching { EqualizerJson.json.decodeFromString<EqProfilesPayload>(raw) }.getOrNull() ?: EqProfilesPayload()
}

private fun encodeProfilesPayload(payload: EqProfilesPayload): String =
    runCatching {
        EqualizerJson.json.encodeToString(payload)
    }.getOrNull().orEmpty()

private fun resampleLevelsByIndex(
    levelsMb: List<Int>,
    targetCount: Int,
): List<Int> {
    if (targetCount <= 0) return emptyList()
    if (levelsMb.isEmpty()) return List(targetCount) { 0 }
    if (levelsMb.size == targetCount) return levelsMb
    if (targetCount == 1) return listOf(levelsMb.sum() / levelsMb.size)

    val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
    return List(targetCount) { i ->
        val pos = i.toFloat() * lastIndex / (targetCount - 1).toFloat()
        val lo =
            kotlin.math
                .floor(pos)
                .toInt()
                .coerceIn(0, levelsMb.lastIndex)
        val hi =
            kotlin.math
                .ceil(pos)
                .toInt()
                .coerceIn(0, levelsMb.lastIndex)
        val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
        val a = levelsMb[lo]
        val b = levelsMb[hi]
        (a + ((b - a) * t)).toInt()
    }
}

private fun formatHz(hz: Int): String {
    if (hz <= 0) return ""
    return if (hz >= 1000) "${(hz / 1000f).let { round(it * 10f) / 10f }}k" else hz.toString()
}

private fun formatDb(db: Float): String {
    val rounded = round(db * 10f) / 10f
    return "${if (rounded > 0f) "+" else ""}$rounded dB"
}
