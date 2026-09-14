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
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber

object SpotifyPlaybackResolver {
    private const val MIN_MATCH_THRESHOLD = 0.35
    private const val CACHE_MAX_SIZE = 512

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<String, MediaMetadata>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean = size > CACHE_MAX_SIZE
        }

    suspend fun resolveToMediaItem(track: SpotifyTrack): MediaItem? = resolveToMetadata(track)?.toMediaItem()

    suspend fun resolveToMetadata(track: SpotifyTrack): MediaMetadata? =
        withContext(Dispatchers.IO) {
            val artistNames = track.artists.joinToString(", ") { it.name }
            Timber.tag("SpotifyPipeline").d(
                "Resolving track: id=${track.id}, title='${track.name}', artist='$artistNames'"
            )

            mutex.withLock {
                cache[track.id]?.let { cached ->
                    Timber.tag("SpotifyPipeline").d(
                        "Cache HIT for track id=${track.id}, videoId=${cached.id}"
                    )
                    return@withContext cached
                }
            }
            Timber.tag("SpotifyPipeline").d("Cache MISS for track id=${track.id}")

            val query = SpotifyMapper.buildSearchQuery(track)
            Timber.tag("SpotifyPipeline").d("Searching YouTube for query: '$query'")

            val searchResult =
                YouTube
                    .search(
                        query = query,
                        filter = YouTube.SearchFilter.FILTER_SONG,
                    ).getOrNull()
            if (searchResult == null) {
                Timber.tag("SpotifyPipeline").w("YouTube search returned null for query: '$query'")
                return@withContext null
            }

            val candidates =
                searchResult.items
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
            if (candidates.isEmpty()) {
                Timber.tag("SpotifyPipeline").w("No song candidates found for query: '$query'")
                return@withContext null
            }

            val precomputed =
                mutex.withLock {
                    SpotifyMapper.precompute(
                        title = track.name,
                        artist = track.artists.joinToString(" ") { it.name },
                        durationMs = track.durationMs,
                    )
                }

            val scoredCandidates =
                mutex.withLock {
                    candidates
                        .map { candidate ->
                            candidate to
                                SpotifyMapper.matchScorePrecomputed(
                                    precomputed = precomputed,
                                    candidateTitle = candidate.title,
                                    candidateArtist = candidate.artists.joinToString(" ") { it.name },
                                    candidateDurationSec = candidate.duration,
                                )
                        }
                }
            val bestCandidatePair = scoredCandidates.maxByOrNull { it.second }
            if (bestCandidatePair == null) {
                Timber.tag("SpotifyPipeline").w("No scored candidate for track id=${track.id}")
                return@withContext null
            }

            val (best, score) = bestCandidatePair
            val bestArtistNames = best.artists.joinToString(", ") { it.name }
            Timber.tag("SpotifyPipeline").d(
                "Best candidate: id=${best.id}, title='${best.title}', artist='$bestArtistNames', score=$score (min threshold=$MIN_MATCH_THRESHOLD)"
            )

            if (score < MIN_MATCH_THRESHOLD) {
                Timber.tag("SpotifyPipeline").w(
                    "Candidate score $score below threshold $MIN_MATCH_THRESHOLD for track id=${track.id}"
                )
                return@withContext null
            }

            val bestMetadata = best.toMediaMetadata()
            val metadata =
                bestMetadata.copy(
                    thumbnailUrl = SpotifyMapper.getTrackThumbnail(track) ?: best.thumbnail,
                    duration = if (track.durationMs > 0) track.durationMs / 1000 else best.duration ?: -1,
                    explicit = track.explicit || best.explicit,
                    album =
                        track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                            ?: bestMetadata.album,
                    spotifyTrackId = track.id.takeIf(String::isNotBlank),
                    isrc = track.externalIds?.isrc?.takeIf { it.isNotBlank() },
                )

            mutex.withLock {
                cache[track.id] = metadata
            }
            Timber.tag("SpotifyPipeline").d("Resolved track id=${track.id} to videoId=${metadata.id}")
            metadata
        }
}
