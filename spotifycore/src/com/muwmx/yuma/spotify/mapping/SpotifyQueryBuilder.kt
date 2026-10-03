/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.mapping

import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyQueryBuilder {
    private val FEAT_PATTERN = Regex("\\(feat\\..*?\\)", RegexOption.IGNORE_CASE)
    private val FT_PATTERN = Regex("\\(ft\\..*?\\)", RegexOption.IGNORE_CASE)
    private val BRACKET_PATTERN = Regex("\\[.*?]")
    private val REMASTER_PATTERN = Regex("\\(.*?remaster.*?\\)", RegexOption.IGNORE_CASE)
    private val REMIX_PATTERN = Regex("\\(.*?remix.*?\\)", RegexOption.IGNORE_CASE)
    private val LIVE_PATTERN = Regex("\\(.*?live.*?\\)", RegexOption.IGNORE_CASE)
    private val VERSION_PATTERN = Regex("\\(.*?version.*?\\)", RegexOption.IGNORE_CASE)
    private val NON_ALNUM_PATTERN = Regex("[^\\p{L}\\p{N}\\s]")
    private val MULTI_SPACE_PATTERN = Regex("\\s+")

    private val VERSION_TAG_REGEX =
        Regex(
            """\(([^)]*(?:remix|live|acoustic|edit|version|sped up|slowed|instrumental|session)[^)]*)\)""",
            RegexOption.IGNORE_CASE,
        )

    fun cleanTrackTitle(title: String): String =
        title
            .replace(FEAT_PATTERN, "")
            .replace(FT_PATTERN, "")
            .replace(BRACKET_PATTERN, "")
            .replace(REMASTER_PATTERN, "")
            .replace(REMIX_PATTERN, "")
            .replace(LIVE_PATTERN, "")
            .replace(VERSION_PATTERN, "")
            .replace(NON_ALNUM_PATTERN, "")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()

    fun isValidDuration(spotifyDurationMs: Int, candidateDurationSec: Int?): Boolean {
        if (candidateDurationSec == null || spotifyDurationMs <= 0) return false
        val diff = kotlin.math.abs(spotifyDurationMs / 1000 - candidateDurationSec)
        return diff <= 5
    }

    fun buildSearchQuery(track: SpotifyTrack): String {
        val artists = track.artists.map { it.name }.filter { it.isNotBlank() }
        val artistStr =
            when {
                artists.isEmpty() -> ""
                artists.size <= 2 -> artists.joinToString(" ")
                else -> "${artists[0]} ${artists[1]}"
            }
        val cleanTitle = cleanTrackTitle(track.name)
        val versionMatch = VERSION_TAG_REGEX.find(track.name)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val titleWithVersion =
            if (versionMatch.isNotBlank() && !cleanTitle.contains(versionMatch, ignoreCase = true)) {
                "$cleanTitle $versionMatch"
            } else {
                cleanTitle
            }
        return if (artistStr.isEmpty()) titleWithVersion else "$artistStr $titleWithVersion"
    }
}
