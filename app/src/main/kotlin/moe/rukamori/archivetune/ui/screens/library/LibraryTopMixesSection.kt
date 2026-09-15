/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.viewmodels.LibraryTopMixUiModel
import moe.rukamori.archivetune.viewmodels.LibraryTopMixesUiState

fun LazyListScope.topMixesSection(
    uiState: LibraryTopMixesUiState,
    onRefreshTopMixes: () -> Unit,
    onConfigureAi: () -> Unit,
    onPlayMix: (LibraryTopMixUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    item(key = "top_mixes") {
        TopMixesForYouSection(
            state = uiState,
            onRefresh = onRefreshTopMixes,
            onConfigureAi = onConfigureAi,
            onPlayMix = onPlayMix,
            modifier = modifier,
        )
    }
}
