/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

internal val WHITESPACE_REGEX = Regex("\\s+")
internal val UNSAFE_XML_REGEX = Regex("""<!\s*(?:DOCTYPE|ENTITY)\b""", RegexOption.IGNORE_CASE)
private val ROOT_TAG_REGEX = Regex("""<(?:[A-Za-z_][\w.-]*:)?tt\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val NAMESPACE_DECLARATION_REGEX = Regex("""\bxmlns:([A-Za-z_][\w.-]*)\s*=""", RegexOption.IGNORE_CASE)
private val KNOWN_NAMESPACES =
    linkedMapOf(
        "ttm" to "http://www.w3.org/ns/ttml#metadata",
        "tts" to "http://www.w3.org/ns/ttml#styling",
        "ttp" to "http://www.w3.org/ns/ttml#parameter",
        "itunes" to "http://music.apple.com/lyric-ttml-internal",
        "amll" to "http://www.example.com/ns/amll",
        "composer" to "http://composer.boidu.dev/ttml",
    )
internal val SUPPLEMENTARY_ROLES = setOf("x-bg", "x-translation", "x-roman")

internal object TtmlXmlUtils {
    fun parseXml(source: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        try {
            factory.isXIncludeAware = false
        } catch (_: UnsupportedOperationException) {
        }
        factory.setExpandEntityReferences(false)
        factory.setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setAttributeIfSupported(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttributeIfSupported(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")

        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("External XML entities are not supported") }
        return builder.parse(InputSource(StringReader(source)))
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(
        name: String,
        value: Boolean,
    ) {
        try {
            setFeature(name, value)
        } catch (_: ParserConfigurationException) {
        }
    }

    private fun DocumentBuilderFactory.setAttributeIfSupported(
        name: String,
        value: String,
    ) {
        try {
            setAttribute(name, value)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun declareMissingNamespaces(source: String): String {
        val rootMatch = ROOT_TAG_REGEX.find(source) ?: return source
        val rootTag = rootMatch.value
        val declaredPrefixes =
            NAMESPACE_DECLARATION_REGEX
                .findAll(rootTag)
                .map { it.groupValues[1].lowercase() }
                .toSet()
        val missing =
            KNOWN_NAMESPACES.filterKeys { prefix ->
                prefix !in declaredPrefixes && Regex("""\b${Regex.escape(prefix)}:[A-Za-z_][\w.-]*""").containsMatchIn(source)
            }
        if (missing.isEmpty()) return source

        val declarations = missing.entries.joinToString(separator = "") { (prefix, uri) -> " xmlns:$prefix=\"$uri\"" }
        val insertionOffset = rootMatch.range.last + 1 - if (rootTag.endsWith("/>")) 2 else 1
        return source.substring(0, insertionOffset) + declarations + source.substring(insertionOffset)
    }
}

internal fun Element.attribute(localName: String): String? {
    val attributes = attributes ?: return null
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index) ?: continue
        val candidate = attribute.localName ?: attribute.nodeName.substringAfter(':')
        if (candidate.equals(localName, ignoreCase = true)) {
            return attribute.nodeValue?.trim()?.takeIf(String::isNotEmpty)
        }
    }
    return null
}

internal fun Element.inheritedAttribute(localName: String): String? {
    var element: Element? = this
    while (element != null) {
        element.attribute(localName)?.let { return it }
        element = element.parentNode as? Element
    }
    return null
}

internal fun Element.hasLocalName(expected: String): Boolean =
    (localName ?: tagName.substringAfter(':')).equals(expected, ignoreCase = true)

internal fun Element.hasSupplementaryRole(): Boolean =
    attribute("role")?.lowercase() in SUPPLEMENTARY_ROLES

internal fun Element.directText(): String {
    val result = StringBuilder()
    val children = childNodes
    for (index in 0 until children.length) {
        val node = children.item(index)
        if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
            result.append(node.nodeValue.orEmpty())
        }
    }
    return result.toString()
}

internal fun Element.directChildElements(): List<Element> {
    val result = mutableListOf<Element>()
    val children = childNodes
    for (index in 0 until children.length) {
        (children.item(index) as? Element)?.let(result::add)
    }
    return result
}

internal fun Element.descendantElements(): Sequence<Element> = sequence {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index) as? Element ?: continue
        yield(child)
        yieldAll(child.descendantElements())
    }
}

internal fun String.normalizedText(): String = replace(WHITESPACE_REGEX, " ").trim()

internal fun String.normalizedEdgeWhitespace(): String = trim { it.isWhitespace() || it == '\u00A0' }
