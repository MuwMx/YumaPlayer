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
 * Pure, bulletproof scorer for the Spotify-URI resolver.
 * Philosophy: NEVER accept a wrong recording (live/cover/sped-up/karaoke/alt-mix).
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
                .thenByDescending { effectiveTitleSim(track.title, titleOf(it)) },
        ) ?: return CandidateDecision(null, 0.0, "no candidate passed gates")

        val bestDur = abs(track.durationMs!! - durationMsOf(best)) / 1000
        val bestSim = effectiveTitleSim(track.title, titleOf(best))

        val ambiguous = passers.any { other ->
            val otherDur = abs(track.durationMs - durationMsOf(other)) / 1000
            val otherSim = effectiveTitleSim(track.title, titleOf(other))
            other !== best &&
                abs(otherSim - bestSim) <= AMBIGUOUS_TITLE_SIM &&
                abs(otherDur - bestDur) <= AMBIGUOUS_DUR_SEC &&
                !sameRecordingGeneric(
                    titleOf(best), artistsOf(best), durationMsOf(best),
                    titleOf(other), artistsOf(other), durationMsOf(other),
                )
        }

        if (ambiguous && (bestSim < 0.95 || bestDur > 2)) {
            return CandidateDecision(null, bestSim, "ambiguous")
        }

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
        val durDelta = abs(track.durationMs - candDurationMs) / 1000
        if (durDelta > DUR_TOLERANCE_SEC) return false

        val titleSim = effectiveTitleSim(track.title, candTitle)
        if (titleSim < TITLE_SIM_THRESHOLD) return false

        if (!artistOkGeneric(track.artist, candArtists, candTitle, titleSim, durDelta)) return false
        if (versionConflict(track.title, candTitle)) return false
        return true
    }

    private fun effectiveTitleSim(trackTitle: String, candTitle: String): Double {
        val normTrack = matcher.canonicalTitle(trackTitle)
        val normCand = matcher.canonicalTitle(candTitle)
        val direct = matcher.jaroWinklerSimilarity(normTrack, normCand)
        if (direct >= TITLE_SIM_THRESHOLD) return direct

        val delim = Regex("""\s*[-–—/|~～]\s*""")
        val candParts = candTitle.split(delim).map { matcher.canonicalTitle(it) }.filter { it.isNotBlank() }
        val trackParts = trackTitle.split(delim).map { matcher.canonicalTitle(it) }.filter { it.isNotBlank() }

        var maxSim = direct
        for (cp in candParts) {
            val s = matcher.jaroWinklerSimilarity(normTrack, cp)
            if (s > maxSim) maxSim = s
        }
        for (tp in trackParts) {
            val s = matcher.jaroWinklerSimilarity(tp, normCand)
            if (s > maxSim) maxSim = s
            for (cp in candParts) {
                val cross = matcher.jaroWinklerSimilarity(tp, cp)
                if (cross > maxSim) maxSim = cross
            }
        }
        return maxSim
    }

    private fun artistOkGeneric(
        trackArtist: String,
        candArtists: List<String>,
        candTitle: String,
        titleSim: Double,
        durDelta: Long,
    ): Boolean {
        val parts = ArtistMatching.artistParts(trackArtist)
        if (parts.isEmpty()) return false

        val primaryPart = parts.first()
        val primaryMatches = candArtists.any { artistPartMatches(primaryPart, it, titleSim, durDelta) }

        if (primaryMatches) {
            val allCandText = (candArtists + candTitle).joinToString(" ")
            return parts.all { part ->
                candArtists.any { artistPartMatches(part, it, titleSim, durDelta) } ||
                        artistPartMatches(part, allCandText, titleSim, durDelta) ||
                        (titleSim >= TITLE_SIM_THRESHOLD && durDelta <= 2L)
            }
        }

        return parts.any { part -> candArtists.any { artistPartMatches(part, it, titleSim, durDelta) } }
    }

    private fun artistPartMatches(
        trackPart: String,
        candArtist: String,
        titleSim: Double = 0.0,
        durDelta: Long = 999L,
    ): Boolean {
        val normTrack = matcher.canonicalArtist(trackPart)
        val normCand = matcher.canonicalArtist(candArtist)
        if (normTrack.isEmpty() || normCand.isEmpty()) return false

        val jw = matcher.jaroWinklerSimilarity(normTrack, normCand)
        if (jw >= ARTIST_SIM_THRESHOLD) return true

        if (normCand.contains(normTrack) || normTrack.contains(normCand)) return true

        val candTokens = candArtist.lowercase().split(Regex("""\W+""")).filter { it.isNotEmpty() }
        val partTokens = trackPart.lowercase().split(Regex("""\W+""")).filter { it.isNotEmpty() }
        if (ArtistMatching.containsRun(candTokens, partTokens)) return true

        if (titleSim >= TITLE_SIM_THRESHOLD && durDelta <= 2L && isCrossScript(trackPart, candArtist)) {
            return true
        }
        return false
    }

    private fun sameRecordingGeneric(
        titleA: String, artistsA: List<String>, durA: Long,
        titleB: String, artistsB: List<String>, durB: Long,
    ): Boolean {
        if (effectiveTitleSim(titleA, titleB) < TITLE_SIM_THRESHOLD) return false
        if (abs(durA - durB) / 1000 > SAME_RECORDING_DUR_SEC) return false
        val aArtists = artistsA.map { cleanArtist(it) }.filter { it.isNotBlank() }.toSet()
        val bArtists = artistsB.map { cleanArtist(it) }.filter { it.isNotBlank() }.toSet()
        if (aArtists.isEmpty() || bArtists.isEmpty()) return true
        if (aArtists == bArtists || aArtists.any { it in bArtists } || bArtists.any { it in aArtists }) return true
        return aArtists.any { a -> bArtists.any { b -> isCrossScript(a, b) } }
    }

    private fun cleanArtist(artist: String): String {
        val withoutSuffix = artist
            .replace(Regex("""\s*-\s*topic\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*official\b""", RegexOption.IGNORE_CASE), "")
        return matcher.canonicalArtist(withoutSuffix)
    }

    private fun versionConflict(trackTitle: String, candName: String): Boolean {
        val a = trackTitle.lowercase()
        val b = candName.lowercase()
        return VERSION_TOKENS.any { token ->
            containsWord(a, token) != containsWord(b, token)
        }
    }

    private fun containsWord(haystack: String, token: String): Boolean {
        val pattern = """(?<![\p{L}\p{N}])${Regex.escape(token)}(?![\p{L}\p{N}])"""
        return Regex(pattern).containsMatchIn(haystack)
    }

    private fun isCrossScript(a: String, b: String): Boolean = hasCjk(a) != hasCjk(b)

    private fun hasCjk(s: String): Boolean = s.any { ch ->
        val code = ch.code
        (code in 0x2E80..0x9FFF) || (code in 0x3040..0x30FF) || (code in 0xF900..0xFAFF)
    }

    private companion object {
        const val DUR_TOLERANCE_SEC = 5L
        const val TITLE_SIM_THRESHOLD = 0.92
        const val ARTIST_SIM_THRESHOLD = 0.85
        const val AMBIGUOUS_TITLE_SIM = 0.02
        const val AMBIGUOUS_DUR_SEC = 2L
        const val SAME_RECORDING_DUR_SEC = 2L

        val VERSION_TOKENS = listOf(
            "live", "concert", "unplugged", "session", "acoustic", "instrumental",
            "karaoke", "remaster", "remastered", "sped up", "spedup", "slowed",
            "nightcore", "cover", "remix", "rework", "edit", "radio edit",
            "extended", "demo", "mono", "re-recorded", "rerecorded",
            "taylor's version", "commentary", "mtv",
        )
    }
}