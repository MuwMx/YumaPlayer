/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalMixQueue
import moe.rukamori.archivetune.ui.component.AssignTagsDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EditPlaylistDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.menu.removeSongFromRemotePlaylist
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@Composable
fun rememberLocalPlaylistUiState(
    playlist: Playlist?,
    songs: List<PlaylistSong>,
    sortType: PlaylistSongSortType,
    sortDescending: Boolean,
    viewCounts: Map<String, Int>,
    isRefreshing: Boolean,
): LocalPlaylistUiState =
    remember(playlist, songs, sortType, sortDescending, viewCounts, isRefreshing) {
        LocalPlaylistUiState(
            playlist = playlist,
            songs = songs,
            sortType = sortType,
            sortDescending = sortDescending,
            viewCounts = viewCounts,
            isRefreshing = isRefreshing,
        )
    }

@Stable
class LocalPlaylistSearchState(
    isSearchingState: androidx.compose.runtime.MutableState<Boolean>,
    queryState: androidx.compose.runtime.MutableState<TextFieldValue>,
    selectionState: androidx.compose.runtime.MutableState<Boolean>,
    selectedSongMapIdsState: androidx.compose.runtime.MutableState<Set<Int>>,
    val focusRequester: FocusRequester,
    val filteredSongs: List<PlaylistSong>,
    val visibleSongMapIds: Set<Int>,
    val selectedPlaylistSongs: List<PlaylistSong>,
) {
    var isSearching by isSearchingState
    var query by queryState
    var selection by selectionState
    var selectedSongMapIds by selectedSongMapIdsState
}

@Composable
fun rememberLocalPlaylistSearchState(songs: List<PlaylistSong>): LocalPlaylistSearchState {
    val isSearchingState = rememberSaveable { mutableStateOf(false) }
    val queryState = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val selectionState = remember { mutableStateOf(false) }
    val selectedSongMapIdsState = remember { mutableStateOf(emptySet<Int>()) }
    val focusRequester = remember { FocusRequester() }

    val query = queryState.value
    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title.contains(query.text, ignoreCase = true) ||
                        song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }
    val visibleSongMapIds = remember(filteredSongs) { filteredSongs.map { it.map.id }.toSet() }
    val selectedPlaylistSongs =
        remember(filteredSongs, selectedSongMapIdsState.value) {
            filteredSongs.filter { it.map.id in selectedSongMapIdsState.value }
        }

    if (isSearchingState.value) {
        BackHandler {
            isSearchingState.value = false
            queryState.value = TextFieldValue()
        }
    } else if (selectionState.value) {
        BackHandler { selectionState.value = false }
    }

    return remember(filteredSongs, visibleSongMapIds, selectedPlaylistSongs) {
        LocalPlaylistSearchState(
            isSearchingState = isSearchingState,
            queryState = queryState,
            selectionState = selectionState,
            selectedSongMapIdsState = selectedSongMapIdsState,
            focusRequester = focusRequester,
            filteredSongs = filteredSongs,
            visibleSongMapIds = visibleSongMapIds,
            selectedPlaylistSongs = selectedPlaylistSongs,
        )
    }
}

@Stable
class LocalPlaylistDownloadUiState(
    downloadsState: androidx.compose.runtime.MutableState<Map<String, Download>>,
    downloadStateState: androidx.compose.runtime.MutableState<HeaderDownloadState>,
    downloadsPausedState: androidx.compose.runtime.MutableState<Boolean>,
    dismissedState: androidx.compose.runtime.MutableState<Boolean>,
    val mutableSongs: SnapshotStateList<PlaylistSong>,
) {
    var downloads by downloadsState
    var downloadState by downloadStateState
    var downloadsPaused by downloadsPausedState
    var dismissed by dismissedState
}

@Composable
fun rememberLocalPlaylistDownloadUiState(): LocalPlaylistDownloadUiState {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val downloadsPaused = remember { mutableStateOf(false) }
    val dismissed = remember { mutableStateOf(true) }
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    return remember {
        LocalPlaylistDownloadUiState(
            downloadsState = downloads,
            downloadStateState = downloadState,
            downloadsPausedState = downloadsPaused,
            dismissedState = dismissed,
            mutableSongs = mutableSongs,
        )
    }
}

@Composable
fun rememberLocalPlaylistGradientState(
    thumbnails: List<String>?,
    context: Context,
    fallbackColor: Int,
    lazyListState: LazyListState,
): Pair<List<Color>, Float> {
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    LocalPlaylistCoverGradientEffect(thumbnails, context, fallbackColor) { gradientColors = it }
    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
    return gradientColors to gradientAlpha
}

