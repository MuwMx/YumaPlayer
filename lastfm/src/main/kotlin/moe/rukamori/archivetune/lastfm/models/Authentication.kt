/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lastfm.models

import kotlinx.serialization.Serializable

@Serializable
data class Authentication(
    val session: Session,
) {
    @Serializable
    data class Session(
        val name: String, // Username
        val key: String, // Session Key
        val subscriber: Int, // Last.fm Pro?
    )
}

@Serializable
data class TokenResponse(
    val token: String,
)

@Serializable
data class LastFmError(
    val error: Int,
    val message: String,
)
