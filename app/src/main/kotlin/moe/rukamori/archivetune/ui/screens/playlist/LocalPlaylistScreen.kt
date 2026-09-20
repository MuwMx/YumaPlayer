/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalMixQueue
import moe.rukamori.archivetune.ui.component.AssignTagsDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.EditPlaylistDialog
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
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
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LocalPlaylistViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val viewCounts by viewModel.viewCounts.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val sortDescending by viewModel.sortDescending.collectAsStateWithLifecycle()
    val onSortTypeChange: (PlaylistSongSortType) -> Unit = { viewModel.updateSortPreference(it, sortDescending) }
    val onSortDescendingChange: (Boolean) -> Unit = { viewModel.updateSortPreference(sortType, it) }
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    var showAssignTagsDialog by remember { mutableStateOf(false) }

    if (showAssignTagsDialog && playlist != null) {
        AssignTagsDialog(
            playlistId = playlist!!.id,
            onDismiss = { showAssignTagsDialog = false },
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var selection by remember { mutableStateOf(false) }
    var selectedSongMapIds by remember { mutableStateOf(emptySet<Int>()) }
    val visibleSongMapIds =
        remember(filteredSongs) {
            filteredSongs.map { it.map.id }.toSet()
        }
    val selectedPlaylistSongs =
        remember(filteredSongs, selectedSongMapIds) {
            filteredSongs.filter { it.map.id in selectedSongMapIds }
        }

    LaunchedEffect(selection, visibleSongMapIds) {
        if (selection) {
            val visibleSelectedSongMapIds = selectedSongMapIds.intersect(visibleSongMapIds)
            if (visibleSelectedSongMapIds.size != selectedSongMapIds.size) {
                selectedSongMapIds = visibleSelectedSongMapIds
            }
        } else if (selectedSongMapIds.isNotEmpty()) {
            selectedSongMapIds = emptySet()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    var downloadsPaused by remember { mutableStateOf(false) }
    var downloadProgressToolbarDismissed by remember { mutableStateOf(true) }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        val songIds = songs.map { it.song.id }
        if (songIds.isEmpty()) {
            downloads = emptyMap()
            downloadState = HeaderDownloadState.None
            return@LaunchedEffect
        }
        downloadUtil.downloads.collect { currentDownloads ->
            downloads = currentDownloads
            downloadState = headerDownloadState(songIds, currentDownloads)
        }
    }

    LaunchedEffect(downloadState) {
        if (downloadState !is HeaderDownloadState.Partial) {
            downloadsPaused = false
        }
    }

    val pickCoverLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
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

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        playlist?.let { playlistData ->
            EditPlaylistDialog(
                initialName = playlistData.playlist.name,
                onDismiss = { showEditDialog = false },
                onSave = { name ->
                    database.query {
                        update(
                            playlistData.playlist.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistData.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        sendRemoveDownloads(
                            context = context,
                            songIds = songs.map { it.song.id },
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showDeletePlaylistDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            val hasContent =
                current != null &&
                    (current.songCount > 0 || current.playlist.remoteSongCount != 0)
            if (hasContent && !isSearching) 2 else 0
        }
    }
    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }
                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                val orderedBeforeMove = songs
                val browseId =
                    viewModel.playlist.value
                        ?.playlist
                        ?.browseId
                val movedSetVideoId = orderedBeforeMove.getOrNull(from)?.map?.setVideoId
                val successorIndex = if (from > to) to else to + 1
                val successorSetVideoId = orderedBeforeMove.getOrNull(successorIndex)?.map?.setVideoId

                coroutineScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
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
                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Extract gradient colors from playlist cover
    LaunchedEffect(playlist?.thumbnails) {
        val thumbnailUrl = playlist?.thumbnails?.firstOrNull()
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
                    gradientColors = extractedColors
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    // Calculate gradient opacity based on scroll position
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

    fun deleteFromPlaylist(song: PlaylistSong) {
        val map = song.map
        val browseId = playlist?.playlist?.browseId
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

    fun deleteFromPlaylistSelected(song: PlaylistSong) {
        val map = song.map
        coroutineScope.launch(Dispatchers.IO) {
            database.withTransaction {
                move(map.playlistId, map.position, Int.MAX_VALUE)
                delete(map.copy(position = Int.MAX_VALUE))
            }
        }
    }

    val heroActions =
        remember(playlist, songs, downloadState, downloads, editable) {
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
                onPickCover = { pickCoverLauncher.launch(arrayOf("image/*")) },
                onDelete = { showDeletePlaylistDialog = true },
                onToggleLike = {
                    playlist?.let { p ->
                        database.transaction {
                            update(p.playlist.toggleLike())
                        }
                    }
                },
                onDownload = {
                    when (downloadState) {
                        HeaderDownloadState.Completed -> {
                            showRemoveDownloadDialog = true
                        }

                        else -> {
                            downloadProgressToolbarDismissed = false
                            sendAddMissingDownloads(
                                context = context,
                                songs =
                                    songs.map {
                                        HeaderDownloadItem(
                                            id = it.song.id,
                                            title = it.song.song.title,
                                        )
                                    },
                                downloads = downloads,
                            )
                        }
                    }
                },
                onEdit = { showEditDialog = true },
                onSync = {
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
                },
            )
        }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        if (!isSearching) {
            LocalPlaylistMeshGradient(
                disableBlur = disableBlur,
                gradientColors = gradientColors,
                gradientAlpha = gradientAlpha,
                surfaceColor = surfaceColor,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            playlist?.let { currentPlaylist ->
                if (currentPlaylist.songCount == 0 && currentPlaylist.playlist.remoteSongCount == 0) {
                    localPlaylistEmptyItem()
                } else {
                    if (!isSearching) {
                        localPlaylistHeroItem(
                            playlist = currentPlaylist,
                            playlistLength = playlistLength,
                            gradientColors = gradientColors,
                            systemBarsTopPadding = systemBarsTopPadding,
                            downloadState = downloadState,
                            actions = heroActions,
                        )

                        localPlaylistSortHeaderItem(
                            sortType = sortType,
                            sortDescending = sortDescending,
                            onSortTypeChange = onSortTypeChange,
                            onSortDescendingChange = onSortDescendingChange,
                            editable = editable,
                            locked = locked,
                            onToggleLock = { locked = !locked },
                        )
                    }
                }
            }

            // Songs List
            if (!selection) {
                localPlaylistSongs(
                    songs = if (isSearching) filteredSongs else mutableSongs,
                    reorderableState = reorderableState,
                    lazyListState = lazyListState,
                    selection = false,
                    selectedSongMapIds = selectedSongMapIds,
                    mediaMetadataId = mediaMetadata?.id,
                    isPlaying = isPlaying,
                    viewCounts = viewCounts,
                    sortType = sortType,
                    locked = locked,
                    isSearching = isSearching,
                    editable = editable,
                    swipeToSongEnabled = swipeToSongEnabled,
                    navController = navController,
                    menuState = menuState,
                    playlistBrowseId = playlist?.playlist?.browseId,
                    onDeleteSong = { song -> deleteFromPlaylist(song) },
                    onSongClick = { _, song ->
                        if (song.song.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = playlist!!.playlist.name,
                                    items = songs.map { it.song.toMediaItem() },
                                    startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                ),
                            )
                        }
                    },
                    onSongLongClick = { song ->
                        haptics.longPress()
                        if (!selection) {
                            selection = true
                        }
                        selectedSongMapIds = setOf(song.map.id)
                    },
                )
            } else {
                localPlaylistSongs(
                    songs = filteredSongs,
                    reorderableState = reorderableState,
                    lazyListState = lazyListState,
                    selection = true,
                    selectedSongMapIds = selectedSongMapIds,
                    mediaMetadataId = mediaMetadata?.id,
                    isPlaying = isPlaying,
                    viewCounts = viewCounts,
                    sortType = sortType,
                    locked = locked,
                    isSearching = isSearching,
                    editable = editable,
                    swipeToSongEnabled = swipeToSongEnabled,
                    navController = navController,
                    menuState = menuState,
                    playlistBrowseId = playlist?.playlist?.browseId,
                    onDeleteSong = { song -> deleteFromPlaylistSelected(song) },
                    onSongClick = { index, song ->
                        if (!selection) {
                            if (song.song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist!!.playlist.name,
                                        items = songs.map { it.song.toMediaItem() },
                                        startIndex = index,
                                    ),
                                )
                            }
                        } else {
                            selectedSongMapIds =
                                if (song.map.id in selectedSongMapIds) {
                                    selectedSongMapIds - song.map.id
                                } else {
                                    selectedSongMapIds + song.map.id
                                }
                        }
                    },
                    onSongLongClick = { song ->
                        haptics.longPress()
                        if (!selection) {
                            selection = true
                        }
                        selectedSongMapIds = setOf(song.map.id)
                    },
                )
            }

            // Playlist Suggestions Section
            if (!selection && !isSearching) {
                item(key = LOCAL_PLAYLIST_KEY_SUGGESTIONS, contentType = CONTENT_TYPE_LOCAL_PLAYLIST_SUGGESTIONS) {
                    PlaylistSuggestionsSection(
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = headerItems,
        )

        TopAppBar(
            colors = GlassDefaults.topAppBarColors(),
            title = {
                if (selection) {
                    val count = selectedPlaylistSongs.size
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
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
                    Text(playlist?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (selection) {
                            selection = false
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
                    val count = selectedPlaylistSongs.size
                    IconButton(
                        onClick = {
                            if (count == filteredSongs.size) {
                                selectedSongMapIds = emptySet()
                            } else {
                                selectedSongMapIds = visibleSongMapIds
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == filteredSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        selectedPlaylistSongs.map { it.song },
                                    songPosition =
                                        selectedPlaylistSongs.map { it.map },
                                    onDismiss = menuState::dismiss,
                                    clearAction = {
                                        selection = false
                                        selectedSongMapIds = emptySet()
                                    },
                                    likeSourceHint = if (playlist?.playlist?.browseId?.startsWith("spotify:") == true) moe.rukamori.archivetune.constants.LikeSource.SPOTIFY else null,
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true },
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

        val currentDownloadState = downloadState
        val showDownloadProgressToolbar =
            currentDownloadState is HeaderDownloadState.Partial &&
                songs.isNotEmpty() &&
                !downloadProgressToolbarDismissed
        AnimatedVisibility(
            visible = showDownloadProgressToolbar,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(bottom = 16.dp),
        ) {
            if (currentDownloadState is HeaderDownloadState.Partial && songs.isNotEmpty()) {
                val songIds = remember(songs) { songs.map { it.song.id } }
                DownloadProgressFloatingToolbar(
                    state =
                        DownloadProgressToolbarState(
                            progress = currentDownloadState.progress,
                            paused = downloadsPaused,
                            canPause = hasActiveDownloads(songIds, downloads),
                        ),
                    onPauseResume = {
                        if (downloadsPaused) {
                            sendResumeDownloads(context, songIds)
                        } else {
                            sendPauseDownloads(context, songIds)
                        }
                        downloadsPaused = !downloadsPaused
                    },
                    onDismiss = {
                        downloadsPaused = false
                        downloadProgressToolbarDismissed = true
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}
