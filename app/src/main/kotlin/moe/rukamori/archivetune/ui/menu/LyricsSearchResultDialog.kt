/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import moe.rukamori.archivetune.viewmodels.LyricsSearchResultUiModel
import moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState

@Composable
internal fun LyricsSearchResultDialog(
    state: LyricsSearchScreenState,
    expandedResultId: String?,
    onExpandedResultChange: (String) -> Unit,
    onResultSelected: (LyricsSearchResultUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val listContentPadding =
                remember {
                    PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)
                }
            val listArrangement = remember { Arrangement.spacedBy(10.dp) }

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LyricsSearchResultHeader(
                        state = state,
                        onDismiss = onDismiss,
                    )
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        contentPadding = listContentPadding,
                        verticalArrangement = listArrangement,
                    ) {
                        when (state) {
                            LyricsSearchScreenState.Loading -> {
                                item(contentType = "lyrics_search_loading") {
                                    LyricsSearchLoadingContent()
                                }
                            }

                            LyricsSearchScreenState.Empty -> {
                                item(contentType = "lyrics_search_empty") {
                                    LyricsSearchEmptyContent()
                                }
                            }

                            is LyricsSearchScreenState.Error -> {
                                item(contentType = "lyrics_search_error") {
                                    LyricsSearchErrorContent(messageResId = state.messageResId)
                                }
                            }

                            is LyricsSearchScreenState.Success -> {
                                itemsIndexed(
                                    items = state.results,
                                    key = { _, result -> result.id },
                                    contentType = { _, _ -> "lyrics_search_result" },
                                ) { _, result ->
                                    LyricsSearchResultItem(
                                        result = result,
                                        isExpanded = result.id == expandedResultId,
                                        onExpandedChange = { onExpandedResultChange(result.id) },
                                        onResultSelected = { onResultSelected(result) },
                                    )
                                }

                                if (state.isSearching) {
                                    item(contentType = "lyrics_search_footer_loading") {
                                        LyricsSearchFooterLoading()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
