/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.cast

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

sealed interface CastScreenState {
    data object Loading : CastScreenState

    data class Success(
        val uiState: CastUiState,
    ) : CastScreenState

    data object Empty : CastScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : CastScreenState
}

@Immutable
data class CastUiState(
    val isAvailable: Boolean,
    val isConnected: Boolean,
    val device: CastDeviceUiModel?,
    val volume: Float,
)

@Immutable
data class CastDeviceUiModel(
    val id: String,
    val name: String,
)
