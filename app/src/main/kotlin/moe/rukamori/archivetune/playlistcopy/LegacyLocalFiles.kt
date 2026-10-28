/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import moe.rukamori.archivetune.spotify.SPOTIFY_LOCAL_FILE_PLACEHOLDER_PREFIX
import kotlin.math.abs

/**
 * Older versions gave a Spotify local file the id `spotify:<length in seconds>`. For each such row,
 * in playlist order, this finds the local file it stood for: the next one of [localFiles] (in
 * Spotify's order) of that length that is not taken yet. A row with none left gets null.
 */
internal fun assignLegacyLocalFiles(
    legacySongIds: List<String>,
    localFiles: List<CopyTrack>,
): List<CopyTrack?> {
    val available = localFiles.filter { it.songId.startsWith(SPOTIFY_LOCAL_FILE_PLACEHOLDER_PREFIX) }.toMutableList()
    return legacySongIds.map { songId ->
        val seconds = songId.substringAfterLast(':').toIntOrNull() ?: return@map null
        available
            .firstOrNull { track -> track.metadata?.duration?.let { abs(it - seconds) <= 1 } == true }
            ?.also { available.remove(it) }
    }
}
