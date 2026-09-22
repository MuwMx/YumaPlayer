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
import moe.rukamori.archivetune.constants.EnablePaxsenixAppleMusicLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixNeteaseLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixSpotifyLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixYouTubeLyricsKey
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
    const val PAXSENIX_WEBSITE_URL = "https://lyrics.paxsenix.org/"

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
    val PaxsenixKey = EnablePaxsenixLyricsKey
    val PaxsenixAppleMusicKey = EnablePaxsenixAppleMusicLyricsKey
    val PaxsenixNeteaseKey = EnablePaxsenixNeteaseLyricsKey
    val PaxsenixSpotifyKey = EnablePaxsenixSpotifyLyricsKey
    val PaxsenixMusixmatchKey = EnablePaxsenixMusixmatchLyricsKey
    val PaxsenixYouTubeKey = EnablePaxsenixYouTubeLyricsKey
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

internal enum class PaxsenixServerStatus {
    Operational,
    Degraded,
    Down,
}

internal fun PreferredLyricsProvider.displayName(): String =
    when (this) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "KuGou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.YOULY_PLUS -> "YouLyPlus"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC -> "Paxsenix: Apple Music"
        PreferredLyricsProvider.PAXSENIX_NETEASE -> "Paxsenix: NetEase"
        PreferredLyricsProvider.PAXSENIX_SPOTIFY -> "Paxsenix: Spotify"
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH -> "Paxsenix: Musixmatch"
        PreferredLyricsProvider.PAXSENIX_YOUTUBE -> "Paxsenix: YouTube"
        PreferredLyricsProvider.UNISON -> "Unison"
    }

internal fun successRateToStatus(rate: Float): PaxsenixServerStatus =
    when {
        rate >= 90f -> PaxsenixServerStatus.Operational
        rate >= 70f -> PaxsenixServerStatus.Degraded
        else -> PaxsenixServerStatus.Down
    }

internal fun formatUptimeSeconds(seconds: Double): String {
    val total = seconds.toLong()
    val days = total / 86400L
    val hours = (total % 86400L) / 3600L
    val minutes = (total % 3600L) / 60L
    return when {
        days > 0L -> "${days}d ${hours}h ${minutes}m"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
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
    val enablePaxsenixLyrics: Boolean = true,
    val enablePaxsenixAppleMusicLyrics: Boolean = true,
    val enablePaxsenixNeteaseLyrics: Boolean = true,
    val enablePaxsenixSpotifyLyrics: Boolean = true,
    val enablePaxsenixMusixmatchLyrics: Boolean = true,
    val enablePaxsenixYouTubeLyrics: Boolean = true,
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
    val onEnablePaxsenixLyricsChange: (Boolean) -> Unit = {},
    val onEnablePaxsenixAppleMusicLyricsChange: (Boolean) -> Unit = {},
    val onEnablePaxsenixNeteaseLyricsChange: (Boolean) -> Unit = {},
    val onEnablePaxsenixSpotifyLyricsChange: (Boolean) -> Unit = {},
    val onEnablePaxsenixMusixmatchLyricsChange: (Boolean) -> Unit = {},
    val onEnablePaxsenixYouTubeLyricsChange: (Boolean) -> Unit = {},
    val onOpenPaxsenixStats: () -> Unit = {},
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
