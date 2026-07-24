package moe.rukamori.archivetune.ui.state

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val startChar: Int,
    val endChar: Int
)
