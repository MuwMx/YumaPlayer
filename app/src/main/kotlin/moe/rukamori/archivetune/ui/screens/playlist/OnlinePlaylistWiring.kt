/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.menu.SelectionMediaMetadataMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads

@Composable
fun rememberOnlinePlaylistUiState(
    playlist: PlaylistItem?,
    songs: List<SongItem>,
    viewCounts: Map<String, Int>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    dbPlaylist: Playlist?,
    hasContinuation: Boolean,
): OnlinePlaylistUiState {
    val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
    return remember(
        playlist,
        songs,
        viewCounts,
        isLoading,
        isRefreshing,
        isLoadingMore,
        error,
        isBookmarked,
        hasContinuation,
    ) {
        OnlinePlaylistUiState(
            playlist = playlist,
            songs = songs,
            viewCounts = viewCounts,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            isLoadingMore = isLoadingMore,
            error = error,
            isBookmarked = isBookmarked,
            hasContinuation = hasContinuation,
        )
    }
}

@Stable
class OnlinePlaylistSearchState(
    isSearchingState: MutableState<Boolean>,
    queryState: MutableState<TextFieldValue>,
    selectionState: MutableState<Boolean>,
    val focusRequester: FocusRequester,
    val wrappedSongs: SnapshotStateList<WrappedSongItem>,
) {
    var isSearching by isSearchingState
    var query by queryState
    var selection by selectionState

    val selectedSongsCount: Int
        get() = wrappedSongs.count { it.isSelected }

    fun toggleSelectAll() {
        val count = selectedSongsCount
        if (count == wrappedSongs.size) {
            wrappedSongs.forEach { it.isSelected = false }
        } else {
            wrappedSongs.forEach { it.isSelected = true }
        }
    }

    fun closeSearch() {
        isSearching = false
        query = TextFieldValue()
    }
}

@Composable
fun rememberOnlinePlaylistSearchState(songs: List<SongItem>): OnlinePlaylistSearchState {
    val isSearchingState = rememberSaveable { mutableStateOf(false) }
    val queryState = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val selectionState = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val query = queryState.value
    val filteredSongs =
        remember(songs, query) {
            filterPlaylistSongs(songs, query.text)
        }

    val wrappedSongs =
        remember(filteredSongs) { filteredSongs.map { item -> ItemWrapper(item) } }
            .toMutableStateList()

    if (isSearchingState.value) {
        BackHandler {
            isSearchingState.value = false
            queryState.value = TextFieldValue()
        }
    } else if (selectionState.value) {
        BackHandler { selectionState.value = false }
    }

    return remember(wrappedSongs) {
        OnlinePlaylistSearchState(
            isSearchingState = isSearchingState,
            queryState = queryState,
            selectionState = selectionState,
            focusRequester = focusRequester,
            wrappedSongs = wrappedSongs,
        )
    }
}

