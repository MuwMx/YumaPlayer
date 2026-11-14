/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.SelectionMediaMetadataMenu
import moe.rukamori.archivetune.ui.screens.playlist.online.OnlinePlaylistSearchState
import moe.rukamori.archivetune.ui.utils.backToMain

@Composable
fun OnlinePlaylistTopBar(
    searchState: OnlinePlaylistSearchState,
    playlist: PlaylistItem?,
    showTopBarTitle: Boolean,
    menuState: MenuState,
    navController: NavController,
    onMenu: (() -> Unit)? = null,
) {
    TopAppBar(
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
            } else if (showTopBarTitle) {
                Text(playlist?.title.orEmpty())
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
                            if (searchState.selection) R.drawable.close else R.drawable.arrow_back,
                        ),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (searchState.selection) {
                val count = searchState.selectedCount
                IconButton(
                    onClick = { searchState.toggleSelectAll() },
                    onLongClick = {},
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (count == searchState.wrappedItems.size) {
                                    R.drawable.deselect
                                } else {
                                    R.drawable.select_all
                                },
                            ),
                        contentDescription = null,
                    )
                }
                IconButton(
                    onClick = {
                        menuState.show {
                            SelectionMediaMetadataMenu(
                                songSelection =
                                    searchState.wrappedItems
                                        .filter { it.isSelected }
                                        .map {
                                            it.item.second
                                                .toMediaItem()
                                                .metadata!!
                                        },
                                onDismiss = menuState::dismiss,
                                clearAction = { searchState.selection = false },
                                currentItems = emptyList(),
                                likeSourceHint =
                                    if (playlist?.id?.startsWith("spotify:") == true) {
                                        LikeSource.SPOTIFY
                                    } else {
                                        null
                                    },
                            )
                        }
                    },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            } else if (!searchState.isSearching) {
                IconButton(
                    onClick = { searchState.isSearching = true },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                }
                if (onMenu != null) {
                    IconButton(
                        onClick = onMenu,
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                }
            }
        },
    )
}
