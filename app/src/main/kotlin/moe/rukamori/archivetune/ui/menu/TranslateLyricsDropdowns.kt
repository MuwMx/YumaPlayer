/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.TranslatorLang

@Composable
internal fun TranslateLyricsSourceDropdown(
    selectedSource: LyricsTranslationSource,
    isAiTranslationEnabled: Boolean,
    isTranslationInProgress: Boolean,
    sourceExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSourceSelect: (LyricsTranslationSource) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.source),
            modifier = Modifier.width(96.dp),
        )

        ExposedDropdownMenuBox(
            expanded = sourceExpanded,
            onExpandedChange = {
                if (!isTranslationInProgress) onExpandedChange(it)
            },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value =
                    when (selectedSource) {
                        LyricsTranslationSource.AI_TRANSLATION -> stringResource(R.string.ai_translation_menu)
                        LyricsTranslationSource.TRANSLATION -> stringResource(R.string.translate)
                    },
                onValueChange = {},
                enabled = !isTranslationInProgress,
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded)
                },
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = sourceExpanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_translation_menu)) },
                    enabled = isAiTranslationEnabled,
                    onClick = {
                        onSourceSelect(LyricsTranslationSource.AI_TRANSLATION)
                        onExpandedChange(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.translate)) },
                    onClick = {
                        onSourceSelect(LyricsTranslationSource.TRANSLATION)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
internal fun TranslateLyricsLanguageDropdown(
    selectedLanguageName: String,
    languages: List<TranslatorLang>,
    isTranslationInProgress: Boolean,
    languageExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLanguageSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.language_label),
            modifier = Modifier.width(96.dp),
        )

        ExposedDropdownMenuBox(
            expanded = languageExpanded,
            onExpandedChange = {
                if (!isTranslationInProgress) onExpandedChange(it)
            },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selectedLanguageName,
                onValueChange = {},
                enabled = !isTranslationInProgress,
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                },
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.name) },
                        onClick = {
                            onLanguageSelect(lang.code)
                            onExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}
