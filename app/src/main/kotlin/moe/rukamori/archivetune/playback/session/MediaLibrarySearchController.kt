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
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playback.engine.extractMediaIdFromCacheKey
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

internal data class OfflineSongSearchResult(
    val items: List<MediaItem>,
    val count: Int,
)

internal class MediaLibrarySearchController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        private val itemFactory: MediaLibraryItemFactory,
    ) {
        private val scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()
        private var pendingSearchJob: Job? = null
        private val onlineSearchItemCache = ConcurrentHashMap<String, MediaItem>()
        @Volatile private var cachedSearchResult: CachedSearchResult? = null

        private data class CachedSearchResult(
            val query: String,
            val items: List<MediaItem>,
            val timestamp: Long = System.currentTimeMillis(),
        )

        fun getOnlineItem(mediaId: String): MediaItem? = onlineSearchItemCache[mediaId]

        fun putOnlineItem(
            mediaId: String,
            item: MediaItem,
        ) {
            onlineSearchItemCache[mediaId] = item
        }

        fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            Futures.immediateFuture(LibraryResult.ofVoid(params)).also {
                val q = query.trim()
                pendingSearchJob?.cancel()
                pendingSearchJob =
                    scope.launch(Dispatchers.IO) {
                        var resultCount: Int? = null
                        try {
                            if (q.isBlank()) {
                                cachedSearchResult = null
                                resultCount = 0
                            } else {
                                val items = executeSearch(q, MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                                cachedSearchResult = CachedSearchResult(q, items)
                                resultCount = items.size
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Timber.tag("MediaLibrary").w(e, "Search failed for %s", q)
                            resultCount = 0
                        } finally {
                            val finalCount = resultCount ?: 0
                            withContext(NonCancellable + Dispatchers.Main) {
                                session.notifySearchResultChanged(browser, query, finalCount, params)
                            }
                        }
                    }
            }

        fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            scope.future(Dispatchers.IO) {
                val q = query.trim()
                val safePage = page.coerceAtLeast(0)
                val safePageSize = pageSize.coerceIn(1, 50)
                if (q.isBlank()) {
                    return@future LibraryResult.ofItemList(emptyList(), params)
                }

                val cached = cachedSearchResult
                val isCacheHit =
                    cached != null &&
                        cached.query == q &&
                        (System.currentTimeMillis() - cached.timestamp < SEARCH_CACHE_TTL_MS)

                val items =
                    if (isCacheHit) {
                        cached.items
                    } else {
                        val requested = (safePage + 1) * safePageSize
                        val searchLimit = max(requested, MediaLibraryItemFactory.AUTO_BROWSE_LIMIT)
                        val fetched = executeSearch(q, searchLimit)
                        cachedSearchResult = CachedSearchResult(q, fetched)
                        fetched
                    }

                val from = safePage * safePageSize
                if (from >= items.size) return@future LibraryResult.ofItemList(emptyList(), params)
                val to = min(from + safePageSize, items.size)

                LibraryResult.ofItemList(items.subList(from, to), params)
            }

        private data class OnlineSearchResults(
            val songs: List<MediaItem>,
            val albums: List<MediaItem>,
            val artists: List<MediaItem>,
        )

        suspend fun executeSearch(
            query: String,
            requested: Int,
        ): List<MediaItem> {
            val q = query.trim()
            if (q.isBlank() || requested <= 0) return emptyList()

            val items = ArrayList<MediaItem>(min(requested, 200))

            val offlineSongs = searchOfflineSongs(q, previewSize = requested)
            val existingSongIds =
                offlineSongs.items
                    .mapTo(HashSet(offlineSongs.items.size * 2), ::searchSongIdentity)
            val onlineResults = searchOnline(q, songLimit = requested)
            val onlineSongs =
                onlineResults.songs.filter { onlineItem ->
                    existingSongIds.add(searchSongIdentity(onlineItem))
                }
            onlineSongs.forEach { onlineSearchItemCache[it.mediaId] = it }
            onlineResults.albums.forEach { onlineSearchItemCache[it.mediaId] = it }
            onlineResults.artists.forEach { onlineSearchItemCache[it.mediaId] = it }
            val songBudget = (requested - onlineResults.albums.size - onlineResults.artists.size).coerceAtLeast(0)
            items +=
                interleaveMediaItems(
                    first = offlineSongs.items,
                    second = onlineSongs,
                ).take(songBudget)

            items += onlineResults.albums
            items += onlineResults.artists

            if (items.size < requested) {
                val remaining = requested - items.size
                val artists = database.searchArtists(q, previewSize = remaining).first()
                items +=
                    artists.map { artist ->
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
                        )
                    }
            }

            if (items.size < requested) {
                val remaining = requested - items.size
                val albums = database.searchAlbums(q, previewSize = remaining).first()
                items +=
                    albums.map { album ->
                        itemFactory.browsableMediaItem(
                            "${MediaLibraryTreeConstants.ALBUM}/${album.id}",
                            album.title,
                            album.artists.joinToString { it.name },
                            album.thumbnailUrl?.toUri(),
                            MediaMetadata.MEDIA_TYPE_ALBUM,
                        )
                    }
            }

            if (items.size < requested) {
                val remaining = requested - items.size
                val playlists = database.searchPlaylists(q, previewSize = remaining).first()
                items +=
                    playlists.map { playlist ->
                        itemFactory.browsableMediaItem(
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

            return items
        }

        suspend fun searchOfflineSongs(
            query: String,
            previewSize: Int,
        ): OfflineSongSearchResult {
            if (query.isBlank() || previewSize <= 0) {
                return OfflineSongSearchResult(
                    items = emptyList(),
                    count = 0,
                )
            }

            val librarySongs = database.searchSongs(query, previewSize = previewSize).first()
            val libraryIds = librarySongs.mapTo(HashSet(librarySongs.size)) { it.id }
            val cachedOnlySongs = searchCachedOnlySongs(query, excludeIds = libraryIds)

            return OfflineSongSearchResult(
                items =
                    interleaveMediaItems(
                        first = librarySongs.map { itemFactory.toLibraryMediaItem(it, MediaLibraryTreeConstants.SONG) },
                        second = cachedOnlySongs.take(previewSize).map { itemFactory.toLibraryMediaItem(it) },
                    ).take(previewSize),
                count = database.searchSongsCount(query) + cachedOnlySongs.size,
            )
        }

        private suspend fun searchCachedOnlySongs(
            query: String,
            excludeIds: Set<String> = emptySet(),
        ): List<Song> {
            val cachedIds = cachedSongIds()
            if (cachedIds.isEmpty()) return emptyList()

            val normalizedQuery = query.lowercase(Locale.getDefault())
            return database
                .getSongsByIds(cachedIds)
                .asSequence()
                .filter { it.song.inLibrary == null }
                .filterNot { it.id in excludeIds }
                .filter {
                    it.song.title
                        .lowercase(Locale.getDefault())
                        .contains(normalizedQuery)
                }.sortedBy { it.song.title.lowercase(Locale.getDefault()) }
                .toList()
        }

        fun cachedSongIds(): List<String> {
            val completedDownloadIds =
                downloadUtil.downloads.value
                    .asSequence()
                    .filter { (_, download) -> download.state == Download.STATE_COMPLETED }
                    .map { (id, _) -> extractMediaIdFromCacheKey(id) }
            val downloadCacheIds =
                runCatching { downloadUtil.downloadCache.keys.asSequence() }
                    .getOrDefault(emptySequence())
                    .map(::extractMediaIdFromCacheKey)
            val playerCacheIds =
                runCatching { downloadUtil.playerCache.keys.asSequence() }
                    .getOrDefault(emptySequence())
                    .map(::extractMediaIdFromCacheKey)

            return sequenceOf(completedDownloadIds, downloadCacheIds, playerCacheIds)
                .flatten()
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .toList()
        }

        fun interleaveMediaItems(
            first: List<MediaItem>,
            second: List<MediaItem>,
        ): List<MediaItem> {
            if (first.isEmpty()) return second
            if (second.isEmpty()) return first

            val merged = ArrayList<MediaItem>(first.size + second.size)
            val maxSize = maxOf(first.size, second.size)
            repeat(maxSize) { index ->
                first.getOrNull(index)?.let(merged::add)
                second.getOrNull(index)?.let(merged::add)
            }
            return merged
        }

        suspend fun resolveVoiceMediaItems(
            query: String,
            previewSize: Int = 50,
        ): List<MediaItem> {
            val q = query.trim()
            if (q.isBlank()) return emptyList()
            val offlineSongs = searchOfflineSongs(q, previewSize)
            val existingIds = offlineSongs.items.mapTo(HashSet(offlineSongs.items.size * 2), ::searchSongIdentity)
            val onlineSongs =
                searchOnlineSongs(q, previewSize).filter { onlineItem ->
                    existingIds.add(searchSongIdentity(onlineItem))
                }
            onlineSongs.forEach { onlineSearchItemCache[it.mediaId] = it }
            return interleaveMediaItems(offlineSongs.items, onlineSongs).take(previewSize)
        }

        fun searchSongIdentity(item: MediaItem): String = item.mediaId.removePrefix("${MediaLibraryTreeConstants.SONG}/")

        private suspend fun searchOnline(
            query: String,
            songLimit: Int,
            albumLimit: Int = ONLINE_ALBUM_ARTIST_LIMIT,
            artistLimit: Int = ONLINE_ALBUM_ARTIST_LIMIT,
        ): OnlineSearchResults {
            if (query.isBlank() || songLimit <= 0) {
                return OnlineSearchResults(emptyList(), emptyList(), emptyList())
            }
            return withTimeoutOrNull(6_000L.milliseconds) {
                coroutineScope {
                    val songsDeferred =
                        async {
                            runCatching {
                                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty()
                            }.getOrDefault(emptyList())
                                .asSequence()
                                .filterIsInstance<SongItem>()
                                .distinctBy { it.id }
                                .take(songLimit)
                                .map { itemFactory.toLibraryMediaItem(it) }
                                .toList()
                        }
                    val albumsDeferred =
                        async {
                            runCatching {
                                YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items.orEmpty()
                            }.getOrDefault(emptyList())
                                .asSequence()
                                .filterIsInstance<AlbumItem>()
                                .distinctBy { it.browseId }
                                .take(albumLimit)
                                .map { album ->
                                    itemFactory.browsableMediaItem(
                                        id = "${MediaLibraryTreeConstants.ONLINE_ALBUM}/${album.browseId}",
                                        title = album.title,
                                        subtitle = album.artists?.joinToString { it.name },
                                        iconUri = album.thumbnail.toUri(),
                                        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
                                    )
                                }.toList()
                        }
                    val artistsDeferred =
                        async {
                            runCatching {
                                YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items.orEmpty()
                            }.getOrDefault(emptyList())
                                .asSequence()
                                .filterIsInstance<ArtistItem>()
                                .distinctBy { it.id }
                                .take(artistLimit)
                                .map { artist ->
                                    itemFactory.browsableMediaItem(
                                        id = "${MediaLibraryTreeConstants.ONLINE_ARTIST}/${artist.id}",
                                        title = artist.title,
                                        subtitle = artist.monthlyListenerCountText ?: artist.subscriberCountText,
                                        iconUri = artist.thumbnail?.toUri(),
                                        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
                                    )
                                }.toList()
                        }
                    OnlineSearchResults(
                        songs = songsDeferred.await(),
                        albums = albumsDeferred.await(),
                        artists = artistsDeferred.await(),
                    )
                }
            } ?: OnlineSearchResults(emptyList(), emptyList(), emptyList())
        }

        suspend fun searchOnlineSongs(
            query: String,
            previewSize: Int,
        ): List<MediaItem> {
            if (query.isBlank() || previewSize <= 0) return emptyList()
            return withTimeoutOrNull(6_000L.milliseconds) {
                YouTube
                    .search(query, YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    .orEmpty()
                    .asSequence()
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
                    .take(previewSize)
                    .map { song ->
                        itemFactory.toLibraryMediaItem(song)
                    }.toList()
            } ?: emptyList()
        }

        companion object {
            private const val SEARCH_CACHE_TTL_MS = 30_000L
            private const val ONLINE_ALBUM_ARTIST_LIMIT = 6
        }
    }
