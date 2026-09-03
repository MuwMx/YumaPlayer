/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

class SpotifyLikedSongsQueue(
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
    private val total: Int = initialTracks.size,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val allTracks = initialTracks.toList()
    private var isInitialized = false

    override suspend fun getInitialStatus(): Queue.Status {
        try {
            if (allTracks.isEmpty()) {
                return Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }
            val targetIndex = startIndex.coerceIn(allTracks.indices)
            val stubItems = allTracks.map { it.toStubMediaItem() }
            return Queue.Status(
                title = title,
                items = stubItems,
                mediaItemIndex = targetIndex,
            )
        } finally {
            isInitialized = true
        }
    }

    override fun hasNextPage(): Boolean = isInitialized && allTracks.size < total

    override suspend fun nextPage(): List<MediaItem> = emptyList()

    private fun SpotifyTrack.toStubMediaItem(): MediaItem {
        val metadata =
            MediaMetadata(
                id = id,
                title = name,
                artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
                duration = if (durationMs > 0) durationMs / 1000 else -1,
                thumbnailUrl = SpotifyMapper.getTrackThumbnail(this),
                album = album?.let { MediaMetadata.Album(id = it.id, title = it.name) },
                explicit = explicit,
                spotifyTrackId = id.takeIf(String::isNotBlank),
                isrc = externalIds?.isrc?.takeIf { it.isNotBlank() },
            )
        return metadata.toMediaItem()
    }
}
