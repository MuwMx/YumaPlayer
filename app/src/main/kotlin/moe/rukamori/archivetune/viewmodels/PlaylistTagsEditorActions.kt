/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import com.google.common.collect.ImmutableList
import kotlinx.coroutines.flow.update
import moe.rukamori.archivetune.db.entities.TagEntity

internal fun PlaylistTagsViewModel.handleOpenCreateEditor() {
    editor.value =
        PlaylistTagEditorState.Visible(
            tagId = null,
            name = "",
            color = TagEntity.DEFAULT_COLORS.first(),
            canSave = false,
        )
}

internal fun PlaylistTagsViewModel.handleOpenEditEditor(tagId: String) {
    val tag = currentTags().firstOrNull { currentTag -> currentTag.id == tagId } ?: return
    editor.value =
        PlaylistTagEditorState.Visible(
            tagId = tag.id,
            name = tag.name,
            color = tag.color,
            canSave = tag.name.isNotBlank(),
        )
}

internal fun PlaylistTagsViewModel.handleUpdateEditorName(name: String) {
    editor.update { current ->
        when (current) {
            PlaylistTagEditorState.Hidden -> {
                current
            }

            is PlaylistTagEditorState.Visible -> {
                current.copy(
                    name = name,
                    canSave = name.trim().isNotEmpty(),
                )
            }
        }
    }
}

internal fun PlaylistTagsViewModel.handleDismissEditor() {
    editor.value = PlaylistTagEditorState.Hidden
}

internal fun PlaylistTagsViewModel.handleSaveEditor() {
    val current = editor.value as? PlaylistTagEditorState.Visible ?: return
    if (!current.canSave) return

    launchWrite {
        if (current.tagId == null) {
            createPlaylistTag(name = current.name, color = current.color)
        } else {
            updatePlaylistTag(
                tagId = current.tagId,
                name = current.name,
                color = current.color,
            )
        }
        editor.value = PlaylistTagEditorState.Hidden
    }
}

internal fun PlaylistTagsViewModel.handleDeleteTag(tagId: String) {
    launchWrite {
        deletePlaylistTag(tagId = tagId)
    }
}

internal fun PlaylistTagsViewModel.handleOpenEditorColorPicker() {
    val current = editor.value as? PlaylistTagEditorState.Visible ?: return
    colorPicker.value =
        PlaylistTagColorPickerState.Visible(
            selectedColor = current.color,
            tagId = current.tagId,
            isEditorTarget = true,
            colors = ImmutableList.copyOf(TagEntity.DEFAULT_COLORS),
        )
}

internal fun PlaylistTagsViewModel.handleOpenTagColorPicker(tagId: String) {
    val tag = currentTags().firstOrNull { currentTag -> currentTag.id == tagId } ?: return
    colorPicker.value =
        PlaylistTagColorPickerState.Visible(
            selectedColor = tag.color,
            tagId = tag.id,
            isEditorTarget = false,
            colors = ImmutableList.copyOf(TagEntity.DEFAULT_COLORS),
        )
}

internal fun PlaylistTagsViewModel.handleSelectColor(color: String) {
    val current = colorPicker.value as? PlaylistTagColorPickerState.Visible ?: return
    if (current.isEditorTarget) {
        editor.update { editorState ->
            when (editorState) {
                PlaylistTagEditorState.Hidden -> editorState
                is PlaylistTagEditorState.Visible -> editorState.copy(color = color)
            }
        }
        colorPicker.value = PlaylistTagColorPickerState.Hidden
        return
    }

    val tagId = current.tagId ?: return
    launchWrite {
        updatePlaylistTagColor(
            tagId = tagId,
            color = color,
        )
        colorPicker.value = PlaylistTagColorPickerState.Hidden
    }
}

internal fun PlaylistTagsViewModel.handleDismissColorPicker() {
    colorPicker.value = PlaylistTagColorPickerState.Hidden
}
