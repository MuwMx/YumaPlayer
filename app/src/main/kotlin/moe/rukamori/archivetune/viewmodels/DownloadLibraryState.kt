/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.downloads.DownloadLibraryUiModel
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.models.MediaMetadata

enum class DownloadLibraryTab {
    DOWNLOADED,
    PROGRESS,
}

enum class DownloadLibrarySortField {
    NAME,
    DATE_MODIFIED,
}

enum class DownloadLibrarySortOrder {
    ASCENDING,
    DESCENDING,
}

@Immutable
data class DownloadLibrarySort(
    val field: DownloadLibrarySortField = DownloadLibrarySortField.DATE_MODIFIED,
    val order: DownloadLibrarySortOrder = DownloadLibrarySortOrder.DESCENDING,
)

sealed interface DownloadLibraryScreenState {
    val pendingRemoval: DownloadRemovalConfirmation?
    val sort: DownloadLibrarySort

    data class Loading(
        val selectedTab: DownloadLibraryTab,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Success(
        val selectedTab: DownloadLibraryTab,
        val library: DownloadLibraryUiModel,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Empty(
        val selectedTab: DownloadLibraryTab,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Error(
        val selectedTab: DownloadLibraryTab,
        @StringRes val messageRes: Int,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState
}

@Immutable
sealed interface DownloadRemovalConfirmation {
    data class Entry(
        val entry: DownloadEntryUiModel,
    ) : DownloadRemovalConfirmation

    data class Section(
        val section: DownloadSectionUiModel,
    ) : DownloadRemovalConfirmation
}

sealed interface DownloadLibraryEvent {
    data class Message(
        @StringRes val messageRes: Int,
    ) : DownloadLibraryEvent

    data class Navigate(
        val route: String,
    ) : DownloadLibraryEvent

    data class PlaySong(
        val metadata: MediaMetadata,
        val queue: ImmutableList<MediaMetadata>,
        val startIndex: Int,
    ) : DownloadLibraryEvent
}

internal sealed interface DownloadLibraryResult {
    data class Data(
        val library: DownloadLibraryUiModel,
    ) : DownloadLibraryResult

    data object Failure : DownloadLibraryResult
}

internal data class DownloadLibraryScreenControls(
    val selectedTab: DownloadLibraryTab,
    val query: String,
    val isSearchActive: Boolean,
    val sort: DownloadLibrarySort,
    val pendingRemoval: DownloadRemovalConfirmation?,
)
