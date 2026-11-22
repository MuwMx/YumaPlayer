package moe.rukamori.archivetune.addons

object AddonJsPreprocessor {
    fun preprocess(jsCode: String): String {
        val code = jsCode.trim()
        val exportPattern = Regex("""^export\s+const\s+\w+\s*=\s*`""")
        val exportMatch = exportPattern.find(code)
        if (exportMatch != null) {
            val contentStart = exportMatch.range.last + 1
            var i = contentStart
            while (i < code.length) {
                if (code[i] == '\\' && i + 1 < code.length) {
                    i += 2
                    continue
                }
                if (code[i] == '`') {
                    return code.substring(contentStart, i).trim()
                }
                i++
            }
        }

        var result = code
        result = result.replace(Regex("""\bexport\s+default\s+(?=function|class|const|let|var|async)"""), "")
        result = result.replace(Regex("""\bexport\s+(const|let|var|function|class|async)\b"""), "$1")
        result = result.replace(Regex("""\bexport\s*\{[^}]*\}\s*;?"""), "")
        return result
    }
}
