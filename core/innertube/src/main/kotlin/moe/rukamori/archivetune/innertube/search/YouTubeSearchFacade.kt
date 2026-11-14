/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.search

import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage

interface YouTubeSearchFacade {
    suspend fun searchSuggestions(query: String): Result<SearchSuggestions>

    suspend fun searchSummary(
        query: String,
        useAccountContext: Boolean = true,
    ): Result<SearchSummaryPage>

    suspend fun search(
        query: String,
        filter: SearchFilter? = null,
        useAccountContext: Boolean = true,
    ): Result<SearchResult>

    suspend fun searchContinuation(
        continuation: String,
        useAccountContext: Boolean = true,
    ): Result<SearchResult>
}
