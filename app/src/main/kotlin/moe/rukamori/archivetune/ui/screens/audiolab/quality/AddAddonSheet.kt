/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.quality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import moe.rukamori.archivetune.ui.screens.settings.components.segmentedSettingsItemShape
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
import moe.rukamori.archivetune.viewmodels.AudioAddonsUiState

@Composable
internal fun AddAddonSheet(
    state: AudioAddonsUiState,
    onAddRepository: (String, () -> Unit) -> Unit,
    onToggleRepository: (String, Boolean) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var urlInput by remember { mutableStateOf("") }
    var invalidUrlError by remember { mutableStateOf(false) }

    val submitUrl = {
        val candidate = urlInput.trim()
        if (isValidRelayUrl(candidate)) {
            invalidUrlError = false
            onAddRepository(candidate) { onDismiss() }
        } else {
            invalidUrlError = true
        }
    }

    val errorText = when {
        invalidUrlError -> stringResource(R.string.addon_index_url_invalid)
        state.errorMessage != null -> state.errorMessage
        else -> null
    }

    QualityConfigSheetShell(
        title = stringResource(R.string.addon_add_sheet_title),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    if (invalidUrlError) invalidUrlError = false
                    if (state.errorMessage != null) onClearError()
                },
                modifier = Modifier.weight(1f),
                enabled = !state.isLoading,
                singleLine = true,
                isError = errorText != null,
                placeholder = { Text(stringResource(R.string.addon_index_url_hint)) },
                supportingText = errorText?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (!state.isLoading) submitUrl() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = submitUrl,
                enabled = !state.isLoading && urlInput.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(48.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.addon_add_button))
                }
            }
        }

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        Text(
            text = stringResource(R.string.addon_repositories_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = SettingsDimensions.SectionHeaderBottomPadding),
        )

        if (state.repositories.isEmpty()) {
            Text(
                text = stringResource(R.string.addon_no_repositories),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                itemsIndexed(
                    items = state.repositories,
                    key = { _, repo -> repo.url },
                ) { index, repo ->
                    val shape = segmentedSettingsItemShape(index, state.repositories.size)
                    val position = yumaSegmentPosition(index, state.repositories.size)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .yumaGlassCard(shape = shape, position = position)
                            .clip(shape)
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = repo.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = repo.enabled,
                            onCheckedChange = { enabled -> onToggleRepository(repo.url, enabled) },
                            enabled = !state.isLoading,
                        )
                        IconButton(
                            onClick = { onRemoveRepository(repo.url) },
                            enabled = !state.isLoading,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.addon_repository_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
