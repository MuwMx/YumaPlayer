/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.youlyplus.models

import kotlinx.serialization.Serializable

@Serializable
data class YouLyPlusTtmlResponse(
    val ttml: String? = null,
)

@Serializable
data class YouLyPlusLyricsResponse(
    val type: String? = null,
    val lyrics: List<YouLyPlusLine> = emptyList(),
)

@Serializable
data class YouLyPlusLine(
    val time: Long? = null,
    val duration: Long? = null,
    val text: String? = null,
    val syllabus: List<YouLyPlusSyllable>? = null,
)

@Serializable
data class YouLyPlusSyllable(
    val time: Long? = null,
    val duration: Long? = null,
    val text: String? = null,
    val isBackground: Boolean = false,
)
