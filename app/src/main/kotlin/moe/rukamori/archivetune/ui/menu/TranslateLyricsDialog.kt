/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.TranslatorLang
import moe.rukamori.archivetune.utils.TranslatorLanguages
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel

@Composable
internal fun TranslateLyricsDialog(
    isVisible: Boolean,
    initialText: String,
    isAiTranslationEnabled: Boolean,
    targetLanguage: String,
    defaultLanguageCode: String,
    isAiTranslating: Boolean,
    mediaMetadata: MediaMetadata,
    viewModel: LyricsMenuViewModel,
    coroutineScope: CoroutineScope,
    context: Context,
    setTargetLanguage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val (textFieldValue, setTextFieldValue) =
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(text = initialText))
        }

    val languages by produceState(initialValue = emptyList<TranslatorLang>()) {
        withContext(Dispatchers.IO) {
            value = TranslatorLanguages.load(context)
        }
    }
    var sourceExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var selectedSource by rememberSaveable {
        mutableStateOf(
            if (isAiTranslationEnabled) {
                LyricsTranslationSource.AI_TRANSLATION
            } else {
                LyricsTranslationSource.TRANSLATION
            },
        )
    }
    var selectedLanguageCode by rememberSaveable { mutableStateOf(targetLanguage.ifBlank { defaultLanguageCode }) }
    val selectedLanguageName =
        languages.firstOrNull { it.code == selectedLanguageCode }?.name ?: selectedLanguageCode
    val canUseSelectedSource = selectedSource != LyricsTranslationSource.AI_TRANSLATION || isAiTranslationEnabled

    var translationJob by remember { mutableStateOf<Job?>(null) }
    var isStandardTranslating by remember { mutableStateOf(false) }
    var isDialogAiTranslationRunning by rememberSaveable { mutableStateOf(false) }
    val isTranslationInProgress = isStandardTranslating || isAiTranslating

    LaunchedEffect(isAiTranslationEnabled) {
        if (!isAiTranslationEnabled && selectedSource == LyricsTranslationSource.AI_TRANSLATION) {
            selectedSource = LyricsTranslationSource.TRANSLATION
        }
    }

    LaunchedEffect(isAiTranslating, isDialogAiTranslationRunning) {
        if (isDialogAiTranslationRunning && !isAiTranslating) {
            isDialogAiTranslationRunning = false
            onDismiss()
        }
    }

    BasicAlertDialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        modifier =
            Modifier
                .padding(24.dp)
                .navigationBarsPadding()
                .imePadding(),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    painter = painterResource(R.drawable.translate),
                    contentDescription = null,
                    tint = AlertDialogDefaults.iconContentColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.translate),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
                TranslateLyricsDialogContent(
                    textFieldValue = textFieldValue,
                    onTextChange = setTextFieldValue,
                    isTranslationInProgress = isTranslationInProgress,
                    selectedSource = selectedSource,
                    isAiTranslationEnabled = isAiTranslationEnabled,
                    sourceExpanded = sourceExpanded,
                    onSourceExpandedChange = { sourceExpanded = it },
                    onSourceSelect = { selectedSource = it },
                    selectedLanguageName = selectedLanguageName,
                    languages = languages,
                    languageExpanded = languageExpanded,
                    onLanguageExpandedChange = { languageExpanded = it },
                    onLanguageSelect = {
                        selectedLanguageCode = it
                        setTargetLanguage(it)
                    },
                )
                Spacer(Modifier.height(24.dp))
                TranslateLyricsDialogActions(
                    isTranslationInProgress = isTranslationInProgress,
                    canUseSelectedSource = canUseSelectedSource,
                    inputText = textFieldValue.text,
                    selectedLanguageCode = selectedLanguageCode,
                    selectedLanguageName = selectedLanguageName,
                    selectedSource = selectedSource,
                    mediaMetadata = mediaMetadata,
                    viewModel = viewModel,
                    coroutineScope = coroutineScope,
                    context = context,
                    setTargetLanguage = setTargetLanguage,
                    translationJob = translationJob,
                    onJobChange = { translationJob = it },
                    isStandardTranslating = isStandardTranslating,
                    onStandardTranslatingChange = { isStandardTranslating = it },
                    isAiTranslating = isAiTranslating,
                    onDialogAiRunningChange = { isDialogAiTranslationRunning = it },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}
