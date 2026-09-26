/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val SearchHorizontalPadding = 12.dp
private val SearchGroupOuterCorner = 24.dp
private val SearchGroupInnerCorner = 6.dp

@Composable
internal fun SearchSectionHeader(
    title: String,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color =
            if (pureBlack) {
                Color.White.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = SearchHorizontalPadding + 4.dp,
                    top = 16.dp,
                    end = SearchHorizontalPadding + 4.dp,
                    bottom = 6.dp,
                ),
    )
}

internal fun segmentedSearchItemShape(
    index: Int,
    count: Int,
): Shape =
    when {
        count <= 1 -> {
            RoundedCornerShape(SearchGroupOuterCorner)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = SearchGroupOuterCorner,
                topEnd = SearchGroupOuterCorner,
                bottomEnd = SearchGroupInnerCorner,
                bottomStart = SearchGroupInnerCorner,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = SearchGroupInnerCorner,
                topEnd = SearchGroupInnerCorner,
                bottomEnd = SearchGroupOuterCorner,
                bottomStart = SearchGroupOuterCorner,
            )
        }

        else -> {
            RoundedCornerShape(SearchGroupInnerCorner)
        }
    }
