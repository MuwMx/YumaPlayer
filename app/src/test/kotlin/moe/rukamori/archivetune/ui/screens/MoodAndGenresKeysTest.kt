package moe.rukamori.archivetune.ui.screens

import moe.rukamori.archivetune.ui.screens.moodandgenres.MOOD_GENRES_HEADER_KEY

import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.ui.screens.moodandgenres.deriveMoodAndGenresKeys
import moe.rukamori.archivetune.ui.screens.moodandgenres.moodAndGenresItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MoodAndGenresKeysTest {

    private fun createItem(
        browseId: String,
        params: String?,
        title: String,
        stripeColor: Long = 0xFF123456,
    ): MoodAndGenres.Item {
        return MoodAndGenres.Item(
            title = title,
            stripeColor = stripeColor,
            endpoint = BrowseEndpoint(browseId = browseId, params = params),
        )
    }

    @Test
    fun deriveMoodAndGenresKeys_emptyList_returnsEmptyList() {
        val keys = deriveMoodAndGenresKeys(emptyList())
        assertTrue(keys.isEmpty())
    }

    @Test
    fun deriveMoodAndGenresKeys_singleItem_returnsKeyWithZeroOccurrence() {
        val item = createItem(browseId = "FE_mood", params = "chill", title = "Chill")
        val keys = deriveMoodAndGenresKeys(listOf(item))
        assertEquals(listOf(moodAndGenresItemKey("FE_mood", "chill", "Chill", 0)), keys)
    }

    @Test
    fun deriveMoodAndGenresKeys_sameBrowseIdDifferentParamsAndTitle_generatesUniqueKeys() {
        val item1 = createItem(browseId = "FE_explore", params = "p1", title = "Title 1")
        val item2 = createItem(browseId = "FE_explore", params = "p2", title = "Title 2")
        val item3 = createItem(browseId = "FE_explore", params = "p1", title = "Title 3")
        val item4 = createItem(browseId = "FE_explore", params = null, title = "Title 1")
        val items = listOf(item1, item2, item3, item4)

        val keys = deriveMoodAndGenresKeys(items)

        assertEquals(4, keys.size)
        assertEquals(4, keys.toSet().size)
    }

    @Test
    fun deriveMoodAndGenresKeys_exactDuplicateOccurrences_generatesUniqueOrdinalKeys() {
        val item1 = createItem(browseId = "FE_explore", params = "param", title = "Focus")
        val item2 = createItem(browseId = "FE_explore", params = "param", title = "Focus")
        val item3 = createItem(browseId = "FE_explore", params = "param", title = "Focus")
        val items = listOf(item1, item2, item3)

        val keys = deriveMoodAndGenresKeys(items)

        assertEquals(3, keys.size)
        assertEquals(3, keys.toSet().size)
        assertEquals(moodAndGenresItemKey("FE_explore", "param", "Focus", 0), keys[0])
        assertEquals(moodAndGenresItemKey("FE_explore", "param", "Focus", 1), keys[1])
        assertEquals(moodAndGenresItemKey("FE_explore", "param", "Focus", 2), keys[2])
    }

    @Test
    fun deriveMoodAndGenresKeys_independentlyRecreatedIdenticalData_yieldsIdenticalKeys() {
        val run1 = listOf(
            createItem("FE_1", "p1", "Rock"),
            createItem("FE_2", null, "Pop"),
            createItem("FE_1", "p1", "Rock"),
        )
        val run2 = listOf(
            createItem("FE_1", "p1", "Rock"),
            createItem("FE_2", null, "Pop"),
            createItem("FE_1", "p1", "Rock"),
        )

        val keys1 = deriveMoodAndGenresKeys(run1)
        val keys2 = deriveMoodAndGenresKeys(run2)

        assertEquals(keys1, keys2)
    }

    @Test
    fun deriveMoodAndGenresKeys_retainsOrderCountAndData() {
        val items = listOf(
            createItem("FE_A", "pA", "Alpha"),
            createItem("FE_B", "pB", "Beta"),
            createItem("FE_C", null, "Gamma"),
        )

        val keys = deriveMoodAndGenresKeys(items)

        assertEquals(items.size, keys.size)
        for (i in items.indices) {
            val expected = moodAndGenresItemKey(
                browseId = items[i].endpoint.browseId,
                params = items[i].endpoint.params,
                title = items[i].title,
                occurrence = 0,
            )
            assertEquals(expected, keys[i])
        }
    }

    @Test
    fun deriveMoodAndGenresKeys_unrelatedInsertionsAndReorders_preservesTupleIdentities() {
        val a1 = createItem("FE_A", "pA", "Alpha")
        val b = createItem("FE_B", "pB", "Beta")
        val a2 = createItem("FE_A", "pA", "Alpha")
        val baseItems = listOf(a1, b, a2)

        val baseKeys = deriveMoodAndGenresKeys(baseItems)
        val expectedKeyA1 = baseKeys[0]
        val expectedKeyB = baseKeys[1]
        val expectedKeyA2 = baseKeys[2]

        val c = createItem("FE_C", "pC", "Gamma")
        val insertedItems = listOf(c, a1, b, a2)
        val insertedKeys = deriveMoodAndGenresKeys(insertedItems)

        assertEquals(expectedKeyA1, insertedKeys[1])
        assertEquals(expectedKeyB, insertedKeys[2])
        assertEquals(expectedKeyA2, insertedKeys[3])

        val reorderedItems = listOf(b, a1)
        val reorderedKeys = deriveMoodAndGenresKeys(reorderedItems)
        assertEquals(expectedKeyB, reorderedKeys[0])
        assertEquals(expectedKeyA1, reorderedKeys[1])
    }

    @Test
    fun deriveMoodAndGenresKeys_delimiterContainingTuples_avoidsSerializationAmbiguity() {
        val item1 = createItem(browseId = "a:b", params = "c", title = "d")
        val item2 = createItem(browseId = "a", params = "b:val:1:c", title = "d")
        val item3 = createItem(browseId = "a", params = "c", title = "b:d")
        val item4 = createItem(browseId = "a:b:val:1:c", params = null, title = "d")
        val item5 = createItem(browseId = "a", params = "null:1:d:0", title = "x")
        val item6 = createItem(browseId = "a", params = null, title = "1:d:0:x")

        val items = listOf(item1, item2, item3, item4, item5, item6)
        val keys = deriveMoodAndGenresKeys(items)

        assertEquals(items.size, keys.size)
        assertEquals(items.size, keys.toSet().size)
    }

    @Test
    fun deriveMoodAndGenresKeys_nullEmptyAndLiteralNullParams_areDistinct() {
        val itemNull = createItem(browseId = "FE_test", params = null, title = "Title")
        val itemEmpty = createItem(browseId = "FE_test", params = "", title = "Title")
        val itemLiteralNull = createItem(browseId = "FE_test", params = "null", title = "Title")

        val keys = deriveMoodAndGenresKeys(listOf(itemNull, itemEmpty, itemLiteralNull))

        assertEquals(3, keys.toSet().size)
        assertNotEquals(keys[0], keys[1])
        assertNotEquals(keys[0], keys[2])
        assertNotEquals(keys[1], keys[2])
    }

    @Test
    fun deriveMoodAndGenresKeys_headerAndPlaceholderKeys_doNotCollideWithGeneratedContent() {
        val shimmerKeys = (0 until 12).map { "mood_genres_shimmer_$it" }.toSet()
        val headerKey = MOOD_GENRES_HEADER_KEY

        val normalItem = createItem("FE_normal", "params", "Normal")
        val adversarialItemHeader = createItem(headerKey, null, headerKey)
        val adversarialItemShimmer = createItem(
            "mood_genres_shimmer_0",
            "mood_genres_shimmer_1",
            "mood_genres_shimmer_2",
        )

        val generatedKeys = deriveMoodAndGenresKeys(
            listOf(normalItem, adversarialItemHeader, adversarialItemShimmer),
        )

        for (key in generatedKeys) {
            assertNotEquals(headerKey, key)
            assertTrue("Generated key $key must not collide with any shimmer placeholder", key !in shimmerKeys)
        }
    }
}
