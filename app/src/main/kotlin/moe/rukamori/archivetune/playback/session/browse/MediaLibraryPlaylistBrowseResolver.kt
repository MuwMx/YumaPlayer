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
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideYtmLikedSongsKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.session.MediaLibraryItemFactory
import moe.rukamori.archivetune.playback.session.MediaLibraryTreeConstants
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_ACTION_ADD_CURRENT_SONG
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_ACTION_SHUFFLE
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_ACTION_SORT
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.PLAYLIST_SORT_OPTIONS
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.playlistSortOption
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.playlistSortPath
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.playlistSortTitle
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.sortDownloadedSongs
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.sortedPlaylistSongs
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistSortHelper.toSongSortType
import moe.rukamori.archivetune.playback.session.pathSegments
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import moe.rukamori.archivetune.utils.isLocalMediaId
import javax.inject.Inject

internal class MediaLibraryPlaylistBrowseResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        private val itemFactory: MediaLibraryItemFactory,
    ) {
        suspend fun playlistChildren(
            session: MediaLibrarySession,
            parentId: String,
        ): List<MediaItem> {
            val path = parentId.pathSegments()
            val playlistId = path.getOrNull(1) ?: return emptyList()
            return when (path.getOrNull(2)) {
                null -> {
                    val actionItems =
                        buildList {
                            add(
                                itemFactory.queueMediaItem(
                                    "$parentId/$PLAYLIST_ACTION_SHUFFLE",
                                    context.getString(R.string.shuffle),
                                    null,
                                    itemFactory.drawableUri(R.drawable.shuffle),
                                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                ),
                            )
                            add(
                                itemFactory.browsableMediaItem(
                                    "$parentId/$PLAYLIST_ACTION_SORT",
                                    context.getString(R.string.android_auto_sort_playlist),
                                    null,
                                    itemFactory.drawableUri(R.drawable.list),
                                    MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                                ),
                            )
                            val playlist = database.getPlaylistById(playlistId)
                            val playlistEntity = playlist?.playlist
                            val currentSongId =
                                withContext(Dispatchers.Main.immediate) {
                                    session.player.currentMediaItem
                                        ?.mediaId
                                        ?.trim()
                                }?.takeIf(String::isNotBlank)
                            val canAddCurrentSong =
                                playlistEntity != null &&
                                    playlistEntity.isEditable &&
                                    currentSongId != null &&
                                    database.checkInPlaylist(playlistId, currentSongId) == 0 &&
                                    (playlistEntity.browseId == null || !currentSongId.isLocalMediaId())
                            if (canAddCurrentSong) {
                                add(
                                    itemFactory.queueMediaItem(
                                        "$parentId/$PLAYLIST_ACTION_ADD_CURRENT_SONG",
                                        context.getString(R.string.add_current_song),
                                        playlistEntity?.name,
                                        itemFactory.drawableUri(R.drawable.playlist_add),
                                        MediaMetadata.MEDIA_TYPE_MUSIC,
                                    ),
                                )
                            }
                        }
                    actionItems + playlistSongs(playlistId).map { itemFactory.toLibraryMediaItem(it, parentId) }
                }

                PLAYLIST_ACTION_SORT -> {
                    if (path.size == 3) {
                        PLAYLIST_SORT_OPTIONS.map { option ->
                            itemFactory.queueMediaItem(
                                playlistSortPath(playlistId, option),
                                playlistSortTitle(context, option),
                                null,
                                itemFactory.drawableUri(R.drawable.list),
                                MediaMetadata.MEDIA_TYPE_PLAYLIST,
                            )
                        }
                    } else {
                        val sortOption =
                            playlistSortOption(path.getOrNull(3), path.getOrNull(4))
                                ?: return emptyList()
                        playlistSongs(playlistId, sortOption).map { itemFactory.toLibraryMediaItem(it, parentId) }
                    }
                }

                PLAYLIST_ACTION_SHUFFLE -> {
                    playlistSongs(playlistId).shuffled().map { itemFactory.toLibraryMediaItem(it, parentId) }
                }

                else -> {
                    emptyList()
                }
            }
        }

        suspend fun playlistItem(mediaId: String): MediaItem? {
            val path = mediaId.pathSegments()
            val playlistId = path.getOrNull(1) ?: return null
            return when (path.getOrNull(2)) {
                null -> {
                    playlistHeaderItem(playlistId)
                }

                PLAYLIST_ACTION_SHUFFLE -> {
                    if (path.size == 3) {
                        itemFactory.queueMediaItem(
                            mediaId,
                            context.getString(R.string.shuffle),
                            playlistHeaderItem(playlistId)?.mediaMetadata?.title?.toString(),
                            itemFactory.drawableUri(R.drawable.shuffle),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        )
                    } else {
                        path.getOrNull(3)?.let { songId ->
                            database.song(songId).first()?.let {
                                itemFactory.toLibraryMediaItem(it, mediaId.substringBeforeLast('/'))
                            }
                        }
                    }
                }

                PLAYLIST_ACTION_ADD_CURRENT_SONG -> {
                    itemFactory.queueMediaItem(
                        mediaId,
                        context.getString(R.string.add_current_song),
                        playlistHeaderItem(playlistId)?.mediaMetadata?.title?.toString(),
                        itemFactory.drawableUri(R.drawable.playlist_add),
                        MediaMetadata.MEDIA_TYPE_MUSIC,
                    )
                }

                PLAYLIST_ACTION_SORT -> {
                    if (path.size == 3) {
                        itemFactory.browsableMediaItem(
                            mediaId,
                            context.getString(R.string.android_auto_sort_playlist),
                            null,
                            itemFactory.drawableUri(R.drawable.list),
                            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                        )
                    } else if (path.size > 5) {
                        path.getOrNull(5)?.let { songId ->
                            database.song(songId).first()?.let {
                                itemFactory.toLibraryMediaItem(it, mediaId.substringBeforeLast('/'))
                            }
                        }
                    } else {
                        playlistSortOption(path.getOrNull(3), path.getOrNull(4))?.let { option ->
                            itemFactory.queueMediaItem(
                                mediaId,
                                playlistSortTitle(context, option),
                                playlistHeaderItem(playlistId)?.mediaMetadata?.title?.toString(),
                                itemFactory.drawableUri(R.drawable.list),
                                MediaMetadata.MEDIA_TYPE_PLAYLIST,
                            )
                        }
                    }
                }

                else -> {
                    val songId =
                        if (path.getOrNull(2) == PLAYLIST_ACTION_SORT) {
                            path.getOrNull(5)
                        } else {
                            path.getOrNull(2)
                        } ?: return null
                    database.song(songId).first()?.let {
                        itemFactory.toLibraryMediaItem(it, mediaId.substringBeforeLast('/'))
                    }
                }
            }
        }

        suspend fun playlistHeaderItem(playlistId: String): MediaItem? =
            when (playlistId) {
                PlaylistEntity.LIKED_PLAYLIST_ID -> {
                    val count = database.likedSongsCount(LikeSource.YTM).first()
                    itemFactory.queueMediaItem(
                        "${MediaLibraryTreeConstants.PLAYLIST}/$playlistId",
                        context.getString(R.string.liked_songs),
                        context.resources.getQuantityString(R.plurals.n_song, count, count),
                        itemFactory.drawableUri(R.drawable.favorite),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    )
                }

                PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> {
                    val count = downloadUtil.downloads.value.size
                    itemFactory.queueMediaItem(
                        "${MediaLibraryTreeConstants.PLAYLIST}/$playlistId",
                        context.getString(R.string.downloaded_songs),
                        context.resources.getQuantityString(R.plurals.n_song, count, count),
                        itemFactory.drawableUri(R.drawable.download),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    )
                }

                else -> {
                    database.playlist(playlistId).first()?.let { playlist ->
                        itemFactory.queueMediaItem(
                            "${MediaLibraryTreeConstants.PLAYLIST}/${playlist.id}",
                            playlist.title,
                            context.resources.getQuantityString(
                                R.plurals.n_song,
                                playlist.songCount,
                                playlist.songCount,
                            ),
                            playlist.thumbnails.firstOrNull()?.toUri(),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        )
                    }
                }
            }

        suspend fun playlistSongs(
            playlistId: String,
            sortOption: AutoPlaylistSortOption? = null,
        ): List<Song> =
            when (playlistId) {
                PlaylistEntity.LIKED_PLAYLIST_ID -> {
                    val songSortType = sortOption?.sortType?.toSongSortType() ?: SongSortType.CREATE_DATE
                    database
                        .likedSongs(
                            songSortType,
                            descending = sortOption?.descending ?: true,
                            source = if (context.dataStore.getAsync(HideYtmLikedSongsKey, false)) LikeSource.SPOTIFY else LikeSource.YTM,
                        ).first()
                }

                PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> {
                    sortDownloadedSongs(downloadedSongs().first(), sortOption)
                }

                else -> {
                    database
                        .playlistSongs(playlistId)
                        .first()
                        .sortedPlaylistSongs(sortOption)
                        .map { it.song }
                }
            }

        fun downloadedSongs(): Flow<List<Song>> {
            val downloads = downloadUtil.downloads.value
            return database
                .allSongs()
                .flowOn(Dispatchers.IO)
                .map { songs ->
                    songs.filter {
                        downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                }.map { songs ->
                    songs
                        .map { it to downloads[it.id] }
                        .sortedBy { it.second?.updateTimeMs ?: 0L }
                        .map { it.first }
                }
        }

        suspend fun addCurrentSongToPlaylist(
            mediaSession: MediaSession,
            playlistId: String,
        ): Boolean {
            val currentItem =
                withContext(Dispatchers.Main.immediate) {
                    mediaSession.player.currentMediaItem
                } ?: return false
            val songId = currentItem.mediaId.trim().takeIf(String::isNotBlank) ?: return false
            return withContext(Dispatchers.IO) {
                val playlist = database.getPlaylistById(playlistId) ?: return@withContext false
                if (!playlist.playlist.isEditable) return@withContext false
                if (database.checkInPlaylist(playlistId, songId) > 0) return@withContext false
                val browseId = playlist.playlist.browseId
                val setVideoId =
                    if (browseId != null) {
                        if (songId.isLocalMediaId()) return@withContext false
                        YouTube.addToPlaylist(browseId, songId).getOrNull() ?: return@withContext false
                    } else {
                        null
                    }
                database.withTransaction {
                    if (getSongByIdBlocking(songId) == null) {
                        currentItem.metadata?.let { insert(it) } ?: return@withTransaction false
                    }
                    val latestPlaylist = getPlaylistByIdBlocking(playlistId) ?: return@withTransaction false
                    if (checkInPlaylist(playlistId, songId) > 0) return@withTransaction false
                    addSongEntriesToPlaylist(latestPlaylist, listOf(songId to setVideoId))
                    true
                }
            }
        }
    }
