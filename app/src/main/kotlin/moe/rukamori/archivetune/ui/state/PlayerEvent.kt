package moe.rukamori.archivetune.ui.state

sealed interface PlayerEvent {
    data class ShareTrack(val url: String) : PlayerEvent
    data class Navigate(val route: String) : PlayerEvent
}