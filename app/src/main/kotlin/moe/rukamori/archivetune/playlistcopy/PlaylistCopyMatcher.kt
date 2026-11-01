/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

data class CopyMatchResult(
    val matched: List<CopyTrack>,
    val failed: List<CopyFailure>,
)

sealed interface TrackMatch {
    data class Found(
        val metadata: MediaMetadata,
    ) : TrackMatch

    data object NotFound : TrackMatch

    data object SearchFailed : TrackMatch
}

suspend fun resolveSpotifyTracks(
    tracks: List<SpotifyTrack>,
    resolve: suspend (SpotifyTrack) -> MediaMetadata?,
    concurrency: Int = 4,
    onProgress: (completed: Int) -> Unit = {},
): List<TrackMatch> =
    withContext(Dispatchers.IO) {
        val permits = Semaphore(concurrency.coerceAtLeast(1))
        val completed = AtomicInteger()
        coroutineScope {
            tracks
                .map { track ->
                    async {
                        val match =
                            permits.withPermit {
                                try {
                                    resolve(track)?.let { TrackMatch.Found(it) } ?: TrackMatch.NotFound
                                } catch (e: IOException) {
                                    TrackMatch.SearchFailed
                                }
                            }
                        onProgress(completed.incrementAndGet())
                        match
                    }
                }.awaitAll()
        }
    }

suspend fun matchSpotifyTracks(
    tracks: List<SpotifyTrack>,
    resolve: suspend (SpotifyTrack) -> MediaMetadata?,
    concurrency: Int = 4,
    onProgress: (completed: Int) -> Unit = {},
): CopyMatchResult {
    val matches = resolveSpotifyTracks(tracks, resolve, concurrency, onProgress)
    val matched = ArrayList<CopyTrack>(tracks.size)
    val failed = ArrayList<CopyFailure>()
    tracks.zip(matches).forEach { (track, match) ->
        when (match) {
            is TrackMatch.Found -> matched += CopyTrack(songId = match.metadata.id, metadata = match.metadata)
            TrackMatch.NotFound -> failed += track.toCopyFailure(CopyFailureReason.NO_MATCH)
            TrackMatch.SearchFailed -> failed += track.toCopyFailure(CopyFailureReason.SEARCH_FAILED)
        }
    }
    return CopyMatchResult(matched, failed)
}

private fun SpotifyTrack.toCopyFailure(reason: CopyFailureReason) =
    CopyFailure(title = name, artist = artists.joinToString { it.name }, reason = reason)
