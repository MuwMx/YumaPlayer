/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.shared

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import moe.rukamori.archivetune.ui.utils.ItemWrapper

/**
 * Shared search + selection state for every playlist screen.
 *
 * This is the AutoPlaylistSearchState behavior generalized over the row model
 * type [T] with a caller-supplied [filter] — the auto (Song) and online
 * (Pair<Int, SongItem>) screens hold identical logic today; the Local screen
 * keeps its PlaylistSongMap-id-based variant because its rows key selection
 * by `map.id`, not by the wrapped item.
 */
@Stable
class PlaylistSearchStateHolder<T>(
    isSearchingState: MutableState<Boolean>,
    queryState: MutableState<TextFieldValue>,
    selectionState: MutableState<Boolean>,
    val focusRequester: FocusRequester,
    val wrappedItems: SnapshotStateList<ItemWrapper<T>>,
    val filteredItems: List<ItemWrapper<T>>,
    private val selectedCountState: State<Int>,
) {
    var isSearching by isSearchingState
    var query by queryState
    var selection by selectionState

    val selectedCount: Int
        get() = selectedCountState.value

    fun clearSelection() {
        selection = false
        wrappedItems.forEach { it.isSelected = false }
    }

    fun toggleSelectAll() {
        if (selectedCount == wrappedItems.size) {
            wrappedItems.forEach { it.isSelected = false }
            selection = false
        } else {
            wrappedItems.forEach { it.isSelected = true }
        }
    }

    fun openSearch() {
        isSearching = true
    }

    fun closeSearch(focusManager: FocusManager? = null) {
        isSearching = false
        query = TextFieldValue()
        focusManager?.clearFocus()
    }
}

@Composable
fun <T> rememberPlaylistSearchStateHolder(
    items: List<T>,
    filter: (T, String) -> Boolean,
    persistSearch: Boolean = false,
): PlaylistSearchStateHolder<T> {
    val isSearchingState =
        if (persistSearch) {
            rememberSaveable { mutableStateOf(false) }
        } else {
            remember { mutableStateOf(false) }
        }
    val queryState =
        if (persistSearch) {
            rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
        } else {
            remember { mutableStateOf(TextFieldValue()) }
        }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchingState.value) {
        if (isSearchingState.value) {
            focusRequester.requestFocus()
        }
    }

    val wrappedItems =
        remember(items) {
            items.map { item -> ItemWrapper(item) }.toMutableStateList()
        }

    val selectionState = remember { mutableStateOf(false) }
    val selectedCountState =
        remember(wrappedItems) {
            derivedStateOf { wrappedItems.count { it.isSelected } }
        }

    LaunchedEffect(selectionState.value, selectedCountState.value) {
        if (selectionState.value && selectedCountState.value == 0) {
            selectionState.value = false
        }
    }

    if (isSearchingState.value) {
        BackHandler {
            isSearchingState.value = false
            queryState.value = TextFieldValue()
        }
    } else if (selectionState.value) {
        BackHandler {
            selectionState.value = false
            wrappedItems.forEach { it.isSelected = false }
        }
    }

    val query = queryState.value
    val filteredItems =
        remember(wrappedItems, query) {
            val searchQuery = query.text.trim()
            if (searchQuery.isEmpty()) {
                wrappedItems
            } else {
                wrappedItems.filter { wrapper -> filter(wrapper.item, searchQuery) }
            }
        }

    return remember(wrappedItems, filteredItems) {
        PlaylistSearchStateHolder(
            isSearchingState = isSearchingState,
            queryState = queryState,
            selectionState = selectionState,
            focusRequester = focusRequester,
            wrappedItems = wrappedItems,
            filteredItems = filteredItems,
            selectedCountState = selectedCountState,
        )
    }
}