fun commitLocalPlaylistReorder(
    coroutineScope: CoroutineScope,
    database: MusicDatabase,
    playlistId: String,
    browseId: String?,
    from: Int,
    to: Int,
    orderedBeforeMove: List<PlaylistSong>,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    val movedSetVideoId = orderedBeforeMove.getOrNull(from)?.map?.setVideoId
    val successorIndex = if (from > to) to else to + 1
    val successorSetVideoId = orderedBeforeMove.getOrNull(successorIndex)?.map?.setVideoId

    coroutineScope.launch(Dispatchers.IO) {
        database.withTransaction {
            move(playlistId, from, to)
        }

        if (browseId != null && movedSetVideoId != null) {
            runCatching {
                YouTube
                    .moveSongPlaylist(
                        browseId,
                        movedSetVideoId,
                        successorSetVideoId,
                    ).getOrThrow()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.error_unknown),
                        withDismissAction = true,
                    )
                }
            }
        }
    }
}

fun deleteSongFromLocalPlaylist(
    coroutineScope: CoroutineScope,
    database: MusicDatabase,
    browseId: String?,
    song: PlaylistSong,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    val map = song.map
    coroutineScope.launch(Dispatchers.IO) {
        if (browseId != null) {
            val remoteResult = removeSongFromRemotePlaylist(browseId, map)
            if (remoteResult.isFailure) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.error_unknown),
                        withDismissAction = true,
                    )
                }
                return@launch
            }
        }
        database.withTransaction {
            move(map.playlistId, map.position, Int.MAX_VALUE)
            delete(map.copy(position = Int.MAX_VALUE))
        }
    }
}

fun deleteSongFromLocalPlaylistSelected(
    coroutineScope: CoroutineScope,
    database: MusicDatabase,
    song: PlaylistSong,
) {
    val map = song.map
    coroutineScope.launch(Dispatchers.IO) {
        database.withTransaction {
            move(map.playlistId, map.position, Int.MAX_VALUE)
            delete(map.copy(position = Int.MAX_VALUE))
        }
    }
}

fun deleteLocalPlaylistSong(
    coroutineScope: CoroutineScope,
    database: MusicDatabase,
    browseId: String?,
    song: PlaylistSong,
    selection: Boolean,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    if (!selection) {
        deleteSongFromLocalPlaylist(coroutineScope, database, browseId, song, snackbarHostState, context)
    } else {
        deleteSongFromLocalPlaylistSelected(coroutineScope, database, song)
    }
}

fun syncLocalPlaylist(
    coroutineScope: CoroutineScope,
    database: MusicDatabase,
    playlist: Playlist?,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    val browseId = playlist?.playlist?.browseId
    val pId = playlist?.id
    if (browseId != null && pId != null) {
        coroutineScope.launch(Dispatchers.IO) {
            val playlistPage =
                YouTube
                    .playlist(browseId)
                    .completed()
                    .getOrNull() ?: return@launch
            database.transaction {
                clearPlaylist(pId)
                playlistPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .mapIndexed { position, song ->
                        PlaylistSongMap(
                            songId = song.id,
                            playlistId = pId,
                            position = position,
                            setVideoId = song.setVideoId,
                        )
                    }.forEach(::insert)
            }
        }
        coroutineScope.launch(Dispatchers.Main) {
            snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
        }
    }
}

fun toggleLocalPlaylistLike(
    database: MusicDatabase,
    playlist: Playlist?,
) {
    playlist?.let { p ->
        database.transaction {
            update(p.playlist.toggleLike())
        }
    }
}

fun renameLocalPlaylist(
    database: MusicDatabase,
    playlistData: Playlist,
    name: String,
    scope: CoroutineScope,
) {
    database.query {
        update(
            playlistData.playlist.copy(
                name = name,
                lastUpdateTime = LocalDateTime.now(),
            ),
        )
    }
    scope.launch(Dispatchers.IO) {
        playlistData.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
    }
}

fun deleteLocalPlaylist(
    database: MusicDatabase,
    playlist: Playlist?,
    scope: CoroutineScope,
    onDeleted: () -> Unit,
) {
    database.query {
        playlist?.let { delete(it.playlist) }
    }
    scope.launch(Dispatchers.IO) {
        playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
    }
    onDeleted()
}

