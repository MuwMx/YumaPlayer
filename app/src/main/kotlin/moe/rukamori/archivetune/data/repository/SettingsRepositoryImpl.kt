package moe.rukamori.archivetune.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    private val prefs = context.getSharedPreferences("_settings", Context.MODE_PRIVATE)
    private val sharedPrefs = context.getSharedPreferences("yuma_prefs", Context.MODE_PRIVATE)

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

}