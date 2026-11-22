/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object SpotifyPageTargets {
    private val artists = ConcurrentHashMap<String, String>()
    private val albums = ConcurrentHashMap<String, String>()

    fun isYouTubeArtistId(id: String?): Boolean = id != null && id.startsWith("UC") && ':' !in id

    fun isYouTubeAlbumId(id: String?): Boolean =
        id != null && (id.startsWith("MPREb") || id.startsWith("OLAK5uy")) && ':' !in id

    suspend fun artistId(
        songId: String,
        artistId: String,
        artistName: String,
        database: MusicDatabase,
    ): String? {
        if (isYouTubeArtistId(artistId)) return artistId
        artists[artistId]?.let { return it }
        return withContext(Dispatchers.IO) {
            matchedSong(songId, database)
                ?.artists
                ?.firstOrNull { isYouTubeArtistId(it.id) && sameArtist(it.name, artistName) }
                ?.id
                ?: database.artistByName(artistName)?.id?.takeIf(::isYouTubeArtistId)
                ?: search {
                    YouTube.search(artistName, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow().items
                        .filterIsInstance<ArtistItem>()
                        .firstOrNull { sameArtist(it.title, artistName) }
                        ?.id
                }
        }.also { Timber.tag("SpotifyPageTargets").d("Artist '%s' (%s) -> %s", artistName, artistId, it) }
            ?.also { artists[artistId] = it }
    }

    suspend fun albumId(
        songId: String,
        albumId: String,
        albumTitle: String?,
        artistName: String?,
        database: MusicDatabase,
    ): String? {
        if (isYouTubeAlbumId(albumId)) return albumId
        albums[albumId]?.let { return it }
        return withContext(Dispatchers.IO) {
            val matched = matchedSong(songId, database)
            val ytSongId = matched?.id ?: songId.takeIf { !SpotifyPlaceholders.isPlaceholder(it) }
            matched?.song?.albumId?.takeIf(::isYouTubeAlbumId)
                ?: ytSongId?.let { queueId ->
                    search {
                        YouTube.queue(listOf(queueId)).getOrThrow().firstOrNull()?.album?.id?.takeIf(::isYouTubeAlbumId)
                    }
                }
                ?: albumTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    search {
                        val query = listOfNotNull(title, artistName).joinToString(" ")
                        val candidates =
                            YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrThrow().items
                                .filterIsInstance<AlbumItem>()
                        (
                            candidates.firstOrNull { album ->
                                sameAlbum(album.title, title) && artistName != null && album.artists.orEmpty().any { sameArtist(it.name, artistName) }
                            }
                                ?: candidates.firstOrNull { sameAlbum(it.title, title) }
                                ?: candidates.firstOrNull { album ->
                                    artistName != null && isCleanTitleMatch(album.title, title) && album.artists.orEmpty().any { sameArtist(it.name, artistName) }
                                }
                                ?: candidates.firstOrNull { isCleanTitleMatch(it.title, title) }
                        )?.browseId
                    }
                }
        }.also { Timber.tag("SpotifyPageTargets").d("Album '%s' (%s) -> %s", albumTitle, albumId, it) }
            ?.also { albums[albumId] = it }
    }

    fun sameArtist(
        a: String,
        b: String,
    ): Boolean = normalizeArtistName(a) == normalizeArtistName(b)

    fun sameAlbum(
        a: String,
        b: String,
    ): Boolean = normalizeAlbumName(a) == normalizeAlbumName(b)

    internal fun normalizeArtistName(name: String): String {
        val stripped = name.replace(ARTIST_SUFFIX, "").trim()
        val target = if (stripped.isNotBlank()) stripped else name
        return target.lowercase().filter { it.isLetterOrDigit() }
    }

    internal fun normalizeAlbumName(name: String): String {
        var cleaned = name
        while (true) {
            val next = cleaned.replace(EDITION_SUFFIX, "").trim()
            if (next == cleaned || next.isBlank()) break
            cleaned = next
        }
        return cleaned.lowercase().filter { it.isLetterOrDigit() }
    }

    internal fun isCleanTitleMatch(a: String, b: String): Boolean {
        val normA = normalizeAlbumName(a)
        val normB = normalizeAlbumName(b)
        if (normA == normB) return true
        val minLen = minOf(normA.length, normB.length)
        if (minLen < 3) return false
        return normA.startsWith(normB) || normB.startsWith(normA)
    }

    private val ARTIST_SUFFIX = Regex(
        """(?i)\s*(-?\s*topic|official(?:\s*artist)?(?:\s*channel)?|vevo|music)\s*$"""
    )

    private val EDITION_SUFFIX = Regex(
        """(?i)\s*(-\s*(single|ep|remaster(?:ed)?(?:\s*\d{4})?|deluxe|bonus(?:\s*track)?(?:\s*version)?)|[(\[][^)\]]*(deluxe|edition|remaster(?:ed)?|expanded|version|single|ep|explicit|bonus|anniversary|special|reissue|mix|remix|original(?:\s*motion\s*picture)?(?:\s*soundtrack)?|soundtrack|ost)[^)\]]*[)\]])\s*$"""
    )

    private fun matchedSong(
        songId: String,
        database: MusicDatabase,
    ): Song? {
        if (SpotifyPlaceholders.isPlaceholder(songId)) {
            val match = database.getSpotifyMatch(songId.removePrefix(SPOTIFY_PLACEHOLDER_PREFIX)) ?: return null
            return database.getSongByIdBlocking(match.youtubeId)
        }
        return database.getSongByIdBlocking(songId)
    }

    private suspend fun search(block: suspend () -> String?): String? {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("SpotifyPageTargets").w(e, "YouTube search attempt 1 failed, retrying...")
        }

        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("SpotifyPageTargets").w(e, "YouTube search retry failed")
            null
        }
    }
}
