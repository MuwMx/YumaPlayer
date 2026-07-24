/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyUser(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val product: String? = null,
    val country: String? = null,
)

@Serializable
data class SpotifyImage(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null,
)
