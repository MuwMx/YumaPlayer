package moe.rukamori.archivetune.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonUrlDetectorTest {

    private val detector = AddonUrlDetector()

    @Test
    fun normalizeBase_handlesVariousFormats() {
        assertEquals("https://example.com/repo", AddonUrlDetector.normalizeBase("https://example.com/repo/manifest.json"))
        assertEquals("https://example.com/repo", AddonUrlDetector.normalizeBase("https://example.com/repo/INDEX.JSON"))
        assertEquals("https://example.com/repo", AddonUrlDetector.normalizeBase("https://example.com/repo/"))
        assertEquals("https://example.com", AddonUrlDetector.normalizeBase("https://example.com/index.json"))
        assertEquals("https://example.com", AddonUrlDetector.normalizeBase("https://example.com/"))
    }

    @Test
    fun detect_rejectsHtml() {
        val html = "<!DOCTYPE html><html><body>Error</body></html>"
        val result = detector.detect(html, "https://example.com")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AddonFormatException)
        assertEquals("This URL returned HTML or text instead of JSON", result.exceptionOrNull()?.message)
    }

    @Test
    fun detect_rejectsBitchordAuth() {
        val manifest = """
            {
                "id": "restricted.addon",
                "name": "Restricted",
                "version": "1.0",
                "bitchordAuth": true,
                "resources": ["search", "stream"]
            }
        """.trimIndent()
        val result = detector.detect(manifest, "https://example.com/manifest.json")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("bitchordAuth") == true)
    }

    @Test
    fun detect_rejectsMissingSearchResource() {
        val manifest = """
            {
                "id": "no.search",
                "name": "No Search",
                "version": "1.0",
                "resources": ["stream"]
            }
        """.trimIndent()
        val result = detector.detect(manifest, "https://example.com/manifest.json")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("does not support search") == true)
    }

    @Test
    fun detect_parsesHttpManifestWithQualityOptions() {
        val manifest = """
            {
                "id": "my.http.addon",
                "name": "My HTTP Addon",
                "version": "1.2.0",
                "resources": ["search", "stream"],
                "settings": [
                    {
                        "key": "token",
                        "default": "abc123"
                    },
                    {
                        "key": "quality",
                        "options": [
                            { "value": "flac", "label": "FLAC Lossless" },
                            { "value": "320", "label": "320 kbps MP3" },
                            { "value": "128", "label": "128 kbps MP3" }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val result = detector.detect(manifest, "https://example.com/addon/manifest.json")
        assertTrue(result.isSuccess)
        val detected = result.getOrThrow()
        assertTrue(detected is DetectedAddonSource.HttpAddon)
        val http = detected as DetectedAddonSource.HttpAddon
        assertEquals("https://example.com/addon", http.baseUrl)
        assertEquals("my.http.addon", http.descriptor.id)
        assertEquals("My HTTP Addon", http.descriptor.name)
        assertEquals(AddonFormat.BITCHORD_HTTP, http.descriptor.format)
        assertEquals(3, http.descriptor.qualityOptions.size)
        assertEquals("flac", http.descriptor.qualityOptions[0].value)
        assertEquals("abc123", http.descriptor.defaultSettings["token"])
    }

    @Test
    fun detect_parsesCategoryIndex() {
        val index = """
            {
                "category:lossless": [
                    {
                        "id": "js.mod1",
                        "name": "JS Mod 1",
                        "download": "mod1.js"
                    }
                ]
            }
        """.trimIndent()
        val result = detector.detect(index, "https://example.com/index.json")
        assertTrue(result.isSuccess)
        val detected = result.getOrThrow()
        assertTrue(detected is DetectedAddonSource.JsModuleIndex)
        val jsIndex = detected as DetectedAddonSource.JsModuleIndex
        assertEquals(1, jsIndex.descriptors.size)
        assertEquals("js.mod1", jsIndex.descriptors[0].id)
    }
}
