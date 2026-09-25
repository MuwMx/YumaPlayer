package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton

@Immutable
data class SelectionTopBarUiState(
    val selection: Boolean,
    val selectedCount: Int,
    val totalCount: Int,
    val isSearching: Boolean,
    val query: TextFieldValue,
    val showTopBarTitle: Boolean,
    val title: String,
    val hasSongs: Boolean,
)

@Immutable
data class SelectionTopBarCallbacks(
    val onQueryChange: (TextFieldValue) -> Unit,
    val onSearchOpen: () -> Unit,
    val onSearchClose: () -> Unit,
    val onSelectionClear: () -> Unit,
    val onSelectAll: () -> Unit,
    val onShowSelectionMenu: () -> Unit,
    val onShowMoreMenu: () -> Unit,
    val onNavigateUp: () -> Unit,
    val onBackToMain: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistSelectionTopBar(
    state: SelectionTopBarUiState,
    callbacks: SelectionTopBarCallbacks,
    focusRequester: FocusRequester,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        title = {
            when {
                state.selection -> {
                    Text(
                        text = pluralStringResource(R.plurals.n_song, state.selectedCount, state.selectedCount),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                state.isSearching -> {
                    TextField(
                        value = state.query,
                        onValueChange = callbacks.onQueryChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                }

                state.showTopBarTitle -> {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    when {
                        state.isSearching -> {
                            callbacks.onSearchClose()
                        }

                        state.selection -> {
                            callbacks.onSelectionClear()
                        }

                        else -> {
                            callbacks.onNavigateUp()
                        }
                    }
                },
                onLongClick = {
                    if (!state.isSearching && !state.selection) {
                        callbacks.onBackToMain()
                    }
                },
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (state.selection) R.drawable.close else R.drawable.arrow_back,
                        ),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (state.selection) {
                M3IconButton(
                    onClick = callbacks.onSelectAll,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (state.selectedCount == state.totalCount) R.drawable.deselect else R.drawable.select_all,
                            ),
                        contentDescription = null,
                    )
                }

                M3IconButton(
                    onClick = callbacks.onShowSelectionMenu,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            } else if (!state.isSearching) {
                M3IconButton(
                    onClick = callbacks.onSearchOpen,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                }
                if (state.hasSongs) {
                    M3IconButton(
                        onClick = callbacks.onShowMoreMenu,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                }
            }
        },
    )
}
