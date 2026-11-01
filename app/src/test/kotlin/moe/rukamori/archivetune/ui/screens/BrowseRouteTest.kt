package moe.rukamori.archivetune.ui.screens

import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.ui.navigation.buildBrowseRoute
import moe.rukamori.archivetune.ui.screens.home.resolveHomeSectionRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@RunWith(JUnit4::class)
class BrowseRouteTest {

    @Test
    fun buildBrowseRoute_nullParams_omitsQuery() {
        val route = buildBrowseRoute("FEmusic_charts", null)
        assertEquals("browse/FEmusic_charts", route)
    }

    @Test
    fun buildBrowseRoute_literalNullStringParams_preservesValue() {
        val route = buildBrowseRoute("FEmusic_charts", "null")
        assertEquals("browse/FEmusic_charts?params=null", route)
    }

    @Test
    fun buildBrowseRoute_blankParams_omitsQuery() {
        val route = buildBrowseRoute("FEmusic_charts", "   ")
        assertEquals("browse/FEmusic_charts", route)
    }

    @Test
    fun buildBrowseRoute_opaqueParams_formatsQueryProperly() {
        val route = buildBrowseRoute("FEmusic_charts", "sgYPRkVtdXNpY19leHBsb3Jl")
        assertEquals("browse/FEmusic_charts?params=sgYPRkVtdXNpY19leHBsb3Jl", route)
    }

    @Test
    fun buildBrowseRoute_specialCharsInBrowseIdAndParams_encodedSafely() {
        val route = buildBrowseRoute("FE charts&more", "a=1&b=2")
        assertEquals("browse/FE%20charts%26more?params=a%3D1%26b%3D2", route)
    }

    @Test
    fun buildBrowseRoute_urlDecoderRoundTrip_preservesSpecialChars() {
        // Format/encoding coverage: verifies URI encoding round-trip via URLDecoder, not actual Android nav runtime.
        val testCases = listOf(
            "simpleId" to "simpleParams",
            "id/with/slash" to "params/with/slash",
            "id%with%percent" to "params%with%percent",
            "id+with+plus" to "params+with+plus",
            "id?with?question" to "params?with?question",
            "id#with#hash" to "params#with#hash",
            "id_unicode_Привет" to "params_unicode_музыка_🎵",
            "id&with&ampersand=equals" to "a=1&b=2&c=foo%20bar",
            "id_opaque" to "%2B123%2F456%25",
        )

        for ((browseId, params) in testCases) {
            val route = buildBrowseRoute(browseId, params)
            val prefix = "browse/"
            val queryIndex = route.indexOf("?params=")
            val encodedId = route.substring(prefix.length, queryIndex)
            val encodedParams = route.substring(queryIndex + "?params=".length)

            val decodedId = URLDecoder.decode(encodedId, StandardCharsets.UTF_8.name())
            val decodedParams = URLDecoder.decode(encodedParams, StandardCharsets.UTF_8.name())

            assertEquals(browseId, decodedId)
            assertEquals(params, decodedParams)
        }
    }

    @Test
    fun resolveHomeSectionRoute_nullEndpoint_returnsNull() {
        assertNull(resolveHomeSectionRoute(null))
    }

    @Test
    fun resolveHomeSectionRoute_moodAndGenres_returnsMoodAndGenresRoute() {
        val endpoint = BrowseEndpoint(browseId = "FEmusic_moods_and_genres", params = null)
        assertEquals(Screens.MoodAndGenres.route, resolveHomeSectionRoute(endpoint))
    }

    @Test
    fun resolveHomeSectionRoute_moodAndGenresWithParams_returnsMoodAndGenresRoute() {
        val endpoint = BrowseEndpoint(browseId = "FEmusic_moods_and_genres", params = "some_opaque_params")
        assertEquals(Screens.MoodAndGenres.route, resolveHomeSectionRoute(endpoint))
    }

    @Test
    fun resolveHomeSectionRoute_genericBrowseWithoutParams_returnsLegacyBrowseRoute() {
        val endpoint = BrowseEndpoint(browseId = "FEmusic_charts", params = null)
        assertEquals("browse/FEmusic_charts", resolveHomeSectionRoute(endpoint))
    }

    @Test
    fun resolveHomeSectionRoute_genericBrowseWithParams_preservesParamsInRoute() {
        val endpoint = BrowseEndpoint(browseId = "FEmusic_charts", params = "sgYPRkVtdXNpY19leHBsb3Jl")
        assertEquals("browse/FEmusic_charts?params=sgYPRkVtdXNpY19leHBsb3Jl", resolveHomeSectionRoute(endpoint))
    }

    @Test
    fun resolveHomeSectionRoute_genericBrowseWithLiteralNullParams_preservesValue() {
        val endpoint = BrowseEndpoint(browseId = "FEmusic_charts", params = "null")
        assertEquals("browse/FEmusic_charts?params=null", resolveHomeSectionRoute(endpoint))
    }
}
