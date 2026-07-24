/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import javax.inject.Inject

class ObserveHomePresentationPreferencesUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        operator fun invoke(): Flow<HomePresentationPreferences> =
            combine(
                repository.showCategoryChips,
                repository.quickPicksDisplayMode,
                repository.showTonalBackdrop,
            ) { showCategoryChips, quickPicksDisplayMode, showTonalBackdrop ->
                HomePresentationPreferences(
                    showCategoryChips = showCategoryChips,
                    quickPicksDisplayMode = quickPicksDisplayMode,
                    showTonalBackdrop = showTonalBackdrop,
                )
            }
    }

@Immutable
data class HomePresentationPreferences(
    val showCategoryChips: Boolean,
    val quickPicksDisplayMode: QuickPicksDisplayMode,
    val showTonalBackdrop: Boolean,
)
