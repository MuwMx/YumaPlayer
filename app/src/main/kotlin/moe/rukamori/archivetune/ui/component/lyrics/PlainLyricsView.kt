/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
internal data class PlainLyrics(
    val items: List<PlainLyricLine>,
)

@Immutable
internal data class PlainLyricLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
    val translation: String? = null,
)

@Composable
internal fun PlainLyricsView(
    lines: PlainLyrics,
    listState: LazyListState,
    selectedLineKeys: Set<String>,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding =
        remember {
            PaddingValues(
                top = 120.dp,
                bottom = 96.dp,
            )
        }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = lines.items,
            key = { line -> line.itemId },
            contentType = { "plain_lyric_line" },
        ) { line ->
            PlainLyricLineItem(
                line = line,
                selected = line.selectionId in selectedLineKeys,
                textColor = textColor,
                textStyle = textStyle,
                onLineClicked = onLineClicked,
                onLinePressed = onLinePressed,
            )
        }
    }
}

@Composable
internal fun PlainLyricLineItem(
    line: PlainLyricLine,
    selected: Boolean,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            textColor
        }

    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .combinedClickable(
            onClick = { onLineClicked(line.selectionId) },
            onLongClick = { onLinePressed(line.selectionId) },
        )
        .padding(vertical = 8.dp)

    if (line.translation != null) {
        Column(modifier = modifier) {
            Text(
                text = line.text,
                style = textStyle,
                color = contentColor,
            )
            Text(
                text = line.translation,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (textStyle.fontSize.value * 0.55f).sp,
                    lineHeight = (textStyle.fontSize.value * 0.75f).sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = contentColor.copy(alpha = 0.76f),
                modifier = Modifier.padding(top = (textStyle.fontSize.value * 0.3f).dp),
            )
        }
    } else {
        Text(
            text = line.text,
            style = textStyle,
            color = contentColor,
            modifier = modifier,
        )
    }
}
