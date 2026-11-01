package moe.rukamori.archivetune.ui.screens.moodandgenres

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder

internal const val MOOD_GENRES_HEADER_KEY = "mood_genres_header"

internal data class MoodAndGenreTuple(
    val browseId: String,
    val params: String?,
    val title: String,
)

internal fun moodAndGenresItemKey(
    browseId: String,
    params: String?,
    title: String,
    occurrence: Int,
): String {
    val paramsEncoding = if (params == null) {
        "null"
    } else {
        "val:${params.length}:$params"
    }
    return "mood_genres_item:${browseId.length}:$browseId:$paramsEncoding:${title.length}:$title:$occurrence"
}

internal fun deriveMoodAndGenresKeys(items: List<MoodAndGenres.Item>): List<String> {
    if (items.isEmpty()) return emptyList()
    val counts = HashMap<MoodAndGenreTuple, Int>(items.size)
    return items.map { item ->
        val tuple = MoodAndGenreTuple(
            browseId = item.endpoint.browseId,
            params = item.endpoint.params,
            title = item.title,
        )
        val occurrence = counts[tuple] ?: 0
        counts[tuple] = occurrence + 1
        moodAndGenresItemKey(
            browseId = tuple.browseId,
            params = tuple.params,
            title = tuple.title,
            occurrence = occurrence,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.moodAndGenresSection(
    moodAndGenres: List<MoodAndGenres.Item>?,
    onItemClick: (BrowseEndpoint) -> Unit,
) {
    if (moodAndGenres == null) {
        items(
            count = 12,
            key = { index -> "mood_genres_shimmer_$index" },
            contentType = { CONTENT_TYPE_MOOD_GENRES_SHIMMER },
        ) {
            ShimmerHost {
                TextPlaceholder(
                    height = MoodAndGenresButtonHeight,
                    shape = MoodAndGenresButtonShape,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
    } else {
        val keys = deriveMoodAndGenresKeys(moodAndGenres)
        itemsIndexed(
            items = moodAndGenres,
            key = { index, _ -> keys[index] },
            contentType = { _, _ -> CONTENT_TYPE_MOOD_GENRES_ITEM },
        ) { _, item ->
            MoodAndGenresButton(
                title = item.title,
                stripeColor = item.stripeColor,
                endpoint = item.endpoint,
                onClick = { onItemClick(item.endpoint) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
            )
        }
    }
}
