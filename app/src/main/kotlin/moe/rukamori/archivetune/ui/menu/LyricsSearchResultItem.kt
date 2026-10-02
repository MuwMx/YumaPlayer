/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.LyricsSearchResultUiModel

@Composable
internal fun LyricsSearchResultItem(
    result: LyricsSearchResultUiModel,
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
    onResultSelected: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val lyricsType =
        when {
            result.isWordSynced -> stringResource(R.string.lyrics_word_sync)
            result.isLineSynced -> stringResource(R.string.lyrics_synced_badge)
            else -> stringResource(R.string.lyrics_search_plain_badge)
        }
    val stats =
        stringResource(
            R.string.lyrics_search_result_stats,
            result.lineCount,
            result.characterCount,
        )
    val metadataArrangement = remember { Arrangement.spacedBy(8.dp) }
    val containerColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    val contentColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val outlineColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    val itemArrangement = remember { Arrangement.spacedBy(14.dp) }

    Surface(
        onClick = onResultSelected,
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, outlineColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = itemArrangement,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = metadataArrangement,
            ) {
                LyricsSearchTypeIcon(
                    result = result,
                    isExpanded = isExpanded,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.providerName,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = lyricsType,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onExpandedChange,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isExpanded) R.drawable.expand_less else R.drawable.expand_more,
                            ),
                        contentDescription = stringResource(R.string.details),
                        tint = contentColor,
                    )
                }
            }
            LyricsSearchResultSupportingContent(
                preview = result.preview,
                isExpanded = isExpanded,
                contentColor = contentColor,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = metadataArrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsSearchMetadataPill(
                    icon = R.drawable.ic_about,
                    text = lyricsType,
                    isExpanded = isExpanded,
                    modifier = Modifier.weight(1f),
                )
                LyricsSearchMetadataPill(
                    icon = R.drawable.text_fields,
                    text = stats,
                    isExpanded = isExpanded,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
