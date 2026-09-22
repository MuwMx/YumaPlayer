/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.search

import moe.rukamori.archivetune.data.repository.SettingsRepository
import moe.rukamori.archivetune.repository.SearchHistoryRepository
import javax.inject.Inject

class AddSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(query: String) {
        if (query.isNotEmpty() && !settingsRepository.isSearchHistoryPaused()) {
            searchHistoryRepository.insertSearchHistory(query)
        }
    }
}
