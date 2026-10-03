package moe.rukamori.archivetune.spotify.matching

import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import kotlin.math.abs

data class TrackQuery(
    val artist: String,
    val title: String,
    val album: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val explicit: Boolean? = null,
    val spotifyUri: String? = null,
    val trackId: Long? = null,
)

/**
 * Pure, bulletproof scorer for the Spotify-URI resolver. Given our local
 * [TrackQuery] and a list of Spotify `/search` candidates, it returns the ONE
 * candidate that is *safe* to accept, or null.
 *
 * Correctness philosophy: NEVER accept a wrong recording (live / remaster /
 * cover / sped-up / karaoke / alt-mix). A missed match is fine; a wrong match
 * is unacceptable. When unsure, return null.
 */
class SpotifySearchScorer(private val matcher: TrackMatcher) {

    data class Decision(val accepted: SpotifyTrack?, val reason: String)

    data class CandidateDecision<T>(
        val accepted: T?,
        val score: Double,
        val reason: String,
    )

    fun <T> pickGeneric(
        track: TrackQuery,
        candidates: List<T>,
        titleOf: (T) -> String,
        artistsOf: (T) -> List<String>,
        durationMsOf: (T) -> Long,
    ): CandidateDecision<T> {
        val passers = candidates.filter { cand ->
            passesGeneric(track, titleOf(cand), artistsOf(cand), durationMsOf(cand))
        }
        val best = passers.minWithOrNull(
            compareBy<T> { abs(track.durationMs!! - durationMsOf(it)) / 1000 }
                .thenByDescending {
                    matcher.jaroWinklerSimilarity(
                        matcher.canonicalTitle(track.title),
                        matcher.canonicalTitle(titleOf(it)),
                    )
                },
        ) ?: return CandidateDecision(null, 0.0, "no candidate passed gates")

        val bestDur = abs(track.durationMs!! - durationMsOf(best)) / 1000
        val bestSim = matcher.jaroWinklerSimilarity(
            matcher.canonicalTitle(track.title),
            matcher.canonicalTitle(titleOf(best)),
        )

        val ambiguous = passers.any { other ->
            other !== best &&
                abs(
                    matcher.jaroWinklerSimilarity(
                        matcher.canonicalTitle(track.title),
                        matcher.canonicalTitle(titleOf(other)),
                    ) - bestSim,
                ) <= AMBIGUOUS_TITLE_SIM &&
                abs((abs(track.durationMs - durationMsOf(other)) / 1000) - bestDur) <= AMBIGUOUS_DUR_SEC &&
                !sameRecordingGeneric(
                    titleOf(best), artistsOf(best), durationMsOf(best),
                    titleOf(other), artistsOf(other), durationMsOf(other),
                )
        }
        if (ambiguous) return CandidateDecision(null, bestSim, "ambiguous")

        return CandidateDecision(best, bestSim, "accepted")
    }

    fun pick(track: TrackQuery, candidates: List<SpotifyTrack>): Decision {
        val res = pickGeneric(
            track = track,
            candidates = candidates,
            titleOf = { it.name },
            artistsOf = { it.artists.map { a -> a.name } },
            durationMsOf = { it.durationMs.toLong() },
        )
        return Decision(res.accepted, res.reason)
    }

    fun passesGeneric(
        track: TrackQuery,
        candTitle: String,
        candArtists: List<String>,
        candDurationMs: Long,
    ): Boolean {
        val durKnown = track.durationMs != null && track.durationMs > 0 && candDurationMs > 0
        if (!durKnown) return false
        if (abs(track.durationMs - candDurationMs) / 1000 > DUR_TOLERANCE_SEC) return false
        if (matcher.jaroWinklerSimilarity(matcher.canonicalTitle(track.title), matcher.canonicalTitle(candTitle)) < TITLE_SIM_THRESHOLD) return false
        if (!artistOkGeneric(track.artist, candArtists)) return false
        if (versionConflict(track.title, candTitle)) return false
        return true
    }

    private fun artistOkGeneric(trackArtist: String, candArtists: List<String>): Boolean {
        val parts = ArtistMatching.artistParts(trackArtist)
        if (parts.isEmpty()) return false
        return parts.all { part -> candArtists.any { artistPartMatches(part, it) } }
    }