fun updateLocalPlaylistCover(
    context: Context,
    database: MusicDatabase,
    playlist: Playlist?,
    uri: Uri?,
) {
    if (uri == null) return
    val oldUriString = playlist?.playlist?.thumbnailUrl
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    if (!oldUriString.isNullOrBlank() && oldUriString != uri.toString()) {
        val oldUri = runCatching { Uri.parse(oldUriString) }.getOrNull()
        if (oldUri?.scheme == "content") {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
    val newUriString = uri.toString()
    playlist?.let { p ->
        database.query {
            update(
                p.playlist.copy(
                    thumbnailUrl = newUriString,
                    lastUpdateTime = LocalDateTime.now(),
                ),
            )
        }
    }
}

fun removeLocalPlaylistDownloads(
    context: Context,
    database: MusicDatabase,
    playlist: Playlist?,
    songs: List<PlaylistSong>,
    editable: Boolean,
) {
    if (!editable) {
        database.transaction {
            playlist?.id?.let { clearPlaylist(it) }
        }
    }
    sendRemoveDownloads(
        context = context,
        songIds = songs.map { it.song.id },
    )
}

fun startLocalPlaylistDownloads(
    context: Context,
    songs: List<PlaylistSong>,
    downloads: Map<String, Download>,
) {
    sendAddMissingDownloads(
        context = context,
        songs = songs.map { HeaderDownloadItem(id = it.song.id, title = it.song.song.title) },
        downloads = downloads,
    )
}

fun handleSelectionSongClick(
    index: Int,
    song: PlaylistSong,
    selection: Boolean,
    selectedSongMapIds: Set<Int>,
    mediaMetadataId: String?,
    playerConnection: PlayerConnection,
    playlistTitle: String,
    songs: List<PlaylistSong>,
    onSelectedSongMapIdsChange: (Set<Int>) -> Unit,
) {
    if (!selection) {
        if (song.song.id == mediaMetadataId) {
            playerConnection.player.togglePlayPause()
        } else {
            playerConnection.playQueue(
                ListQueue(
                    title = playlistTitle,
                    items = songs.map { it.song.toMediaItem() },
                    startIndex = index,
                ),
            )
        }
    } else {
        onSelectedSongMapIdsChange(
            if (song.map.id in selectedSongMapIds) {
                selectedSongMapIds - song.map.id
            } else {
                selectedSongMapIds + song.map.id
            },
        )
    }
}

fun LazyListScope.localPlaylistSongs(
    searchState: LocalPlaylistSearchState,
    downloadUiState: LocalPlaylistDownloadUiState,
    reorderableState: ReorderableLazyListState,
    lazyListState: LazyListState,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    uiState: LocalPlaylistUiState,
    locked: Boolean,
    editable: Boolean,
    swipeToSongEnabled: Boolean,
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    database: MusicDatabase,
    snackbarHostState: SnackbarHostState,
    context: Context,
    playerConnection: PlayerConnection,
    haptics: YumaHaptics,
) {
    localPlaylistSongs(
        songs = if (searchState.selection || searchState.isSearching) searchState.filteredSongs else downloadUiState.mutableSongs,
        reorderableState = reorderableState,
        lazyListState = lazyListState,
        selection = searchState.selection,
        selectedSongMapIds = searchState.selectedSongMapIds,
        mediaMetadataId = mediaMetadataId,
        isPlaying = isPlaying,
        viewCounts = uiState.viewCounts,
        sortType = uiState.sortType,
        locked = locked,
        isSearching = searchState.isSearching,
        editable = editable,
        swipeToSongEnabled = swipeToSongEnabled,
        navController = navController,
        menuState = menuState,
        playlistBrowseId = uiState.playlist?.playlist?.browseId,
        onDeleteSong = { song ->
            deleteLocalPlaylistSong(
                coroutineScope,
                database,
                uiState.playlist?.playlist?.browseId,
                song,
                searchState.selection,
                snackbarHostState,
                context,
            )
        },
        onSongClick = { index, song ->
            handleSelectionSongClick(
                index = index,
                song = song,
                selection = searchState.selection,
                selectedSongMapIds = searchState.selectedSongMapIds,
                mediaMetadataId = mediaMetadataId,
                playerConnection = playerConnection,
                playlistTitle = uiState.playlist?.playlist?.name.orEmpty(),
                songs = uiState.songs,
                onSelectedSongMapIdsChange = { searchState.selectedSongMapIds = it },
            )
        },
        onSongLongClick = { song ->
            haptics.longPress()
            if (!searchState.selection) searchState.selection = true
            searchState.selectedSongMapIds = setOf(song.map.id)
        },
    )
}

@Composable
fun rememberLocalPlaylistActions(
    playlist: Playlist?,
    songs: List<PlaylistSong>,
    playlistId: String,
    playerConnection: PlayerConnection,
    mediaMetadataId: String?,
    database: MusicDatabase,
    downloadState: HeaderDownloadState,
    downloads: Map<String, Download>,
    editable: Boolean,
    context: Context,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onPickCover: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onEditPlaylist: () -> Unit,
    onRemoveDownloadDialog: () -> Unit,
    onStartDownload: () -> Unit,
    onSortTypeChange: (PlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLockToggle: () -> Unit,
    onSongLongClick: (PlaylistSong) -> Unit = {},
    onMenu: (PlaylistSong) -> Unit = {},
): LocalPlaylistActions =
    remember(
        playlist,
        songs,
        playlistId,
        playerConnection,
        mediaMetadataId,
        database,
        downloadState,
        downloads,
        editable,
        context,
        coroutineScope,
        snackbarHostState,
    ) {
        LocalPlaylistActions(
            onPlay = {
                playlist?.let { p ->
                    playerConnection.playQueue(
                        ListQueue(
                            title = p.playlist.name,
                            items = songs.map { it.song.toMediaItem() },
                        ),
                    )
                }
            },
            onShuffle = {
                playlist?.let { p ->
                    playerConnection.playQueue(
                        ListQueue(
                            title = p.playlist.name,
                            items = songs.shuffled().map { it.song.toMediaItem() },
                        ),
                    )
                }
            },
            onMix = {
                playlist?.let { p ->
                    playerConnection.playQueue(
                        LocalMixQueue(
                            database = database,
                            playlistId = p.id,
                            maxMixSize = 50,
                        ),
                    )
                }
            },
            onSongClick = { index, song ->
                if (song.song.id == mediaMetadataId) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist?.playlist?.name.orEmpty(),
                            items = songs.map { it.song.toMediaItem() },
                            startIndex = songs.indexOfFirst { it.map.id == song.map.id }.takeIf { it >= 0 } ?: index,
                        ),
                    )
                }
            },
            onSongLongClick = onSongLongClick,
            onMenu = onMenu,
            onSort = { sortType, descending ->
                onSortTypeChange(sortType)
                onSortDescendingChange(descending)
            },
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            onRefresh = onRefresh,
            onReorderCommit = { from, to ->
                commitLocalPlaylistReorder(
                    coroutineScope = coroutineScope,
                    database = database,
                    playlistId = playlistId,
                    browseId = playlist?.playlist?.browseId,
                    from = from,
                    to = to,
                    orderedBeforeMove = songs,
                    snackbarHostState = snackbarHostState,
                    context = context,
                )
            },
            onDownload = {
                when (downloadState) {
                    HeaderDownloadState.Completed -> {
                        onRemoveDownloadDialog()
                    }

                    else -> {
                        onStartDownload()
                    }
                }
            },
            onEdit = onEditPlaylist,
            onDelete = onDeletePlaylist,
            onToggleLike = {
                toggleLocalPlaylistLike(database, playlist)
            },
            onSync = {
                syncLocalPlaylist(
                    coroutineScope = coroutineScope,
                    database = database,
                    playlist = playlist,
                    snackbarHostState = snackbarHostState,
                    context = context,
                )
            },
            onPickCover = onPickCover,
            onLockToggle = onLockToggle,
        )
    }

