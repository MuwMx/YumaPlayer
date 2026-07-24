package moe.rukamori.archivetune.data.repository

interface SettingsRepository {
    fun isBlurBackgroundEnabled(): Boolean
    fun setBlurBackgroundEnabled(enabled: Boolean)
    fun isAutoDownloadLyricsEnabled(): Boolean
    fun setAutoDownloadLyricsEnabled(enabled: Boolean)
    fun isFirstLaunch(): Boolean
    fun setFirstLaunch(isFirst: Boolean)
    fun isSearchHistoryPaused(): Boolean
    fun isImmersiveEnabled(): Boolean
    fun setImmersiveEnabled(enabled: Boolean)
}