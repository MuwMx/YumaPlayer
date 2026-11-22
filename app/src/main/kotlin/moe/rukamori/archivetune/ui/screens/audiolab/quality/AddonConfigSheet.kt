/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.quality

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.addons.AddonDescriptor
import moe.rukamori.archivetune.addons.AddonFormat
import moe.rukamori.archivetune.addons.AddonRepository
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.viewmodels.AddonProbeUiState
import moe.rukamori.archivetune.viewmodels.AudioAddonsUiState

@Composable
internal fun rememberAddonProviderItems(
    addons: List<AddonDescriptor>,
    probeStates: Map<String, AddonProbeUiState>,
    losslessEnabled: Boolean,
): Map<String, ProviderItemUi> {
    val checkingLabel = stringResource(R.string.server_status_checking)
    val jsLabel = stringResource(R.string.addon_type_js)
    val httpLabel = stringResource(R.string.addon_type_http)
    return addons.associate { addon ->
        val typeLabel = if (addon.format == AddonFormat.BITCHORD_HTTP) httpLabel else jsLabel
        val status = when (val probe = probeStates[addon.id]) {
            is AddonProbeUiState.Checking -> checkingLabel
            is AddonProbeUiState.Online -> stringResource(R.string.server_status_online, probe.latencyMs)
            is AddonProbeUiState.Error -> probe.message
            null, AddonProbeUiState.Idle -> typeLabel
        }
        addon.id to ProviderItemUi(id = addon.id, name = addon.name, status = status, active = losslessEnabled)
    }
}

@Composable
internal fun AddonSheetHost(
    activeSheet: String?,
    addonsState: AudioAddonsUiState,
    addonsById: Map<String, AddonDescriptor>,
    onAddRepository: (String, (List<String>) -> Unit) -> Unit,
    onToggleRepository: (String, Boolean) -> Unit,
    onSetRepositoryQuality: (String, String?) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onTestAddon: (String) -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (activeSheet == SHEET_ADD_ADDON) {
        AddAddonSheet(
            state = addonsState,
            onAddRepository = onAddRepository,
            onToggleRepository = onToggleRepository,
            onRemoveRepository = onRemoveRepository,
            onClearError = onClearError,
            onDismiss = onDismiss,
        )
    } else if (activeSheet != null) {
        addonsById[activeSheet]?.let { addon ->
            val repo = findRepositoryForAddon(addon, addonsState.repositories)
            AddonConfigSheet(
                addon = addon,
                repository = repo,
                probeState = addonsState.probeStates[addon.id] ?: AddonProbeUiState.Idle,
                onToggleRepository = { enabled -> repo?.let { onToggleRepository(it.url, enabled) } },
                onSetQuality = { quality -> repo?.let { onSetRepositoryQuality(it.url, quality) } },
                onTestAddon = { onTestAddon(addon.id) },
                onRefresh = onRefresh,
                onRemoveRepository = { repo?.let { onRemoveRepository(it.url); onDismiss() } },
                onDismiss = onDismiss,
            )
        }
    }
}

internal const val SHEET_ADD_ADDON = "__add_addon__"

@Composable
internal fun AddonConfigSheet(
    addon: AddonDescriptor,
    repository: AddonRepository?,
    probeState: AddonProbeUiState,
    onToggleRepository: (Boolean) -> Unit,
    onSetQuality: (String?) -> Unit,
    onTestAddon: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveRepository: () -> Unit,
    onDismiss: () -> Unit,
) {
    val metaParts = buildList {
        if (addon.version.isNotBlank()) add(stringResource(R.string.addon_config_version, addon.version))
        if (addon.tags.isNotEmpty()) add(addon.tags.joinToString(" · "))
    }
    val repoUrl = repository?.url ?: addon.downloadUrl
    val subtitleText = (listOf(repoUrl) + metaParts).filter { it.isNotBlank() }.joinToString("\n")

    val qualityValues = remember(addon.qualityOptions) {
        listOf("") + if (addon.qualityOptions.isNotEmpty()) addon.qualityOptions.map { it.value } else listOf("LOSSLESS", "HIGH", "LOW")
    }
    val qualityLabels = remember(addon.qualityOptions) {
        addon.qualityOptions.associate { it.value to it.label }
    }

    QualityConfigSheetShell(title = addon.name, subtitle = subtitleText.ifBlank { null }, onDismiss = onDismiss) {
        PreferenceGroup(horizontalPadding = 0.dp) {
            if (repository != null) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.addon_config_enable)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        checked = repository.enabled,
                        onCheckedChange = onToggleRepository,
                    )
                }
                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.addon_quality_title)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = repository.pickedQuality ?: "",
                        values = qualityValues,
                        valueText = { v ->
                            when (v) {
                                "" -> stringResource(R.string.addon_quality_auto)
                                "LOSSLESS" -> qualityLabels[v] ?: stringResource(R.string.addon_quality_flac)
                                "HIGH" -> qualityLabels[v] ?: stringResource(R.string.addon_quality_high)
                                "LOW" -> qualityLabels[v] ?: stringResource(R.string.addon_quality_low)
                                else -> qualityLabels[v] ?: v
                            }
                        },
                        onValueSelected = { v -> onSetQuality(v.ifEmpty { null }) },
                    )
                }
            }
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.addon_config_test_title)) },
                    icon = { Icon(painterResource(R.drawable.cached), null) },
                    content = {
                        if (probeState !is AddonProbeUiState.Idle) {
                            Spacer(Modifier.height(2.dp))
                            AddonProbeStatusText(probeState)
                        }
                    },
                    trailingContent = {
                        FilledTonalButton(
                            onClick = onTestAddon,
                            enabled = probeState !is AddonProbeUiState.Checking,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(text = stringResource(R.string.addon_config_test_button), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                )
            }
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.addon_config_refresh)) },
                    icon = { Icon(painterResource(R.drawable.ic_refresh), null) },
                    onClick = onRefresh,
                )
            }
            if (repository != null) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.addon_config_remove), color = MaterialTheme.colorScheme.error) },
                        icon = { Icon(painterResource(R.drawable.delete), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = onRemoveRepository,
                    )
                }
            }
        }
        Spacer(Modifier.height(SettingsDimensions.SegmentGap))
    }
}

@Composable
private fun AddonProbeStatusText(state: AddonProbeUiState, modifier: Modifier = Modifier) {
    val (text, color) = when (state) {
        is AddonProbeUiState.Idle -> return
        is AddonProbeUiState.Checking -> stringResource(R.string.server_status_checking) to MaterialTheme.colorScheme.onSurfaceVariant
        is AddonProbeUiState.Online -> stringResource(R.string.addon_probe_online_tracks, state.latencyMs, state.tracksFound) to MaterialTheme.colorScheme.primary
        is AddonProbeUiState.Error -> state.message to MaterialTheme.colorScheme.error
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

internal fun findRepositoryForAddon(
    addon: AddonDescriptor,
    repositories: List<AddonRepository>,
): AddonRepository? {
    if (addon.repositoryUrl.isNotBlank()) {
        val byExactUrl = repositories.firstOrNull { it.url == addon.repositoryUrl }
        if (byExactUrl != null) return byExactUrl
    }
    val byPrefix = repositories.firstOrNull { repo ->
        val base = repo.url.substringBeforeLast("/")
        base.isNotBlank() && addon.downloadUrl.startsWith(base)
    }
    return byPrefix ?: repositories.singleOrNull()
}