class LocalPlaylistDialogState {
    var showAssignTags by mutableStateOf(false)
    var showEdit by mutableStateOf(false)
    var showRemoveDownload by mutableStateOf(false)
    var showDelete by mutableStateOf(false)
}

@Composable
fun rememberLocalPlaylistDialogState(): LocalPlaylistDialogState = remember { LocalPlaylistDialogState() }

@Composable
fun rememberLocalPlaylistActions(
    playlist: Playlist?,
    songs: List<PlaylistSong>,
    playlistId: String,
    playerConnection: PlayerConnection,
    mediaMetadataId: String?,
    database: MusicDatabase,
    downloadState: HeaderDownloadState,
    downloads: Map<String, Download>,
    editable: Boolean,
    context: Context,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    dialogState: LocalPlaylistDialogState,
    onPickCover: () -> Unit,
    onStartDownload: () -> Unit,
    onSortTypeChange: (PlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLockToggle: () -> Unit,
    onSongLongClick: (PlaylistSong) -> Unit = {},
    onMenu: (PlaylistSong) -> Unit = {},
): LocalPlaylistActions =
    rememberLocalPlaylistActions(
        playlist = playlist,
        songs = songs,
        playlistId = playlistId,
        playerConnection = playerConnection,
        mediaMetadataId = mediaMetadataId,
        database = database,
        downloadState = downloadState,
        downloads = downloads,
        editable = editable,
        context = context,
        coroutineScope = coroutineScope,
        snackbarHostState = snackbarHostState,
        onPickCover = onPickCover,
        onDeletePlaylist = { dialogState.showDelete = true },
        onEditPlaylist = { dialogState.showEdit = true },
        onRemoveDownloadDialog = { dialogState.showRemoveDownload = true },
        onStartDownload = onStartDownload,
        onSortTypeChange = onSortTypeChange,
        onSortDescendingChange = onSortDescendingChange,
        onRefresh = onRefresh,
        onLockToggle = onLockToggle,
        onSongLongClick = onSongLongClick,
        onMenu = onMenu,
    )

@Composable
fun rememberLocalPlaylistReorderableState(
    lazyListState: LazyListState,
    headerItems: Int,
    mutableSongs: SnapshotStateList<PlaylistSong>,
    dragInfo: Pair<Int, Int>?,
    onDragInfoChange: (Pair<Int, Int>?) -> Unit,
) = rememberReorderableLazyListState(
    lazyListState = lazyListState,
    scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
) { from, to ->
    if (to.index >= headerItems && from.index >= headerItems) {
        val newDragInfo =
            if (dragInfo == null) {
                (from.index - headerItems) to (to.index - headerItems)
            } else {
                dragInfo.first to (to.index - headerItems)
            }
        onDragInfoChange(newDragInfo)
        mutableSongs.move(from.index - headerItems, to.index - headerItems)
    }
}

@Composable
fun LocalPlaylistSearchFocusEffect(
    isSearching: Boolean,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun LocalPlaylistSelectionPruneEffect(
    selection: Boolean,
    visibleSongMapIds: Set<Int>,
    selectedSongMapIds: Set<Int>,
    onSelectedSongMapIdsChange: (Set<Int>) -> Unit,
) {
    LaunchedEffect(selection, visibleSongMapIds) {
        if (selection) {
            val visibleSelectedSongMapIds = selectedSongMapIds.intersect(visibleSongMapIds)
            if (visibleSelectedSongMapIds.size != selectedSongMapIds.size) {
                onSelectedSongMapIdsChange(visibleSelectedSongMapIds)
            }
        } else if (selectedSongMapIds.isNotEmpty()) {
            onSelectedSongMapIdsChange(emptySet())
        }
    }
}

@Composable
fun LocalPlaylistDownloadSyncEffect(
    songs: List<PlaylistSong>,
    mutableSongs: SnapshotStateList<PlaylistSong>,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
) {
    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        val songIds = songs.map { it.song.id }
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
fun LocalPlaylistDownloadPausedEffect(
    downloadState: HeaderDownloadState,
    onResetPaused: () -> Unit,
) {
    LaunchedEffect(downloadState) {
        if (downloadState !is HeaderDownloadState.Partial) {
            onResetPaused()
        }
    }
}

@Composable
fun LocalPlaylistReorderEffect(
    isDragging: Boolean,
    dragInfo: Pair<Int, Int>?,
    onReorderCommit: (from: Int, to: Int) -> Unit,
    onResetDragInfo: () -> Unit,
) {
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            dragInfo?.let { (from, to) ->
                onReorderCommit(from, to)
                onResetDragInfo()
            }
        }
    }
}

@Composable
fun LocalPlaylistCoverGradientEffect(
    thumbnails: List<String>?,
    context: Context,
    fallbackColor: Int,
    onGradientColorsChange: (List<Color>) -> Unit,
) {
    LaunchedEffect(thumbnails) {
        val thumbnailUrl = thumbnails?.firstOrNull()
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
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
                    onGradientColorsChange(extractedColors)
                }
            }
        } else {
            onGradientColorsChange(emptyList())
        }
    }
}

