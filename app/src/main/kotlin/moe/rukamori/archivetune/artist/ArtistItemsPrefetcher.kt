/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.artist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistSection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts loading an artist's full album list as soon as the artist page has loaded, so tapping "Albums"
 * shows a list that is already loaded (or on its way) instead of starting the request then.
 *
 * Lists are kept for [MAX_AGE_MS] and shared by every screen that asks for the same list; a failed load is
 * dropped, so the Albums screen then loads it itself as before.
 */
@Singleton
class ArtistItemsPrefetcher internal constructor(
    private val scope: CoroutineScope,
    private val load: suspend (BrowseEndpoint) -> Result<ArtistItemsPage>,
    private val now: () -> Long,
) {
    @Inject
    constructor() : this(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { endpoint -> YouTube.artistItems(endpoint) },
        System::currentTimeMillis,
    )

    private class Entry(
        val page: Deferred<Result<ArtistItemsPage>>,
        val startedAt: Long,
    )

    // keyed by what the request sends: the route to the Albums screen drops a blank params and the page context
    private val entries = LinkedHashMap<Pair<String, String?>, Entry>()

    /** Starts loading the album list behind the artist page's first album section with a "see all" link. */
    fun prefetchAlbums(sections: List<ArtistSection>) {
        sections
            .firstOrNull { section -> section.moreEndpoint != null && section.items.any { it is AlbumItem } }
            ?.moreEndpoint
            ?.let(::prefetch)
    }

    fun prefetch(endpoint: BrowseEndpoint) {
        val key = keyOf(endpoint)
        synchronized(entries) {
            if (entries[key]?.let(::isFresh) == true) return
            entries[key] = Entry(scope.async { load(endpoint) }, now())
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
    }

    /** The list for [endpoint] if it was prefetched recently, waiting for it if it is still loading; else null. */
    suspend fun prefetched(endpoint: BrowseEndpoint): ArtistItemsPage? {
        val key = keyOf(endpoint)
        val entry = synchronized(entries) { entries[key]?.takeIf(::isFresh) } ?: return null
        val page = entry.page.await().getOrNull()
        if (page == null) {
            synchronized(entries) { if (entries[key] === entry) entries.remove(key) }
        }
        return page
    }

    private fun isFresh(entry: Entry) = now() - entry.startedAt < MAX_AGE_MS

    private fun keyOf(endpoint: BrowseEndpoint) = endpoint.browseId to endpoint.params?.takeUnless { it.isBlank() }

    internal companion object {
        const val MAX_AGE_MS = 5 * 60_000L
        const val MAX_ENTRIES = 8
    }
}
