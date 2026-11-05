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

    fun isYouTubeAlbumId(id: String?): Boolean = id != null && id.startsWith("MPREb")

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
                ?.firstOrNull { isYouTubeArtistId(it.id) && it.name.equals(artistName, ignoreCase = true) }
                ?.id
                ?: database.artistByName(artistName)?.id?.takeIf(::isYouTubeArtistId)
                ?: search {
                    YouTube.search(artistName, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow().items
                        .filterIsInstance<ArtistItem>()
                        .firstOrNull { sameName(it.title, artistName) }
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
            matched?.song?.albumId?.takeIf(::isYouTubeAlbumId)
                ?: matched?.let { song ->
                    search {
                        YouTube.queue(listOf(song.id)).getOrThrow().firstOrNull()?.album?.id?.takeIf(::isYouTubeAlbumId)
                    }
                }
                ?: albumTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    search {
                        val query = listOfNotNull(title, artistName).joinToString(" ")
                        val found =
                            YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrThrow().items
                                .filterIsInstance<AlbumItem>()
                                .filter { sameName(it.title, title) }
                        (
                            found.firstOrNull { album -> artistName != null && album.artists.orEmpty().any { sameName(it.name, artistName) } }
                                ?: found.firstOrNull()
                        )?.browseId
                    }
                }
        }.also { Timber.tag("SpotifyPageTargets").d("Album '%s' (%s) -> %s", albumTitle, albumId, it) }
            ?.also { albums[albumId] = it }
    }

    private fun sameName(
        a: String,
        b: String,
    ): Boolean = normalized(a) == normalized(b)

    private fun normalized(name: String): String =
        name
            .lowercase()
            .replace(EDITION_SUFFIX, "")
            .filter { it.isLetterOrDigit() }

    private val EDITION_SUFFIX = Regex("""\s*(-\s*(single|ep)|[(\[][^)\]]*(deluxe|edition|remaster|expanded|version)[^)\]]*[)\]])\s*$""")

    private fun matchedSong(
        songId: String,
        database: MusicDatabase,
    ): Song? {
        if (!SpotifyPlaceholders.isPlaceholder(songId)) return null
        val match = database.getSpotifyMatch(songId.removePrefix(SPOTIFY_PLACEHOLDER_PREFIX)) ?: return null
        return database.getSongByIdBlocking(match.youtubeId)
    }

    private suspend fun search(block: suspend () -> String?): String? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("SpotifyPageTargets").w(e, "YouTube search failed")
            null
        }
}
