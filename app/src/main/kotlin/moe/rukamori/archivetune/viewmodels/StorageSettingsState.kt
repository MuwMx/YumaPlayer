/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.storage.StorageCacheClearProgress
import moe.rukamori.archivetune.storage.StorageCacheKind
import moe.rukamori.archivetune.storage.StorageFolderSelection
import moe.rukamori.archivetune.storage.StorageLocationKind
import moe.rukamori.archivetune.storage.StorageLocationOption
import moe.rukamori.archivetune.storage.StorageLocationOptions
import moe.rukamori.archivetune.storage.StorageMigrationPhase
import moe.rukamori.archivetune.storage.StorageMigrationProgress

sealed interface StorageSettingsScreenState {
    data object Loading : StorageSettingsScreenState

    data class Success(
        val model: StorageSettingsUiModel,
    ) : StorageSettingsScreenState

    data object Empty : StorageSettingsScreenState

    data class Error(
        val messageResId: Int,
    ) : StorageSettingsScreenState
}

@Immutable
data class StorageSettingsUiModel(
    val folder: StorageFolderUiModel,
    val storageOptions: StorageLocationUiOptions,
    val picker: StorageLocationPickerUiModel,
    val migration: StorageMigrationUiModel?,
    val cacheClear: StorageCacheClearUiModel?,
)

@Immutable
data class StorageFolderUiModel(
    val selectedOptionId: String,
    val kind: StorageLocationKind,
    val volumeLabel: String?,
    val availableBytes: Long,
)

@Immutable
data class StorageLocationUiOptions(
    private val values: List<StorageLocationUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): StorageLocationUiModel = values[index]

    fun firstOrNull(predicate: (StorageLocationUiModel) -> Boolean): StorageLocationUiModel? = values.firstOrNull(predicate)

    fun forEach(action: (StorageLocationUiModel) -> Unit) {
        values.forEach(action)
    }
}

@Immutable
data class StorageLocationUiModel(
    val id: String,
    val kind: StorageLocationKind,
    val volumeLabel: String?,
    val availableBytes: Long,
    val isSelected: Boolean,
)

@Immutable
data class StorageLocationPickerUiModel(
    val visible: Boolean = false,
    val selectedOptionId: String? = null,
)

@Immutable
data class StorageMigrationUiModel(
    val phase: StorageMigrationUiPhase,
    val percent: Int,
)

enum class StorageMigrationUiPhase {
    CACHE,
    DOWNLOADS,
}

@Immutable
data class StorageCacheClearUiModel(
    val kind: StorageCacheClearUiKind,
    val percent: Int,
)

enum class StorageCacheClearUiKind {
    SONGS,
    DOWNLOADS,
    IMAGES,
    CANVAS,
}

@Immutable
data class StorageSettingsEffect(
    val messageResId: Int,
    val restartApp: Boolean,
)

internal data class StorageSettingsStatePayload(
    val selection: StorageFolderSelection,
    val picker: StorageLocationPickerUiModel,
    val migration: StorageMigrationUiModel?,
    val cacheClear: StorageCacheClearUiModel?,
)

internal fun StorageFolderSelection.toUiModel(): StorageFolderUiModel =
    StorageFolderUiModel(
        selectedOptionId = selectedOption.id,
        kind = selectedOption.kind,
        volumeLabel = selectedOption.volumeLabel,
        availableBytes = selectedOption.availableBytes,
    )

internal fun StorageLocationOptions.toUiOptions(): StorageLocationUiOptions {
    val items = mutableListOf<StorageLocationUiModel>()
    forEach { option ->
        items += option.toUiModel()
    }
    return StorageLocationUiOptions(items)
}

internal fun StorageLocationOption.toUiModel(): StorageLocationUiModel =
    StorageLocationUiModel(
        id = id,
        kind = kind,
        volumeLabel = volumeLabel,
        availableBytes = availableBytes,
        isSelected = isSelected,
    )

internal fun StorageMigrationProgress.toUiModel(): StorageMigrationUiModel =
    StorageMigrationUiModel(
        phase =
            when (phase) {
                StorageMigrationPhase.CACHE -> StorageMigrationUiPhase.CACHE
                StorageMigrationPhase.DOWNLOADS -> StorageMigrationUiPhase.DOWNLOADS
            },
        percent = percent.coerceIn(0, 100),
    )

internal fun StorageCacheClearProgress.toUiModel(): StorageCacheClearUiModel =
    StorageCacheClearUiModel(
        kind = kind.toUiKind(),
        percent = percent.coerceIn(0, 100),
    )

internal fun StorageCacheKind.toUiKind(): StorageCacheClearUiKind =
    when (this) {
        StorageCacheKind.SONGS -> StorageCacheClearUiKind.SONGS
        StorageCacheKind.DOWNLOADS -> StorageCacheClearUiKind.DOWNLOADS
        StorageCacheKind.IMAGES -> StorageCacheClearUiKind.IMAGES
        StorageCacheKind.CANVAS -> StorageCacheClearUiKind.CANVAS
    }
