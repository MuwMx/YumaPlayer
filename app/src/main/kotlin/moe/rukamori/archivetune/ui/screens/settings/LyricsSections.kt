/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.component.NumberPickerPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference

@Composable
internal fun ClearLyricsDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ActionPromptDialog(
        title = stringResource(R.string.clear_lyrics_cache),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        onCancel = onDismiss,
    ) {
        Text(stringResource(R.string.clear_lyrics_cache_confirm))
    }
}

@Composable
internal fun LyricsRomanizationSection(
    lyricsRomanizeJapanese: Boolean,
    onLyricsRomanizeJapaneseChange: (Boolean) -> Unit,
    lyricsRomanizeKorean: Boolean,
    onLyricsRomanizeKoreanChange: (Boolean) -> Unit,
    lyricsRomanizeChinese: Boolean,
    onLyricsRomanizeChineseChange: (Boolean) -> Unit,
    lyricsRomanizeHindi: Boolean,
    onLyricsRomanizeHindiChange: (Boolean) -> Unit,
    lyricsRomanizeOtherLanguages: Boolean,
    onLyricsRomanizeOtherLanguagesChange: (Boolean) -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.romanization)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_japanese)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsRomanizeJapanese,
                onCheckedChange = onLyricsRomanizeJapaneseChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_korean)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsRomanizeKorean,
                onCheckedChange = onLyricsRomanizeKoreanChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_chinese)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsRomanizeChinese,
                onCheckedChange = onLyricsRomanizeChineseChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_hindi)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsRomanizeHindi,
                onCheckedChange = onLyricsRomanizeHindiChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_other_languages)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsRomanizeOtherLanguages,
                onCheckedChange = onLyricsRomanizeOtherLanguagesChange,
            )
        }
    }
}

@Composable
internal fun LyricsQueueSection(
    preloadQueueLyricsEnabled: Boolean,
    onPreloadQueueLyricsEnabledChange: (Boolean) -> Unit,
    queueLyricsPreloadCount: Int,
    onQueueLyricsPreloadCountChange: (Int) -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.queue)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.preload_queue_lyrics)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = preloadQueueLyricsEnabled,
                onCheckedChange = onPreloadQueueLyricsEnabledChange,
            )
        }

        item(visible = preloadQueueLyricsEnabled) {
            NumberPickerPreference(
                title = { Text(stringResource(R.string.queue_lyrics_preload_count)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                value = queueLyricsPreloadCount,
                onValueChange = onQueueLyricsPreloadCountChange,
                minValue = 0,
                maxValue = 10,
                valueText = { if (it == 0) "Off" else it.toString() },
            )
        }
    }
}

@Composable
internal fun LyricsCacheSection(
    onClearLyricsCacheClick: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.cache)) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.clear_lyrics_cache)) },
                icon = { Icon(painterResource(R.drawable.delete), null) },
                onClick = onClearLyricsCacheClick,
            )
        }
    }
}

