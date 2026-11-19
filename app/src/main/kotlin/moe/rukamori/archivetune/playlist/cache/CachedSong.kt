/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.cache

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem

/**
 * A song that is fully cached by the player cache (not merely downloaded).
 * Lives in the domain layer so [PlaylistTrack.origin] can carry it without
 * leaking UI framework types into the playlist source package.
 */
data class CachedSong(
    val song: Song,
    val source: String,
    val cachedBytes: Long,
    val formattedSize: String,
    val isFullyCached: Boolean,
) {
    val id: String get() = song.id
    val title: String get() = song.song.title
    val artists get() = song.artists
    val duration: Int get() = song.song.duration
    val thumbnailUrl get() = song.song.thumbnailUrl

    fun toMediaItem() = song.toMediaItem()
}
