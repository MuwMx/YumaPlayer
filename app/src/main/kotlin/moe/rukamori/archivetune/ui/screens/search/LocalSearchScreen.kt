/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.drop
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.ChipsRow
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.viewmodels.LocalFilter
import moe.rukamori.archivetune.viewmodels.LocalSearchViewModel

@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    pureBlack: Boolean,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Surface(
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            ChipsRow(
                chips = LOCAL_SEARCH_FILTER_CHIPS.map { it.first to stringResource(it.second) },
                currentValue = searchFilter,
                onValueUpdate = { viewModel.filter.value = it },
                icons = LOCAL_SEARCH_FILTER_ICONS,
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            result.map.forEach { (filter, items) ->
                if (result.filter == LocalFilter.ALL) {
                    localSearchSectionHeader(
                        filter = filter,
                        pureBlack = pureBlack,
                        onClick = { viewModel.filter.value = filter },
                    )
                }

                localSearchItems(
                    items = items,
                    allSongs = result.map.getOrDefault(LocalFilter.SONG, emptyList()).filterIsInstance<Song>(),
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    playerConnection = playerConnection,
                    navController = navController,
                    menuState = menuState,
                    onDismiss = onDismiss,
                    isFromCache = isFromCache,
                    context = context,
                )
            }

            if (result.query.isNotEmpty() && result.map.isEmpty()) {
                localSearchEmptyItem()
            }
        }
    }
}
