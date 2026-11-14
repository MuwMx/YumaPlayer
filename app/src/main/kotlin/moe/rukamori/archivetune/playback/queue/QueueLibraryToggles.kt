/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.queue

import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber

@OptIn(UnstableApi::class)
internal class QueueLibraryToggles(
    private val scope: CoroutineScope,
    private val ioScope: CoroutineScope,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    private val dataStore: DataStore<Preferences>,
    private val delegate: Delegate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(
        scope: CoroutineScope,
        database: MusicDatabase,
        syncUtils: SyncUtils,
        dataStore: DataStore<Preferences>,
        delegate: Delegate,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        scope = scope,
        ioScope = scope,
        database = database,
        syncUtils = syncUtils,
        dataStore = dataStore,
        delegate = delegate,
        ioDispatcher = ioDispatcher,
    )

    interface Delegate {
        fun ensureScopesActive()
        suspend fun resolveVoiceMediaItems(query: String): List<MediaItem>
        fun playQueue(queue: Queue)
        fun getCurrentSong(): Song?
        fun getCurrentMediaMetadata(): MediaMetadata?
        fun getCurrentMetadata(): MediaMetadata?
        fun getActiveQueue(): Queue
        fun downloadSong(song: SongEntity)
    }

    private val toggleLikeMutex = Mutex()

    fun playFromVoiceSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        delegate.ensureScopesActive()
        scope.launch(SilentHandler) {
            val mediaItems =
                withContext(ioDispatcher) {
                    delegate.resolveVoiceMediaItems(trimmed)
                }
            if (mediaItems.isEmpty()) return@launch
            delegate.playQueue(ListQueue(items = mediaItems))
        }
    }

    fun toggleLibrary() {
        database.query {
            delegate.getCurrentSong()?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        val mediaMetadata = delegate.getCurrentMediaMetadata() ?: delegate.getCurrentMetadata() ?: return
        Timber.tag("MediaNotification").d("toggleLike() called for mediaId=${mediaMetadata.id}, title=${mediaMetadata.title}")
        val currentSongSong = delegate.getCurrentSong()?.song
        val source = LikeSourceResolver.resolve(
            mediaId = mediaMetadata.id,
            spotifyTrackId = mediaMetadata.spotifyTrackId,
            queue = delegate.getActiveQueue(),
            isLocal = currentSongSong?.isLocal ?: mediaMetadata.id.isLocalMediaId(),
        )
        val cleanSpotifyId = (mediaMetadata.spotifyTrackId ?: mediaMetadata.id)
            .removePrefix("spotify:track:")
            .removePrefix("spotify:")
            .takeIf { it.isNotBlank() }
        ioScope.launch {
            try {
                val song =
                    toggleLikeMutex.withLock {
                        database.withTransaction {
                            val currentSongEntity =
                                getSongByMetadata(
                                    songId = mediaMetadata.id,
                                    spotifyId = cleanSpotifyId,
                                    isrc = mediaMetadata.isrc?.takeIf { it.isNotBlank() },
                                )
                                    ?: run {
                                        insert(mediaMetadata) {
                                            it.copy(isLocal = mediaMetadata.id.isLocalMediaId())
                                        }
                                        getSongByMetadata(
                                            songId = mediaMetadata.id,
                                            spotifyId = cleanSpotifyId,
                                            isrc = mediaMetadata.isrc?.takeIf { it.isNotBlank() },
                                        ) ?: getSongById(mediaMetadata.id)
                                    }
                                    ?: return@withTransaction null
                            currentSongEntity.song.localToggleLike(source).also(::update)
                        }
                    } ?: return@launch

                Timber.tag("MediaNotification").d("toggleLike() successful: song=${song.id}, liked=${song.liked}")
                val spotifyId = if (!mediaMetadata.spotifyTrackId.isNullOrBlank()) {
                    mediaMetadata.spotifyTrackId
                } else if (cleanSpotifyId != null && LikeSourceResolver.isSpotifyId(mediaMetadata.id, song.isLocal)) {
                    cleanSpotifyId
                } else if (LikeSourceResolver.isSpotifyId(song.id, song.isLocal)) {
                    song.id
                } else {
                    null
                }
                syncUtils.likeSong(song, source, spotifyId)

                if (!song.isLocal && dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                    delegate.downloadSong(song)
                }
            } catch (e: Exception) {
                Timber.tag("MediaNotification").e(e, "toggleLike() failed for mediaId=${mediaMetadata.id}")
                reportException(e)
            }
        }
    }
}
