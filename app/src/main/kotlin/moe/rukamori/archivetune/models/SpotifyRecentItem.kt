package moe.rukamori.archivetune.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface SpotifyRecentItem {
    val id: String
    val title: String
    val subtitle: String
    val thumbnailUrl: String?

    @Immutable
    @Serializable
    data class Playlist(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val thumbnailUrl: String?,
        val trackCount: Int = 0,
    ) : SpotifyRecentItem

    @Immutable
    @Serializable
    data class Album(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val thumbnailUrl: String?,
        val artistName: String = "",
    ) : SpotifyRecentItem
}
