package moe.rukamori.archivetune.ui.state

import androidx.annotation.StringRes

sealed interface PlayerEvent {
    data class ShareTrack(val url: String) : PlayerEvent
    data class Navigate(val route: String) : PlayerEvent
    data class ShowToast(@StringRes val messageRes: Int) : PlayerEvent
}
