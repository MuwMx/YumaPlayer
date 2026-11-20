/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadProgressToolbar
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder

/**
 * The non-list overlays shared by every playlist source: fast-scroll
 * scrollbar, header haze backdrop, download progress toolbar and snackbar.
 * Extracted from PlaylistDetailScreen to keep that file under the
 * 250-line rule.
 */
@Composable
internal fun BoxScope.PlaylistDetailOverlays(
    lazyListState: LazyListState,
    headerItems: Int,
    headerProgress: () -> Float,
    hasArtworkBackdrop: Boolean,
    hazeState: HazeState,
    downloadHolder: PlaylistDownloadStateHolder,
    songIds: List<String>,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    DraggableScrollbar(
        modifier = modifier
            .padding(
                LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            ).align(Alignment.CenterEnd),
        scrollState = lazyListState,
        headerItems = headerItems,
    )

    HeaderHazeBackdrop(
        progress = headerProgress,
        hasCollapsibleHeader = hasArtworkBackdrop,
        hazeState = hazeState,
        modifier = Modifier.align(Alignment.TopCenter),
    )

    PlaylistDownloadProgressToolbar(
        holder = downloadHolder,
        songIds = songIds,
        context = context,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                .padding(bottom = 16.dp),
    )

    SnackbarHost(
        hostState = snackbarHostState,
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter),
    )
}