@Composable
fun LocalPlaylistEffects(
    isSearching: Boolean,
    focusRequester: FocusRequester,
    selection: Boolean,
    visibleSongMapIds: Set<Int>,
    selectedSongMapIds: Set<Int>,
    onSelectedSongMapIdsChange: (Set<Int>) -> Unit,
    songs: List<PlaylistSong>,
    mutableSongs: SnapshotStateList<PlaylistSong>,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    downloadState: HeaderDownloadState,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
    onResetDownloadsPaused: () -> Unit,
    isDragging: Boolean,
    dragInfo: Pair<Int, Int>?,
    onReorderCommit: (from: Int, to: Int) -> Unit,
    onResetDragInfo: () -> Unit,
    thumbnails: List<String>?,
    context: Context,
    fallbackColor: Int,
    onGradientColorsChange: (List<Color>) -> Unit,
) {
    LocalPlaylistSearchFocusEffect(isSearching, focusRequester)
    LocalPlaylistSelectionPruneEffect(selection, visibleSongMapIds, selectedSongMapIds, onSelectedSongMapIdsChange)
    LocalPlaylistDownloadSyncEffect(songs, mutableSongs, downloadUtil, onDownloadsChange, onDownloadStateChange)
    LocalPlaylistDownloadPausedEffect(downloadState, onResetDownloadsPaused)
    LocalPlaylistReorderEffect(isDragging, dragInfo, onReorderCommit, onResetDragInfo)
    LocalPlaylistCoverGradientEffect(thumbnails, context, fallbackColor, onGradientColorsChange)
}

