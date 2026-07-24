/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.obfuscator

import java.security.MessageDigest

internal const val JAVA_SCRIPT_PLAN_COMPILER_VERSION = 2

internal class JavaScriptPlanCompiler {
    fun compile(
        script: PlayerScript,
        nowMillis: Long,
    ): TransformPlan {
        val signatureFunction = findFunctionName(script.source, signatureCallPatterns)
        val nTransform = findNTransform(script.source)
        val nFunction = nTransform.functionName
        val declarationCache = HashMap<String, String?>()
        val signatureProgram =
            signatureFunction?.let {
                buildProgram(
                    source = script.source,
                    rootName = it,
                    declarationCache = declarationCache,
                )
            }
        val nProgram =
            nFunction?.let {
                buildProgram(
                    source = script.source,
                    rootName = it,
                    declarationCache = declarationCache,
                )
            }
        val signatureTimestamp = findSignatureTimestamp(script.source)

        if (
            signatureProgram == null &&
            nProgram == null &&
            signatureTimestamp == null &&
            nTransform.state != NTransformState.NOT_REQUIRED
        ) {
            throw MoriCipherException("No supported YouTube player capability was discovered")
        }

        return TransformPlan(
            playerId = script.playerId,
            playerUrl = script.url,
            sourceSha256 = script.source.sha256(),
            signatureTimestamp = signatureTimestamp,
            signatureProgram = signatureProgram,
            signatureFunction = signatureFunction,
            nProgram = nProgram,
            nFunction = nFunction,
            createdAtMillis = nowMillis,
            nTransformState = nTransform.state,
            compilerVersion = JAVA_SCRIPT_PLAN_COMPILER_VERSION,
        )
    }

    private fun findFunctionName(
        source: String,
        patterns: List<Regex>,
    ): String? =
        patterns.firstNotNullOfOrNull { regex ->
            regex
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf(IDENTIFIER_PATTERN::matches)
        }

    private fun findNTransform(source: String): NTransformDiscovery {
        findFunctionName(source, nCallPatterns)?.let {
            return NTransformDiscovery(NTransformState.REQUIRED, it)
        }
        for (pattern in nIndexedCallPatterns) {
            val match = pattern.find(source) ?: continue
            val arrayName = match.groupValues.getOrNull(1)?.takeIf { IDENTIFIER_PATTERN.matches(it) } ?: continue
            val index = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            resolveArrayElement(source, arrayName, index)?.let {
                return NTransformDiscovery(NTransformState.REQUIRED, it)
            }
        }
        val state =
            if (nTransformMarkers.any { it.containsMatchIn(source) }) {
                NTransformState.UNKNOWN
            } else {
                NTransformState.NOT_REQUIRED
            }
        return NTransformDiscovery(state, null)
    }

    private fun resolveArrayElement(
        source: String,
        arrayName: String,
        index: Int,
    ): String? {
        val escaped = Regex.escape(arrayName)
        val pattern = Regex("""(?:^|[;,])\s*(?:var|let|const)?\s*$escaped\s*=\s*\[([^\]]*)\]""")
        return pattern
            .find(source)
            ?.groupValues
            ?.getOrNull(1)
            ?.split(",")
            ?.getOrNull(index)
            ?.trim()
            ?.takeIf { IDENTIFIER_PATTERN.matches(it) }
    }

    private fun findSignatureTimestamp(source: String): Int? =
        signatureTimestampPatterns.firstNotNullOfOrNull { regex ->
            regex
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }

