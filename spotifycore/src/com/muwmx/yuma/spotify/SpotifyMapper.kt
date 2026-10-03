/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.mapping.SpotifyEntityMapper
import moe.rukamori.archivetune.spotify.mapping.SpotifyQueryBuilder
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyMapper {
    private const val NORM_CACHE_MAX_SIZE = 256
    private const val EARLY_EXIT_THRESHOLD = 0.95

    private val normalizeCache =
        object : LinkedHashMap<String, String>(NORM_CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > NORM_CACHE_MAX_SIZE
        }

    private val bigramCache =
        object : LinkedHashMap<String, Set<String>>(NORM_CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>?): Boolean = size > NORM_CACHE_MAX_SIZE
        }

    data class PrecomputedTrack(
        val normalizedTitle: String,
        val titleBigrams: Set<String>,
        val normalizedArtist: String,
        val artistBigrams: Set<String>,
        val durationMs: Int,
    )

    fun cleanTrackTitle(title: String): String = SpotifyQueryBuilder.cleanTrackTitle(title)

    fun isValidDuration(spotifyDurationMs: Int, candidateDurationSec: Int?): Boolean =
        SpotifyQueryBuilder.isValidDuration(spotifyDurationMs, candidateDurationSec)

    fun buildSearchQuery(track: SpotifyTrack): String = SpotifyQueryBuilder.buildSearchQuery(track)

    fun getPlaylistThumbnail(playlist: SpotifyPlaylist): String? = SpotifyEntityMapper.getPlaylistThumbnail(playlist)

    fun getTrackThumbnail(track: SpotifyTrack): String? = SpotifyEntityMapper.getTrackThumbnail(track)

    fun getTrackThumbnailMedium(track: SpotifyTrack): String? = SpotifyEntityMapper.getTrackThumbnailMedium(track)

    fun precompute(title: String, artist: String, durationMs: Int): PrecomputedTrack {
        val normTitle = cachedNormalize(title)
        val normArtist = cachedNormalize(artist)
        return PrecomputedTrack(
            normalizedTitle = normTitle,
            titleBigrams = cachedBigrams(normTitle),
            normalizedArtist = normArtist,
            artistBigrams = cachedBigrams(normArtist),
            durationMs = durationMs,
        )
    }

    fun matchScore(
        spotifyTitle: String,
        spotifyArtist: String,
        spotifyDurationMs: Int,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): Double {
        val normSpotifyTitle = cachedNormalize(spotifyTitle)
        val normCandidateTitle = cachedNormalize(candidateTitle)
        val normSpotifyArtist = cachedNormalize(spotifyArtist)
        val normCandidateArtist = cachedNormalize(candidateArtist)

        val titleScore = bigramSimilarity(
            normSpotifyTitle, cachedBigrams(normSpotifyTitle),
            normCandidateTitle, cachedBigrams(normCandidateTitle),
        )
        val artistScore = bigramSimilarity(
            normSpotifyArtist, cachedBigrams(normSpotifyArtist),
            normCandidateArtist, cachedBigrams(normCandidateArtist),
        )

        val durationScore = durationScore(spotifyDurationMs, candidateDurationSec)
        return titleScore * 0.45 + artistScore * 0.35 + durationScore * 0.20
    }

    fun matchScorePrecomputed(
        precomputed: PrecomputedTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): Double {
        val normCandidateTitle = cachedNormalize(candidateTitle)
        val normCandidateArtist = cachedNormalize(candidateArtist)

        val titleScore = bigramSimilarity(
            precomputed.normalizedTitle, precomputed.titleBigrams,
            normCandidateTitle, cachedBigrams(normCandidateTitle),
        )
        val artistScore = bigramSimilarity(
            precomputed.normalizedArtist, precomputed.artistBigrams,
            normCandidateArtist, cachedBigrams(normCandidateArtist),
        )

        val durationScore = durationScore(precomputed.durationMs, candidateDurationSec)
        return titleScore * 0.45 + artistScore * 0.35 + durationScore * 0.20
    }

    fun earlyExitThreshold(): Double = EARLY_EXIT_THRESHOLD

    private fun durationScore(spotifyDurationMs: Int, candidateDurationSec: Int?): Double {
        if (candidateDurationSec == null || spotifyDurationMs <= 0) return 0.5
        val diff = kotlin.math.abs(spotifyDurationMs / 1000 - candidateDurationSec)
        return when {
            diff <= 2 -> 1.0
            diff <= 5 -> 0.8
            diff <= 10 -> 0.5
            diff <= 30 -> 0.2
            else -> 0.0
        }
    }

    private fun cachedNormalize(title: String): String {
        normalizeCache[title]?.let { return it }
        val normalized = normalizeTitle(title)
        normalizeCache[title] = normalized
        return normalized
    }

    private fun cachedBigrams(normalized: String): Set<String> {
        bigramCache[normalized]?.let { return it }
        val bigrams = if (normalized.length < 2) emptySet() else normalized.windowed(2).toSet()
        bigramCache[normalized] = bigrams
        return bigrams
    }

    private fun normalizeTitle(title: String): String = cleanTrackTitle(title).lowercase()

    private fun bigramSimilarity(
        a: String,
        bigramsA: Set<String>,
        b: String,
        bigramsB: Set<String>,
    ): Double {
        if (a == b) return 1.0
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0
        val intersection = bigramsA.count { it in bigramsB }
        return (2.0 * intersection) / (bigramsA.size + bigramsB.size)
    }
}
