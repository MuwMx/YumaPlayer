/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.session

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.constants.MediaSessionConstants
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryHomeBrowseResolver
import moe.rukamori.archivetune.playback.session.browse.MediaLibraryPlaylistBrowseResolver
import timber.log.Timber
import javax.inject.Inject

class MediaLibrarySessionCallback
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        val database: MusicDatabase,
        val downloadUtil: DownloadUtil,
    ) : MediaLibrarySession.Callback {
        private val itemFactory = MediaLibraryItemFactory(context)
        private val searchController =
            MediaLibrarySearchController(context, database, downloadUtil, itemFactory)
        private val playlistBrowseResolver =
            MediaLibraryPlaylistBrowseResolver(context, database, downloadUtil, itemFactory)
        private val homeBrowseResolver =
            MediaLibraryHomeBrowseResolver(context, database, itemFactory, searchController)
        private val browseTreeResolver =
            MediaLibraryBrowseTreeResolver(
                context = context,
                database = database,
                downloadUtil = downloadUtil,
                itemFactory = itemFactory,
                searchController = searchController,
                playlistBrowseResolver = playlistBrowseResolver,
                homeBrowseResolver = homeBrowseResolver,
            )
        private val playbackItemResolver =
            MediaLibraryPlaybackItemResolver(
                context = context,
                database = database,
                downloadUtil = downloadUtil,
                itemFactory = itemFactory,
                searchController = searchController,
                playlistBrowseResolver = playlistBrowseResolver,
                homeBrowseResolver = homeBrowseResolver,
            )

        var toggleLike: () -> Unit = {}
        var toggleStartRadio: () -> Unit = {}
        var toggleLibrary: () -> Unit = {}
        var moveInShuffleOrder: (fromIndex: Int, toIndex: Int) -> Boolean = { _, _ -> false }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .add(MediaSessionConstants.CommandToggleLike)
                    .add(MediaSessionConstants.CommandToggleStartRadio)
                    .add(MediaSessionConstants.CommandToggleLibrary)
                    .add(MediaSessionConstants.CommandToggleShuffle)
                    .add(MediaSessionConstants.CommandToggleRepeatMode)
                    .add(MediaSessionConstants.CommandMoveInShuffleOrder)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            Timber.tag("MediaNotification").d("onCustomCommand: action=${customCommand.customAction}")
            when (customCommand.customAction) {
                MediaSessionConstants.ACTION_TOGGLE_LIKE -> toggleLike()
                MediaSessionConstants.ACTION_TOGGLE_START_RADIO -> toggleStartRadio()
                MediaSessionConstants.ACTION_TOGGLE_LIBRARY -> toggleLibrary()
                MediaSessionConstants.ACTION_TOGGLE_SHUFFLE -> {
                    session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                }
                MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE -> {
                    session.player.toggleRepeatMode()
                }
                MediaSessionConstants.ACTION_MOVE_IN_SHUFFLE_ORDER -> {
                    val moved =
                        moveInShuffleOrder(
                            args.getInt(MediaSessionConstants.EXTRA_FROM_INDEX, C.INDEX_UNSET),
                            args.getInt(MediaSessionConstants.EXTRA_TO_INDEX, C.INDEX_UNSET),
                        )
                    if (!moved) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            browseTreeResolver.onGetLibraryRoot(session, browser, params)

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            browseTreeResolver.onGetChildren(session, browser, parentId, page, pageSize, params)

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            browseTreeResolver.onGetItem(session, browser, mediaId)

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            searchController.onSearch(session, browser, query, params)

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            searchController.onGetSearchResult(session, browser, query, page, pageSize, params)

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackItemResolver.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            playbackItemResolver.onAddMediaItems(mediaSession, controller, mediaItems)

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackItemResolver.onPlaybackResumption(mediaSession, controller)

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackItemResolver.onPlaybackResumption(mediaSession, controller, isForPlayback)

        internal fun cachedSongIds(): List<String> = searchController.cachedSongIds()

        internal suspend fun resolveVoiceMediaItems(
            query: String,
            previewSize: Int = 50,
        ): List<MediaItem> = searchController.resolveVoiceMediaItems(query, previewSize)
    }