    private fun buildProgram(
        source: String,
        rootName: String,
        declarationCache: MutableMap<String, String?>,
    ): String? {
        val declarations = LinkedHashMap<String, String>()
        val namespaceOwners = linkedSetOf<String>()
        val pending = ArrayDeque<String>()
        val discovered = HashSet<String>()

        fun enqueue(name: String) {
            if (
                discovered.size >= MAX_DEPENDENCY_CANDIDATES ||
                name in ignoredIdentifiers ||
                !discovered.add(name)
            ) {
                return
            }
            pending.addLast(name)
        }

        enqueue(rootName)

        while (pending.isNotEmpty() && declarations.size < MAX_DECLARATIONS) {
            val name = pending.removeFirst()
            if (name in namespaceOwners) continue
            val declaration =
                if (declarationCache.containsKey(name)) {
                    declarationCache[name]
                } else {
                    findDeclaration(source, name).also { declarationCache[name] = it }
                } ?: continue
            declarations[name] = declaration
            name
                .substringBefore('.', missingDelimiterValue = "")
                .takeIf(String::isNotEmpty)
                ?.let(namespaceOwners::add)

            dependencyPattern
                .findAll(declaration)
                .map { it.groupValues[1] }
                .filter(IDENTIFIER_PATTERN::matches)
                .forEach(::enqueue)

            qualifiedDependencyPattern
                .findAll(declaration)
                .map { it.groupValues[1] }
                .filterNot { it.substringBefore('.') in ignoredIdentifiers }
                .forEach(::enqueue)

            propertyOwnerPattern
                .findAll(declaration)
                .map { it.groupValues[1] }
                .filter(IDENTIFIER_PATTERN::matches)
                .forEach(::enqueue)
        }

        if (rootName !in declarations) return null

        val namespaceInitializers =
            namespaceOwners.joinToString(separator = "\n") { owner -> "var $owner={};" }
        val baseProgram = declarations.values.reversed().joinToString(separator = "\n")
        val program =
            buildString {
                append("var window=this;var globalThis=this;var self=this;\n")
                if (namespaceInitializers.isNotEmpty()) {
                    append(namespaceInitializers)
                    append('\n')
                }
                append(baseProgram)
            }

        return program.takeIf { it.length <= MAX_PROGRAM_LENGTH }
    }

    private fun findDeclaration(
        source: String,
        name: String,
    ): String? {
        var searchStart = 0
        while (searchStart < source.length) {
            val nameStart = source.indexOf(name, searchStart)
            if (nameStart < 0) return null
            searchStart = nameStart + name.length
            if (!hasIdentifierBoundaries(source, nameStart, name.length)) continue

            findFunctionDeclarationStart(source, nameStart)?.let { declarationStart ->
                val afterName = skipWhitespaceForward(source, nameStart + name.length)
                if (source.getOrNull(afterName) == '(') {
                    val declarationEnd =
                        findBalancedDeclarationEnd(
                            source = source,
                            searchStart = afterName,
                            open = '{',
                            close = '}',
                        )
                    if (declarationEnd != null) {
                        return source.normalizedDeclaration(declarationStart, declarationEnd)
                    }
                }
            }

            val equalsIndex = skipWhitespaceForward(source, nameStart + name.length)
            if (source.getOrNull(equalsIndex) != '=') continue
            if (source.getOrNull(equalsIndex + 1) == '=' || source.getOrNull(equalsIndex + 1) == '>') continue

            val declarationStart = findAssignmentDeclarationStart(source, nameStart, name) ?: continue
            val initializerStart = skipWhitespaceForward(source, equalsIndex + 1)
            val declarationEnd = findInitializerEnd(source, initializerStart) ?: continue
            return source.normalizedDeclaration(declarationStart, declarationEnd)
        }
        return null
    }

    private fun hasIdentifierBoundaries(
        source: String,
        start: Int,
        length: Int,
    ): Boolean {
        val before = source.getOrNull(start - 1)
        val after = source.getOrNull(start + length)
        return before?.isJavaScriptIdentifierPart() != true &&
            after?.isJavaScriptIdentifierPart() != true
    }

    private fun findFunctionDeclarationStart(
        source: String,
        nameStart: Int,
    ): Int? {
        if (nameStart == 0 || !source[nameStart - 1].isWhitespace()) return null
        val keywordEnd = skipWhitespaceBackward(source, nameStart - 1)
        val keywordStart = keywordEnd - FUNCTION_KEYWORD.length + 1
        if (keywordStart < 0 || !source.startsWith(FUNCTION_KEYWORD, keywordStart)) return null
        if (source.getOrNull(keywordStart - 1)?.isJavaScriptIdentifierPart() == true) return null
        return keywordStart
    }

    private fun findAssignmentDeclarationStart(
        source: String,
        nameStart: Int,
        name: String,
    ): Int? {
        var declarationStart = nameStart
        if ('.' !in name && nameStart > 0 && source[nameStart - 1].isWhitespace()) {
            val keywordEnd = skipWhitespaceBackward(source, nameStart - 1)
            declarationKeywords
                .firstOrNull { keyword ->
                    val keywordStart = keywordEnd - keyword.length + 1
                    keywordStart >= 0 &&
                        source.startsWith(keyword, keywordStart) &&
                        source.getOrNull(keywordStart - 1)?.isJavaScriptIdentifierPart() != true
                }?.let { keyword ->
                    declarationStart = keywordEnd - keyword.length + 1
                }
        }

        val delimiterIndex = skipWhitespaceBackward(source, declarationStart - 1)
        return if (
            delimiterIndex < 0 ||
            source[delimiterIndex] == ';' ||
            source[delimiterIndex] == ','
        ) {
            declarationStart
        } else {
            null
        }
    }

