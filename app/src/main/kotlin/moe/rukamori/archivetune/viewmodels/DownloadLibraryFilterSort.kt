/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.downloads.DownloadLibraryUiModel
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel

internal fun DownloadLibraryUiModel.filteredBy(query: String): DownloadLibraryUiModel {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return this

    fun List<DownloadSectionUiModel>.filteredSections(): List<DownloadSectionUiModel> =
        mapNotNull { section ->
            section.entries
                .filter { entry ->
                    entry.title.contains(normalizedQuery, ignoreCase = true) ||
                        entry.supportingText.orEmpty().contains(normalizedQuery, ignoreCase = true)
                }.takeIf { it.isNotEmpty() }
                ?.let { entries -> section.copy(entries = entries) }
        }

    return copy(
        downloadedSections = downloadedSections.filteredSections(),
        progressSections = progressSections.filteredSections(),
    )
}

internal fun DownloadLibraryUiModel.sortedBy(sort: DownloadLibrarySort): DownloadLibraryUiModel {
    fun List<DownloadEntryUiModel>.sortedEntries(): List<DownloadEntryUiModel> {
        val comparator: Comparator<DownloadEntryUiModel> =
            when (sort.field) {
                DownloadLibrarySortField.NAME ->
                    Comparator { first, second ->
                        String.CASE_INSENSITIVE_ORDER.compare(first.title, second.title)
                    }

                DownloadLibrarySortField.DATE_MODIFIED -> compareBy(DownloadEntryUiModel::modifiedAtMs)
            }.thenBy(DownloadEntryUiModel::id)
        val sortedEntries = sortedWith(comparator)
        return if (sort.order == DownloadLibrarySortOrder.ASCENDING) sortedEntries else sortedEntries.asReversed()
    }

    fun List<DownloadSectionUiModel>.sortedSections(): List<DownloadSectionUiModel> =
        map { section ->
            section.copy(
                entries = section.entries.sortedEntries(),
            )
        }

    return copy(
        downloadedSections = downloadedSections.sortedSections(),
        progressSections = progressSections.sortedSections(),
    )
}
