/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder

/**
 * The scaffold tail every route renders after its LazyColumn:
 * [PlaylistDetailOverlays] (scrollbar / haze / download toolbar / snackbar)
 * then [PlaylistDetailTopBarHost]. On the local route the dialogs host
 * (edit chooser / rename / delete) rides along and the trailing action
 * is sync-only for non-editable playlists.
 *
 * Extracted from [PlaylistDetailLocalGlue] to keep that file under 250 lines
 * (`YUMA_RULES.md` §1.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.PlaylistDetailScaffoldTail(
    viewModel: PlaylistDetailViewModel,
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    menuState: MenuState,
    navController: NavController,
    lazyListState: LazyListState,
    headerItems: Int,
    headerProgress: () -> Float,
    hasArtworkBackdrop: Boolean,
    hazeState: HazeState,
    downloadHolder: PlaylistDownloadStateHolder,
    tracks: List<PlaylistTrack>,
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbarHostState: SnackbarHostState,
    localGlue: LocalPlaylistGlue?,
    editable: Boolean?,
    likeSourceHint: moe.rukamori.archivetune.constants.LikeSource?,
) {
    PlaylistDetailOverlays(
        lazyListState = lazyListState,
        headerItems = headerItems,
        headerProgress = headerProgress,
        hasArtworkBackdrop = hasArtworkBackdrop,
        hazeState = hazeState,
        downloadHolder = downloadHolder,
        songIds = tracks.map { it.id },
        snackbarHostState = snackbarHostState,
    )

    PlaylistDetailTopBarHost(
        searchState = searchState,
        menuState = menuState,
        navController = navController,
        lazyListState = lazyListState,
        title = title,
        scrollBehavior = scrollBehavior,
        onRemoveSongFromCache = onPlaylistDetailRemoveSongFromCache(viewModel),
        likeSourceHint = likeSourceHint,
        // LOCAL PLAYLIST SLOT — trailing edit/sync + rename/delete dialogs
        // (plain trailing action on other routes).
        trailingAction =
            localPlaylistTrailingAndDialogsOrUnified(
                viewModel, menuState, tracks, viewModel.uiState.value.header,
                localGlue, editable,
                onClearSelection = { searchState.clearSelection() },
                onStartSelection = { searchState.selection = true },
            ),
    )
}