    private fun sameRecordingGeneric(
        titleA: String, artistsA: List<String>, durA: Long,
        titleB: String, artistsB: List<String>, durB: Long,
    ): Boolean {
        if (matcher.canonicalTitle(titleA) != matcher.canonicalTitle(titleB)) return false
        if (abs(durA - durB) / 1000 > SAME_RECORDING_DUR_SEC) return false
        val aArtists = artistsA.map { matcher.canonicalArtist(it) }.toSet()
        val bArtists = artistsB.map { matcher.canonicalArtist(it) }.toSet()
        return aArtists.isNotEmpty() && aArtists == bArtists
    }

    private fun artistPartMatches(trackPart: String, candArtist: String): Boolean {
        val jw = matcher.jaroWinklerSimilarity(
            matcher.canonicalArtist(trackPart),
            matcher.canonicalArtist(candArtist),
        )
        if (jw >= ARTIST_SIM_THRESHOLD) return true
        // Token-run containment: the candidate artist's tokens contain the
        // track-part's tokens as a contiguous run (handles "feat."-style
        // sub-credits and minor word-order/punctuation drift).
        val candTokens = candArtist.lowercase().split(Regex("""\W+""")).filter { it.isNotEmpty() }
        val partTokens = trackPart.lowercase().split(Regex("""\W+""")).filter { it.isNotEmpty() }
        return ArtistMatching.containsRun(candTokens, partTokens)
    }

    /**
     * Version veto over RAW lowercased titles (never canonical). For each
     * disqualifying token, its presence as a whole-word / contiguous
     * word-sequence must be the SAME in both titles. Present in one but not
     * the other → conflict. (Symmetric: both or neither is fine.)
     */
    private fun versionConflict(trackTitle: String, candName: String): Boolean {
        val a = trackTitle.lowercase()
        val b = candName.lowercase()
        return VERSION_TOKENS.any { token ->
            containsWord(a, token) != containsWord(b, token)
        }
    }

    /** True when [token] appears as a whole word / contiguous word-sequence in [haystack]. */
    private fun containsWord(haystack: String, token: String): Boolean {
        val pattern = """(?<![\p{L}\p{N}])${Regex.escape(token)}(?![\p{L}\p{N}])"""
        return Regex(pattern).containsMatchIn(haystack)
    }

    private fun titleSim(track: TrackQuery, cand: SpotifyTrack): Double =
        matcher.jaroWinklerSimilarity(
            matcher.canonicalTitle(track.title),
            matcher.canonicalTitle(cand.name),
        )

    private fun durDeltaSec(track: TrackQuery, cand: SpotifyTrack): Long =
        abs(track.durationMs!! - cand.durationMs) / 1000

    private companion object {
        /**
         * 5s: Constraint C7 strict threshold for lossless matching.
         * Radio versions and album tracks in Spotify and Qobuz often differ by 2-4 seconds
         * due to pauses at the end of the file. If the difference is > 5s, the track is rejected.
         */
        const val DUR_TOLERANCE_SEC = 5L
        const val TITLE_SIM_THRESHOLD = 0.92
        const val ARTIST_SIM_THRESHOLD = 0.85

        /** Two passers within these deltas of the best are treated as ambiguous. */
        const val AMBIGUOUS_TITLE_SIM = 0.02
        const val AMBIGUOUS_DUR_SEC = 2L

        /** Duration window within which two same-title/same-artist candidates
         * count as one recording (edition-to-edition drift is sub-second). */
        const val SAME_RECORDING_DUR_SEC = 2L

        /**
         * Disqualifying version markers. If any of these is present (as a whole
         * word / contiguous word-sequence) in one title but not the other, the
         * candidate is a different recording and is vetoed. Multi-word entries
         * ("sped up", "radio edit", "taylor's version") match contiguously.
         */
        val VERSION_TOKENS = listOf(
            "live", "concert", "unplugged", "session", "acoustic", "instrumental",
            "karaoke", "remaster", "remastered", "sped up", "spedup", "slowed",
            "nightcore", "cover", "remix", "rework", "edit", "radio edit",
            "extended", "demo", "mono", "re-recorded", "rerecorded",
            "taylor's version", "commentary", "mtv",
        )
    }
}
