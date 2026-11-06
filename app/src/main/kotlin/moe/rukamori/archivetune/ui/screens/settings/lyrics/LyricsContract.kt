/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnableSimpMusicLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsLineSpacingKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsScrollKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.PreloadQueueLyricsEnabledKey
import moe.rukamori.archivetune.constants.QueueLyricsPreloadCountKey

internal object LyricsContract {
    const val DEFAULT_TEXT_SIZE = 26f
    const val DEFAULT_LINE_SPACING = 1.3f
    const val RESET_TEXT_SIZE = 24f
    const val RESET_LINE_SPACING = 1.3f
    const val DEFAULT_QUEUE_PRELOAD_COUNT = 1

    val ClickKey = LyricsClickKey
    val ScrollKey = LyricsScrollKey
    val TextSizeKey = LyricsTextSizeKey
    val LineSpacingKey = LyricsLineSpacingKey
    val BetterLyricsKey = EnableBetterLyricsKey
    val YouLyPlusKey = EnableYouLyPlusLyricsKey
    val LrcLibKey = EnableLrcLibKey
    val KugouKey = EnableKugouKey
    val UnisonKey = EnableUnisonLyricsKey
    val SimpMusicKey = EnableSimpMusicLyricsKey
    val ProviderOrderKey = LyricsProviderOrderKey
    val LineBlurKey = LyricsLineBlurKey
    val RomanizeJapaneseKey = LyricsRomanizeJapaneseKey
    val RomanizeKoreanKey = LyricsRomanizeKoreanKey
    val RomanizeChineseKey = LyricsRomanizeChineseKey
    val RomanizeHindiKey = LyricsRomanizeHindiKey
    val RomanizeOtherLanguagesKey = LyricsRomanizeOtherLanguagesKey
    val PreloadQueueKey = PreloadQueueLyricsEnabledKey
    val QueueLyricsPreloadCount = QueueLyricsPreloadCountKey
}

internal fun PreferredLyricsProvider.displayName(): String =
    when (this) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "KuGou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.YOULY_PLUS -> "YouLyPlus"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.UNISON -> "Unison"
    }

@Immutable
data class LyricsSettingsUiState(
    val lyricsClick: Boolean = true,
    val lyricsScroll: Boolean = true,
    val lyricsLineBlur: Boolean = true,
    val lyricsTextSize: Float = LyricsContract.DEFAULT_TEXT_SIZE,
    val lyricsLineSpacing: Float = LyricsContract.DEFAULT_LINE_SPACING,
    val enableBetterLyrics: Boolean = true,
    val enableYouLyPlusLyrics: Boolean = true,
    val enableLrclib: Boolean = true,
    val enableKugou: Boolean = true,
    val enableUnisonLyrics: Boolean = true,
    val enableSimpMusicLyrics: Boolean = true,
    val providerOrder: List<PreferredLyricsProvider> = emptyList(),
    val lyricsRomanizeJapanese: Boolean = true,
    val lyricsRomanizeKorean: Boolean = true,
    val lyricsRomanizeChinese: Boolean = true,
    val lyricsRomanizeHindi: Boolean = true,
    val lyricsRomanizeOtherLanguages: Boolean = true,
    val preloadQueueLyricsEnabled: Boolean = true,
    val queueLyricsPreloadCount: Int = LyricsContract.DEFAULT_QUEUE_PRELOAD_COUNT,
)

typealias LyricsUiState = LyricsSettingsUiState

@Immutable
data class LyricsSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onLyricsClickChange: (Boolean) -> Unit = {},
    val onLyricsScrollChange: (Boolean) -> Unit = {},
    val onLyricsLineBlurChange: (Boolean) -> Unit = {},
    val onLyricsTextSizeChange: (Float) -> Unit = {},
    val onLyricsLineSpacingChange: (Float) -> Unit = {},
    val onOpenTextSizeDialog: () -> Unit = {},
    val onOpenLineSpacingDialog: () -> Unit = {},
    val onEnableBetterLyricsChange: (Boolean) -> Unit = {},
    val onEnableYouLyPlusLyricsChange: (Boolean) -> Unit = {},
    val onEnableLrclibChange: (Boolean) -> Unit = {},
    val onEnableKugouChange: (Boolean) -> Unit = {},
    val onEnableUnisonLyricsChange: (Boolean) -> Unit = {},
    val onEnableSimpMusicLyricsChange: (Boolean) -> Unit = {},
    val onOpenProviderOrderDialog: () -> Unit = {},
    val onLyricsRomanizeJapaneseChange: (Boolean) -> Unit = {},
    val onLyricsRomanizeKoreanChange: (Boolean) -> Unit = {},
    val onLyricsRomanizeChineseChange: (Boolean) -> Unit = {},
    val onLyricsRomanizeHindiChange: (Boolean) -> Unit = {},
    val onLyricsRomanizeOtherLanguagesChange: (Boolean) -> Unit = {},
    val onPreloadQueueLyricsEnabledChange: (Boolean) -> Unit = {},
    val onQueueLyricsPreloadCountChange: (Int) -> Unit = {},
    val onClearLyricsCacheClick: () -> Unit = {},
)

typealias LyricsUiActions = LyricsSettingsUiActions
typealias LyricsActions = LyricsSettingsUiActions
typealias LyricsSettingsActions = LyricsSettingsUiActions
