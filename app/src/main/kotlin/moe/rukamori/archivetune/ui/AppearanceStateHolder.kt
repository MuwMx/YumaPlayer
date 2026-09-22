package moe.rukamori.archivetune.ui

import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.data.repository.SettingsRepository
import moe.rukamori.archivetune.ui.state.PlayerUiState

class AppearanceStateHolder(
    private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val uiStateProvider: () -> PlayerUiState,
    private val updateUiState: ((PlayerUiState) -> PlayerUiState) -> Unit,
) {
    @Volatile
    private var hasExtractedColors = false

    init {
        updateUiState { current ->
            current.copy(
                isBlurBackgroundEnabled = settingsRepository.isBlurBackgroundEnabled(),
                isAutoDownloadEnabled = settingsRepository.isAutoDownloadLyricsEnabled(),
                isImmersiveEnabled = settingsRepository.isImmersiveEnabled(),
                showCodecInfo = settingsRepository.isShowCodecInfoEnabled(),
                isAlbumCoverGlowEnabled = settingsRepository.isAlbumCoverGlowEnabled(),
                shouldShowWelcome = settingsRepository.isFirstLaunch(),
            )
        }

        coroutineScope.launch {
            val colors = settingsRepository.playerColorsFlow
                .catch { }
                .firstOrNull() ?: return@launch

            if (!hasExtractedColors && (colors.vibrant != null || colors.darkMuted != null || colors.gradient != null)) {
                updateUiState { current ->
                    val isDefault = current.vibrantColor == Color.WHITE &&
                        current.darkMutedColor == Color.parseColor("#282828") &&
                        current.gradientColor == Color.parseColor("#121212")
                    if (isDefault && !hasExtractedColors) {
                        current.copy(
                            vibrantColor = colors.vibrant ?: current.vibrantColor,
                            darkMutedColor = colors.darkMuted ?: current.darkMutedColor,
                            gradientColor = colors.gradient ?: current.gradientColor,
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun setAutoDownloadEnabled(enabled: Boolean) {
        settingsRepository.setAutoDownloadLyricsEnabled(enabled)
        updateUiState { it.copy(isAutoDownloadEnabled = enabled) }
    }

    fun toggleAutoDownload() {
        setAutoDownloadEnabled(!uiStateProvider().isAutoDownloadEnabled)
    }

    fun setBlurBackgroundEnabled(enabled: Boolean) {
        settingsRepository.setBlurBackgroundEnabled(enabled)
        updateUiState { it.copy(isBlurBackgroundEnabled = enabled) }
    }

    fun setImmersiveEnabled(enabled: Boolean) {
        settingsRepository.setImmersiveEnabled(enabled)
        updateUiState { it.copy(isImmersiveEnabled = enabled) }
    }

    fun dismissWelcome() {
        settingsRepository.setFirstLaunch(false)
        updateUiState { it.copy(shouldShowWelcome = false) }
    }

    fun toggleCodecInfo() {
        val newValue = !uiStateProvider().showCodecInfo
        settingsRepository.setShowCodecInfoEnabled(newValue)
        updateUiState { it.copy(showCodecInfo = newValue) }
    }

    fun toggleAlbumCoverGlow() {
        val newValue = !uiStateProvider().isAlbumCoverGlowEnabled
        settingsRepository.setAlbumCoverGlowEnabled(newValue)
        updateUiState { it.copy(isAlbumCoverGlowEnabled = newValue) }
    }

    fun updateColors(vibrant: Int, darkMuted: Int, gradient: Int) {
        hasExtractedColors = true
        updateUiState {
            it.copy(
                vibrantColor = vibrant,
                darkMutedColor = darkMuted,
                gradientColor = gradient,
            )
        }
        coroutineScope.launch {
            settingsRepository.savePlayerColors(vibrant, darkMuted, gradient)
        }
    }

    fun resetColors() {
        updateUiState {
            it.copy(
                vibrantColor = Color.WHITE,
                darkMutedColor = Color.parseColor("#282828"),
                gradientColor = Color.parseColor("#121212"),
            )
        }
    }
}
