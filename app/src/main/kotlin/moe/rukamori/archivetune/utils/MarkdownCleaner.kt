package moe.rukamori.archivetune.utils

object MarkdownCleaner {
    /**
     * Очищает GitHub Markdown от спецсимволов, оставляя только чистый текст и переносы строк.
     */
    fun clean(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(Regex("(?m)^#{1,6}\\s*"), "")      // Убираем # Заголовки
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")  // **Bold** -> Bold
            .replace(Regex("\\*(.*?)\\*"), "$1")        // *Italic* -> Italic
            .replace(Regex("_(.*?)_"), "$1")            // _Italic_ -> Italic
            .replace(Regex("`(.*?)`"), "$1")            // `Code` -> Code
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // [Link](url) -> Link
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "• ") // Списки -> буллиты
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")           // Убираем лишние пустые строки
            .trim()
    }
}