/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.db.entities.Song

internal fun Song.discordAlbumMusicUrl(): String? =
    if (song.isLocal || song.id.isLocalMediaId()) {
        null
    } else {
        album
            ?.takeUnless { it.isLocal }
            ?.let { album ->
                album.playlistId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "https://music.youtube.com/playlist?list=$it" }
                    ?: album.id.toYouTubeMusicAlbumUrl()
            }
            ?: song.albumId.toYouTubeMusicAlbumUrl()
    }

private fun String?.toYouTubeMusicAlbumUrl(): String? {
    val id = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (id.isLocalMediaId()) return null

    return if (id.startsWith("OLAK5uy_", ignoreCase = true)) {
        "https://music.youtube.com/playlist?list=$id"
    } else {
        "https://music.youtube.com/browse/$id"
    }
}
