/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.betterlyrics.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TTMLResponse(
    @JsonNames("ttml", "lyrics")
    val ttml: String = "",
    @SerialName("provider")
    val provider: String? = null,
)

@Serializable
data class SearchResponse(
    val results: List<Track>,
)

@Serializable
data class Track(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Double,
    val lyrics: Lyrics? = null,
)

@Serializable
data class Lyrics(
    val lines: List<Line>,
)

@Serializable
data class Line(
    val text: String,
    val startTime: Double,
    val words: List<Word>? = null,
)

@Serializable
data class Word(
    val text: String,
    val startTime: Double,
    val endTime: Double,
)
