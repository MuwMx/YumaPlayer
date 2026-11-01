/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.lazy.LazyListScope
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeUiState

internal fun LazyListScope.homeCategoryChipsSection(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    if (!uiState.showCategoryChips) return

    item(
        key = "home_category_chips",
        contentType = "category_chips",
    ) {
        val chips = uiState.homePage?.chips
        if (!chips.isNullOrEmpty()) {
            HomeCategoryChips(
                chips = chips,
                selectedChip = uiState.selectedChip,
                onChipSelected = { onAction(HomeAction.SelectChip(it)) },
            )
        }
    }
}