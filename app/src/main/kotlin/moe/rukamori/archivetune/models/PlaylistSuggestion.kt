/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.models

import moe.rukamori.archivetune.innertube.models.YTItem

data class PlaylistSuggestion(
    val items: List<YTItem>,
    val continuation: String?,
    val currentQueryIndex: Int,
    val totalQueries: Int,
    val query: String,
    val hasMore: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
)

data class PlaylistSuggestionPage(
    val items: List<YTItem>,
    val continuation: String?,
)

data class PlaylistSuggestionQuery(
    val query: String,
    val priority: Int,
)
