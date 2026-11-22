package moe.rukamori.archivetune.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitChordIndexLoaderTest {

    private val sampleJson = """
    {
        "category:music": [
            {
                "id": "module.one",
                "name": "Module One",
                "author": "Alice",
                "version": "1.0.0",
                "download": "module-one.js",
                "tags": ["LOSSLESS", "FLAC"]
            }
        ],
        "category:extra": [
            {
                "id": "module.two",
                "name": "Module Two",
                "author": "Bob",
                "download": "https://example.com/module-two.js",
                "labels": ["HI-RES"]
            }
        ],
        "category:testing": [
            {
                "id": "module.test",
                "name": "Testing Module",
                "download": "test.js"
            }
        ]
    }
    """.trimIndent()

    @Test
    fun parseIndex_extractsValidCategories_andIgnoresExcluded() {
        val loader = BitChordIndexLoader()
        val result = loader.parseIndex(sampleJson, "https://modules.example.com/index.json")

        assertEquals(2, result.size)
        val mod1 = result.find { it.id == "module.one" }
        assertNotNull(mod1)
        assertEquals("Module One", mod1?.name)
        assertEquals("https://modules.example.com/module-one.js", mod1?.downloadUrl)
        assertTrue(mod1?.tags?.contains("LOSSLESS") == true)

        val mod2 = result.find { it.id == "module.two" }
        assertNotNull(mod2)
        assertEquals("https://example.com/module-two.js", mod2?.downloadUrl)

        val testMod = result.find { it.id == "module.test" }
        assertEquals(null, testMod)
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateUrl_rejectsInvalidScheme() {
        BitChordIndexLoader.validateUrl("javascript:alert(1)")
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateUrl_rejectsFileScheme() {
        BitChordIndexLoader.validateUrl("file:///etc/passwd")
    }

    @Test
    fun validateUrl_acceptsValidHttpAndHttps() {
        BitChordIndexLoader.validateUrl("https://example.com/index.json")
        BitChordIndexLoader.validateUrl("http://localhost:8080/index.json")
    }
}
