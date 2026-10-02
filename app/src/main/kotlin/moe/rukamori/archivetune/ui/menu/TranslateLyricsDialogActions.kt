/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.bush.translator.Language
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel

@Composable
internal fun TranslateLyricsDialogActions(
    isTranslationInProgress: Boolean,
    canUseSelectedSource: Boolean,
    inputText: String,
    selectedLanguageCode: String,
    selectedLanguageName: String,
    selectedSource: LyricsTranslationSource,
    mediaMetadata: MediaMetadata,
    viewModel: LyricsMenuViewModel,
    coroutineScope: CoroutineScope,
    context: Context,
    setTargetLanguage: (String) -> Unit,
    translationJob: Job?,
    onJobChange: (Job?) -> Unit,
    isStandardTranslating: Boolean,
    onStandardTranslatingChange: (Boolean) -> Unit,
    isAiTranslating: Boolean,
    onDialogAiRunningChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = {
                translationJob?.cancel()
                onJobChange(null)
                onStandardTranslatingChange(false)
                if (isAiTranslating) {
                    viewModel.cancelAiTranslation()
                }
                onDialogAiRunningChange(false)
                onDismiss()
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(android.R.string.cancel))
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            enabled = !isTranslationInProgress && canUseSelectedSource,
            onClick = {
                val languageCode = selectedLanguageCode
                val languageName = selectedLanguageName
                setTargetLanguage(languageCode)

                when (selectedSource) {
                    LyricsTranslationSource.AI_TRANSLATION -> {
                        onDialogAiRunningChange(true)
                        viewModel.translateLyricsWithAi(
                            mediaMetadata = mediaMetadata,
                            lyrics = inputText,
                            targetLanguage = languageCode,
                        )
                    }

                    LyricsTranslationSource.TRANSLATION -> {
                        onStandardTranslatingChange(true)
                        onJobChange(
                            coroutineScope.launch {
                                try {
                                    val lang =
                                        try {
                                            Language(languageCode)
                                        } catch (e: Exception) {
                                            try {
                                                Language(languageName)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }

                                    if (lang == null) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.unsupported_language, languageName),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        return@launch
                                    }

                                    val translatedLyrics = translateLyricsWithTranslator(inputText, lang)
                                    viewModel.updateLyrics(
                                        mediaMetadata = mediaMetadata,
                                        lyrics = translatedLyrics,
                                        source = LyricsEntity.Source.AI_TRANSLATION,
                                    )
                                    onDismiss()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.translation_failed) + ": " +
                                                (e.localizedMessage ?: e.toString()),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                } finally {
                                    onStandardTranslatingChange(false)
                                    onJobChange(null)
                                }
                            },
                        )
                    }
                }
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            if (isTranslationInProgress) {
                LoadingIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.translate))
        }
    }
}
