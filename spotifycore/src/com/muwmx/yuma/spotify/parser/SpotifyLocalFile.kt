/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.parser

import java.net.URLDecoder
import java.security.MessageDigest

/**
 * A Spotify "local file": a song the user added to a playlist from their own computer. It has no
 * track id; its URI is `spotify:local:<artist>:<album>:<title>:<seconds>`, URL-encoded. Taking the
 * last part of that URI as the id, as for real tracks, gives every local file of the same length
 * the same id.
 */
data class SpotifyLocalFile(
    val id: String,
    val artist: String?,
    val album: String?,
    val title: String?,
    val durationSec: Int?,
) {
    companion object {
        private const val URI_PREFIX = "spotify:local:"

        /** Ids of local files start with this, so they can be told apart from real track ids. */
        const val ID_PREFIX = "local-"

        fun parse(uri: String): SpotifyLocalFile? {
            if (!uri.startsWith(URI_PREFIX)) return null
            val fields = uri.removePrefix(URI_PREFIX).split(':')
            return SpotifyLocalFile(
                id = ID_PREFIX + stableHash(uri),
                artist = fields.getOrNull(0)?.let(::decode),
                album = fields.getOrNull(1)?.let(::decode),
                title = fields.getOrNull(2)?.let(::decode),
                durationSec = fields.getOrNull(3)?.toIntOrNull(),
            )
        }

        private fun decode(part: String): String? =
            runCatching { URLDecoder.decode(part, Charsets.UTF_8.name()) }
                .getOrDefault(part)
                .trim()
                .takeIf { it.isNotEmpty() }

        private fun stableHash(text: String): String =
            MessageDigest
                .getInstance("SHA-1")
                .digest(text.toByteArray(Charsets.UTF_8))
                .take(10)
                .joinToString("") { "%02x".format(it) }
    }
}
