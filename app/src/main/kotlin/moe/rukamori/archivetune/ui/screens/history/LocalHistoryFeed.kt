/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.EventWithSong
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.viewmodels.DateAgo

@Composable
internal fun LocalHistoryFeed(
    listState: LazyListState,
    topPadding: Dp,
    headerContent: @Composable () -> Unit,
    filteredEvents: Map<DateAgo, List<EventWithSong>>,
    visibleEvents: List<EventWithSong>,
    isSearchActive: Boolean,
    selectedEventIds: Set<Long>,
    isPlaying: Boolean,
    activeMediaId: String?,
    dateAgoToString: (DateAgo) -> String,
    navController: NavController,
    onToggleSelection: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onSongMenu: (EventWithSong) -> Unit,
    onSongClick: (DateAgo, List<EventWithSong>, Int, EventWithSong) -> Unit,
) {
    val isSelectionMode = selectedEventIds.isNotEmpty()

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item("history_header_spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item("history_overview") {
            headerContent()
        }

        if (visibleEvents.isEmpty()) {
            item("local_history_empty") {
                HistoryStateCard(
                    title =
                        stringResource(
                            if (isSearchActive) R.string.history_no_results_title else R.string.history_local_empty_title,
                        ),
                    description =
                        stringResource(
                            if (isSearchActive) R.string.history_no_results_desc else R.string.history_local_empty_desc,
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else {
            filteredEvents.forEach { (dateAgo, songsForDate) ->
                stickyHeader(key = "header_$dateAgo") {
                    NavigationTitle(
                        title = dateAgoToString(dateAgo),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                itemsIndexed(
                    items = songsForDate,
                    key = { index, event -> "${event.event.id}_$index" },
                    contentType = { _, _ -> "local_history_song" },
                ) { index, event ->
                    SongListItem(
                        song = event.song,
                        isActive = event.song.id == activeMediaId,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        isSelected = event.event.id in selectedEventIds,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    if (!isSelectionMode) {
                                        onSongMenu(event)
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelection(event.event.id)
                                        } else {
                                            onSongClick(dateAgo, songsForDate, index, event)
                                        }
                                    },
                                    onLongClick = {
                                        onStartSelection(event.event.id)
                                    },
                                ).animateItem(),
                    )
                }
            }
        }
    }
}
