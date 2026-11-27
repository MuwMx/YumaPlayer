/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class YumaLyricsWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val isBackground: Boolean = false,
)

@Immutable
data class YumaLyricsLine(
    val timeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val words: List<YumaLyricsWord> = emptyList(),
    val agent: String? = null,
    val isInstrumental: Boolean = false,
    val romanizedText: String? = null,
    val translationText: String? = null,
)

@Immutable
data class YumaLyricsDocument(
    val lines: List<YumaLyricsLine>,
    val songwriters: List<String> = emptyList(),
    val isWordSynced: Boolean = false,
)

enum class YumaLyricsAlignment {
    Start,
    Center,
    End,
    AutoByAgent,
}
