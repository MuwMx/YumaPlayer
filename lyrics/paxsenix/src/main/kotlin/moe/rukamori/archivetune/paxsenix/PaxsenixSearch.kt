/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

internal object PaxsenixSearch {
    private const val MINIMUM_MATCH_SCORE = 10
    private val TRACK_ID_KEYS = listOf("id", "trackId", "track_id", "realId")
    private val TRACK_TITLE_KEYS = listOf("name", "title", "trackName", "track_name")
    private val TRACK_ARTIST_KEYS = listOf("artistName", "artist_name")
    private val TRACK_DURATION_KEYS = listOf("durationInMillis", "durationMs", "duration_ms", "duration")

    internal data class TrackCandidate(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
    )

    suspend fun searchTrackId(
        path: String,
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val raw =
            PaxsenixApi.apiBody(path) {
                parameter("q", "$title $artist")
            }
        val payload = PaxsenixApi.json.parseToJsonElement(raw)
        val candidates = buildList { payload.collectTrackCandidates(this) }
        return candidates
            .map { candidate -> candidate to candidate.score(title, artist, durationMs) }
            .maxByOrNull { (_, score) -> score }
            ?.takeIf { (_, score) -> score >= MINIMUM_MATCH_SCORE }
            ?.first
            ?.id
    }

    private fun JsonElement.collectTrackCandidates(destination: MutableList<TrackCandidate>) {
        when (this) {
            is JsonArray -> forEach { it.collectTrackCandidates(destination) }
            is JsonObject -> {
                toTrackCandidate()?.let(destination::add)
                values.forEach { it.collectTrackCandidates(destination) }
            }

            else -> Unit
        }
    }

    private fun JsonObject.toTrackCandidate(): TrackCandidate? {
        val attributes = this["attributes"] as? JsonObject
        val details = attributes ?: this
        val id = firstString(TRACK_ID_KEYS) ?: details.firstString(TRACK_ID_KEYS) ?: return null
        val title = details.firstString(TRACK_TITLE_KEYS) ?: return null
        val artist = details.firstString(TRACK_ARTIST_KEYS) ?: details.artistNames()
        val duration = details.firstLong(TRACK_DURATION_KEYS).toDurationMs()
        return TrackCandidate(id = id, title = title, artist = artist.orEmpty(), durationMs = duration)
    }

    private fun JsonObject.firstString(keys: List<String>): String? =
        keys
            .asSequence()
            .mapNotNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)

    private fun JsonObject.firstLong(keys: List<String>): Long? =
        keys
            .asSequence()
            .mapNotNull { key -> (this[key] as? JsonPrimitive)?.longOrNull }
            .firstOrNull()

    private fun JsonObject.artistNames(): String? {
        val artists = this["artists"] ?: this["artist"] ?: return null
        return when (artists) {
            is JsonPrimitive -> artists.contentOrNull
            is JsonObject -> artists.firstString(listOf("name", "artistName", "title"))
            is JsonArray ->
                artists
                    .mapNotNull { artist ->
                        when (artist) {
                            is JsonPrimitive -> artist.contentOrNull
                            is JsonObject -> artist.firstString(listOf("name", "artistName", "title"))
                            else -> null
                        }
                    }.joinToString(", ")
                    .takeIf(String::isNotEmpty)
        }
    }

    private fun Long?.toDurationMs(): Long =
        when {
            this == null || this <= 0L -> 0L
            this < 10_000L -> this * 1_000L
            else -> this
        }

    private fun TrackCandidate.score(
        requestedTitle: String,
        requestedArtist: String,
        requestedDurationMs: Long,
    ): Int {
        var score = 0
        score += textMatchScore(title, requestedTitle, exactScore = 20, partialScore = 10)
        score += textMatchScore(artist, requestedArtist, exactScore = 15, partialScore = 5)
        if (requestedDurationMs > 0L && durationMs > 0L) {
            val difference = abs(durationMs - requestedDurationMs)
            score +=
                when {
                    difference < 3_000L -> 10
                    difference < 10_000L -> 5
                    else -> 0
                }
        }
        return score
    }

    private fun textMatchScore(
        candidate: String,
        requested: String,
        exactScore: Int,
        partialScore: Int,
    ): Int {
        if (candidate.isBlank() || requested.isBlank()) return 0
        return when {
            candidate.equals(requested, ignoreCase = true) -> exactScore
            candidate.contains(requested, ignoreCase = true) ||
                requested.contains(candidate, ignoreCase = true) -> partialScore
            else -> 0
        }
    }
}
