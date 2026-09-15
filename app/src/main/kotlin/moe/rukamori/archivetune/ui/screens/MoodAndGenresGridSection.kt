package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder

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
        items(
            items = moodAndGenres,
            key = { item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
            contentType = { CONTENT_TYPE_MOOD_GENRES_ITEM },
        ) { item ->
            MoodAndGenresButton(
                title = item.title,
                stripeColor = item.stripeColor,
                endpoint = item.endpoint,
                onClick = { onItemClick(item.endpoint) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                        .animateItem(),
            )
        }
    }
}
