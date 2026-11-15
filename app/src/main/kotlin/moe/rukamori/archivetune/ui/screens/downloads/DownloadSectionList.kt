/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder

private const val CONTENT_TYPE_SECTION_HEADER = "download_section_header"
private const val CONTENT_TYPE_DOWNLOAD_ENTRY = "download_entry"
private const val CONTENT_TYPE_SECTION_SPACER = "download_section_spacer"

@Composable
internal fun DownloadSections(
    sections: List<DownloadSectionUiModel>,
    inProgress: Boolean,
    query: String,
    contentPadding: PaddingValues,
    onOpenEntry: (DownloadEntryUiModel) -> Unit,
    onPauseEntry: (DownloadEntryUiModel) -> Unit,
    onResumeEntry: (DownloadEntryUiModel) -> Unit,
    onRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onRequestRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onPauseSection: (DownloadSectionUiModel) -> Unit,
    onResumeSection: (DownloadSectionUiModel) -> Unit,
    onRemoveSection: (DownloadSectionUiModel) -> Unit,
    onRequestRemoveSection: (DownloadSectionUiModel) -> Unit,
) {
    var expandedItemIds by remember { mutableStateOf(emptySet<String>()) }
    if (sections.isEmpty()) {
        EmptyPlaceholder(
            icon =
                if (query.isNotBlank()) {
                    R.drawable.search
                } else if (inProgress) {
                    R.drawable.download
                } else {
                    R.drawable.offline
                },
            text =
                stringResource(
                    if (query.isNotBlank()) {
                        R.string.no_matching_downloads
                    } else if (inProgress) {
                        R.string.no_downloads_in_progress
                    } else {
                        R.string.no_downloads
                    },
                ),
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val playerPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = playerPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sections.forEach { section ->
            item(
                key = "header:${section.mediaType}",
                contentType = CONTENT_TYPE_SECTION_HEADER,
            ) {
                DownloadSectionHeader(
                    section = section,
                    inProgress = inProgress,
                    onPause = { onPauseSection(section) },
                    onResume = { onResumeSection(section) },
                    onRemove = {
                        if (inProgress) {
                            onRemoveSection(section)
                        } else {
                            onRequestRemoveSection(section)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp).animateItem(),
                )
            }
            items(
                items = section.entries,
                key = DownloadEntryUiModel::id,
                contentType = { CONTENT_TYPE_DOWNLOAD_ENTRY },
            ) { entry ->
                val isExpanded = entry.id in expandedItemIds
                DownloadEntry(
                    entry = entry,
                    inProgress = inProgress,
                    onOpen = { onOpenEntry(entry) },
                    onPause = { onPauseEntry(entry) },
                    onResume = { onResumeEntry(entry) },
                    onRemove = {
                        if (inProgress) {
                            onRemoveEntry(entry)
                        } else {
                            onRequestRemoveEntry(entry)
                        }
                    },
                    isExpanded = isExpanded,
                    onExpandToggle = {
                        expandedItemIds =
                            if (isExpanded) {
                                expandedItemIds - entry.id
                            } else {
                                expandedItemIds + entry.id
                            }
                    },
                    onRemoveChild = if (inProgress) onRemoveEntry else onRequestRemoveEntry,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp).animateItem(),
                )
            }
            item(
                key = "spacer:${section.mediaType}",
                contentType = CONTENT_TYPE_SECTION_SPACER,
            ) {
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
