/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import java.util.Locale

@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showTranslateDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSyncOffsetDialog by rememberSaveable { mutableStateOf(false) }
    var showRefetchLoadingDialog by rememberSaveable { mutableStateOf(false) }
    val isRefetching by viewModel.isRefetching.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isRefetching) {
        if (!isRefetching && showRefetchLoadingDialog) {
            showRefetchLoadingDialog = false
            onDismiss()
        }
    }

    var showSearchDialog by rememberSaveable { mutableStateOf(false) }
    var showSearchResultDialog by rememberSaveable { mutableStateOf(false) }

    val searchMediaMetadata = remember(showSearchDialog) { mediaMetadataProvider() }
    val (titleField, onTitleFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(text = mediaMetadataProvider().title))
        }
    val (artistField, onArtistFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(text = mediaMetadataProvider().artists.joinToString { it.name }))
        }

    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val lyricsSearchState by viewModel.lyricsSearchState.collectAsStateWithLifecycle()
    val isAiTranslating by viewModel.isAiTranslating.collectAsStateWithLifecycle()
    val (aiProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (aiApiKey) = rememberPreference(AiApiKeyKey, "")
    val (aiCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (aiValidationStatus) = rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)
    var expandedSearchResultId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentLyrics = lyricsProvider()?.lyrics.orEmpty()
    val isTranslateEnabled = currentLyrics.isNotBlank() && currentLyrics != LyricsEntity.LYRICS_NOT_FOUND
    val isAiProviderConfigured = aiProvider != AiProvider.NONE
    val isAiTranslationEnabled =
        currentLyrics.isNotBlank() &&
            currentLyrics != LyricsEntity.LYRICS_NOT_FOUND &&
            isAiProviderConfigured &&
            aiApiKey.isNotBlank() &&
            (aiProvider != AiProvider.CUSTOM || aiCustomEndpoint.isNotBlank()) &&
            aiValidationStatus != AiApiValidationStatus.FAILED

    LaunchedEffect(Unit) {
        viewModel.aiTranslationEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LyricsMenuDialogs(
        showEditDialog = showEditDialog,
        showRefetchLoadingDialog = showRefetchLoadingDialog,
        showSearchDialog = showSearchDialog,
        showSearchResultDialog = showSearchResultDialog,
        titleField = titleField,
        onTitleFieldChange = onTitleFieldChange,
        artistField = artistField,
        onArtistFieldChange = onArtistFieldChange,
        searchMediaMetadata = searchMediaMetadata,
        lyricsSearchState = lyricsSearchState,
        expandedSearchResultId = expandedSearchResultId,
        onExpandedResultChange = { resultId ->
            if (resultId == null) {
                expandedSearchResultId = null
            } else {
                expandedSearchResultId = if (expandedSearchResultId == resultId) null else resultId
            }
        },
        onOpenSearchResult = { showSearchResultDialog = true },
        isNetworkAvailable = isNetworkAvailable,
        lyricsProvider = lyricsProvider,
        mediaMetadataProvider = mediaMetadataProvider,
        viewModel = viewModel,
        onDismissEdit = { showEditDialog = false },
        onDismissSearch = { showSearchDialog = false },
        onDismissSearchResult = {
            expandedSearchResultId = null
            showSearchResultDialog = false
            viewModel.resetSearchState()
        },
        onDismiss = onDismiss,
    )

    LyricsSyncOffsetDialog(
        isVisible = showLyricsSyncOffsetDialog,
        lyricsSyncOffset = lyricsSyncOffset,
        onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
        onDismiss = { showLyricsSyncOffsetDialog = false },
        onDismissMenu = onDismiss,
    )

    val configuration = LocalConfiguration.current
    val defaultLanguageCode =
        remember(configuration) {
            configuration.locales
                .get(0)
                .getDisplayLanguage(Locale.ENGLISH)
                .uppercase(Locale.US)
                .replace(' ', '_')
        }
    val (targetLanguage, setTargetLanguage) = rememberPreference(TranslatorTargetLangKey, defaultLanguageCode)

    TranslateLyricsDialog(
        isVisible = showTranslateDialog,
        initialText = lyricsProvider()?.lyrics.orEmpty(),
        isAiTranslationEnabled = isAiTranslationEnabled,
        targetLanguage = targetLanguage,
        defaultLanguageCode = defaultLanguageCode,
        isAiTranslating = isAiTranslating,
        mediaMetadata = mediaMetadataProvider(),
        viewModel = viewModel,
        coroutineScope = coroutineScope,
        context = context,
        setTargetLanguage = setTargetLanguage,
        onDismiss = { showTranslateDialog = false },
    )

    val darkScheme = darkColorScheme()
    MaterialTheme(colorScheme = darkScheme) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalYumaColors provides darkYumaColorScheme(darkScheme),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            LyricsMenuContent(
                isTranslateEnabled = isTranslateEnabled,
                onEdit = { showEditDialog = true },
                onRefetch = {
                    showRefetchLoadingDialog = true
                    viewModel.refetchLyrics(mediaMetadataProvider())
                },
                onTranslate = { showTranslateDialog = true },
                onSyncOffset = { showLyricsSyncOffsetDialog = true },
                onSearch = { showSearchDialog = true },
            )
        }
    }
}
