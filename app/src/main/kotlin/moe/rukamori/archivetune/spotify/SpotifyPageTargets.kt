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

/**
 * Songs that came from Spotify carry Spotify artist and album ids, which the YouTube artist and
 * album pages cannot open (they stayed blank). These find the YouTube page instead: from the
 * YouTube song the track is matched to, then the library, and only then with one search.
 */
object SpotifyPageTargets {
    private val artists = ConcurrentHashMap<String, String>()
    private val albums = ConcurrentHashMap<String, String>()

    fun isYouTubeArtistId(id: String?): Boolean = id != null && id.startsWith("UC") && ':' !in id

    fun isYouTubeAlbumId(id: String?): Boolean = id != null && id.startsWith("MPREb")

    /** The YouTube channel id for [artistName] on song [songId], or null when none can be found. */
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

    /** The YouTube album id for album [albumTitle] on song [songId], or null when none can be found. */
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
                // The saved row keeps Spotify's album, so YouTube is asked for the matched song's own.
                ?: matched?.let { song ->
                    search {
                        YouTube.queue(listOf(song.id)).getOrThrow().firstOrNull()?.album?.id?.takeIf(::isYouTubeAlbumId)
                    }
                }
                ?: albumTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    search {
                        val query = listOfNotNull(title, artistName).joinToString(" ")
                        // Only an album of that name is opened, preferably one by the song's artist;
                        // the first result is often a different album altogether.
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

    // "Album - Single", "Album (Deluxe Edition)" and the like name the same album.
    private val EDITION_SUFFIX = Regex("""\s*(-\s*(single|ep)|[(\[][^)\]]*(deluxe|edition|remaster|expanded|version)[^)\]]*[)\]])\s*$""")

    /** The YouTube song a Spotify placeholder is matched to, if it is stored. */
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
