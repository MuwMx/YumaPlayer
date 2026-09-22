package moe.rukamori.archivetune.data.repository

import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences

data class UserSettings(
    val isDarkTheme: Boolean = true,
    val themeColorHex: String = "ED5564",
    val fontPreference: AppFontPreference = AppFontPreference.DEFAULT,
)

data class PlayerColors(
    val vibrant: Int? = null,
    val darkMuted: Int? = null,
    val gradient: Int? = null,
)

interface SettingsRepository {
    val userSettings: Flow<UserSettings>
    val lyricsRomanizationPrefsFlow: Flow<LyricsRomanizationPreferences>
    val playerColorsFlow: Flow<PlayerColors>
    suspend fun savePlayerColors(vibrant: Int, darkMuted: Int, gradient: Int)
    suspend fun updateThemeColor(colorHex: String): Result<Unit>
    suspend fun updateFontPreference(preference: AppFontPreference): Result<Unit>
    fun isBlurBackgroundEnabled(): Boolean
    fun setBlurBackgroundEnabled(enabled: Boolean)
    fun isAutoDownloadLyricsEnabled(): Boolean
    fun setAutoDownloadLyricsEnabled(enabled: Boolean)
    fun isFirstLaunch(): Boolean
    fun setFirstLaunch(isFirst: Boolean)
    fun isSearchHistoryPaused(): Boolean
    fun isImmersiveEnabled(): Boolean
    fun setImmersiveEnabled(enabled: Boolean)
    fun isShowCodecInfoEnabled(): Boolean
    fun setShowCodecInfoEnabled(enabled: Boolean)
    fun isAlbumCoverGlowEnabled(): Boolean
    fun setAlbumCoverGlowEnabled(enabled: Boolean)
}