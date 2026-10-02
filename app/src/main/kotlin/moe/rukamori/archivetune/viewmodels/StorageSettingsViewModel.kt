/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.storage.ClearStorageCacheUseCase
import moe.rukamori.archivetune.storage.ObserveStorageFoldersUseCase
import moe.rukamori.archivetune.storage.SetStorageFolderUseCase
import moe.rukamori.archivetune.storage.StorageCacheClearResult
import moe.rukamori.archivetune.storage.StorageCacheKind
import moe.rukamori.archivetune.storage.StorageFolderUpdateResult
import javax.inject.Inject

@HiltViewModel
class StorageSettingsViewModel
    @Inject
    constructor(
        observeStorageFolders: ObserveStorageFoldersUseCase,
        private val setStorageFolder: SetStorageFolderUseCase,
        private val clearStorageCache: ClearStorageCacheUseCase,
    ) : ViewModel() {
        private val _effects = MutableSharedFlow<StorageSettingsEffect>(extraBufferCapacity = 1)
        val effects = _effects.asSharedFlow()
        private val pickerState = MutableStateFlow(StorageLocationPickerUiModel())
        private val migrationState = MutableStateFlow<StorageMigrationUiModel?>(null)
        private val cacheClearState = MutableStateFlow<StorageCacheClearUiModel?>(null)
        private val activeCacheClearKinds = mutableSetOf<StorageCacheKind>()

        val state: StateFlow<StorageSettingsScreenState> =
            combine(
                observeStorageFolders(),
                pickerState,
                migrationState,
                cacheClearState,
            ) { selection, picker, migration, cacheClear ->
                val selectedOptionId =
                    picker.selectedOptionId
                        ?.takeIf { optionId ->
                            selection.options.firstOrNull { option -> option.id == optionId } != null
                        }
                        ?: selection.selectedOption.id
                val normalizedPicker = picker.copy(selectedOptionId = selectedOptionId)
                StorageSettingsStatePayload(
                    selection = selection,
                    picker = normalizedPicker,
                    migration = migration,
                    cacheClear = cacheClear,
                )
            }.map<StorageSettingsStatePayload, StorageSettingsScreenState> { payload ->
                StorageSettingsScreenState.Success(
                    StorageSettingsUiModel(
                        folder = payload.selection.toUiModel(),
                        storageOptions = payload.selection.options.toUiOptions(),
                        picker = payload.picker,
                        migration = payload.migration,
                        cacheClear = payload.cacheClear,
                    ),
                )
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(StorageSettingsScreenState.Error(R.string.error_unknown))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StorageSettingsScreenState.Loading,
            )

        fun openStorageLocationPicker() {
            val selectedOptionId =
                (state.value as? StorageSettingsScreenState.Success)
                    ?.model
                    ?.folder
                    ?.selectedOptionId
                    ?: return
            pickerState.value =
                StorageLocationPickerUiModel(
                    visible = true,
                    selectedOptionId = selectedOptionId,
                )
        }

        fun chooseStorageLocation(optionId: String) {
            pickerState.update { picker ->
                picker.copy(selectedOptionId = optionId)
            }
        }

        fun dismissStorageLocationPicker() {
            pickerState.update { picker ->
                picker.copy(visible = false)
            }
        }

        fun applyStorageLocationSelection() {
            val model = (state.value as? StorageSettingsScreenState.Success)?.model ?: return
            val optionId = model.picker.selectedOptionId ?: model.folder.selectedOptionId
            pickerState.update { picker ->
                picker.copy(visible = false)
            }
            selectStorageLocation(optionId)
        }

        fun clearSongCache(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.SONGS, showFeedback)
        }

        fun clearDownloads(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.DOWNLOADS, showFeedback)
        }

        fun clearImageCache(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.IMAGES, showFeedback)
        }

        fun clearCanvasCache(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.CANVAS, showFeedback)
        }

        private fun selectStorageLocation(optionId: String) {
            viewModelScope.launch(Dispatchers.IO) {
                migrationState.value =
                    StorageMigrationUiModel(
                        phase = StorageMigrationUiPhase.CACHE,
                        percent = 0,
                    )
                val result =
                    withContext(NonCancellable + Dispatchers.IO) {
                        setStorageFolder(optionId) { progress ->
                            migrationState.value = progress.toUiModel()
                        }
                    }
                migrationState.value = null
                val messageResId =
                    when (result) {
                        StorageFolderUpdateResult.Success -> R.string.storage_folder_selected_restart
                        StorageFolderUpdateResult.InvalidTree -> R.string.storage_folder_invalid
                        StorageFolderUpdateResult.UnsupportedProvider -> R.string.storage_folder_unsupported
                        StorageFolderUpdateResult.NotWritable -> R.string.storage_folder_not_writable
                    }
                _effects.emit(
                    StorageSettingsEffect(
                        messageResId = messageResId,
                        restartApp = result == StorageFolderUpdateResult.Success,
                    ),
                )
            }
        }

        private fun clearCache(
            kind: StorageCacheKind,
            showFeedback: Boolean,
        ) {
            synchronized(activeCacheClearKinds) {
                if (!activeCacheClearKinds.add(kind)) return
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (showFeedback) {
                        cacheClearState.value =
                            StorageCacheClearUiModel(
                                kind = kind.toUiKind(),
                                percent = 0,
                            )
                    }
                    val result =
                        clearStorageCache(kind) { progress ->
                            if (showFeedback) {
                                cacheClearState.value = progress.toUiModel()
                            }
                        }
                    if (showFeedback) {
                        val messageResId =
                            when (result) {
                                StorageCacheClearResult.Success -> R.string.storage_cache_cleared
                                StorageCacheClearResult.Failed -> R.string.storage_cache_clear_failed
                            }
                        _effects.emit(
                            StorageSettingsEffect(
                                messageResId = messageResId,
                                restartApp = false,
                            ),
                        )
                    }
                } finally {
                    synchronized(activeCacheClearKinds) {
                        activeCacheClearKinds.remove(kind)
                    }
                    if (showFeedback) {
                        cacheClearState.value = null
                    }
                }
            }
        }
    }
