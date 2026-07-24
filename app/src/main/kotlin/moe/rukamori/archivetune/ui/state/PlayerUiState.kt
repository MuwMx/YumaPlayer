package moe.rukamori.archivetune.ui.state

import android.graphics.Color
import android.graphics.drawable.Drawable
//import moe.rukamori.archivetune.data.lyrics.internal.LyricLine
import androidx.compose.runtime.Immutable
/**
 * Единый источник правды (State) для всего интерфейса Spot.
 * Описывает текущий визуальный снимок экрана в любую миллисекунду времени.
 */
@Immutable
data class PlayerUiState(
    // Метаданные текущего трека
    val title: String = "Yuma",
    val artist: String = "Playback...",
    val coverUrl: String = "",
    val trackUrl: String = "",

    // Статусы плеера
    val isPlaying: Boolean = false,
    val isLiked: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,

    // Состояние лирики
    val lyricsList: List<LyricLine> = emptyList(),
    val currentLineIndex: Int = -1,
    val isSynced: Boolean = false,
    val isLyricsVisible: Boolean = false,
    val isRefreshingLyrics: Boolean = false,
    val isLoadingLyrics: Boolean = false,
    val lyricsError: String? = null,

    // Динамическая палитра цветов (Material You)
    val vibrantColor: Int = Color.WHITE,
    val darkMutedColor: Int = Color.parseColor("#282828"), // Цвет таблетки мини-плеера и шапки лирики
    val gradientColor: Int = Color.parseColor("#121212"),   // Верхний цвет градиента большого пульта

    val isBlurBackgroundEnabled: Boolean = false,
    val isAutoDownloadEnabled: Boolean = false,
    val shouldShowWelcome: Boolean = false,
    val shuffleState: String = "off",
    val repeatState: String = "off",

    val sleepTimerRemainingSeconds: Int? = null,

    val placeholderResId: Int = moe.rukamori.archivetune.R.drawable.mascot_1,

    val isLoading: Boolean = false,
    val isConnectActive: Boolean = false,

    // Смещение таймингов и состояние меню лирики
    val lyricsSyncOffset: Int = 0,
    val lyricsEditText: String = "",
    val lyricsTranslateLanguage: String = "",
    val isAiTranslating: Boolean = false,
    val isStandardTranslating: Boolean = false,
    val aiTranslationError: String? = null,

    val isImmersiveEnabled: Boolean = false,
    val coverDrawable: android.graphics.drawable.Drawable? = null,
    val isSheetCollapseRequested: Boolean = false,
)

