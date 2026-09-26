/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.SearchHistory

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.onlineSearchSuggestions(
    history: List<SearchHistory>,
    suggestions: List<String>,
    pureBlack: Boolean,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    onQueryChange: (TextFieldValue) -> Unit,
) {
    if (history.isNotEmpty()) {
        item(
            key = "history_header",
            contentType = "section_header",
        ) {
            SearchSectionHeader(
                title = stringResource(R.string.search_history),
                pureBlack = pureBlack,
                modifier = Modifier.animateItem(),
            )
        }

        itemsIndexed(
            items = history,
            key = { _, historyItem -> "history_${historyItem.query}" },
            contentType = { _, _ -> "history" },
        ) { index, historyItem ->
            val itemShape =
                remember(index, history.size) {
                    segmentedSearchItemShape(index, history.size)
                }
            SuggestionItem(
                query = historyItem.query,
                online = false,
                onClick = {
                    onSearch(historyItem.query)
                    onDismiss()
                },
                onDelete = {
                    onDeleteHistory(historyItem)
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(historyItem.query, TextRange(historyItem.query.length)))
                },
                shape = itemShape,
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack,
            )
        }
    }

    if (suggestions.isNotEmpty()) {
        item(
            key = "suggestions_header",
            contentType = "section_header",
        ) {
            SearchSectionHeader(
                title = stringResource(R.string.suggestions),
                pureBlack = pureBlack,
                modifier = Modifier.animateItem(),
            )
        }

        itemsIndexed(
            items = suggestions,
            key = { _, suggestion -> "suggestion_$suggestion" },
            contentType = { _, _ -> "suggestion" },
        ) { index, suggestion ->
            val itemShape =
                remember(index, suggestions.size) {
                    segmentedSearchItemShape(index, suggestions.size)
                }
            SuggestionItem(
                query = suggestion,
                online = true,
                onClick = {
                    onSearch(suggestion)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(suggestion, TextRange(suggestion.length)))
                },
                shape = itemShape,
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack,
            )
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val containerColor =
        if (pureBlack) {
            Color.White.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

    val iconContainerColor =
        if (pureBlack) {
            Color.White.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }

    val iconTint =
        if (pureBlack) {
            Color.White.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SearchHorizontalPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SearchRowMinHeight)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(
                            color = iconContainerColor,
                            shape = MaterialTheme.shapes.medium,
                        ),
            ) {
                Icon(
                    painterResource(if (online) R.drawable.ic_search else R.drawable.history),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = if (pureBlack) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (!online) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.remove_from_history),
                        tint =
                            if (pureBlack) {
                                Color.White.copy(alpha = 0.62f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            IconButton(onClick = onFillTextField) {
                Icon(
                    painter = painterResource(R.drawable.arrow_top_left),
                    contentDescription = stringResource(R.string.search),
                    tint =
                        if (pureBlack) {
                            Color.White.copy(alpha = 0.62f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private val SearchRowMinHeight = 64.dp
