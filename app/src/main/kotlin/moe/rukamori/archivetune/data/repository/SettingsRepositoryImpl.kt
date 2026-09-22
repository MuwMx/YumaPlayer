package moe.rukamori.archivetune.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.LastDarkMutedColorKey
import moe.rukamori.archivetune.constants.LastGradientColorKey
import moe.rukamori.archivetune.constants.LastVibrantColorKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    private val prefs = context.getSharedPreferences("_settings", Context.MODE_PRIVATE)
    private val sharedPrefs = context.getSharedPreferences("yuma_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    override val userSettings: Flow<UserSettings> = _settingsFlow.asStateFlow()

    override val lyricsRomanizationPrefsFlow: Flow<LyricsRomanizationPreferences> = context.dataStore.data.map { preferences ->
        LyricsRomanizationPreferences(
            romanizeJapanese = preferences[LyricsRomanizeJapaneseKey] ?: true,
            romanizeKorean = preferences[LyricsRomanizeKoreanKey] ?: true,
            romanizeChinese = preferences[LyricsRomanizeChineseKey] ?: true,
            romanizeHindi = preferences[LyricsRomanizeHindiKey] ?: true,
            romanizeOther = preferences[LyricsRomanizeOtherLanguagesKey] ?: true
        )
    }

    override val playerColorsFlow: Flow<PlayerColors> = context.dataStore.data.map { prefs ->
        PlayerColors(
            vibrant = prefs[LastVibrantColorKey],
            darkMuted = prefs[LastDarkMutedColorKey],
            gradient = prefs[LastGradientColorKey],
        )
    }

    override suspend fun savePlayerColors(vibrant: Int, darkMuted: Int, gradient: Int) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[LastVibrantColorKey] = vibrant
                prefs[LastDarkMutedColorKey] = darkMuted
                prefs[LastGradientColorKey] = gradient
            }
        }
    }

    private fun loadSettings(): UserSettings {
        val colorHex = prefs.getString("theme_color_hex", "ED5564") ?: "ED5564"
        val fontPrefStr = prefs.getString("font_preference", AppFontPreference.DEFAULT.name)
        val fontPref = try {
            AppFontPreference.valueOf(fontPrefStr ?: AppFontPreference.DEFAULT.name)
        } catch (_: Exception) {
            AppFontPreference.DEFAULT
        }
        return UserSettings(
            isDarkTheme = prefs.getBoolean("dark_theme", true),
            themeColorHex = colorHex.removePrefix("#"),
            fontPreference = fontPref
        )
    }

    override suspend fun updateThemeColor(colorHex: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanHex = colorHex.removePrefix("#")
            prefs.edit().putString("theme_color_hex", cleanHex).apply()
            _settingsFlow.value = _settingsFlow.value.copy(themeColorHex = cleanHex)
        }
    }

    override suspend fun updateFontPreference(preference: AppFontPreference): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            prefs.edit().putString("font_preference", preference.name).apply()
            _settingsFlow.value = _settingsFlow.value.copy(fontPreference = preference)
        }
    }

    override fun isBlurBackgroundEnabled(): Boolean =
        prefs.getBoolean("blur_bg_enabled", false)

    override fun setBlurBackgroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("blur_bg_enabled", enabled).apply()
    }

    override fun isAutoDownloadLyricsEnabled(): Boolean =
        prefs.getBoolean("auto_download_lyrics", true)

    override fun setAutoDownloadLyricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_download_lyrics", enabled).apply()
    }

    override fun isFirstLaunch(): Boolean =
        sharedPrefs.getBoolean("is_first_launch", true)

    override fun setFirstLaunch(isFirst: Boolean) {
        sharedPrefs.edit().putBoolean("is_first_launch", isFirst).apply()
    }

    override fun isSearchHistoryPaused(): Boolean =
        prefs.getBoolean("pause_search_history", false)

    override fun isImmersiveEnabled(): Boolean =
        prefs.getBoolean("immersive_enabled", false)

    override fun setImmersiveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("immersive_enabled", enabled).apply()
    }

    override fun isShowCodecInfoEnabled(): Boolean =
        prefs.getBoolean("show_codec_info", false)

    override fun setShowCodecInfoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("show_codec_info", enabled).apply()
    }

    override fun isAlbumCoverGlowEnabled(): Boolean =
        prefs.getBoolean("album_cover_glow", false)

    override fun setAlbumCoverGlowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("album_cover_glow", enabled).apply()
    }
}