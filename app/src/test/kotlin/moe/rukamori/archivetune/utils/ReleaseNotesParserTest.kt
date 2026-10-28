/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReleaseNotesParserTest {

    @Test
    fun `parses generated workflow sections in order`() {
        val notes =
            """
            # Changelog

            ## Features

            - Add release timeline — by @alex
            - Add badges — by @sam

            ## Fixes

            - Fix date formatting — by @kim

            ## Performance

            - Speed up note parsing — by @lee

            ## Refactors

            - Split timeline UI — by @alex

            ## Documentation

            - Document parser contract — by @sam

            ## Build / CI

            - Update release workflow — by @kim

            <details>
            <summary>Other changes</summary>

            - Update dependencies — by @lee
            </details>
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(
            listOf(
                ReleaseNoteCategory.FEATURES,
                ReleaseNoteCategory.FIXES,
                ReleaseNoteCategory.PERFORMANCE,
                ReleaseNoteCategory.REFACTORS,
                ReleaseNoteCategory.DOCUMENTATION,
                ReleaseNoteCategory.BUILD_CI,
                ReleaseNoteCategory.OTHER,
            ),
            parsed.sections.map { it.category },
        )
        assertEquals(listOf(2, 1, 1, 1, 1, 1, 1), parsed.sections.map { it.items.size })
        assertEquals(ReleaseNotesSummary(features = 2, fixes = 1, performance = 1), parsed.summary)
        assertEquals(notes, parsed.source)
        parsed.sections.dropLast(1).forEach { section ->
            assertNull(section.title)
        }
        assertNull(parsed.sections.last().title)
    }

    @Test
    fun `recognizes case insensitive aliases and canonical titles`() {
        val notes =
            """
            ## NEW FEATURES
            - One
            ## bug fixes
            - Two
            ## Performance Improvements
            - Three
            ## Improvements
            - Four
            ## docs
            - Five
            ## build/ci
            - Six
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(
            listOf(
                ReleaseNoteCategory.FEATURES,
                ReleaseNoteCategory.FIXES,
                ReleaseNoteCategory.PERFORMANCE,
                ReleaseNoteCategory.IMPROVEMENTS,
                ReleaseNoteCategory.DOCUMENTATION,
                ReleaseNoteCategory.BUILD_CI,
            ),
            parsed.sections.map { it.category },
        )
        assertEquals(ReleaseNotesSummary(features = 1, fixes = 1, performance = 1), parsed.summary)
        parsed.sections.forEach { section ->
            assertNull(section.title)
        }
    }

    @Test
    fun `preserves item order and keeps nested descriptions with their parent`() {
        val notes =
            """
            ## Fixes

            - First fix — by @alex
              More detail about the fix.

              - Nested follow-up
              > A quoted note
            - Second fix — by @sam
              Second description line.
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(1, parsed.sections.size)
        assertEquals(2, parsed.sections.single().items.size)
        assertEquals(2, parsed.summary.fixes)
        assertTrue(parsed.sections.single().items.first().markdown.startsWith("- First fix"))
        assertTrue(parsed.sections.single().items.first().markdown.contains("Nested follow-up"))
        assertTrue(parsed.sections.single().items.first().markdown.contains("> A quoted note"))
        assertTrue(parsed.sections.single().items.last().markdown.startsWith("- Second fix"))
    }

    @Test
    fun `supports bullet variants and ordered lists`() {
        val notes =
            """
            ## Features

            * Star item
            + Plus item
            - Dash item
            1. Ordered dot item
            2) Ordered parenthesis item
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(5, parsed.sections.single().items.size)
        assertEquals(5, parsed.summary.features)
    }

    @Test
    fun `empty notes fall back with exact source`() {
        listOf(null, "", "   \n\n").forEach { body ->
            val notes = ReleaseNotesParser.parse(body)
            assertTrue(notes is ReleaseNotes.Fallback)
            assertEquals(body.orEmpty(), notes.source)
        }
    }

    @Test
    fun `canary prose is kept as one uncounted section`() {
        val notes =
            """
            ## ⚡ YumaPlayer Canary (2026-10-21)
            Automated test build from [`MuwMx/YumaPlayer:main`](https://github.com/MuwMx/YumaPlayer).

            > ⚠️ **Notice:** Contains experimental changes and works in progress.
            """.trimIndent()

        val parsed = parsed(notes)
        val section = parsed.sections.single()

        assertEquals(ReleaseNoteCategory.OTHER, section.category)
        assertEquals(1, section.items.size)
        assertEquals(ReleaseNotesSummary(), parsed.summary)
    }

    @Test
    fun `unknown heading with items is preserved as an other section`() {
        val notes =
            """
            ## Breaking Changes

            - Remove legacy API — by @alex
            """.trimIndent()

        val parsed = parsed(notes)
        val section = parsed.sections.single()

        assertEquals(ReleaseNoteCategory.OTHER, section.category)
        assertEquals("Breaking Changes", section.title)
        assertEquals(1, section.items.size)
        assertEquals(ReleaseNotesSummary(), parsed.summary)
    }

    @Test
    fun `a fenced block with no bullet before it is skipped and later bullets are kept`() {
        val notes =
            """
            ## Features

            ```
            val example = "not a change"
            ```

            - A real change
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(1, parsed.sections.single().items.size)
        assertEquals(1, parsed.summary.features)
    }

    @Test
    fun `fenced code and nested tables inside an item do not add counts`() {
        val notes =
            """
            ## Fixes

            - Fix parser — by @alex

              ```
              val nested = true
              ```

              | Column | Value |
              | --- | --- |
              | Nested | Table |

                - Nested bullet
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(1, parsed.sections.single().items.size)
        assertEquals(1, parsed.summary.fixes)
        assertTrue(parsed.sections.single().items.single().markdown.contains("val nested = true"))
        assertTrue(parsed.sections.single().items.single().markdown.contains("| Nested | Table |"))
    }

    @Test
    fun `uncategorized list without headings becomes an other section`() {
        val notes =
            """
            - First item
            - Second item
            """.trimIndent()

        val parsed = parsed(notes)
        val section = parsed.sections.single()

        assertEquals(ReleaseNoteCategory.OTHER, section.category)
        assertNull(section.title)
        assertEquals(2, section.items.size)
        assertEquals(ReleaseNotesSummary(), parsed.summary)
    }

    @Test
    fun `prose under an unknown heading is kept without being counted`() {
        val notes =
            """
            ## Notes

            This release needs a manual migration.
            """.trimIndent()

        val parsed = parsed(notes)
        val section = parsed.sections.single()

        assertEquals("Notes", section.title)
        assertEquals(1, section.items.size)
        assertEquals(ReleaseNotesSummary(), parsed.summary)
    }

    @Test
    fun `a table after a bullet stays inside that bullet`() {
        val notes =
            """
            ## Features

            - One

            | Column | Value |
            | --- | --- |
            | Nested | Table |
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(1, parsed.sections.single().items.size)
        assertEquals(1, parsed.summary.features)
    }

    @Test
    fun `an html block after a bullet stays inside that bullet`() {
        val notes =
            """
            ## Features
            - One
            <table>
            <tr><td>Cell</td></tr>
            </table>
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(1, parsed.sections.single().items.size)
        assertEquals(1, parsed.summary.features)
    }

    @Test
    fun `nested fence closes only on the matching marker and length`() {
        val notes =
            """
            ## Fixes

            - Fix parser — by @alex

              ````
              before
              ~~~
              - Not a change
              ```
              after
              ````
            """.trimIndent()

        val parsed = parsed(notes)
        val markdown = parsed.sections.single().items.single().markdown

        assertEquals(1, parsed.sections.single().items.size)
        assertEquals(1, parsed.summary.fixes)
        assertTrue(markdown.contains("- Not a change"))
        assertTrue(markdown.contains("after"))
    }

    @Test
    fun `details body without a summary does not inherit the previous category`() {
        val notes =
            """
            ## Features

            - Shipped feature

            <details>
            - Uncategorized note
            </details>
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(2, parsed.sections.size)
        assertEquals(ReleaseNoteCategory.FEATURES, parsed.sections[0].category)
        assertEquals(1, parsed.sections[0].items.size)
        assertEquals(ReleaseNoteCategory.OTHER, parsed.sections[1].category)
        assertNull(parsed.sections[1].title)
        assertEquals(1, parsed.sections[1].items.size)
        assertEquals(ReleaseNotesSummary(features = 1), parsed.summary)
    }

    @Test
    fun `cleans quote and bullet prefixes from unknown section titles`() {
        val notes =
            """
            ## > - **Breaking Changes**

            - Remove legacy API — by @alex
            """.trimIndent()

        val parsed = parsed(notes)
        val section = parsed.sections.single()

        assertEquals(ReleaseNoteCategory.OTHER, section.category)
        assertEquals("**Breaking Changes**", section.title)
        assertEquals(1, section.items.size)
        assertEquals(notes, parsed.source)
    }

    @Test
    fun `cleans listed prefixes before matching a canonical heading`() {
        val notes =
            """
            ## * Features

            - Add a real feature
            """.trimIndent()

        val parsed = parsed(notes)
        val section = parsed.sections.single()

        assertEquals(ReleaseNoteCategory.FEATURES, section.category)
        assertNull(section.title)
        assertEquals(1, parsed.summary.features)
    }

    @Test
    fun `keeps inline emphasis in section titles`() {
        val notes =
            """
            ## **Migration** notes

            - Move local data
            """.trimIndent()

        val parsed = parsed(notes)
        val section = parsed.sections.single()

        assertEquals(ReleaseNoteCategory.OTHER, section.category)
        assertEquals("**Migration** notes", section.title)
    }

    @Test
    fun `an empty unknown heading is dropped`() {
        val notes =
            """
            ## Notes

            ## Features

            - One
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(ReleaseNoteCategory.FEATURES, parsed.sections.single().category)
        assertEquals(1, parsed.summary.features)
    }

    @Test
    fun `empty top level changelog wrapper heading is allowed`() {
        val notes =
            """
            # Changelog

            ## Features

            - One
            """.trimIndent()

        val parsed = parsed(notes)

        assertEquals(1, parsed.sections.size)
        assertEquals(ReleaseNoteCategory.FEATURES, parsed.sections.single().category)
    }

    private fun parsed(body: String): ReleaseNotes.Parsed {
        val notes = ReleaseNotesParser.parse(body)
        if (notes is ReleaseNotes.Parsed) return notes
        fail("Expected structured release notes for:\n$body")
        error("Unreachable")
    }
}
