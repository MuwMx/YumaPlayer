/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

open class SpotifyLikedSongsQueue(
    title: String? = null,
    initialTracks: List<SpotifyTrack> = emptyList(),
    startIndex: Int = 0,
    preloadItem: MediaMetadata? = null,
) : SpotifyTracksQueue(
    title = title,
    initialTracks = initialTracks,
    startIndex = startIndex,
    preloadItem = preloadItem,
) {
    constructor(
        allTracks: List<SpotifyTrack>,
        startIndex: Int = 0,
        preloadItem: MediaMetadata? = null,
        title: String? = null,
    ) : this(
        title = title,
        initialTracks = allTracks,
        startIndex = startIndex,
        preloadItem = preloadItem,
    )

    override suspend fun fetchPage(offset: Int, limit: Int): PageResult {
        val result = Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
        return PageResult(
            tracks = result.items.map { it.track }.filter { !it.isLocal },
            total = result.total,
            rawCount = result.items.size,
        )
    }
}