@Composable
fun LocalPlaylistEffects(
    searchState: LocalPlaylistSearchState,
    downloadState: LocalPlaylistDownloadUiState,
    songs: List<PlaylistSong>,
    downloadUtil: DownloadUtil,
    isDragging: Boolean,
    dragInfo: Pair<Int, Int>?,
    onReorderCommit: (from: Int, to: Int) -> Unit,
    onResetDragInfo: () -> Unit,
) {
    LocalPlaylistSearchFocusEffect(searchState.isSearching, searchState.focusRequester)
    LocalPlaylistSelectionPruneEffect(
        selection = searchState.selection,
        visibleSongMapIds = searchState.visibleSongMapIds,
        selectedSongMapIds = searchState.selectedSongMapIds,
        onSelectedSongMapIdsChange = { searchState.selectedSongMapIds = it },
    )
    LocalPlaylistDownloadSyncEffect(
        songs = songs,
        mutableSongs = downloadState.mutableSongs,
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloadState.downloads = it },
        onDownloadStateChange = { downloadState.downloadState = it },
    )
    LocalPlaylistDownloadPausedEffect(
        downloadState = downloadState.downloadState,
        onResetPaused = { downloadState.downloadsPaused = false },
    )
    LocalPlaylistReorderEffect(
        isDragging = isDragging,
        dragInfo = dragInfo,
        onReorderCommit = onReorderCommit,
        onResetDragInfo = onResetDragInfo,
    )
}

