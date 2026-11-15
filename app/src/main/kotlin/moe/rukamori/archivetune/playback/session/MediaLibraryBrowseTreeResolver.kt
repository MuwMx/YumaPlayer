/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.session

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideYtmLikedSongsKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryHomeBrowseResolver
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistBrowseResolver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import javax.inject.Inject

internal class MediaLibraryBrowseTreeResolver
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

        fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(
                    itemFactory.rootMediaItem(),
                    params,
                ),
            )

        fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            scope.future(Dispatchers.IO) {
                val items =
                    when (parentId) {
                        MediaLibraryTreeConstants.ROOT -> rootChildren()
                        MediaLibraryTreeConstants.HOME -> homeChildren()
                        MediaLibraryTreeConstants.HOME_QUICK_PICKS -> {
                            database
                                .quickPicks()
                                .first()
                                .shuffled()
                                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                                .map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.HOME_FORGOTTEN_FAVORITES -> {
                            database
                                .forgottenFavorites()
                                .first()
                                .shuffled()
                                .take(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                                .map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.HOME_KEEP_LISTENING -> {
                            homeBrowseResolver.homeKeepListeningSongs().map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.HOME_SUGGESTED_SONGS -> {
                            homeBrowseResolver.homeSuggestedSongs().map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.HOME_MIXES_AND_RADIOS -> {
                            homeBrowseResolver.homeMixesAndRadios()
                        }
                        MediaLibraryTreeConstants.QUICK_PICKS -> {
                            database.quickPicks().first().map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.RECENT -> {
                            database.recentSongs(MediaLibraryItemFactory.AUTO_BROWSE_LIMIT).first().map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.LIKED -> {
                            database
                                .likedSongs(
                                    SongSortType.CREATE_DATE,
                                    descending = true,
                                    source = if (context.dataStore.getAsync(HideYtmLikedSongsKey, false)) LikeSource.SPOTIFY else null,
                                ).first()
                                .map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.DOWNLOADED -> {
                            playlistBrowseResolver.downloadedSongs().first().map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.SONG -> {
                            database.songsByCreateDateAsc().first().map { itemFactory.toLibraryMediaItem(it, parentId) }
                        }
                        MediaLibraryTreeConstants.ARTIST -> {
                            database.artistsByCreateDateAsc().first().map { artist ->
                                itemFactory.browsableMediaItem(
                                    "${MediaLibraryTreeConstants.ARTIST}/${artist.id}",
                                    artist.artist.name,
                                    context.resources.getQuantityString(
                                        R.plurals.n_song,
                                        artist.songCount,
                                        artist.songCount,
                                    ),
                                    artist.artist.thumbnailUrl?.toUri(),
                                    MediaMetadata.MEDIA_TYPE_ARTIST,
                                )
                            }
                        }
                        MediaLibraryTreeConstants.ALBUM -> {
                            database.albumsByCreateDateAsc().first().map { album ->
                                itemFactory.browsableMediaItem(
                                    "${MediaLibraryTreeConstants.ALBUM}/${album.id}",
                                    album.album.title,
                                    album.artists.joinToString { it.name },
                                    album.album.thumbnailUrl?.toUri(),
                                    MediaMetadata.MEDIA_TYPE_ALBUM,
                                )
                            }
                        }
                        MediaLibraryTreeConstants.PLAYLIST -> playlistBrowseChildren()
                        else -> {
                            when {
                                parentId.startsWith("${MediaLibraryTreeConstants.ARTIST}/") -> {
                                    database
                                        .artistSongsByCreateDateAsc(parentId.removePrefix("${MediaLibraryTreeConstants.ARTIST}/"))
                                        .first()
                                        .map { itemFactory.toLibraryMediaItem(it, parentId) }
                                }
                                parentId.startsWith("${MediaLibraryTreeConstants.ALBUM}/") -> {
                                    database
                                        .albumSongs(parentId.removePrefix("${MediaLibraryTreeConstants.ALBUM}/"))
                                        .first()
                                        .map { itemFactory.toLibraryMediaItem(it, parentId) }
                                }
                                parentId.startsWith("${MediaLibraryTreeConstants.PLAYLIST}/") -> {
                                    playlistBrowseResolver.playlistChildren(session, parentId)
                                }
                                parentId.startsWith("${MediaLibraryTreeConstants.ONLINE_PLAYLIST}/") -> {
                                    homeBrowseResolver.onlinePlaylistChildren(parentId)
                                }
                                else -> emptyList()
                            }
                        }
                    }

                LibraryResult.ofItemList(with(itemFactory) { items.paged(page, pageSize) }, params)
            }

        private fun rootChildren(): List<MediaItem> =
            listOf(
                itemFactory.browsableMediaItem(
                    MediaLibraryTreeConstants.HOME,
                    context.getString(R.string.home),
                    null,
                    itemFactory.drawableUri(R.drawable.ic_home),
                    MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                ),
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.QUICK_PICKS,
                    context.getString(R.string.quick_picks),
                    null,
                    itemFactory.drawableUri(R.drawable.playlist_play),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.RECENT,
                    context.getString(R.string.history),
                    null,
                    itemFactory.drawableUri(R.drawable.history),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.LIKED,
                    context.getString(R.string.liked_songs),
                    null,
                    itemFactory.drawableUri(R.drawable.favorite),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.DOWNLOADED,
                    context.getString(R.string.downloaded_songs),
                    null,
                    itemFactory.drawableUri(R.drawable.download),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.browsableMediaItem(
                    MediaLibraryTreeConstants.SONG,
                    context.getString(R.string.songs),
                    null,
                    itemFactory.drawableUri(R.drawable.music_note),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.browsableMediaItem(
                    MediaLibraryTreeConstants.ARTIST,
                    context.getString(R.string.artists),
                    null,
                    itemFactory.drawableUri(R.drawable.artist),
                    MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                ),
                itemFactory.browsableMediaItem(
                    MediaLibraryTreeConstants.ALBUM,
                    context.getString(R.string.albums),
                    null,
                    itemFactory.drawableUri(R.drawable.album),
                    MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                ),
                itemFactory.browsableMediaItem(
                    MediaLibraryTreeConstants.PLAYLIST,
                    context.getString(R.string.playlists),
                    null,
                    itemFactory.drawableUri(R.drawable.queue_music),
                    MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                ),
            )

        private fun homeChildren(): List<MediaItem> =
            listOf(
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.HOME_QUICK_PICKS,
                    context.getString(R.string.quick_picks),
                    null,
                    itemFactory.drawableUri(R.drawable.playlist_play),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.HOME_FORGOTTEN_FAVORITES,
                    context.getString(R.string.forgotten_favorites),
                    null,
                    itemFactory.drawableUri(R.drawable.favorite),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.HOME_KEEP_LISTENING,
                    context.getString(R.string.keep_listening),
                    null,
                    itemFactory.drawableUri(R.drawable.history),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.queueMediaItem(
                    MediaLibraryTreeConstants.HOME_SUGGESTED_SONGS,
                    context.getString(R.string.android_auto_suggested_songs),
                    null,
                    itemFactory.drawableUri(R.drawable.music_note),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                ),
                itemFactory.browsableMediaItem(
                    MediaLibraryTreeConstants.HOME_MIXES_AND_RADIOS,
                    context.getString(R.string.android_auto_mixes_and_radios),
                    null,
                    itemFactory.drawableUri(R.drawable.radio),
                    MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                ),
            )

        private suspend fun playlistBrowseChildren(): List<MediaItem> {
            val likedSongCount = database.likedSongsCount(LikeSource.YTM).first()
            val downloadedSongCount = downloadUtil.downloads.value.size
            val defaultPlaylists =
                listOf(
                    itemFactory.queueMediaItem(
                        "${MediaLibraryTreeConstants.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}",
                        context.getString(R.string.liked_songs),
                        context.resources.getQuantityString(
                            R.plurals.n_song,
                            likedSongCount,
                            likedSongCount,
                        ),
                        itemFactory.drawableUri(R.drawable.favorite),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    ),
                    itemFactory.queueMediaItem(
                        "${MediaLibraryTreeConstants.PLAYLIST}/${PlaylistEntity.DOWNLOADED_PLAYLIST_ID}",
                        context.getString(R.string.downloaded_songs),
                        context.resources.getQuantityString(
                            R.plurals.n_song,
                            downloadedSongCount,
                            downloadedSongCount,
                        ),
                        itemFactory.drawableUri(R.drawable.download),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    ),
                )
            val userPlaylists =
                database.playlists(PlaylistSortType.CUSTOM, descending = false).first().map { playlist ->
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
            return defaultPlaylists + userPlaylists
        }

        fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            scope.future(Dispatchers.IO) {
                val staticItem =
                    when (mediaId) {
                        MediaLibraryTreeConstants.ROOT -> itemFactory.rootMediaItem()
                        else -> rootChildren().firstOrNull { it.mediaId == mediaId }
                            ?: homeChildren().firstOrNull { it.mediaId == mediaId }
                    }
                if (staticItem != null) {
                    return@future LibraryResult.ofItem(staticItem, null)
                }

                when {
                    mediaId.startsWith("${MediaLibraryTreeConstants.ONLINE_PLAYLIST}/") -> {
                        homeBrowseResolver.onlinePlaylistItem(mediaId)?.let {
                            LibraryResult.ofItem(itemFactory.ensureLibraryMediaItem(it), null)
                        } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                    mediaId.isAutoQueueSongPath() -> {
                        autoQueueSongPathItem(mediaId)?.let {
                            LibraryResult.ofItem(itemFactory.ensureLibraryMediaItem(it), null)
                        } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                    mediaId.startsWith("${MediaLibraryTreeConstants.SONG}/") -> {
                        database.song(mediaId.removePrefix("${MediaLibraryTreeConstants.SONG}/")).first()?.let {
                            LibraryResult.ofItem(itemFactory.toLibraryMediaItem(it, MediaLibraryTreeConstants.SONG), null)
                        } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                    mediaId.startsWith("${MediaLibraryTreeConstants.ARTIST}/") -> {
                        database.artist(mediaId.removePrefix("${MediaLibraryTreeConstants.ARTIST}/")).first()?.let { artist ->
                            LibraryResult.ofItem(
                                itemFactory.browsableMediaItem(
                                    "${MediaLibraryTreeConstants.ARTIST}/${artist.id}",
                                    artist.title,
                                    context.resources.getQuantityString(
                                        R.plurals.n_song,
                                        artist.songCount,
                                        artist.songCount,
                                    ),
                                    artist.thumbnailUrl?.toUri(),
                                    MediaMetadata.MEDIA_TYPE_ARTIST,
                                ),
                                null,
                            )
                        } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                    mediaId.startsWith("${MediaLibraryTreeConstants.ALBUM}/") -> {
                        database.album(mediaId.removePrefix("${MediaLibraryTreeConstants.ALBUM}/")).first()?.let { album ->
                            LibraryResult.ofItem(
                                itemFactory.browsableMediaItem(
                                    "${MediaLibraryTreeConstants.ALBUM}/${album.id}",
                                    album.title,
                                    album.artists.joinToString { it.name },
                                    album.thumbnailUrl?.toUri(),
                                    MediaMetadata.MEDIA_TYPE_ALBUM,
                                ),
                                null,
                            )
                        } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                    mediaId.startsWith("${MediaLibraryTreeConstants.PLAYLIST}/") -> {
                        playlistBrowseResolver.playlistItem(mediaId)?.let {
                            LibraryResult.ofItem(itemFactory.ensureLibraryMediaItem(it), null)
                        } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                    else -> {
                        searchController.getOnlineItem(mediaId)?.let {
                            LibraryResult.ofItem(itemFactory.ensureLibraryMediaItem(it), null)
                        } ?: database.song(mediaId).first()?.let {
                            LibraryResult.ofItem(itemFactory.toLibraryMediaItem(it), null)
                        } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

        private suspend fun autoQueueSongPathItem(mediaId: String): MediaItem? {
            val path = mediaId.pathSegments()
            val root = path.getOrNull(0) ?: return null
            val songId = path.getOrNull(1) ?: return null
            val parentId = mediaId.substringBeforeLast('/')
            return if (root == MediaLibraryTreeConstants.HOME_SUGGESTED_SONGS) {
                searchController.getOnlineItem(songId)
                    ?.buildUpon()
                    ?.setMediaId(mediaId)
                    ?.build()
                    ?: homeBrowseResolver.homeSuggestedSongs()
                        .firstOrNull { it.id == songId }
                        ?.let { itemFactory.toLibraryMediaItem(it, parentId) }
            } else {
                database.song(songId).first()?.let { itemFactory.toLibraryMediaItem(it, parentId) }
            }
        }

        private fun String.isAutoQueueSongPath(): Boolean {
            val root = pathSegments().firstOrNull() ?: return false
            return root in AUTO_QUEUE_SONG_ROOTS && contains("/")
        }

        companion object {
            private val AUTO_QUEUE_SONG_ROOTS =
                setOf(
                    MediaLibraryTreeConstants.HOME_QUICK_PICKS,
                    MediaLibraryTreeConstants.HOME_FORGOTTEN_FAVORITES,
                    MediaLibraryTreeConstants.HOME_KEEP_LISTENING,
                    MediaLibraryTreeConstants.HOME_SUGGESTED_SONGS,
                    MediaLibraryTreeConstants.QUICK_PICKS,
                    MediaLibraryTreeConstants.RECENT,
                    MediaLibraryTreeConstants.LIKED,
                    MediaLibraryTreeConstants.DOWNLOADED,
                    MediaLibraryTreeConstants.SONG,
                )
        }
    }
