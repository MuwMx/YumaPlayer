/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.session

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.HideYtmLikedSongsKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.playback.queue.PlaybackResumptionPlanner
import moe.rukamori.archivetune.playback.queue.QueuePersistenceStore.Companion.PERSISTENT_QUEUE_FILE
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryHomeBrowseResolver
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistBrowseResolver
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_ACTION_ADD_CURRENT_SONG
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_ACTION_SHUFFLE
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_ACTION_SORT
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import java.io.ObjectInputStream
import javax.inject.Inject

internal class MediaLibraryPlaybackItemResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        private val itemFactory: MediaLibraryItemFactory,
        private val searchController: MediaLibrarySearchController,
        private val playlistBrowseResolver: MediaLibraryPlaylistBrowseResolver,
        private val homeBrowseResolver: MediaLibraryHomeBrowseResolver,
    ) {
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = onPlaybackResumption(mediaSession, controller, true)

        fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            scope.future {
                val player = mediaSession.player
                val currentItems = List(player.mediaItemCount) { index -> player.getMediaItemAt(index) }
                val persistedItems =
                    withContext(Dispatchers.IO) {
                        readPersistentQueue()?.let { queue ->
                            PlaybackResumptionPlanner.PersistedItems(
                                items = queue.items.map { it.toMediaItem() },
                                mediaItemIndex = queue.mediaItemIndex,
                                positionMs = queue.position,
                            )
                        }
                    }
                val result =
                    PlaybackResumptionPlanner.resolve(
                        currentItems = currentItems,
                        currentIndex = player.currentMediaItemIndex,
                        currentPositionMs = player.currentPosition,
                        persistedItems = persistedItems,
                        isForPlayback = isForPlayback,
                    )
                MediaSession.MediaItemsWithStartPosition(
                    result.items,
                    result.startIndex,
                    result.startPositionMs,
                )
            }

        private fun readPersistentQueue(): PersistQueue? {
            val file = context.filesDir.resolve(PERSISTENT_QUEUE_FILE)
            if (!file.exists() || !file.isFile) return null
            return try {
                file.inputStream().use { fis ->
                    ObjectInputStream(fis).use { input ->
                        input.readObject() as? PersistQueue
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            scope.future(Dispatchers.IO) {
                val defaultResult =
                    MediaSession.MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
                val firstItem = mediaItems.firstOrNull() ?: return@future defaultResult
                val voiceQuery =
                    firstItem.requestMetadata.searchQuery
                        ?.trim()
                        .orEmpty()
                if (voiceQuery.isNotBlank()) {
                    val searchQueue = searchController.resolveVoiceMediaItems(voiceQuery, 50)
                    if (searchQueue.isNotEmpty()) {
                        return@future MediaSession.MediaItemsWithStartPosition(
                            searchQueue,
                            0,
                            startPositionMs,
                        )
                    }
                }
                val path = firstItem.mediaId.pathSegments()
                when (path.firstOrNull()) {
                    MediaLibraryTreeConstants.HOME_QUICK_PICKS -> {
                        val songs =
                            database
                                .quickPicks()
                                .first()
                                .shuffled()
                                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.HOME_FORGOTTEN_FAVORITES -> {
                        val songs =
                            database
                                .forgottenFavorites()
                                .first()
                                .shuffled()
                                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.HOME_KEEP_LISTENING -> {
                        val songs = homeBrowseResolver.homeKeepListeningSongs()
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.HOME_SUGGESTED_SONGS -> {
                        val songs = homeBrowseResolver.homeSuggestedSongs().map { it.toMediaItem() }
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.QUICK_PICKS -> {
                        val songs = database.quickPicks().first()
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.RECENT -> {
                        val songs = database.recentSongs(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT).first()
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.LIKED -> {
                        val songs =
                            database
                                .likedSongs(
                                    SongSortType.CREATE_DATE,
                                    descending = true,
                                    source = if (context.dataStore.getAsync(HideYtmLikedSongsKey, false)) LikeSource.SPOTIFY else null,
                                ).first()
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.DOWNLOADED -> {
                        val songs = playlistBrowseResolver.downloadedSongs().first()
                        songs.toMediaItemsWithStartPosition(path.getOrNull(1), startPositionMs)
                    }

                    MediaLibraryTreeConstants.SONG -> {
                        val songId = path.getOrNull(1) ?: return@future defaultResult
                        val allSongs = database.songsByCreateDateAsc().first()
                        MediaSession.MediaItemsWithStartPosition(
                            allSongs.map { it.toMediaItem() },
                            allSongs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                            startPositionMs,
                        )
                    }

                    MediaLibraryTreeConstants.ARTIST -> {
                        val songId = path.getOrNull(2) ?: return@future defaultResult
                        val artistId = path.getOrNull(1) ?: return@future defaultResult
                        val songs = database.artistSongsByCreateDateAsc(artistId).first()
                        MediaSession.MediaItemsWithStartPosition(
                            songs.map { it.toMediaItem() },
                            songs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                            startPositionMs,
                        )
                    }

                    MediaLibraryTreeConstants.ALBUM -> {
                        val songId = path.getOrNull(2) ?: return@future defaultResult
                        val albumId = path.getOrNull(1) ?: return@future defaultResult
                        val albumWithSongs =
                            database.albumWithSongs(albumId).first() ?: return@future defaultResult
                        MediaSession.MediaItemsWithStartPosition(
                            albumWithSongs.songs.map { it.toMediaItem() },
                            albumWithSongs.songs.indexOfFirst { it.id == songId }.takeIf { it != -1 }
                                ?: 0,
                            startPositionMs,
                        )
                    }

                    MediaLibraryTreeConstants.PLAYLIST -> {
                        val playlistId = path.getOrNull(1) ?: return@future defaultResult
                        val action = path.getOrNull(2)
                        if (action == PLAYLIST_ACTION_ADD_CURRENT_SONG) {
                            playlistBrowseResolver.addCurrentSongToPlaylist(mediaSession, playlistId)
                            return@future mediaSession.currentMediaItemsWithStartPosition()
                        }
                        val sortOption =
                            if (action == PLAYLIST_ACTION_SORT) {
                                MediaLibraryPlaylistSortHelper.playlistSortOption(
                                    sortType = path.getOrNull(3),
                                    order = path.getOrNull(4),
                                )
                            } else {
                                null
                            }
                        val selectedSongId =
                            when {
                                sortOption != null -> path.getOrNull(5)
                                action == PLAYLIST_ACTION_SHUFFLE -> path.getOrNull(3)
                                else -> path.getOrNull(2)
                            }
                        val songs =
                            playlistBrowseResolver.playlistSongs(
                                playlistId = playlistId,
                                sortOption = sortOption,
                            ).let { playlistSongList ->
                                if (action == PLAYLIST_ACTION_SHUFFLE) playlistSongList.shuffled() else playlistSongList
                            }
                        if (action == PLAYLIST_ACTION_SHUFFLE) {
                            withContext(Dispatchers.Main.immediate) {
                                mediaSession.player.shuffleModeEnabled = true
                            }
                        }
                        MediaSession.MediaItemsWithStartPosition(
                            songs.map { it.toMediaItem() },
                            selectedSongId?.let { songId ->
                                songs.indexOfFirst { it.id == songId }.takeIf { it != -1 }
                            } ?: 0,
                            startPositionMs,
                        )
                    }

                    MediaLibraryTreeConstants.ONLINE_PLAYLIST -> {
                        val playlistId = path.getOrNull(1) ?: return@future defaultResult
                        val action = path.getOrNull(2)
                        val selectedSongId =
                            if (action == PLAYLIST_ACTION_SHUFFLE) path.getOrNull(3) else path.getOrNull(2)
                        val songs =
                            homeBrowseResolver.onlinePlaylistSongs(playlistId).let { playlistSongList ->
                                if (action == PLAYLIST_ACTION_SHUFFLE) playlistSongList.shuffled() else playlistSongList
                            }
                        if (action == PLAYLIST_ACTION_SHUFFLE) {
                            withContext(Dispatchers.Main.immediate) {
                                mediaSession.player.shuffleModeEnabled = true
                            }
                        }
                        val mediaItemsList = songs.map { it.toMediaItem() }
                        MediaSession.MediaItemsWithStartPosition(
                            mediaItemsList,
                            selectedSongId?.let { songId ->
                                mediaItemsList.indexOfFirst { it.mediaId == songId }.takeIf { it != -1 }
                            } ?: 0,
                            startPositionMs,
                        )
                    }

                    else -> {
                        val directMediaId = firstItem.mediaId.trim()
                        if (directMediaId.isNotBlank() && !directMediaId.contains("/")) {
                            val selectedItem = searchController.getOnlineItem(directMediaId) ?: firstItem
                            return@future MediaSession.MediaItemsWithStartPosition(
                                listOf(selectedItem),
                                0,
                                startPositionMs,
                            )
                        }

                        val query =
                            firstItem.requestMetadata.searchQuery
                                ?.trim()
                                .orEmpty()
                        if (query.isBlank()) return@future defaultResult

                        val matchedSongs = database.searchSongs(query, previewSize = 50).first()
                        val songId = matchedSongs.firstOrNull()?.id ?: return@future defaultResult
                        val allSongs = database.songsByCreateDateAsc().first()
                        MediaSession.MediaItemsWithStartPosition(
                            allSongs.map { it.toMediaItem() },
                            allSongs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                            startPositionMs,
                        )
                    }
                }
            }

        fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            scope.future(Dispatchers.IO) {
                mediaItems
                    .flatMap { item ->
                        val query =
                            item.requestMetadata.searchQuery
                                ?.trim()
                                .orEmpty()
                        if (query.isBlank()) {
                            listOf(item)
                        } else {
                            val resolved = searchController.resolveVoiceMediaItems(query)
                            resolved.ifEmpty { listOf(item) }
                        }
                    }.toMutableList()
            }

        private suspend fun MediaSession.currentMediaItemsWithStartPosition(): MediaSession.MediaItemsWithStartPosition =
            withContext(Dispatchers.Main.immediate) {
                MediaSession.MediaItemsWithStartPosition(
                    List(player.mediaItemCount) { index -> player.getMediaItemAt(index) },
                    player.currentMediaItemIndex.coerceAtLeast(0),
                    player.currentPosition,
                )
            }

        private fun List<Song>.toMediaItemsWithStartPosition(
            selectedSongId: String?,
            startPositionMs: Long,
        ) = MediaSession.MediaItemsWithStartPosition(
            map { it.toMediaItem() },
            selectedSongId?.let { id ->
                indexOfFirst { it.id == id }.takeIf { it != -1 }
            } ?: 0,
            startPositionMs,
        )

        @JvmName("mediaItemsToMediaItemsWithStartPosition")
        private fun List<MediaItem>.toMediaItemsWithStartPosition(
            selectedMediaId: String?,
            startPositionMs: Long,
        ) = MediaSession.MediaItemsWithStartPosition(
            this,
            selectedMediaId?.let { mediaId ->
                indexOfFirst { it.mediaId == mediaId }.takeIf { it != -1 }
            } ?: 0,
            startPositionMs,
        )
    }
