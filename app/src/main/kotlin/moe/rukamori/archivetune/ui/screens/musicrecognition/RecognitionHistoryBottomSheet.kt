/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.RecognitionHistorySheetUiState
import moe.rukamori.archivetune.viewmodels.RecognitionHistoryUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecognitionHistoryBottomSheet(
    state: RecognitionHistorySheetUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val clearQuery = remember(onQueryChange) { { onQueryChange("") } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.music_recognition_history),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }

                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = {
                                Text(stringResource(R.string.music_recognition_history_search))
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon =
                                if (state.query.isNotEmpty()) {
                                    {
                                        IconButton(onClick = clearQuery) {
                                            Icon(
                                                painter = painterResource(R.drawable.close),
                                                contentDescription = stringResource(R.string.clear),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                ) {}

                when {
                    state.allItems.items.isEmpty() -> {
                        RecognitionHistoryEmptyState(
                            iconRes = R.drawable.history,
                            title = stringResource(R.string.music_recognition_history_empty_title),
                            body = stringResource(R.string.music_recognition_history_empty_body),
                        )
                    }

                    state.filteredItems.items.isEmpty() -> {
                        RecognitionHistoryEmptyState(
                            iconRes = R.drawable.search_off,
                            title = stringResource(R.string.music_recognition_history_no_results_title),
                            body = stringResource(R.string.music_recognition_history_no_results_body),
                        )
                    }

                    else -> {
                        RecognitionHistoryList(
                            history = state.filteredItems,
                            onSearch = onSearch,
                            onOpenUri = onOpenUri,
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun RecognitionHistoryList(
    history: RecognitionHistoryUiModel,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = history.items,
            key = { _, item -> item.stableKey },
            contentType = { _, _ -> RecognitionHistoryItemContentType },
        ) { index, item ->
            RecognitionHistoryListItem(
                item = item,
                index = index,
                count = history.items.size,
                onSearch = onSearch,
                onOpenUri = onOpenUri,
            )
        }
    }
}
