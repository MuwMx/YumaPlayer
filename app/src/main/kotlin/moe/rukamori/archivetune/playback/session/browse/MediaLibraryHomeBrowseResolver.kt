/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.session.browse

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.playback.session.MediaLibraryItemFactory
import moe.rukamori.archivetune.playback.session.MediaLibrarySearchController
import moe.rukamori.archivetune.playback.session.MediaLibraryTreeConstants
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_ACTION_SHUFFLE
import moe.rukamori.archivetune.playback.session.pathSegments
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import javax.inject.Inject

internal class MediaLibraryHomeBrowseResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val itemFactory: MediaLibraryItemFactory,
        private val searchController: MediaLibrarySearchController,
    ) {
        suspend fun homeKeepListeningSongs(): List<Song> {
            val fromTimestamp = System.currentTimeMillis() - HOME_RECENT_WINDOW_MS
            return database
                .mostPlayedSongs(fromTimestamp, limit = MediaLibraryItemFactory.AUTO_BROWSE_LIMIT, offset = 0)
                .first()
                .ifEmpty { database.recentSongs(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT).first() }
        }

        suspend fun homeSuggestedSongs(): List<SongItem> {
            val hideExplicit = context.dataStore.getAsync(HideExplicitKey, false)
            val hideVideo = context.dataStore.getAsync(HideVideoKey, false)
            return YouTube
                .home()
                .getOrNull()
                ?.sections
                .orEmpty()
                .flatMap { it.items }
                .filterExplicit(hideExplicit)
                .filterVideo(hideVideo)
                .filterIsInstance<SongItem>()
                .distinctBy { it.id }
                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                .onEach { searchController.putOnlineItem(it.id, itemFactory.toLibraryMediaItem(it)) }
        }

        suspend fun homeMixesAndRadios(): List<MediaItem> {
            val localPlaylists =
                database
                    .playlists(PlaylistSortType.LAST_UPDATED, descending = true)
                    .first()
                    .take(AUTO_HOME_PLAYLIST_LIMIT)
                    .map { playlist ->
                        itemFactory.queueMediaItem(
                            "${MediaLibraryTreeConstants.PLAYLIST}/${playlist.id}",
                            playlist.playlist.name,
                            context.resources.getQuantityString(
                                R.plurals.n_song,
                                playlist.songCount,
                                playlist.songCount,
                            ),
                            playlist.thumbnails.firstOrNull()?.toUri(),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        )
                    }
            val onlinePlaylists = homeOnlinePlaylists()
            return localPlaylists + onlinePlaylists
        }

        suspend fun homeOnlinePlaylists(): List<MediaItem> {
            val hideExplicit = context.dataStore.getAsync(HideExplicitKey, false)
            val hideVideo = context.dataStore.getAsync(HideVideoKey, false)
            return YouTube
                .home()
                .getOrNull()
                ?.sections
                .orEmpty()
                .flatMap { it.items }
                .filterExplicit(hideExplicit)
                .filterVideo(hideVideo)
                .filterIsInstance<PlaylistItem>()
                .distinctBy { it.id }
                .take(AUTO_HOME_PLAYLIST_LIMIT)
                .map { playlist ->
                    itemFactory.queueMediaItem(
                        "${MediaLibraryTreeConstants.ONLINE_PLAYLIST}/${playlist.id}",
                        playlist.title,
                        playlist.songCountText ?: playlist.author?.name,
                        playlist.thumbnail?.toUri(),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    )
                }
        }

        suspend fun onlinePlaylistChildren(parentId: String): List<MediaItem> {
            val path = parentId.pathSegments()
            val playlistId = path.getOrNull(1) ?: return emptyList()
            return when (path.getOrNull(2)) {
                null -> {
                    listOf(
                        itemFactory.queueMediaItem(
                            "$parentId/$PLAYLIST_ACTION_SHUFFLE",
                            context.getString(R.string.shuffle),
                            null,
                            itemFactory.drawableUri(R.drawable.shuffle),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        ),
                    ) + onlinePlaylistSongs(playlistId).map { itemFactory.toLibraryMediaItem(it, parentId) }
                }

                PLAYLIST_ACTION_SHUFFLE -> {
                    onlinePlaylistSongs(playlistId).map { itemFactory.toLibraryMediaItem(it, parentId) }
                }

                else -> {
                    emptyList()
                }
            }
        }

        suspend fun onlinePlaylistItem(mediaId: String): MediaItem? {
            val path = mediaId.pathSegments()
            val playlistId = path.getOrNull(1) ?: return null
            return when (path.getOrNull(2)) {
                null -> {
                    val playlist = YouTube.playlist(playlistId).getOrNull()?.playlist
                    itemFactory.queueMediaItem(
                        "${MediaLibraryTreeConstants.ONLINE_PLAYLIST}/$playlistId",
                        playlist?.title ?: playlistId,
                        playlist?.songCountText ?: playlist?.author?.name,
                        playlist?.thumbnail?.toUri(),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    )
                }

                PLAYLIST_ACTION_SHUFFLE -> {
                    if (path.size == 3) {
                        itemFactory.queueMediaItem(
                            mediaId,
                            context.getString(R.string.shuffle),
                            null,
                            itemFactory.drawableUri(R.drawable.shuffle),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        )
                    } else {
                        path.getOrNull(3)?.let { songId ->
                            onlinePlaylistSongItem(
                                playlistId = playlistId,
                                songId = songId,
                                parentId = mediaId.substringBeforeLast('/'),
                            )
                        }
                    }
                }

                else -> {
                    val songId = path.getOrNull(2) ?: return null
                    onlinePlaylistSongItem(
                        playlistId = playlistId,
                        songId = songId,
                        parentId = mediaId.substringBeforeLast('/'),
                    )
                }
            }
        }

        suspend fun onlinePlaylistSongs(playlistId: String): List<SongItem> =
            YouTube
                .playlist(playlistId)
                .getOrNull()
                ?.songs
                .orEmpty()
                .distinctBy { it.id }
                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                .onEach { searchController.putOnlineItem(it.id, itemFactory.toLibraryMediaItem(it)) }

        suspend fun onlinePlaylistSongItem(
            playlistId: String,
            songId: String,
            parentId: String,
        ): MediaItem? =
            searchController.getOnlineItem(songId)
                ?.buildUpon()
                ?.setMediaId("$parentId/$songId")
                ?.build()
                ?: onlinePlaylistSongs(playlistId)
                    .firstOrNull { it.id == songId }
                    ?.let { itemFactory.toLibraryMediaItem(it, parentId) }

        companion object {
            private const val AUTO_HOME_PLAYLIST_LIMIT = 20
            private const val HOME_RECENT_WINDOW_MS = 86400000L * 14L
        }
    }
