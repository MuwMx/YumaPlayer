/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.TranslatorLang

@Composable
internal fun TranslateLyricsDialogContent(
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    isTranslationInProgress: Boolean,
    selectedSource: LyricsTranslationSource,
    isAiTranslationEnabled: Boolean,
    sourceExpanded: Boolean,
    onSourceExpandedChange: (Boolean) -> Unit,
    onSourceSelect: (LyricsTranslationSource) -> Unit,
    selectedLanguageName: String,
    languages: List<TranslatorLang>,
    languageExpanded: Boolean,
    onLanguageExpandedChange: (Boolean) -> Unit,
    onLanguageSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = onTextChange,
            enabled = !isTranslationInProgress,
            singleLine = false,
            label = { Text(stringResource(R.string.lyrics)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 220.dp),
        )

        Spacer(Modifier.height(12.dp))

        TranslateLyricsSourceDropdown(
            selectedSource = selectedSource,
            isAiTranslationEnabled = isAiTranslationEnabled,
            isTranslationInProgress = isTranslationInProgress,
            sourceExpanded = sourceExpanded,
            onExpandedChange = onSourceExpandedChange,
            onSourceSelect = onSourceSelect,
        )

        Spacer(Modifier.height(12.dp))

        TranslateLyricsLanguageDropdown(
            selectedLanguageName = selectedLanguageName,
            languages = languages,
            isTranslationInProgress = isTranslationInProgress,
            languageExpanded = languageExpanded,
            onExpandedChange = onLanguageExpandedChange,
            onLanguageSelect = onLanguageSelect,
        )
    }
}
