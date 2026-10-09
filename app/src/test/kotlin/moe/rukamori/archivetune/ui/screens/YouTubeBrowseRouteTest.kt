package moe.rukamori.archivetune.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class YouTubeBrowseRouteTest {

    @Test
    fun buildYouTubeBrowseRoute_nullParams_omitsQuery() {
        val route = buildYouTubeBrowseRoute("FEmusic_charts", null)
        assertEquals("youtube_browse/FEmusic_charts", route)
    }

    @Test
    fun buildYouTubeBrowseRoute_literalNullStringParams_omitsQuery() {
        val route = buildYouTubeBrowseRoute("FEmusic_charts", "null")
        assertEquals("youtube_browse/FEmusic_charts", route)
    }

    @Test
    fun buildYouTubeBrowseRoute_blankParams_omitsQuery() {
        val route = buildYouTubeBrowseRoute("FEmusic_charts", "   ")
        assertEquals("youtube_browse/FEmusic_charts", route)
    }

    @Test
    fun buildYouTubeBrowseRoute_opaqueParams_formatsQueryProperly() {
        val route = buildYouTubeBrowseRoute("FEmusic_charts", "sgYPRkVtdXNpY19leHBsb3Jl")
        assertEquals("youtube_browse/FEmusic_charts?params=sgYPRkVtdXNpY19leHBsb3Jl", route)
    }

    @Test
    fun buildYouTubeBrowseRoute_specialCharsInBrowseIdAndParams_encodedSafely() {
        val route = buildYouTubeBrowseRoute("FE charts&more", "a=1&b=2")
        // Checks that browseId is safely encoded and params is safely encoded
        assertEquals("youtube_browse/FE%20charts%26more?params=a%3D1%26b%3D2", route)
    }
}
