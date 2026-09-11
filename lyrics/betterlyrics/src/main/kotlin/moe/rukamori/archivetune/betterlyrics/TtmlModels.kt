/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

enum class TtmlTimingMode {
    WORD,
    LINE,
}

enum class TtmlAgentType {
    PERSON,
    CHARACTER,
    GROUP,
    ORGANIZATION,
    OTHER,
}

data class TtmlTimeRange(
    val startMs: Long,
    val endMs: Long,
)

data class TtmlSegment(
    val text: String,
    val timing: TtmlTimeRange?,
)

data class TtmlTrack(
    val text: String,
    val language: String?,
    val segments: List<TtmlSegment>,
)

data class TtmlAgent(
    val id: String,
    val name: String?,
    val type: TtmlAgentType,
    val order: Int,
)

data class TtmlLine(
    val key: String?,
    val sourceOrder: Int,
    val timing: TtmlTimeRange,
    val text: String,
    val main: TtmlTrack,
    val backgrounds: List<TtmlTrack>,
    val translations: List<TtmlTrack>,
    val romanizations: List<TtmlTrack>,
    val agent: TtmlAgent?,
)

data class TtmlDocument(
    val language: String?,
    val timingMode: TtmlTimingMode,
    val agents: List<TtmlAgent>,
    val lines: List<TtmlLine>,
)

enum class TtmlParseFailure {
    MALFORMED_XML,
    UNSAFE_XML,
    MISSING_LINES,
    INVALID_TIMING,
}

class TtmlParseException(
    val failure: TtmlParseFailure,
    cause: Throwable? = null,
) : IllegalArgumentException(failure.name, cause)
