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
import moe.rukamori.archivetune.innertube.models.AlbumItem
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
            resolveOnlineSongItem(songId, parentId) { onlinePlaylistSongs(playlistId) }

        suspend fun onlineAlbumChildren(parentId: String): List<MediaItem> {
            val path = parentId.pathSegments()
            val browseId = path.getOrNull(1) ?: return emptyList()
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
                    ) + onlineAlbumSongs(browseId).map { itemFactory.toLibraryMediaItem(it, parentId) }
                }

                PLAYLIST_ACTION_SHUFFLE -> {
                    onlineAlbumSongs(browseId).map { itemFactory.toLibraryMediaItem(it, parentId) }
                }

                else -> {
                    emptyList()
                }
            }
        }

        suspend fun onlineAlbumItem(mediaId: String): MediaItem? {
            val path = mediaId.pathSegments()
            val browseId = path.getOrNull(1) ?: return null
            return when (path.getOrNull(2)) {
                null -> {
                    val album = YouTube.album(browseId, withSongs = false).getOrNull()?.album
                    itemFactory.browsableMediaItem(
                        "${MediaLibraryTreeConstants.ONLINE_ALBUM}/$browseId",
                        album?.title ?: browseId,
                        album?.artists?.joinToString { it.name },
                        album?.thumbnail?.toUri(),
                        MediaMetadata.MEDIA_TYPE_ALBUM,
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
                            resolveOnlineSongItem(
                                songId = songId,
                                parentId = mediaId.substringBeforeLast('/'),
                            ) { onlineAlbumSongs(browseId) }
                        }
                    }
                }

                else -> {
                    val songId = path.getOrNull(2) ?: return null
                    resolveOnlineSongItem(
                        songId = songId,
                        parentId = mediaId.substringBeforeLast('/'),
                    ) { onlineAlbumSongs(browseId) }
                }
            }
        }

        suspend fun onlineAlbumSongs(browseId: String): List<SongItem> =
            YouTube
                .album(browseId)
                .getOrNull()
                ?.songs
                .orEmpty()
                .distinctBy { it.id }
                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                .onEach { searchController.putOnlineItem(it.id, itemFactory.toLibraryMediaItem(it)) }

        suspend fun onlineArtistChildren(parentId: String): List<MediaItem> {
            val path = parentId.pathSegments()
            val artistId = path.getOrNull(1) ?: return emptyList()
            return when (path.getOrNull(2)) {
                null -> {
                    val allItems =
                        YouTube
                            .artist(artistId)
                            .getOrNull()
                            ?.sections
                            .orEmpty()
                            .flatMap { it.items }
                    val songs =
                        allItems
                            .filterIsInstance<SongItem>()
                            .distinctBy { it.id }
                            .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                            .onEach { searchController.putOnlineItem(it.id, itemFactory.toLibraryMediaItem(it)) }
                    val albums =
                        allItems
                            .filterIsInstance<AlbumItem>()
                            .distinctBy { it.browseId }
                            .take(AUTO_ARTIST_ALBUM_LIMIT)
                            .map { album ->
                                itemFactory.browsableMediaItem(
                                    "${MediaLibraryTreeConstants.ONLINE_ALBUM}/${album.browseId}",
                                    album.title,
                                    album.artists?.joinToString { it.name },
                                    album.thumbnail.toUri(),
                                    MediaMetadata.MEDIA_TYPE_ALBUM,
                                )
                            }
                    val shuffleItem =
                        if (songs.isNotEmpty()) {
                            listOf(
                                itemFactory.queueMediaItem(
                                    "$parentId/$PLAYLIST_ACTION_SHUFFLE",
                                    context.getString(R.string.shuffle),
                                    null,
                                    itemFactory.drawableUri(R.drawable.shuffle),
                                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                    shuffleItem + songs.map { itemFactory.toLibraryMediaItem(it, parentId) } + albums
                }

                PLAYLIST_ACTION_SHUFFLE -> {
                    onlineArtistSongs(artistId).map { itemFactory.toLibraryMediaItem(it, parentId) }
                }

                else -> {
                    emptyList()
                }
            }
        }

        suspend fun onlineArtistItem(mediaId: String): MediaItem? {
            val path = mediaId.pathSegments()
            val artistId = path.getOrNull(1) ?: return null
            return when (path.getOrNull(2)) {
                null -> {
                    val artist = YouTube.artist(artistId).getOrNull()?.artist
                    itemFactory.browsableMediaItem(
                        "${MediaLibraryTreeConstants.ONLINE_ARTIST}/$artistId",
                        artist?.title ?: artistId,
                        artist?.monthlyListenerCountText ?: artist?.subscriberCountText,
                        artist?.thumbnail?.toUri(),
                        MediaMetadata.MEDIA_TYPE_ARTIST,
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
                            resolveOnlineSongItem(
                                songId = songId,
                                parentId = mediaId.substringBeforeLast('/'),
                            ) { onlineArtistSongs(artistId) }
                        }
                    }
                }

                else -> {
                    val songId = path.getOrNull(2) ?: return null
                    resolveOnlineSongItem(
                        songId = songId,
                        parentId = mediaId.substringBeforeLast('/'),
                    ) { onlineArtistSongs(artistId) }
                }
            }
        }

        suspend fun onlineArtistSongs(artistId: String): List<SongItem> =
            YouTube
                .artist(artistId)
                .getOrNull()
                ?.sections
                .orEmpty()
                .flatMap { it.items }
                .filterIsInstance<SongItem>()
                .distinctBy { it.id }
                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                .onEach { searchController.putOnlineItem(it.id, itemFactory.toLibraryMediaItem(it)) }

        private suspend fun resolveOnlineSongItem(
            songId: String,
            parentId: String,
            loadSongs: suspend () -> List<SongItem>,
        ): MediaItem? =
            searchController.getOnlineItem(songId)
                ?.buildUpon()
                ?.setMediaId("$parentId/$songId")
                ?.build()
                ?: loadSongs()
                    .firstOrNull { it.id == songId }
                    ?.let { itemFactory.toLibraryMediaItem(it, parentId) }

        companion object {
            private const val AUTO_HOME_PLAYLIST_LIMIT = 20
            private const val AUTO_ARTIST_ALBUM_LIMIT = 10
            private const val HOME_RECENT_WINDOW_MS = 86400000L * 14L
        }
    }
