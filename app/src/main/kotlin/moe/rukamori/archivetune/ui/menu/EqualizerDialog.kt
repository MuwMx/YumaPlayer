/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.EqProfilesPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerDialog(
    onDismiss: () -> Unit,
    openSystemEqualizer: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val eqCapabilities by playerConnection.service.eqCapabilities.collectAsStateWithLifecycle()

    val eqState = rememberEqualizerState()

    val caps = eqCapabilities
    val bandCount = caps?.bandCount ?: 0
    val minMb = caps?.minBandLevelMb ?: -1500
    val maxMb = caps?.maxBandLevelMb ?: 1500

    var outputGainLocal by rememberSaveable { mutableIntStateOf(eqState.outputGainMb) }
    LaunchedEffect(eqState.outputGainMb) { outputGainLocal = eqState.outputGainMb }

    var bassBoostStrengthLocal by rememberSaveable { mutableIntStateOf(eqState.bassBoostStrength) }
    LaunchedEffect(eqState.bassBoostStrength) { bassBoostStrengthLocal = eqState.bassBoostStrength }

    var virtualizerStrengthLocal by rememberSaveable { mutableIntStateOf(eqState.virtualizerStrength) }
    LaunchedEffect(eqState.virtualizerStrength) { virtualizerStrengthLocal = eqState.virtualizerStrength }

    var bandLevelsMb by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(eqState.bandLevelsRaw, bandCount) {
        bandLevelsMb = resampleLevelsByIndex(decodeBandLevelsMb(eqState.bandLevelsRaw), bandCount)
    }

    val profiles = remember(eqState.customProfilesJson) { decodeProfilesPayload(eqState.customProfilesJson).profiles }
    val (currentProfileTitle, currentProfileSupportingText) =
        resolveProfileTitleAndSubtitle(
            selectedProfileId = eqState.selectedProfileId,
            systemPresets = caps?.systemPresets,
            profiles = profiles,
            flatString = stringResource(R.string.eq_flat),
            manualString = stringResource(R.string.eq_manual),
            customProfileString = stringResource(R.string.eq_custom_profile),
            systemPresetString = stringResource(R.string.eq_system_preset),
            profileHintString = stringResource(R.string.eq_profile_hint),
        )

    val coroutineScope = rememberCoroutineScope()
    var showSaveProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showManageProfilesDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExportProfileJson by rememberSaveable { mutableStateOf<String?>(null) }

    val exportFileLauncher =
        rememberEqualizerExportLauncher(
            pendingJsonProvider = { pendingExportProfileJson },
            onClearPendingJson = { pendingExportProfileJson = null },
            coroutineScope = coroutineScope,
            context = context,
        )

    val importFileLauncher =
        rememberEqualizerImportLauncher(
            profiles = profiles,
            coroutineScope = coroutineScope,
            context = context,
            onImportApplied = { firstImported, updatedPayload, count ->
                applyProfileToEqualizer(eqState, firstImported, updatedPayload)
                android.widget.Toast
                    .makeText(
                        context,
                        context.getString(R.string.eq_import_success, count),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            },
        )

    EqualizerSaveProfileDialog(
        isVisible = showSaveProfileDialog,
        profiles = profiles,
        bandCenterFreqHz = caps?.centerFreqHz.orEmpty(),
        bandLevelsMb = bandLevelsMb,
        outputGainMb = eqState.outputGainMb,
        bassBoostStrength = eqState.bassBoostStrength,
        virtualizerStrength = eqState.virtualizerStrength,
        onSaveProfile = { newProfile, updatedPayload, filename ->
            eqState.setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
            eqState.setSelectedProfileId("profile:${newProfile.id}")
            pendingExportProfileJson = encodeProfilesPayload(EqProfilesPayload(listOf(newProfile)))
            exportFileLauncher.launch(filename)
        },
        onDismiss = { showSaveProfileDialog = false },
    )

    EqualizerManageProfilesDialog(
        isVisible = showManageProfilesDialog,
        profiles = profiles,
        selectedProfileId = eqState.selectedProfileId,
        onSelectProfile = { profile ->
            applyProfileToEqualizer(eqState, profile)
        },
        onDeleteProfile = { profile, updatedPayload ->
            eqState.setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
            if (eqState.selectedProfileId == "profile:${profile.id}") {
                eqState.setSelectedProfileId("manual")
            }
        },
        onDismiss = { showManageProfilesDialog = false },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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

                EqualizerDialogContent(
                    eqEnabled = eqState.eqEnabled,
                    selectedProfileId = eqState.selectedProfileId,
                    currentProfileTitle = currentProfileTitle,
                    currentProfileSupportingText = currentProfileSupportingText,
                    caps = caps,
                    bandCount = bandCount,
                    bandLevelsMb = bandLevelsMb,
                    minMb = minMb,
                    maxMb = maxMb,
                    outputGainEnabled = eqState.outputGainEnabled,
                    outputGainLocal = outputGainLocal,
                    bassBoostEnabled = eqState.bassBoostEnabled,
                    bassBoostStrengthLocal = bassBoostStrengthLocal,
                    virtualizerEnabled = eqState.virtualizerEnabled,
                    virtualizerStrengthLocal = virtualizerStrengthLocal,
                    playerConnection = playerConnection,
                    onEnabledChange = {
                        eqState.setEqEnabled(it)
                        if (it && eqState.selectedProfileId.isBlank()) eqState.setSelectedProfileId("manual")
                    },
                    onOpenSystemEqualizer = openSystemEqualizer,
                    onSelectProfileId = { eqState.setSelectedProfileId(it) },
                    onShowManageProfiles = { showManageProfilesDialog = true },
                    onShowSaveProfile = { showSaveProfileDialog = true },
                    onImportClick = { importFileLauncher.launch(arrayOf("application/json")) },
                    onResetBands = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setBandLevelsRaw(encodeBandLevelsMb(List(bandCount) { 0 }))
                    },
                    onBandLevelsChange = { bandLevelsMb = it },
                    onBandLevelsChangeFinished = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setBandLevelsRaw(encodeBandLevelsMb(bandLevelsMb))
                    },
                    onOutputGainEnabledChange = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setOutputGainEnabled(it)
                    },
                    onOutputGainLocalChange = { outputGainLocal = it },
                    onOutputGainFinished = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setOutputGainMb(it)
                    },
                    onBassBoostEnabledChange = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setBassBoostEnabled(it)
                    },
                    onBassBoostStrengthChange = { bassBoostStrengthLocal = it },
                    onBassBoostFinished = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setBassBoostStrength(it)
                    },
                    onVirtualizerEnabledChange = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setVirtualizerEnabled(it)
                    },
                    onVirtualizerStrengthChange = { virtualizerStrengthLocal = it },
                    onVirtualizerFinished = {
                        eqState.setSelectedProfileId("manual")
                        eqState.setVirtualizerStrength(it)
                    },
                )
            }
        }
    }
}
