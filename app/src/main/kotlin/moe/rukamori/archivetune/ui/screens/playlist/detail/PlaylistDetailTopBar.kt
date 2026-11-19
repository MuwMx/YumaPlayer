/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder
import moe.rukamori.archivetune.ui.utils.backToMain

/**
 * The unified top bar — the shared chrome every legacy playlist screen
 * carried: transparent title area that switches between counted-selection
 * title, search field and (on scroll) playlist title; a nav icon that closes
 * search/selection first and long-presses back to the main tab; select-all +
 * overflow in selection; search + any source-specific trailing action
 * otherwise. Per-origin selection menus are supplied via [onSelectionMenu].
 */
@Composable
internal fun PlaylistDetailTopBar(
    searchState: PlaylistSearchStateHolder<*>,
    navController: NavController,
    title: String,
    showTitle: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onSelectionMenu: () -> Unit,
    onSelectionRemoveFromCache: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        title = {
            if (searchState.selection) {
                val count = searchState.selectedCount
                Text(
                    text = pluralStringResource(R.plurals.n_song, count, count),
                    style = MaterialTheme.typography.titleLarge,
                )
            } else if (searchState.isSearching) {
                TextField(
                    value = searchState.query,
                    onValueChange = { searchState.query = it },
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
                            .focusRequester(searchState.focusRequester),
                )
            } else if (showTitle) {
                Text(title)
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (searchState.isSearching) {
                        searchState.closeSearch()
                    } else if (searchState.selection) {
                        searchState.selection = false
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (!searchState.isSearching && !searchState.selection) {
                        navController.backToMain()
                    }
                },
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (searchState.selection || searchState.isSearching) {
                                R.drawable.close
                            } else {
                                R.drawable.arrow_back
                            },
                        ),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (searchState.selection) {
                // CACHE PLAYLIST SLOT — delete-from-cache lives in the TopBar
                // on the cache screen (CachePlaylistScreen.kt:526-536); other
                // sources pass null. Do not delete this branch.
                if (onSelectionRemoveFromCache != null) {
                    IconButton(onClick = onSelectionRemoveFromCache, onLongClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.remove_from_cache),
                        )
                    }
                }
                IconButton(
                    onClick = searchState::toggleSelectAll,
                    onLongClick = {},
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (searchState.selectedCount == searchState.wrappedItems.size) {
                                    R.drawable.deselect
                                } else {
                                    R.drawable.select_all
                                },
                            ),
                        contentDescription = null,
                    )
                }
                IconButton(
                    onClick = onSelectionMenu,
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            } else if (!searchState.isSearching) {
                IconButton(
                    onClick = searchState::openSearch,
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                }
                trailingAction?.invoke()
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