@Composable
fun LocalPlaylistAssignTagsDialog(
    show: Boolean,
    playlistId: String?,
    onDismiss: () -> Unit,
) {
    if (show && playlistId != null) {
        AssignTagsDialog(
            playlistId = playlistId,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun LocalPlaylistEditDialog(
    show: Boolean,
    playlist: Playlist?,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    if (show) {
        playlist?.let { playlistData ->
            EditPlaylistDialog(
                initialName = playlistData.playlist.name,
                onDismiss = onDismiss,
                onSave = { name ->
                    renameLocalPlaylist(
                        database = database,
                        playlistData = playlistData,
                        name = name,
                        scope = coroutineScope,
                    )
                },
            )
        }
    }
}

@Composable
fun LocalPlaylistRemoveDownloadDialog(
    show: Boolean,
    playlistName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (show && playlistName != null) {
        DefaultDialog(
            onDismiss = onDismiss,
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlistName,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        onDismiss()
                        onConfirm()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
fun LocalPlaylistDeleteDialog(
    show: Boolean,
    playlistName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (show && playlistName != null) {
        DefaultDialog(
            onDismiss = onDismiss,
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlistName,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onConfirm()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
fun LocalPlaylistDialogsHost(
    showAssignTagsDialog: Boolean,
    onDismissAssignTags: () -> Unit,
    showEditDialog: Boolean,
    onDismissEditDialog: () -> Unit,
    showRemoveDownloadDialog: Boolean,
    onDismissRemoveDownloadDialog: () -> Unit,
    showDeletePlaylistDialog: Boolean,
    onDismissDeletePlaylistDialog: () -> Unit,
    playlist: Playlist?,
    songs: List<PlaylistSong>,
    editable: Boolean,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    context: Context,
    onDeleteSuccess: () -> Unit,
) {
    LocalPlaylistAssignTagsDialog(
        show = showAssignTagsDialog,
        playlistId = playlist?.id,
        onDismiss = onDismissAssignTags,
    )
    LocalPlaylistEditDialog(
        show = showEditDialog,
        playlist = playlist,
        database = database,
        coroutineScope = coroutineScope,
        onDismiss = onDismissEditDialog,
    )
    LocalPlaylistRemoveDownloadDialog(
        show = showRemoveDownloadDialog,
        playlistName = playlist?.playlist?.name,
        onDismiss = onDismissRemoveDownloadDialog,
        onConfirm = {
            removeLocalPlaylistDownloads(context, database, playlist, songs, editable)
        },
    )
    LocalPlaylistDeleteDialog(
        show = showDeletePlaylistDialog,
        playlistName = playlist?.playlist?.name,
        onDismiss = onDismissDeletePlaylistDialog,
        onConfirm = {
            deleteLocalPlaylist(database, playlist, coroutineScope, onDeleteSuccess)
        },
    )
}

@Composable
fun LocalPlaylistDialogsHost(
    dialogState: LocalPlaylistDialogState,
    playlist: Playlist?,
    songs: List<PlaylistSong>,
    editable: Boolean,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    context: Context,
    onDeleteSuccess: () -> Unit,
) {
    LocalPlaylistDialogsHost(
        showAssignTagsDialog = dialogState.showAssignTags,
        onDismissAssignTags = { dialogState.showAssignTags = false },
        showEditDialog = dialogState.showEdit,
        onDismissEditDialog = { dialogState.showEdit = false },
        showRemoveDownloadDialog = dialogState.showRemoveDownload,
        onDismissRemoveDownloadDialog = { dialogState.showRemoveDownload = false },
        showDeletePlaylistDialog = dialogState.showDelete,
        onDismissDeletePlaylistDialog = { dialogState.showDelete = false },
        playlist = playlist,
        songs = songs,
        editable = editable,
        database = database,
        coroutineScope = coroutineScope,
        context = context,
        onDeleteSuccess = onDeleteSuccess,
    )
}

fun showLocalPlaylistSelectionMenu(
    menuState: MenuState,
    selectedPlaylistSongs: List<PlaylistSong>,
    browseId: String?,
    onClearSelection: () -> Unit,
) {
    menuState.show {
        SelectionSongMenu(
            songSelection = selectedPlaylistSongs.map { it.song },
            songPosition = selectedPlaylistSongs.map { it.map },
            onDismiss = menuState::dismiss,
            clearAction = onClearSelection,
            likeSourceHint =
                if (browseId?.startsWith("spotify:") == true) {
                    LikeSource.SPOTIFY
                } else {
                    null
                },
        )
    }
}

@Composable
fun LocalPlaylistTopBar(
    selection: Boolean,
    isSearching: Boolean,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    showTopBarTitle: Boolean,
    playlistTitle: String,
    selectedCount: Int,
    isAllSelected: Boolean,
    focusRequester: FocusRequester,
    onCloseSearch: () -> Unit,
    onCloseSelection: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onOpenSelectionMenu: () -> Unit,
    navController: NavController,
) {
    TopAppBar(
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        title = {
            if (selection) {
                Text(
                    text = pluralStringResource(R.plurals.n_song, selectedCount, selectedCount),
                    style = MaterialTheme.typography.titleLarge,
                )
            } else if (isSearching) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                )
            } else if (showTopBarTitle) {
                Text(playlistTitle)
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isSearching) {
                        onCloseSearch()
                    } else if (selection) {
                        onCloseSelection()
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (!isSearching) {
                        navController.backToMain()
                    }
                },
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (selection || isSearching) R.drawable.close else R.drawable.arrow_back,
                        ),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (selection) {
                IconButton(
                    onClick = onToggleSelectAll,
                    onLongClick = {},
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isAllSelected) R.drawable.deselect else R.drawable.select_all,
                            ),
                        contentDescription = null,
                    )
                }

                IconButton(
                    onClick = onOpenSelectionMenu,
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            } else if (!isSearching) {
                IconButton(
                    onClick = onOpenSearch,
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                }
            }
        },
    )
}

@Composable
fun LocalPlaylistTopBar(
    isSearching: Boolean,
    onSearchChange: (Boolean) -> Unit,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    selection: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    selectedSongMapIds: Set<Int>,
    onSelectedSongMapIdsChange: (Set<Int>) -> Unit,
    visibleSongMapIds: Set<Int>,
    selectedPlaylistSongs: List<PlaylistSong>,
    filteredSongsCount: Int,
    playlistTitle: String,
    browseId: String?,
    showTopBarTitle: Boolean,
    focusRequester: FocusRequester,
    menuState: MenuState,
    navController: NavController,
) {
    LocalPlaylistTopBar(
        selection = selection,
        isSearching = isSearching,
        query = query,
        onQueryChange = onQueryChange,
        showTopBarTitle = showTopBarTitle,
        playlistTitle = playlistTitle,
        selectedCount = selectedPlaylistSongs.size,
        isAllSelected = selectedPlaylistSongs.size == filteredSongsCount,
        focusRequester = focusRequester,
        onCloseSearch = {
            onSearchChange(false)
            onQueryChange(TextFieldValue())
        },
        onCloseSelection = { onSelectionChange(false) },
        onOpenSearch = { onSearchChange(true) },
        onToggleSelectAll = {
            onSelectedSongMapIdsChange(
                if (selectedPlaylistSongs.size == filteredSongsCount) {
                    emptySet()
                } else {
                    visibleSongMapIds
                },
            )
        },
        onOpenSelectionMenu = {
            showLocalPlaylistSelectionMenu(
                menuState = menuState,
                selectedPlaylistSongs = selectedPlaylistSongs,
                browseId = browseId,
                onClearSelection = {
                    onSelectionChange(false)
                    onSelectedSongMapIdsChange(emptySet())
                },
            )
        },
        navController = navController,
    )
}

@Composable
fun LocalPlaylistTopBar(
    searchState: LocalPlaylistSearchState,
    playlistTitle: String,
    browseId: String?,
    showTopBarTitle: Boolean,
    menuState: MenuState,
    navController: NavController,
) {
    LocalPlaylistTopBar(
        isSearching = searchState.isSearching,
        onSearchChange = { searchState.isSearching = it },
        query = searchState.query,
        onQueryChange = { searchState.query = it },
        selection = searchState.selection,
        onSelectionChange = { searchState.selection = it },
        selectedSongMapIds = searchState.selectedSongMapIds,
        onSelectedSongMapIdsChange = { searchState.selectedSongMapIds = it },
        visibleSongMapIds = searchState.visibleSongMapIds,
        selectedPlaylistSongs = searchState.selectedPlaylistSongs,
        filteredSongsCount = searchState.filteredSongs.size,
        playlistTitle = playlistTitle,
        browseId = browseId,
        showTopBarTitle = showTopBarTitle,
        focusRequester = searchState.focusRequester,
        menuState = menuState,
        navController = navController,
    )
}

@Composable
fun LocalPlaylistDownloadProgressToolbar(
    downloadState: HeaderDownloadState,
    songs: List<PlaylistSong>,
    downloads: Map<String, Download>,
    downloadsPaused: Boolean,
    dismissed: Boolean,
    context: Context,
    onDownloadsPausedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showDownloadProgressToolbar =
        downloadState is HeaderDownloadState.Partial &&
            songs.isNotEmpty() &&
            !dismissed
    AnimatedVisibility(
        visible = showDownloadProgressToolbar,
        modifier = modifier,
    ) {
        if (downloadState is HeaderDownloadState.Partial && songs.isNotEmpty()) {
            val songIds = remember(songs) { songs.map { it.song.id } }
            DownloadProgressFloatingToolbar(
                state =
                    DownloadProgressToolbarState(
                        progress = downloadState.progress,
                        paused = downloadsPaused,
                        canPause = hasActiveDownloads(songIds, downloads),
                    ),
                onPauseResume = {
                    if (downloadsPaused) {
                        sendResumeDownloads(context, songIds)
                    } else {
                        sendPauseDownloads(context, songIds)
                    }
                    onDownloadsPausedChange(!downloadsPaused)
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
fun LocalPlaylistDownloadProgressToolbar(
    downloadUiState: LocalPlaylistDownloadUiState,
    songs: List<PlaylistSong>,
    context: Context,
    modifier: Modifier = Modifier,
) {
    LocalPlaylistDownloadProgressToolbar(
        downloadState = downloadUiState.downloadState,
        songs = songs,
        downloads = downloadUiState.downloads,
        downloadsPaused = downloadUiState.downloadsPaused,
        dismissed = downloadUiState.dismissed,
        context = context,
        onDownloadsPausedChange = { downloadUiState.downloadsPaused = it },
        onDismiss = {
            downloadUiState.downloadsPaused = false
            downloadUiState.dismissed = true
        },
        modifier = modifier,
    )
}
