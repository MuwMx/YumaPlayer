/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.models.SpotifyRecentItem
import moe.rukamori.archivetune.spotify.models.SpotifyArtist

sealed interface SpotifyHomeScreenState {
    data object Loading : SpotifyHomeScreenState

    data class Success(
        val sections: List<SpotifyHomeSection>,
        val recentItems: List<SpotifyRecentItem> = emptyList(),
        val frequentArtists: List<SpotifyArtist> = emptyList(),
    ) : SpotifyHomeScreenState

    data object Empty : SpotifyHomeScreenState

    data class Error(
        val messageResId: Int,
        val notAuthenticated: Boolean = false,
    ) : SpotifyHomeScreenState
}

sealed interface SpotifyHomeNavigationEvent {
    data class OpenArtist(
        val id: String,
    ) : SpotifyHomeNavigationEvent
}

sealed interface SpotifyHomeAction {
    data object Refresh : SpotifyHomeAction

    data class ArtistClick(
        val artist: SpotifyArtist,
    ) : SpotifyHomeAction
}
