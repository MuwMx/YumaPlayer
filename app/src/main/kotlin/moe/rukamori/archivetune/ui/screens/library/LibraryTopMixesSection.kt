package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.viewmodels.LibraryTopMixUiModel
import moe.rukamori.archivetune.viewmodels.LibraryTopMixesUiState

fun LazyListScope.topMixesSection(
    uiState: LibraryTopMixesUiState,
    isRefreshing: Boolean = false,
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
