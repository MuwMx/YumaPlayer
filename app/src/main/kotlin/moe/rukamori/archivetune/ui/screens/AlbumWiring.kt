/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.media3.exoplayer.offline.Download
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.viewmodels.AlbumUiState

@Immutable
data class AlbumScreenUiState(
    val albumWithSongs: AlbumWithSongs? = null,
    val uiState: AlbumUiState = AlbumUiState.Loading,
    val otherVersions: List<AlbumItem> = emptyList(),
)
@Composable
fun rememberAlbumUiState(
    albumWithSongs: AlbumWithSongs?,
    uiState: AlbumUiState,
    otherVersions: List<AlbumItem>,
): AlbumScreenUiState {
    return remember(albumWithSongs, uiState, otherVersions) {
        AlbumScreenUiState(
            albumWithSongs = albumWithSongs,
            uiState = uiState,
            otherVersions = otherVersions,
        )
    }
}
@Stable
class AlbumSelectionState(
    selectionState: MutableState<Boolean>,
    val wrappedSongs: SnapshotStateList<WrappedSong>,
) {
    var selection by selectionState

    val selectedCount: Int
        get() = wrappedSongs.count { it.isSelected }

    fun toggleSelectAll() {
        val count = selectedCount
        if (count == wrappedSongs.size) {
            wrappedSongs.forEach { it.isSelected = false }
        } else {
            wrappedSongs.forEach { it.isSelected = true }
        }
    }

    fun clearSelection() {
        selection = false
    }
}
@Composable
fun rememberAlbumSelectionState(
    albumWithSongs: AlbumWithSongs?,
    hideExplicit: Boolean,
): AlbumSelectionState {
    val wrappedSongs =
        remember(albumWithSongs, hideExplicit) {
            val filteredSongs =
                if (hideExplicit) {
                    albumWithSongs?.songs?.filter { !it.song.explicit } ?: emptyList()
                } else {
                    albumWithSongs?.songs ?: emptyList()
                }
            filteredSongs.map { item -> ItemWrapper(item) }.toMutableStateList()
        }

    val selectionState = remember { mutableStateOf(false) }

    if (selectionState.value) {
        BackHandler {
            selectionState.value = false
        }
    }

    return remember(wrappedSongs) {
        AlbumSelectionState(
            selectionState = selectionState,
            wrappedSongs = wrappedSongs,
        )
    }
}
@Stable
class AlbumDownloadUiState(
    downloadsState: MutableState<Map<String, Download>>,
    downloadStateState: MutableState<HeaderDownloadState>,
    downloadsPausedState: MutableState<Boolean>,
    dismissedState: MutableState<Boolean>,
) {
    var downloads by downloadsState
    var downloadState by downloadStateState
    var downloadsPaused by downloadsPausedState
    var dismissed by dismissedState
}
@Composable
fun rememberAlbumDownloadUiState(): AlbumDownloadUiState {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val downloadsPaused = remember { mutableStateOf(false) }
    val dismissed = remember { mutableStateOf(true) }
    return remember {
        AlbumDownloadUiState(
            downloadsState = downloads,
            downloadStateState = downloadState,
            downloadsPausedState = downloadsPaused,
            dismissedState = dismissed,
        )
    }
}
@Composable
fun AlbumCoverGradientEffect(
    thumbnailUrl: String?,
    context: Context,
    fallbackColor: Int,
    onColorsExtracted: (List<Color>) -> Unit,
) {
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                    .allowHardware(false)
                    .build()

            val result =
                runCatching {
                    context.imageLoader.execute(request)
                }.getOrNull()

            if (result != null) {
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette =
                        withContext(Dispatchers.Default) {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }

                    val extractedColors =
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    onColorsExtracted(extractedColors)
                }
            }
        } else {
            onColorsExtracted(emptyList())
        }
    }
}
@Composable
fun rememberAlbumGradientColors(
    thumbnailUrl: String?,
    context: Context,
    fallbackColor: Int = MaterialTheme.colorScheme.surface.toArgb(),
): List<Color> {
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    AlbumCoverGradientEffect(thumbnailUrl, context, fallbackColor) {
        gradientColors = it
    }
    return gradientColors
}
@Composable
fun AlbumDownloadSyncEffect(
    albumWithSongs: AlbumWithSongs?,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
) {
    LaunchedEffect(albumWithSongs) {
        val songIds = albumWithSongs?.songs?.map { it.id }.orEmpty()
        if (songIds.isEmpty()) {
            onDownloadsChange(emptyMap())
            onDownloadStateChange(HeaderDownloadState.None)
            return@LaunchedEffect
        }
        downloadUtil.downloads.collect { currentDownloads ->
            onDownloadsChange(currentDownloads)
            onDownloadStateChange(headerDownloadState(songIds, currentDownloads))
        }
    }
}
@Composable
fun AlbumDownloadPausedEffect(
    downloadState: HeaderDownloadState,
    onResetPaused: () -> Unit,
) {
    LaunchedEffect(downloadState) {
        if (downloadState !is HeaderDownloadState.Partial) {
            onResetPaused()
        }
    }
}
