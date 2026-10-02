/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import moe.rukamori.archivetune.playlisttags.PlaylistTagModel
import moe.rukamori.archivetune.playlisttags.PlaylistTagPlaylistModel

sealed interface PlaylistTagsScreenState {
    data object Loading : PlaylistTagsScreenState

    @Immutable
    data class Success(
        val tags: ImmutableList<PlaylistTagUiModel>,
        val selectedTagIds: ImmutableSet<String>,
        val playlists: ImmutableList<PlaylistTagPlaylistUiModel>,
        val selectedBulkTagIds: ImmutableSet<String>,
        val selectedBulkPlaylistIds: ImmutableSet<String>,
        val isBulkAssignVisible: Boolean,
    ) : PlaylistTagsScreenState

    data object Empty : PlaylistTagsScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : PlaylistTagsScreenState
}

@Immutable
data class PlaylistTagUiModel(
    val id: String,
    val name: String,
    val color: String,
)

@Immutable
data class PlaylistTagPlaylistUiModel(
    val id: String,
    val name: String,
    val songCount: Int,
)

sealed interface PlaylistTagEditorState {
    data object Hidden : PlaylistTagEditorState

    @Immutable
    data class Visible(
        val tagId: String?,
        val name: String,
        val color: String,
        val canSave: Boolean,
    ) : PlaylistTagEditorState
}

sealed interface PlaylistTagColorPickerState {
    data object Hidden : PlaylistTagColorPickerState

    @Immutable
    data class Visible(
        val selectedColor: String,
        val tagId: String?,
        val isEditorTarget: Boolean,
        val colors: ImmutableList<String>,
    ) : PlaylistTagColorPickerState
}

internal data class PlaylistTagsControls(
    val selectedTagIds: Set<String>?,
    val selectedBulkTagIds: Set<String>,
    val selectedBulkPlaylistIds: Set<String>,
    val isBulkAssignVisible: Boolean,
    @StringRes val operationErrorResId: Int?,
)

internal fun List<PlaylistTagModel>.mapTagsToUiModels(): List<PlaylistTagUiModel> =
    map { tag ->
        PlaylistTagUiModel(
            id = tag.id,
            name = tag.name,
            color = tag.color,
        )
    }

internal fun List<PlaylistTagPlaylistModel>.mapPlaylistsToUiModels(): List<PlaylistTagPlaylistUiModel> =
    map { playlist ->
        PlaylistTagPlaylistUiModel(
            id = playlist.id,
            name = playlist.name,
            songCount = playlist.songCount,
        )
    }

internal fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) {
        this - value
    } else {
        this + value
    }

internal fun Set<String>.sanitize(validIds: Set<String>): Set<String> = filterTo(LinkedHashSet()) { id -> id in validIds }