@Composable
internal fun LyricsSettingsContent(
    state: LyricsSettingsUiState,
    actions: LyricsSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LyricsDisplaySection(
            lyricsClick = state.lyricsClick,
            onLyricsClickChange = actions.onLyricsClickChange,
            lyricsScroll = state.lyricsScroll,
            onLyricsScrollChange = actions.onLyricsScrollChange,
            lyricsLineBlur = state.lyricsLineBlur,
            onLyricsLineBlurChange = actions.onLyricsLineBlurChange,
            lyricsTextSize = state.lyricsTextSize,
            lyricsLineSpacing = state.lyricsLineSpacing,
            onOpenTextSizeDialog = actions.onOpenTextSizeDialog,
            onOpenLineSpacingDialog = actions.onOpenLineSpacingDialog,
        )

        LyricsProvidersSection(
            enableBetterLyrics = state.enableBetterLyrics,
            onEnableBetterLyricsChange = actions.onEnableBetterLyricsChange,
            enableYouLyPlusLyrics = state.enableYouLyPlusLyrics,
            onEnableYouLyPlusLyricsChange = actions.onEnableYouLyPlusLyricsChange,
            enableLrclib = state.enableLrclib,
            onEnableLrclibChange = actions.onEnableLrclibChange,
            enableKugou = state.enableKugou,
            onEnableKugouChange = actions.onEnableKugouChange,
            enableUnisonLyrics = state.enableUnisonLyrics,
            onEnableUnisonLyricsChange = actions.onEnableUnisonLyricsChange,
            enableSimpMusicLyrics = state.enableSimpMusicLyrics,
            onEnableSimpMusicLyricsChange = actions.onEnableSimpMusicLyricsChange,
            enablePaxsenixLyrics = state.enablePaxsenixLyrics,
            onEnablePaxsenixLyricsChange = actions.onEnablePaxsenixLyricsChange,
            paxsenixApiKey = state.paxsenixApiKey,
            onOpenPaxsenixApiKeyDialog = actions.onOpenPaxsenixApiKeyDialog,
            enablePaxsenixAppleMusicLyrics = state.enablePaxsenixAppleMusicLyrics,
            onEnablePaxsenixAppleMusicLyricsChange = actions.onEnablePaxsenixAppleMusicLyricsChange,
            enablePaxsenixNeteaseLyrics = state.enablePaxsenixNeteaseLyrics,
            onEnablePaxsenixNeteaseLyricsChange = actions.onEnablePaxsenixNeteaseLyricsChange,
            enablePaxsenixSpotifyLyrics = state.enablePaxsenixSpotifyLyrics,
            onEnablePaxsenixSpotifyLyricsChange = actions.onEnablePaxsenixSpotifyLyricsChange,
            enablePaxsenixMusixmatchLyrics = state.enablePaxsenixMusixmatchLyrics,
            onEnablePaxsenixMusixmatchLyricsChange = actions.onEnablePaxsenixMusixmatchLyricsChange,
            enablePaxsenixYouTubeLyrics = state.enablePaxsenixYouTubeLyrics,
            onEnablePaxsenixYouTubeLyricsChange = actions.onEnablePaxsenixYouTubeLyricsChange,
            providerOrder = state.providerOrder,
            onOpenPaxsenixStats = actions.onOpenPaxsenixStats,
            onOpenProviderOrderDialog = actions.onOpenProviderOrderDialog,
        )

        LyricsRomanizationSection(
            lyricsRomanizeJapanese = state.lyricsRomanizeJapanese,
            onLyricsRomanizeJapaneseChange = actions.onLyricsRomanizeJapaneseChange,
            lyricsRomanizeKorean = state.lyricsRomanizeKorean,
            onLyricsRomanizeKoreanChange = actions.onLyricsRomanizeKoreanChange,
            lyricsRomanizeChinese = state.lyricsRomanizeChinese,
            onLyricsRomanizeChineseChange = actions.onLyricsRomanizeChineseChange,
            lyricsRomanizeHindi = state.lyricsRomanizeHindi,
            onLyricsRomanizeHindiChange = actions.onLyricsRomanizeHindiChange,
            lyricsRomanizeOtherLanguages = state.lyricsRomanizeOtherLanguages,
            onLyricsRomanizeOtherLanguagesChange = actions.onLyricsRomanizeOtherLanguagesChange,
        )

        LyricsQueueSection(
            preloadQueueLyricsEnabled = state.preloadQueueLyricsEnabled,
            onPreloadQueueLyricsEnabledChange = actions.onPreloadQueueLyricsEnabledChange,
            queueLyricsPreloadCount = state.queueLyricsPreloadCount,
            onQueueLyricsPreloadCountChange = actions.onQueueLyricsPreloadCountChange,
        )

        LyricsCacheSection(
            onClearLyricsCacheClick = actions.onClearLyricsCacheClick,
        )
    }
}
