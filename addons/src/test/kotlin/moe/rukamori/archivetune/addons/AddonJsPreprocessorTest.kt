package moe.rukamori.archivetune.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AddonJsPreprocessorTest {

    @Test
    fun preprocess_rewritesEsmExports() {
        val source = """
            export default async function searchTracks(query, limit) {
                return [];
            }
            export const getTrackStreamUrl = function(id) {
                return null;
            };
            export { helper1, helper2 };
        """.trimIndent()

        val processed = AddonJsPreprocessor.preprocess(source)

        assertFalse(processed.contains("export default"))
        assertFalse(processed.contains("export const"))
        assertFalse(processed.contains("export {"))
        assertEquals(
            """
            async function searchTracks(query, limit) {
                return [];
            }
            const getTrackStreamUrl = function(id) {
                return null;
            };
            """.trimIndent(),
            processed.trim()
        )
    }

    @Test
    fun preprocess_handlesTemplateLiteralExport() {
        val source = """
            export const spine = `
                function searchTracks(q) { return [q]; }
                module.exports = { searchTracks };
            `
        """.trimIndent()

        val processed = AddonJsPreprocessor.preprocess(source)

        assertEquals(
            """
            function searchTracks(q) { return [q]; }
                module.exports = { searchTracks };
            """.trimIndent(),
            processed
        )
    }
}