@Stable
class OnlinePlaylistDownloadUiState(
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
fun rememberOnlinePlaylistDownloadUiState(): OnlinePlaylistDownloadUiState {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val downloadsPaused = remember { mutableStateOf(false) }
    val dismissed = remember { mutableStateOf(true) }
    return remember {
        OnlinePlaylistDownloadUiState(
            downloadsState = downloads,
            downloadStateState = downloadState,
            downloadsPausedState = downloadsPaused,
            dismissedState = dismissed,
        )
    }
}

@Composable
fun OnlinePlaylistSearchFocusEffect(
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
fun OnlinePlaylistDownloadSyncEffect(
    songs: List<SongItem>,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
) {
    LaunchedEffect(songs) {
        val songIds = songs.map { it.id }
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
fun OnlinePlaylistDownloadPausedEffect(
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
fun OnlinePlaylistCoverGradientEffect(
    thumbnailUrl: String?,
    context: Context,
    fallbackColor: Int,
    onGradientColorsChange: (List<Color>) -> Unit,
) {
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(
                        PlayerColorExtractor.Config.IMAGE_SIZE,
                        PlayerColorExtractor.Config.IMAGE_SIZE,
                    ).allowHardware(false)
                    .build()

            val result = runCatching { context.imageLoader.execute(request) }.getOrNull()

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
fun rememberOnlinePlaylistGradientState(
    thumbnailUrl: String?,
    context: Context,
    fallbackColor: Int,
    lazyListState: LazyListState,
): Pair<List<Color>, Float> {
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    OnlinePlaylistCoverGradientEffect(thumbnailUrl, context, fallbackColor) { gradientColors = it }
    val gradientAlpha by remember {
        derivedStateOf {
            calculatePlaylistGradientAlpha(
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
            )
        }
    }
    return gradientColors to gradientAlpha
}

@Composable
fun OnlinePlaylistContinuationEffect(
    lazyListState: LazyListState,
    songsCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                songsCount >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= songsCount - 5
            ) {
                onLoadMore()
            }
        }
    }
}

@Composable
fun OnlinePlaylistEffects(
    searchState: OnlinePlaylistSearchState,
    downloadUiState: OnlinePlaylistDownloadUiState,
    songs: List<SongItem>,
    downloadUtil: DownloadUtil,
    lazyListState: LazyListState,
    onLoadMore: () -> Unit,
) {
    OnlinePlaylistSearchFocusEffect(
        isSearching = searchState.isSearching,
        focusRequester = searchState.focusRequester,
    )
    OnlinePlaylistDownloadSyncEffect(
        songs = songs,
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloadUiState.downloads = it },
        onDownloadStateChange = { downloadUiState.downloadState = it },
    )
    OnlinePlaylistDownloadPausedEffect(
        downloadState = downloadUiState.downloadState,
        onResetPaused = { downloadUiState.downloadsPaused = false },
    )
    OnlinePlaylistContinuationEffect(
        lazyListState = lazyListState,
        songsCount = songs.size,
        onLoadMore = onLoadMore,
    )
}

fun SongItem.toPlaylistPlaybackEndpoint(
    playlistId: String,
    playlistPlayParams: String?,
): WatchEndpoint {
    val baseEndpoint = endpoint ?: WatchEndpoint(videoId = id)
    return baseEndpoint.copy(
        videoId = baseEndpoint.videoId ?: id,
        playlistId = baseEndpoint.playlistId ?: playlistId,
        playlistSetVideoId = baseEndpoint.playlistSetVideoId ?: setVideoId,
        params = baseEndpoint.params ?: playlistPlayParams,
    )
}

fun toggleOnlinePlaylistLike(
    database: MusicDatabase,
    playlist: PlaylistItem?,
    dbPlaylist: Playlist?,
    songs: List<SongItem>,
) {
    val current = playlist ?: return
    if (dbPlaylist?.playlist == null) {
        database.transaction {
            val existingPlaylist = playlistEntityByBrowseId(current.id)
            val targetPlaylistId =
                if (existingPlaylist == null) {
                    val playlistEntity =
                        PlaylistEntity(
                            name = current.title,
                            browseId = current.id,
                            thumbnailUrl = current.thumbnail,
                            isEditable = current.isEditable,
                            playEndpointParams = current.playEndpoint?.params,
                            shuffleEndpointParams = current.shuffleEndpoint?.params,
                            radioEndpointParams = current.radioEndpoint?.params,
                        ).toggleLike()
                    insert(playlistEntity)
                    playlistEntityByBrowseId(current.id)?.id ?: playlistEntity.id
                } else {
                    val refreshedPlaylist =
                        existingPlaylist.copy(
                            name = current.title,
                            browseId = current.id,
                            thumbnailUrl = current.thumbnail,
                            isEditable = current.isEditable,
                            playEndpointParams = current.playEndpoint?.params,
                            shuffleEndpointParams = current.shuffleEndpoint?.params,
                            radioEndpointParams = current.radioEndpoint?.params,
                        )
                    update(
                        if (existingPlaylist.bookmarkedAt == null) {
                            refreshedPlaylist.toggleLike()
                        } else {
                            refreshedPlaylist
                        },
                    )
                    existingPlaylist.id
                }
            if (songs.isNotEmpty()) {
                clearPlaylist(targetPlaylistId)
                songs
                    .onEach { song -> insert(song.toMediaMetadata()) }
                    .mapIndexed { index, song ->
                        PlaylistSongMap(
                            songId = song.id,
                            playlistId = targetPlaylistId,
                            position = index,
                            setVideoId = song.setVideoId,
                        )
                    }.forEach(::insert)
            }
        }
    } else {
        database.transaction {
            val currentPlaylist = dbPlaylist.playlist
            update(currentPlaylist, current)
            update(currentPlaylist.toggleLike())
        }
    }
}

@Composable
fun rememberOnlinePlaylistActions(
    playlist: PlaylistItem?,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    downloadUiState: OnlinePlaylistDownloadUiState,
    searchState: OnlinePlaylistSearchState,
    mediaMetadataId: String?,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    navController: NavController,
    menuState: MenuState,
    context: Context,
    haptics: YumaHaptics,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
): OnlinePlaylistActions =
    remember(
        playlist,
        songs,
        dbPlaylist,
        downloadUiState.downloadState,
        downloadUiState.downloads,
        searchState.selection,
        mediaMetadataId,
    ) {
        OnlinePlaylistActions(
            onPlay = {
                playlist?.playEndpoint?.let { playEndpoint ->
                    playerConnection.playQueue(
                        YouTubeQueue.playlist(playEndpoint),
                    )
                }
            },
            onShuffle = {
                playlist?.shuffleEndpoint?.let { shuffleEndpoint ->
                    playerConnection.playQueue(
                        YouTubeQueue.playlist(shuffleEndpoint),
                    )
                }
            },
            onRadio = {
                playlist?.radioEndpoint?.let { radioEndpoint ->
                    playerConnection.playQueue(
                        YouTubeQueue(radioEndpoint),
                    )
                }
            },
            onMix = {
                val currentPlaylist = playlist ?: return@OnlinePlaylistActions
                val mixEndpoint = currentPlaylist.shuffleEndpoint ?: currentPlaylist.radioEndpoint
                if (mixEndpoint != null) {
                    playerConnection.playQueue(
                        if (mixEndpoint == currentPlaylist.shuffleEndpoint) {
                            YouTubeQueue.playlist(mixEndpoint)
                        } else {
                            YouTubeQueue(mixEndpoint)
                        },
                    )
                }
            },
            onSongClick = { song ->
                if (!searchState.selection) {
                    if (song.item.second.id == mediaMetadataId) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        val currentPlaylist = playlist ?: return@OnlinePlaylistActions
                        playerConnection.playQueue(
                            YouTubeQueue.playlist(
                                endpoint =
                                    song.item.second
                                        .toPlaylistPlaybackEndpoint(
                                            playlistId = currentPlaylist.id,
                                            playlistPlayParams =
                                                currentPlaylist.playEndpoint
                                                    ?.params,
                                        ),
                                preloadItem = song.item.second.toMediaMetadata(),
                            ),
                        )
                    }
                } else {
                    song.isSelected = !song.isSelected
                }
            },
            onSongLongClick = { song ->
                haptics.longPress()
                if (!searchState.selection) {
                    searchState.selection = true
                }
                searchState.wrappedSongs.forEach { it.isSelected = false }
                song.isSelected = true
            },
            onLike = {
                toggleOnlinePlaylistLike(
                    database = database,
                    playlist = playlist,
                    dbPlaylist = dbPlaylist,
                    songs = songs,
                )
            },
            onMenu = {
                val current = playlist ?: return@OnlinePlaylistActions
                menuState.show {
                    YouTubePlaylistMenu(
                        playlist = current,
                        songs = songs,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                        selectAction = { searchState.selection = true },
                        canSelect = true,
                        snackbarHostState = snackbarHostState,
                    )
                }
            },
            onSongMenu = { songItem ->
                menuState.show {
                    YouTubeSongMenu(
                        song = songItem,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            onDownload = {
                when (downloadUiState.downloadState) {
                    HeaderDownloadState.Completed -> {
                        sendRemoveDownloads(
                            context = context,
                            songIds = songs.map { it.id },
                        )
                    }

                    else -> {
                        downloadUiState.dismissed = false
                        sendAddMissingDownloads(
                            context = context,
                            songs =
                                songs.map { song ->
                                    HeaderDownloadItem(
                                        id = song.id,
                                        title = song.title,
                                    )
                                },
                            downloads = downloadUiState.downloads,
                        )
                    }
                }
            },
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onArtistClick = { artistId ->
                navController.navigate("artist/$artistId")
            },
        )
    }

@Composable
fun OnlinePlaylistTopBar(
    searchState: OnlinePlaylistSearchState,
    playlist: PlaylistItem?,
    showTopBarTitle: Boolean,
    menuState: MenuState,
    navController: NavController,
    onMenu: (() -> Unit)? = null,
) {
    TopAppBar(
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        title = {
            if (searchState.selection) {
                val count = searchState.selectedSongsCount
                Text(
                    text = pluralStringResource(R.plurals.n_song, count, count),
                    style = MaterialTheme.typography.titleLarge,
                )
            } else if (searchState.isSearching) {
                TextField(
                    value = searchState.query,
                    onValueChange = { searchState.query = it },
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
                            .focusRequester(searchState.focusRequester),
                )
            } else if (showTopBarTitle) {
                Text(playlist?.title.orEmpty())
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (searchState.isSearching) {
                        searchState.closeSearch()
                    } else if (searchState.selection) {
                        searchState.selection = false
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (!searchState.isSearching && !searchState.selection) {
                        navController.backToMain()
                    }
                },
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (searchState.selection) R.drawable.close else R.drawable.arrow_back,
                        ),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (searchState.selection) {
                val count = searchState.selectedSongsCount
                IconButton(
                    onClick = { searchState.toggleSelectAll() },
                    onLongClick = {},
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (count == searchState.wrappedSongs.size) {
                                    R.drawable.deselect
                                } else {
                                    R.drawable.select_all
                                },
                            ),
                        contentDescription = null,
                    )
                }
                IconButton(
                    onClick = {
                        menuState.show {
                            SelectionMediaMetadataMenu(
                                songSelection =
                                    searchState.wrappedSongs
                                        .filter { it.isSelected }
                                        .map {
                                            it.item.second
                                                .toMediaItem()
                                                .metadata!!
                                        },
                                onDismiss = menuState::dismiss,
                                clearAction = { searchState.selection = false },
                                currentItems = emptyList(),
                                likeSourceHint =
                                    if (playlist?.id?.startsWith("spotify:") == true) {
                                        LikeSource.SPOTIFY
                                    } else {
                                        null
                                    },
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
            } else if (!searchState.isSearching) {
                IconButton(
                    onClick = { searchState.isSearching = true },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                }
                if (onMenu != null) {
                    IconButton(
                        onClick = onMenu,
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun OnlinePlaylistDownloadProgressToolbar(
    downloadUiState: OnlinePlaylistDownloadUiState,
    songs: List<SongItem>,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val currentDownloadState = downloadUiState.downloadState
    val showDownloadProgressToolbar =
        currentDownloadState is HeaderDownloadState.Partial &&
            songs.isNotEmpty() &&
            !downloadUiState.dismissed
    AnimatedVisibility(
        visible = showDownloadProgressToolbar,
        modifier = modifier,
    ) {
        if (currentDownloadState is HeaderDownloadState.Partial && songs.isNotEmpty()) {
            val songIds = remember(songs) { songs.map { it.id } }
            DownloadProgressFloatingToolbar(
                state =
                    DownloadProgressToolbarState(
                        progress = currentDownloadState.progress,
                        paused = downloadUiState.downloadsPaused,
                        canPause = hasActiveDownloads(songIds, downloadUiState.downloads),
                    ),
                onPauseResume = {
                    if (downloadUiState.downloadsPaused) {
                        sendResumeDownloads(context, songIds)
                    } else {
                        sendPauseDownloads(context, songIds)
                    }
                    downloadUiState.downloadsPaused = !downloadUiState.downloadsPaused
                },
                onDismiss = {
                    downloadUiState.downloadsPaused = false
                    downloadUiState.dismissed = true
                },
            )
        }
    }
}

fun calculatePlaylistGradientAlpha(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Float =
    if (firstVisibleItemIndex == 0) {
        (1f - (firstVisibleItemScrollOffset / 600f)).coerceIn(0f, 1f)
    } else {
        0f
    }

@Composable
fun OnlinePlaylistGradientBackground(
    gradientColors: List<Color>,
    gradientAlpha: Float,
    surfaceColor: Color,
    disableBlur: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.55f)
                    .zIndex(-1f)
                    .drawBehind {
                        val width = size.width
                        val height = size.height

                        if (gradientColors.size >= 3) {
                            val c0 = gradientColors[0]
                            val c1 = gradientColors[1]
                            val c2 = gradientColors[2]
                            val c3 = gradientColors.getOrElse(3) { c0 }
                            val c4 = gradientColors.getOrElse(4) { c1 }
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c0.copy(
                                                    alpha = gradientAlpha * 0.75f,
                                                ),
                                                c0.copy(
                                                    alpha = gradientAlpha * 0.4f,
                                                ),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.15f),
                                        radius = width * 0.8f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c1.copy(
                                                    alpha = gradientAlpha * 0.55f,
                                                ),
                                                c1.copy(
                                                    alpha = gradientAlpha * 0.3f,
                                                ),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.1f, height * 0.4f),
                                        radius = width * 0.6f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c2.copy(
                                                    alpha = gradientAlpha * 0.5f,
                                                ),
                                                c2.copy(
                                                    alpha = gradientAlpha * 0.25f,
                                                ),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.9f, height * 0.35f),
                                        radius = width * 0.55f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c3.copy(
                                                    alpha = gradientAlpha * 0.35f,
                                                ),
                                                c3.copy(
                                                    alpha = gradientAlpha * 0.18f,
                                                ),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.25f, height * 0.65f),
                                        radius = width * 0.75f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c4.copy(
                                                    alpha = gradientAlpha * 0.3f,
                                                ),
                                                c4.copy(
                                                    alpha = gradientAlpha * 0.15f,
                                                ),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.55f, height * 0.85f),
                                        radius = width * 0.9f,
                                    ),
                            )
                        } else if (gradientColors.isNotEmpty()) {
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                gradientColors[0].copy(
                                                    alpha = gradientAlpha * 0.7f,
                                                ),
                                                gradientColors[0].copy(
                                                    alpha = gradientAlpha * 0.35f,
                                                ),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.25f),
                                        radius = width * 0.85f,
                                    ),
                            )
                        }

                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                            surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                            surfaceColor,
                                        ),
                                    startY = height * 0.4f,
                                    endY = height,
                                ),
                        )
                    },
        )
    }
}
