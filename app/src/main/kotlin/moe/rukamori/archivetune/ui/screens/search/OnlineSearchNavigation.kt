/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import android.util.Base64

internal const val OnlineSearchResultRoute = "search/{encodedQuery}"
internal const val OnlineSearchResultRoutePrefix = "search/"
internal const val OnlineSearchResultArgument = "encodedQuery"

private const val EmptyOnlineSearchQuery = "~"
private val OnlineSearchQueryEncodingFlags =
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

internal fun onlineSearchResultRoute(query: String): String {
    val encodedQuery =
        if (query.isEmpty()) {
            EmptyOnlineSearchQuery
        } else {
            Base64.encodeToString(
                query.toByteArray(Charsets.UTF_8),
                OnlineSearchQueryEncodingFlags,
            )
        }

    return "$OnlineSearchResultRoutePrefix$encodedQuery"
}

internal fun decodeOnlineSearchQuery(encodedQuery: String): String =
    if (encodedQuery == EmptyOnlineSearchQuery) {
        ""
    } else {
        runCatching {
            String(
                Base64.decode(encodedQuery, OnlineSearchQueryEncodingFlags),
                Charsets.UTF_8,
            )
        }.getOrElse {
            encodedQuery
        }
    }
