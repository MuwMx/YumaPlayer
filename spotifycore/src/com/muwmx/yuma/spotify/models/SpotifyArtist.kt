/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyArtist(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    val genres: List<String> = emptyList(),
    val popularity: Int? = null,
    val uri: String? = null,
)
