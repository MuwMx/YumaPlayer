/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.deeplink

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.repository.DeepLinkMediaRepository
import javax.inject.Inject

class ResolveAlbumBrowseIdUseCase @Inject constructor(
    private val repository: DeepLinkMediaRepository,
) {
    suspend operator fun invoke(playlistId: String): Result<String?> =
        repository.getAlbumSongs(playlistId).map { songs ->
            songs.firstOrNull()?.album?.id
        }
}

class ResolveQueueMediaItemUseCase @Inject constructor(
    private val repository: DeepLinkMediaRepository,
) {
    suspend operator fun invoke(videoId: String, playlistId: String?): Result<MediaItem> =
        repository.getQueue(videoId, playlistId).map { queued ->
            queued.firstOrNull { it.id == videoId }?.toMediaItem()
                ?: queued.firstOrNull()?.toMediaItem()
                ?: MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri(videoId)
                    .setCustomCacheKey(videoId)
                    .build()
        }
}

class ResolveWatchPlaylistEndpointUseCase @Inject constructor(
    private val repository: DeepLinkMediaRepository,
) {
    suspend operator fun invoke(playlistId: String, shuffle: Boolean): Result<WatchEndpoint?> =
        repository.getPlaylist(playlistId).map { playlistPage ->
            if (shuffle) {
                playlistPage.playlist.shuffleEndpoint ?: playlistPage.playlist.playEndpoint
            } else {
                playlistPage.playlist.playEndpoint ?: playlistPage.playlist.shuffleEndpoint
            }
        }
}
