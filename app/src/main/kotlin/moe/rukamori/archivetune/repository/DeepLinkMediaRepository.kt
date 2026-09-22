/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkMediaRepository @Inject constructor() {
    suspend fun getAlbumSongs(playlistId: String): Result<List<SongItem>> =
        withContext(Dispatchers.IO) {
            YouTube.albumSongs(playlistId)
        }

    suspend fun getQueue(videoId: String, playlistId: String?): Result<List<SongItem>> =
        withContext(Dispatchers.IO) {
            YouTube.queue(listOf(videoId), playlistId)
        }

    suspend fun getPlaylist(playlistId: String): Result<PlaylistPage> =
        withContext(Dispatchers.IO) {
            YouTube.playlist(playlistId)
        }
}
