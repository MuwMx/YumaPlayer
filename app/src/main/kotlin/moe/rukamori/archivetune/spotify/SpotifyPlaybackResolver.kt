/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.mapping.SpotifyEntityMapper
import moe.rukamori.archivetune.spotify.mapping.SpotifyQueryBuilder
import moe.rukamori.archivetune.spotify.matching.SpotifySearchScorer
import moe.rukamori.archivetune.spotify.matching.TrackMatcher
import moe.rukamori.archivetune.spotify.matching.TrackQuery
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber

object SpotifyPlaybackResolver {
    private const val CACHE_MAX_SIZE = 512

    private val matcher = TrackMatcher()
    private val scorer = SpotifySearchScorer(matcher)

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<String, MediaMetadata>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean = size > CACHE_MAX_SIZE
        }

    @Volatile
    private var databaseRef: MusicDatabase? = null

    suspend fun resolveToMediaItem(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
    ): MediaItem? = resolveToMetadata(track, database)?.toMediaItem()

    suspend fun resolveToMetadata(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
    ): MediaMetadata? =
        withContext(Dispatchers.IO) {
            val db = database ?: databaseRef
            if (database != null && databaseRef == null) {
                databaseRef = database
            }

            val rawSpotifyId = track.id.removePrefix("spotify:track:").removePrefix("spotify:")

            mutex.withLock {
                (cache[rawSpotifyId] ?: cache[track.id])?.let { cached ->
                    Timber.tag("SpotifyMatching").d("CACHE HIT: '${track.name}' -> YT ${cached.id}")
                    return@withContext cached
                }
            }

            if (db != null) {
                val match =
                    if (rawSpotifyId.isNotBlank()) {
                        db.getSpotifyMatch(rawSpotifyId) ?: db.getSpotifyMatch(track.id)
                    } else {
                        db.getSpotifyMatch(track.id)
                    }
                if (match != null) {
                    val dbSong = db.getSongByIdBlocking(match.youtubeId)
                    val metadata =
                        if (dbSong != null) {
                            dbSong.toMediaMetadata().copy(
                                thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: dbSong.song.thumbnailUrl,
                                duration = if (track.durationMs > 0) track.durationMs / 1000 else dbSong.song.duration,
                                album =
                                    track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                                        ?: dbSong.album?.let { MediaMetadata.Album(id = it.id, title = it.title) },
                                explicit = track.explicit || dbSong.song.explicit,
                                spotifyTrackId = track.id.takeIf(String::isNotBlank),
                                isrc = track.externalIds?.isrc?.takeIf { it.isNotBlank() } ?: match.isrc ?: dbSong.song.isrc,
                            )
                        } else {
                            MediaMetadata(
                                id = match.youtubeId,
                                title = track.name,
                                artists = track.artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
                                duration = if (track.durationMs > 0) track.durationMs / 1000 else -1,
                                thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track),
                                album = track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) },
                                explicit = track.explicit,
                                spotifyTrackId = track.id.takeIf(String::isNotBlank),
                                isrc = track.externalIds?.isrc?.takeIf { it.isNotBlank() } ?: match.isrc,
                            )
                        }

                    mutex.withLock {
                        cache[track.id] = metadata
                        if (rawSpotifyId.isNotBlank()) {
                            cache[rawSpotifyId] = metadata
                        }
                    }
                    Timber.tag("SpotifyMatching").d("DB MATCH HIT: '${track.name}' -> YT ${metadata.id}")
                    return@withContext metadata
                }
            }

            val artistsList = track.artists.map { it.name }.filter { it.isNotBlank() }
            val queryArtist = artistsList.joinToString(", ")
            Timber.tag("SpotifyMatching").d(
                "INPUT: title='${track.name}', artists=[$queryArtist], dur=${track.durationMs}ms, " +
                "isrc=${track.externalIds?.isrc}, explicit=${track.explicit}",
            )

            val query = SpotifyQueryBuilder.buildSearchQuery(track)
            Timber.tag("SpotifyMatching").d("QUERY: '$query'")

            val searchResult =
                YouTube
                    .search(
                        query = query,
                        filter = YouTube.SearchFilter.FILTER_SONG,
                    ).getOrNull()
            if (searchResult == null) {
                Timber.tag("SpotifyMatching").w("MISS '${track.name}': YouTube search failed")
                return@withContext null
            }

            val candidates =
                searchResult.items
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
            if (candidates.isEmpty()) {
                Timber.tag("SpotifyMatching").w("MISS '${track.name}': No SongItem candidates on YouTube")
                return@withContext null
            }

            candidates.forEach { c ->
                val candDurSec = c.duration ?: 0
                val deltaSec = kotlin.math.abs(track.durationMs / 1000 - candDurSec)
                val candArtists = c.artists.joinToString { it.name }
                Timber.tag("SpotifyMatching").d(
                    "CANDIDATE: id=${c.id}, title='${c.title}', artists='${candArtists}', " +
                    "dur=${candDurSec}s (delta=${deltaSec}s)",
                )
            }

            val trackQuery =
                TrackQuery(
                    artist = queryArtist,
                    title = track.name,
                    album = track.album?.name,
                    isrc = track.externalIds?.isrc,
                    durationMs = track.durationMs.toLong(),
                    explicit = track.explicit,
                )

            val decision =
                scorer.pickGeneric(
                    track = trackQuery,
                    candidates = candidates,
                    titleOf = { it.title },
                    artistsOf = { it.artists.map { a -> a.name } },
                    durationMsOf = { (it.duration ?: 0) * 1000L },
                )

            val best =
                decision.accepted ?: run {
                    Timber.tag("SpotifyMatching").w("REJECTED '${track.name}': reason='${decision.reason}'")
                    return@withContext null
                }
            Timber.tag("SpotifyMatching").i(
                "ACCEPTED '${track.name}' -> YT id=${best.id}, title='${best.title}', score=${decision.score}",
            )

            val bestMetadata = best.toMediaMetadata()
            val metadata =
                bestMetadata.copy(
                    thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: best.thumbnail,
                    duration = if (track.durationMs > 0) track.durationMs / 1000 else best.duration ?: -1,
                    explicit = track.explicit || best.explicit,
                    album =
                        track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                            ?: bestMetadata.album,
                    spotifyTrackId = track.id.takeIf(String::isNotBlank),
                    isrc = track.externalIds?.isrc?.takeIf(String::isNotBlank) ?: bestMetadata.isrc,
                )

            Timber.tag("SpotifyMatching").d(
                "PLAYER OUTPUT: id=${metadata.id}, title='${metadata.title}', artists='${metadata.artists.joinToString { it.name }}', " +
                "dur=${metadata.duration}s, isrc=${metadata.isrc}, explicit=${metadata.explicit}",
            )

            mutex.withLock {
                cache[track.id] = metadata
                if (rawSpotifyId.isNotBlank()) {
                    cache[rawSpotifyId] = metadata
                }
            }

            val matchKey = if (rawSpotifyId.isNotBlank()) rawSpotifyId else track.id
            val existingMatch = db?.getSpotifyMatch(matchKey)
            if (existingMatch == null || !existingMatch.isManualOverride) {
                db?.insert(
                    SpotifyMatchEntity(
                        spotifyId = matchKey,
                        youtubeId = metadata.id,
                        title = track.name,
                        artist = track.artists.joinToString(" ") { it.name },
                        matchScore = decision.score,
                        isrc = metadata.isrc,
                    ),
                )
            }

            metadata
        }
}