    private fun skipWhitespaceForward(
        source: String,
        start: Int,
    ): Int {
        var index = start
        while (index < source.length && source[index].isWhitespace()) index++
        return index
    }

    private fun skipWhitespaceBackward(
        source: String,
        start: Int,
    ): Int {
        var index = start
        while (index >= 0 && source[index].isWhitespace()) index--
        return index
    }

    private fun Char.isJavaScriptIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'

    private fun String.normalizedDeclaration(
        start: Int,
        end: Int,
    ): String {
        val declaration =
            substring(start, (end + 1).coerceAtMost(length))
                .trimStart(',', ';')
        return if (declaration.endsWith(';')) declaration else "$declaration;"
    }

    private fun findInitializerEnd(
        source: String,
        start: Int,
    ): Int? {
        var parentheses = 0
        var brackets = 0
        var braces = 0
        var quote: Char? = null
        var escaped = false
        var index = start
        while (index < source.length) {
            val current = source[index]
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    current == '\\' -> escaped = true
                    current == quote -> quote = null
                }
                index++
                continue
            }
            when (current) {
                '\'', '"', '`' -> {
                    quote = current
                }

                '(' -> {
                    parentheses++
                }

                ')' -> {
                    parentheses--
                }

                '[' -> {
                    brackets++
                }

                ']' -> {
                    brackets--
                }

                '{' -> {
                    braces++
                }

                '}' -> {
                    braces--
                }

                ',', ';' -> {
                    if (parentheses == 0 && brackets == 0 && braces == 0) return index - 1
                }
            }
            index++
        }
        return null
    }

    private fun findBalancedDeclarationEnd(
        source: String,
        searchStart: Int,
        open: Char,
        close: Char,
    ): Int? {
        val opening = source.indexOf(open, searchStart)
        if (opening < 0) return null
        var depth = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        var index = opening

        while (index < source.length) {
            val current = source[index]
            val next = source.getOrNull(index + 1)
            when {
                lineComment -> {
                    if (current == '\n') lineComment = false
                }

                blockComment -> {
                    if (current == '*' && next == '/') {
                        blockComment = false
                        index++
                    }
                }

                quote != null -> {
                    when {
                        escaped -> escaped = false
                        current == '\\' -> escaped = true
                        current == quote -> quote = null
                    }
                }

                current == '/' && next == '/' -> {
                    lineComment = true
                    index++
                }

                current == '/' && next == '*' -> {
                    blockComment = true
                    index++
                }

                current == '\'' || current == '"' || current == '`' -> {
                    quote = current
                }

                current == open -> {
                    depth++
                }

                current == close -> {
                    depth--
                    if (depth == 0) {
                        val semicolon = source.indexOf(';', index).takeIf { it in index..(index + 2) }
                        return semicolon ?: index
                    }
                }
            }
            index++
        }
        return null
    }

    private companion object {
        const val MAX_DECLARATIONS = 128
        const val MAX_DEPENDENCY_CANDIDATES = 256
        const val MAX_PROGRAM_LENGTH = 1_500_000
        const val FUNCTION_KEYWORD = "function"
        val IDENTIFIER_PATTERN = Regex("""^[A-Za-z_$][\w$]*$""")
        val declarationKeywords = arrayOf("var", "let", "const")

        val dependencyPattern = Regex("""(?<!\.|\])\b([A-Za-z_$][\w$]*)\s*\(""")
        val qualifiedDependencyPattern =
            Regex("""\b([A-Za-z_$][\w$]*\.[A-Za-z_$][\w$]*)\b""")
        val propertyOwnerPattern = Regex("""\b([A-Za-z_$][\w$]*)\s*(?:\.|\[)""")

        val signatureCallPatterns =
            listOf(
                Regex("""(?:signature|sig)\s*[,=:]\s*([A-Za-z_$][\w$]*)\("""),
                Regex("""\.set\(\s*["'](?:signature|sig)["']\s*,\s*([A-Za-z_$][\w$]*)\("""),
                Regex("""["']signature["']\s*,\s*([A-Za-z_$][\w$]*)\("""),
                Regex("""\bc\s*&&\s*\(\s*c\s*=\s*([A-Za-z_$][\w$]*)\(decodeURIComponent"""),
                Regex("""c\s*&&\s*\(\s*c\s*=\s*decodeURIComponent\s*\([^)]*\)\s*,\s*c\s*=\s*([A-Za-z_$][\w$]*)\s*\("""),
                Regex("""\.set\(\s*["']alr["'][^;]*;\s*c\s*&&\s*\(\s*c\s*=\s*([A-Za-z_$][\w$]*)\("""),
                Regex(
                    """(?:\b(?:a|b|c|sig|signature)\s*=\s*|decodeURIComponent\([^)]*\)\s*=\s*)([A-Za-z_$][\w$]*)\(decodeURIComponent\(""",
                ),
            )

        val nCallPatterns =
            listOf(
                Regex(
                    """\.get\(\s*["']n["']\s*\)\s*\)\s*&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\(\s*[A-Za-z_$][\w$]*\s*\)""",
                ),
                Regex("""\[\s*["']n["']\s*]\s*\)\s*&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\(\s*[A-Za-z_$][\w$]*\s*\)"""),
                Regex(
                    """String\.fromCharCode\(\s*110\s*\)[\s\S]{0,256}?&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\(\s*[A-Za-z_$][\w$]*\s*\)""",
                ),
                Regex(
                    """["']nn["']\s*\[\s*\+[A-Za-z_$][\w$]*\.[A-Za-z_$][\w$]*\s*\][\s\S]{0,512}?&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\(\s*[A-Za-z_$][\w$]*\s*\)""",
                ),
                Regex("""\.set\(\s*["']n["']\s*,\s*([A-Za-z_$][\w$]*)\s*\("""),
            )

        val nIndexedCallPatterns =
            listOf(
                Regex("""\.get\(\s*["']n["']\s*\)\s*\)\s*&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\[\s*(\d+)\s*]\s*\("""),
                Regex("""\[\s*["']n["']\s*]\s*\)\s*&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\[\s*(\d+)\s*]\s*\("""),
                Regex(
                    """String\.fromCharCode\(\s*110\s*\)[\s\S]{0,256}?&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\[\s*(\d+)\s*]\s*\(""",
                ),
                Regex(
                    """["']nn["']\s*\[\s*\+[A-Za-z_$][\w$]*\.[A-Za-z_$][\w$]*\s*\][\s\S]{0,512}?&&\s*\(\s*[A-Za-z_$][\w$]*\s*=\s*([A-Za-z_$][\w$]*)\s*\[\s*(\d+)\s*]\s*\(""",
                ),
                Regex("""\.set\(\s*["']n["']\s*,\s*([A-Za-z_$][\w$]*)\s*\[\s*(\d+)\s*]\s*\("""),
            )

        val nTransformMarkers =
            listOf(
                Regex("""["']nn["']\s*\[\s*\+"""),
                Regex("""String\.fromCharCode\(\s*110\s*\)"""),
                Regex("""\.get\(\s*["']n["']\s*\)\s*\)\s*&&"""),
                Regex("""\[\s*["']n["']\s*]\s*\)\s*&&"""),
                Regex("""\.set\(\s*["']n["']\s*,"""),
            )

        val signatureTimestampPatterns =
            listOf(
                Regex("""signatureTimestamp["']?\s*[:=]\s*(\d{4,8})"""),
                Regex("""["']STS["']\s*:\s*(\d{4,8})"""),
                Regex("""\bsts\s*[:=]\s*(\d{4,8})"""),
            )

        val ignoredIdentifiers =
            setOf(
                "Array",
                "BigInt",
                "Boolean",
                "Date",
                "Error",
                "Function",
                "Intl",
                "JSON",
                "Map",
                "Math",
                "Number",
                "Object",
                "Promise",
                "RegExp",
                "Set",
                "String",
                "Symbol",
                "Uint8Array",
                "WeakMap",
                "WeakSet",
                "clearInterval",
                "clearTimeout",
                "decodeURI",
                "decodeURIComponent",
                "encodeURI",
                "encodeURIComponent",
                "isFinite",
                "isNaN",
                "parseFloat",
                "parseInt",
                "queueMicrotask",
                "setInterval",
                "setTimeout",
                "return",
                "if",
                "for",
                "while",
                "switch",
                "catch",
                "function",
                "var",
                "let",
                "const",
                "true",
                "false",
                "null",
                "undefined",
                "NaN",
                "console",
                "window",
                "document",
                "this",
                "void",
            )
    }

    private data class NTransformDiscovery(
        val state: NTransformState,
        val functionName: String?,
    )
}

internal fun String.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
