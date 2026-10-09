package moe.rukamori.archivetune.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class NavigationTabOwnershipTest {

    @Test
    fun libraryTab_ownsLibraryAndCacheAndLocalRoutes() {
        assertEquals(Screens.Library, findTabForRoute(Screens.Library.route))
        assertEquals(Screens.Library, findTabForRoute("cache_playlist/cached"))
        assertEquals(Screens.Library, findTabForRoute("cache_playlist/{playlist}"))
        assertEquals(Screens.Library, findTabForRoute("local_playlist/favs"))
        assertEquals(Screens.Library, findTabForRoute("local_songs"))
        assertEquals(Screens.Library, findTabForRoute("history"))
        assertEquals(Screens.Library, findTabForRoute("stats"))
        assertEquals(Screens.Library, findTabForRoute("spotify_liked_songs"))
    }

    @Test
    fun searchTab_ownsSearchAndBrowseRoutes() {
        assertEquals(Screens.Search, findTabForRoute(Screens.Search.route))
        assertEquals(Screens.Search, findTabForRoute(Screens.MoodAndGenres.route))
        assertEquals(Screens.Search, findTabForRoute("charts_screen"))
        assertEquals(Screens.Search, findTabForRoute("youtube_browse/FEmusic_charts"))
        assertEquals(Screens.Search, findTabForRoute("online_search_result/test"))
    }

    @Test
    fun homeTab_ownsHomeAndAccountRoutes() {
        assertEquals(Screens.Home, findTabForRoute(Screens.Home.route))
        assertEquals(Screens.Home, findTabForRoute("account"))
        assertEquals(Screens.Home, findTabForRoute("new_release"))
        assertEquals(Screens.Home, findTabForRoute("year_in_music"))
    }

    @Test
    fun nullOrUnknownRoute_returnsNull() {
        assertNull(findTabForRoute(null))
        assertNull(findTabForRoute("unknown/arbitrary_route"))
    }
}
