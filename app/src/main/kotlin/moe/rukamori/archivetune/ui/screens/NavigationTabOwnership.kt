package moe.rukamori.archivetune.ui.screens

internal fun findTabForRoute(route: String?): Screens? {
    if (route == null) return null
    return when {
        route == Screens.Home.route -> Screens.Home
        route == Screens.Search.route -> Screens.Search
        route == Screens.Library.route -> Screens.Library

        // Library sub-routes (e.g. Cache playlist, local library screens)
        route == "local_songs" ||
            route == "history" ||
            route == "stats" ||
            route == "spotify_liked_songs" ||
            route.startsWith("cache_playlist") ||
            route.startsWith("local_playlist") ||
            route.startsWith("auto_playlist") ||
            route.startsWith("top_playlist") -> Screens.Library

        // Search sub-routes
        route == Screens.MoodAndGenres.route ||
            route == "charts_screen" ||
            route.startsWith("youtube_browse") ||
            route.startsWith("online_search_result") -> Screens.Search

        // Home sub-routes
        route == "account" ||
            route == "new_release" ||
            route.startsWith("year_in_music") ||
            route == "news" ||
            route.startsWith("view_news") -> Screens.Home

        else -> null
    }
}
