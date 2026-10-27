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

    /**
     * Looser alternative to [pickGeneric] for candidates the strict gates reject,
     * such as official uploads whose title carries the artist or whose duration is missing.
     * Candidates are accepted on token overlap with the Spotify title and artist, but only
     * when the Spotify artist appears on the candidate and it is not a different version
     * (remix, sped up, live and so on) of the song.
     */
    fun <T> pickByTokenOverlap(
        track: TrackQuery,
        candidates: List<T>,
        titleOf: (T) -> String,
        artistsOf: (T) -> List<String>,
        durationMsOf: (T) -> Long,
    ): CandidateDecision<T>? {
        val wantsVersion = VERSION_UPLOAD_WORDS.containsMatchIn(track.title)
        val trackDurationSec = (track.durationMs ?: 0L) / 1000.0
        val trackArtists = listOf(track.artist)
        var best: T? = null
        var bestScore = 0.0
        var bestTitleSim = 0.0

        for (candidate in candidates) {
            val candidateTitle = titleOf(candidate)
            if (!wantsVersion && VERSION_UPLOAD_WORDS.containsMatchIn(candidateTitle)) continue
            if (versionConflict(track.title, candidateTitle)) continue
            // A title-only search returns same-named songs by other artists.
            if (!hasArtistEvidence(trackArtists, candidateTitle, artistsOf(candidate))) continue

            val candidateArtists = artistsOf(candidate).joinToString(" ")
            val textSim =
                matcher.tokenSimilarity(
                    "${track.title} ${track.artist}",
                    "$candidateTitle $candidateArtists",
                )
            // "Artist - Song" uploads: compare the title without the artist as well.
            val titleSim =
                maxOf(
                    matcher.tokenSimilarity(track.title, candidateTitle),
                    matcher.tokenSimilarity(track.title, stripArtists(candidateTitle, track.artist)),
                )
            val candidateDurationSec = durationMsOf(candidate) / 1000.0
            val durationOk =
                trackDurationSec > 0.0 &&
                    candidateDurationSec > 0.0 &&
                    abs(trackDurationSec - candidateDurationSec) <= TOKEN_OVERLAP_DURATION_TOLERANCE_SEC
            val score = textSim + (if (durationOk) 0.6 else 0.0) + titleSim * 0.3
            val accepted = titleSim >= 0.5 && (durationOk || titleSim >= 0.6 || textSim >= 0.55)
            if (accepted && score > bestScore) {
                best = candidate
                bestScore = score
                bestTitleSim = titleSim
            }
        }

        val accepted = best ?: return null
        return CandidateDecision(
            accepted = accepted,
            score = bestScore,
            reason = "token overlap titleSimilarity=$bestTitleSim",
        )
    }

    /**
     * True when at least one of the Spotify artists shows up on the candidate,
     * either as a credited artist or in the title (`Artist - Song` uploads).
     * Tracks without artist information cannot be checked and pass.
     */
    fun hasArtistEvidence(
        trackArtists: List<String>,
        candidateTitle: String,
        candidateArtists: List<String>,
    ): Boolean {
        val parts = trackArtists.flatMap { ArtistMatching.artistParts(it) }.distinct()
        if (parts.isEmpty()) return true
        val compactArtists =
            candidateArtists
                .flatMap { ArtistMatching.artistParts(it) + compact(it) }
                .filter { it.length >= 2 }
        val compactTitle = compact(candidateTitle)
        return parts.any { part ->
            compactTitle.contains(part) ||
                compactArtists.any { artist ->
                    artist == part ||
                        artist.contains(part) ||
                        (artist.length >= 3 && part.contains(artist)) ||
                        matcher.jaroWinklerSimilarity(part, artist) >= ARTIST_SIM_THRESHOLD
                } ||
                candidateArtists.any { isCrossScript(part, it) }
        }
    }

    private fun compact(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun stripArtists(
        candidateTitle: String,
        trackArtist: String,
    ): String =
        trackArtist
            .split(ArtistMatching.ARTIST_PART_SEPARATOR)
            .map(String::trim)
            .filter { it.length >= 2 }
            .fold(candidateTitle) { title, artist ->
                title.replace(Regex(Regex.escape(artist), RegexOption.IGNORE_CASE), " ")
            }.replace(EDGE_SEPARATORS, "")
            .trim()

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

    /**
     * True when one name is written only in CJK and the other only in Latin
     * letters (須田景凪 / Keina Suda), so a direct comparison cannot work. A
     * name that already carries Latin letters ("Gateway ゲートウェイ") does
     * not qualify; it has to match on those letters.
     */
    private fun isCrossScript(a: String, b: String): Boolean =
        (isOnlyCjk(a) && isOnlyLatin(b)) || (isOnlyLatin(a) && isOnlyCjk(b))

    private fun isOnlyCjk(s: String): Boolean = hasCjk(s) && !hasLatin(s)

    private fun isOnlyLatin(s: String): Boolean = hasLatin(s) && !hasCjk(s)

    private fun hasLatin(s: String): Boolean = s.any { it in 'a'..'z' || it in 'A'..'Z' }

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
        const val TOKEN_OVERLAP_DURATION_TOLERANCE_SEC = 3.0

        private val EDGE_SEPARATORS = Regex("""^[\s\-–—|:~]+|[\s\-–—|:~]+$""")

        /** Uploads that are a different version of the song, even when their duration is close. */
        val VERSION_UPLOAD_WORDS =
            Regex(
                """\b(remix|sped[ -]?up|slowed|nightcore|reverb|8d|bass[ -]?boost(ed)?|cover|flip|mashup|instrumental|karaoke|loop|bootleg)\b""",
                RegexOption.IGNORE_CASE,
            )

        val VERSION_TOKENS = listOf(
            "live", "concert", "unplugged", "session", "acoustic", "instrumental",
            "karaoke", "remaster", "remastered", "sped up", "spedup", "slowed",
            "nightcore", "cover", "remix", "rework", "edit", "radio edit",
            "extended", "demo", "mono", "re-recorded", "rerecorded",
            "taylor's version", "commentary", "mtv",
        )
    }
}