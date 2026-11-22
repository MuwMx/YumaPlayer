/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.quality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.components.segmentedSettingsItemShape
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Composable
internal fun MonochromeConfigSheet(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    serversRaw: String,
    onServersRawChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val servers = remember(serversRaw) { parseRelayServers(serversRaw) }
    var draftUrl by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val submitRelay = {
        val candidate = draftUrl.trim()
        if (isValidRelayUrl(candidate)) {
            if (candidate !in servers) {
                onServersRawChange((servers + candidate).joinToString(","))
            }
            draftUrl = ""
            showError = false
        } else {
            showError = true
        }
    }

    QualityConfigSheetShell(
        title = stringResource(R.string.monochrome_config_title),
        onDismiss = onDismiss,
    ) {
        PreferenceGroup {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.monochrome_enable)) },
                    icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.monochrome_builtin_server)) },
                    description = stringResource(R.string.monochrome_builtin_server_host),
                    icon = { Icon(painterResource(R.drawable.cached), null) },
                )
            }
        }

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        Text(
            text = stringResource(R.string.monochrome_custom_relays),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = SettingsDimensions.SectionHeaderBottomPadding),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draftUrl,
                onValueChange = {
                    draftUrl = it
                    if (showError) showError = false
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = showError,
                placeholder = { Text(stringResource(R.string.monochrome_relay_hint)) },
                supportingText = if (showError) {
                    { Text(stringResource(R.string.monochrome_relay_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submitRelay() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = submitRelay) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = stringResource(R.string.monochrome_relay_add),
                )
            }
        }

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        if (servers.isEmpty()) {
            Text(
                text = stringResource(R.string.monochrome_no_relays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                itemsIndexed(
                    items = servers,
                    key = { _, url -> url },
                ) { index, url ->
                    val shape = segmentedSettingsItemShape(index, servers.size)
                    val position = yumaSegmentPosition(index, servers.size)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .yumaGlassCard(shape = shape, position = position)
                            .clip(shape)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val updated = servers.filterNot { it == url }
                                onServersRawChange(updated.joinToString(","))
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.monochrome_relay_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun parseRelayServers(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

private fun isValidRelayUrl(url: String): Boolean {
    if (url.contains(",")) return false
    val afterScheme = when {
        url.startsWith("https://") -> url.removePrefix("https://")
        url.startsWith("http://") -> url.removePrefix("http://")
        else -> return false
    }
    return afterScheme.isNotBlank() && !afterScheme.contains(" ")
}
