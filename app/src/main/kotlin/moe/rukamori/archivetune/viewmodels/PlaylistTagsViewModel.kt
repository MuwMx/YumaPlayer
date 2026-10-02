/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlisttags.AddTagsToPlaylistsUseCase
import moe.rukamori.archivetune.playlisttags.CreatePlaylistTagUseCase
import moe.rukamori.archivetune.playlisttags.DeletePlaylistTagUseCase
import moe.rukamori.archivetune.playlisttags.ObservePlaylistTagsUseCase
import moe.rukamori.archivetune.playlisttags.SavePlaylistTagsUseCase
import moe.rukamori.archivetune.playlisttags.UpdatePlaylistTagColorUseCase
import moe.rukamori.archivetune.playlisttags.UpdatePlaylistTagUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistTagsViewModel
    @Inject
    constructor(
        private val observePlaylistTags: ObservePlaylistTagsUseCase,
        internal val createPlaylistTag: CreatePlaylistTagUseCase,
        internal val updatePlaylistTag: UpdatePlaylistTagUseCase,
        internal val updatePlaylistTagColor: UpdatePlaylistTagColorUseCase,
        internal val deletePlaylistTag: DeletePlaylistTagUseCase,
        private val savePlaylistTags: SavePlaylistTagsUseCase,
        private val addTagsToPlaylists: AddTagsToPlaylistsUseCase,
    ) : ViewModel() {
        private val playlistId = MutableStateFlow<String?>(null)
        private val selectedTagIds = MutableStateFlow<Set<String>?>(null)
        private val selectedBulkTagIds = MutableStateFlow<Set<String>>(emptySet())
        private val selectedBulkPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
        private val isBulkAssignVisible = MutableStateFlow(false)
        private val isManagementVisible = MutableStateFlow(false)
        private val operationError = MutableStateFlow<Int?>(null)
        internal val editor = MutableStateFlow<PlaylistTagEditorState>(PlaylistTagEditorState.Hidden)
        internal val colorPicker = MutableStateFlow<PlaylistTagColorPickerState>(PlaylistTagColorPickerState.Hidden)
        private var writeJob: Job? = null

        private val controls =
            combine(
                selectedTagIds,
                selectedBulkTagIds,
                selectedBulkPlaylistIds,
                isBulkAssignVisible,
                operationError,
            ) { selectedTagIds, selectedBulkTagIds, selectedBulkPlaylistIds, isBulkAssignVisible, operationError ->
                PlaylistTagsControls(
                    selectedTagIds = selectedTagIds,
                    selectedBulkTagIds = selectedBulkTagIds,
                    selectedBulkPlaylistIds = selectedBulkPlaylistIds,
                    isBulkAssignVisible = isBulkAssignVisible,
                    operationErrorResId = operationError,
                )
            }

        val screenState: StateFlow<PlaylistTagsScreenState> =
            playlistId
                .flatMapLatest { currentPlaylistId -> observePlaylistTags(currentPlaylistId) }
                .combine(controls) { snapshot, controls ->
                    controls.operationErrorResId?.let { messageResId ->
                        return@combine PlaylistTagsScreenState.Error(messageResId)
                    }

                    val tags = snapshot.tags.mapTagsToUiModels()
                    val validTagIds = snapshot.tags.mapTo(LinkedHashSet()) { tag -> tag.id }
                    val validPlaylistIds = snapshot.playlists.mapTo(LinkedHashSet()) { playlist -> playlist.id }
                    val selected =
                        controls.selectedTagIds
                            ?.sanitize(validTagIds)
                            ?: snapshot.selectedTagIds.sanitize(validTagIds)

                    val success =
                        PlaylistTagsScreenState.Success(
                            tags = ImmutableList.copyOf(tags),
                            selectedTagIds = ImmutableSet.copyOf(selected),
                            playlists = ImmutableList.copyOf(snapshot.playlists.mapPlaylistsToUiModels()),
                            selectedBulkTagIds = ImmutableSet.copyOf(controls.selectedBulkTagIds.sanitize(validTagIds)),
                            selectedBulkPlaylistIds =
                                ImmutableSet.copyOf(
                                    controls.selectedBulkPlaylistIds.sanitize(validPlaylistIds),
                                ),
                            isBulkAssignVisible = controls.isBulkAssignVisible,
                        )

                    if (tags.isEmpty()) {
                        PlaylistTagsScreenState.Empty
                    } else {
                        success
                    }
                }.catch { emit(PlaylistTagsScreenState.Error(R.string.error_unknown)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = PlaylistTagsScreenState.Loading,
                )

        val editorState: StateFlow<PlaylistTagEditorState> = editor
        val colorPickerState: StateFlow<PlaylistTagColorPickerState> = colorPicker
        val managementVisible: StateFlow<Boolean> = isManagementVisible

        fun setPlaylistId(id: String?) {
            if (playlistId.value == id) return
            playlistId.value = id
            selectedTagIds.value = null
            selectedBulkTagIds.value = emptySet()
            selectedBulkPlaylistIds.value = id?.let(::setOf).orEmpty()
            isBulkAssignVisible.value = false
            isManagementVisible.value = false
        }

        fun toggleTagSelection(tagId: String) {
            val current = (screenState.value as? PlaylistTagsScreenState.Success)?.selectedTagIds?.toSet().orEmpty()
            selectedTagIds.value = current.toggle(tagId)
        }

        fun openBulkAssign() {
            val state = screenState.value as? PlaylistTagsScreenState.Success ?: return
            selectedBulkTagIds.value = state.selectedTagIds
            selectedBulkPlaylistIds.value = playlistId.value?.let(::setOf).orEmpty()
            isBulkAssignVisible.value = true
        }

        fun dismissBulkAssign() {
            isBulkAssignVisible.value = false
        }

        fun openManagement() {
            isManagementVisible.value = true
        }

        fun dismissManagement() {
            isManagementVisible.value = false
        }

        fun toggleBulkTag(tagId: String) {
            selectedBulkTagIds.update { current -> current.toggle(tagId) }
        }

        fun toggleBulkPlaylist(playlistId: String) {
            selectedBulkPlaylistIds.update { current -> current.toggle(playlistId) }
        }

        fun saveSelectedPlaylistTags(onSaved: () -> Unit) {
            val currentPlaylistId = playlistId.value ?: return
            val state = screenState.value as? PlaylistTagsScreenState.Success ?: return
            launchWrite {
                savePlaylistTags(
                    playlistId = currentPlaylistId,
                    tagIds = state.selectedTagIds,
                )
                selectedTagIds.value = null
                onSaved()
            }
        }

        fun saveBulkAssignments(onSaved: () -> Unit) {
            val state = screenState.value as? PlaylistTagsScreenState.Success ?: return
            if (state.selectedBulkPlaylistIds.isEmpty() || state.selectedBulkTagIds.isEmpty()) return
            launchWrite {
                addTagsToPlaylists(
                    playlistIds = state.selectedBulkPlaylistIds,
                    tagIds = state.selectedBulkTagIds,
                )
                isBulkAssignVisible.value = false
                onSaved()
            }
        }

        fun openCreateEditor() = handleOpenCreateEditor()

        fun openEditEditor(tagId: String) = handleOpenEditEditor(tagId)

        fun updateEditorName(name: String) = handleUpdateEditorName(name)

        fun dismissEditor() = handleDismissEditor()

        fun saveEditor() = handleSaveEditor()

        fun deleteTag(tagId: String) = handleDeleteTag(tagId)

        fun openEditorColorPicker() = handleOpenEditorColorPicker()

        fun openTagColorPicker(tagId: String) = handleOpenTagColorPicker(tagId)

        fun selectColor(color: String) = handleSelectColor(color)

        fun dismissColorPicker() = handleDismissColorPicker()

        internal fun launchWrite(block: suspend () -> Unit) {
            if (writeJob?.isActive == true) return
            writeJob =
                viewModelScope.launch {
                    try {
                        operationError.value = null
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        operationError.value = R.string.error_unknown
                    }
                }
        }

        internal fun currentTags(): List<PlaylistTagUiModel> =
            when (val state = screenState.value) {
                is PlaylistTagsScreenState.Success -> state.tags

                PlaylistTagsScreenState.Empty,
                is PlaylistTagsScreenState.Error,
                PlaylistTagsScreenState.Loading,
                -> emptyList()
            }
    }
