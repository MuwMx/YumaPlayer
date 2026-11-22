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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.addons.AddonDescriptor
import moe.rukamori.archivetune.addons.AddonRepository
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
    return addons.associate { addon ->
        val status = when (val probe = probeStates[addon.id]) {
            is AddonProbeUiState.Checking -> checkingLabel
            is AddonProbeUiState.Online -> stringResource(R.string.server_status_online, probe.latencyMs)
            is AddonProbeUiState.Error -> probe.message
            null, AddonProbeUiState.Idle -> jsLabel
        }
        addon.id to ProviderItemUi(
            id = addon.id,
            name = addon.name,
            status = status,
            active = losslessEnabled,
        )
    }
}

@Composable
internal fun AddonSheetHost(
    activeSheet: String?,
    addonsState: AudioAddonsUiState,
    addonsById: Map<String, AddonDescriptor>,
    onAddRepository: (String, () -> Unit) -> Unit,
    onToggleRepository: (String, Boolean) -> Unit,
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
                onTestAddon = { onTestAddon(addon.id) },
                onRefresh = onRefresh,
                onRemoveRepository = {
                    repo?.let {
                        onRemoveRepository(it.url)
                        onDismiss()
                    }
                },
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

    QualityConfigSheetShell(
        title = addon.name,
        subtitle = subtitleText.ifBlank { null },
        onDismiss = onDismiss,
    ) {
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
                            Text(
                                text = stringResource(R.string.addon_config_test_button),
                                style = MaterialTheme.typography.labelMedium,
                            )
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
                        title = {
                            Text(
                                text = stringResource(R.string.addon_config_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = onRemoveRepository,
                    )
                }
            }
        }
        Spacer(Modifier.height(SettingsDimensions.SegmentGap))
    }
}

@Composable
private fun AddonProbeStatusText(
    state: AddonProbeUiState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is AddonProbeUiState.Idle -> Unit
        is AddonProbeUiState.Checking -> Text(
            text = stringResource(R.string.server_status_checking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        is AddonProbeUiState.Online -> Text(
            text = stringResource(R.string.addon_probe_online_tracks, state.latencyMs, state.tracksFound),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
        is AddonProbeUiState.Error -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

internal fun findRepositoryForAddon(
    addon: AddonDescriptor,
    repositories: List<AddonRepository>,
): AddonRepository? {
    val byPrefix = repositories.firstOrNull { repo ->
        val base = repo.url.substringBeforeLast("/")
        base.isNotBlank() && addon.downloadUrl.startsWith(base)
    }
    return byPrefix ?: repositories.singleOrNull()
}
