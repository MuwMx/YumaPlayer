/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
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
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.OnlinePlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
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
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    var downloadsPaused by remember { mutableStateOf(false) }
    var downloadProgressToolbarDismissed by remember { mutableStateOf(true) }

    var selection by remember { mutableStateOf(false) }
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs =
        remember(songs, query) {
            filterPlaylistSongs(songs, query.text)
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler { selection = false }
    }

    val wrappedSongs =
        remember(filteredSongs) { filteredSongs.map { item -> ItemWrapper(item) } }
            .toMutableStateList()

    LaunchedEffect(songs) {
        val songIds = songs.map { it.id }
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

    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }

    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    LaunchedEffect(playlist?.thumbnail) {
        val thumbnailUrl = playlist?.thumbnail
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
                    gradientColors = extractedColors
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
    val uiState =
        remember(
            playlist,
            songs,
            viewCounts,
            isLoading,
            isRefreshing,
            isLoadingMore,
            error,
            isBookmarked,
            viewModel.continuation,
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
                hasContinuation = viewModel.continuation != null,
            )
        }

    val gradientAlpha by remember {
        derivedStateOf {
            calculatePlaylistGradientAlpha(
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
            )
        }
    }

    val headerItems by remember {
        derivedStateOf {
            val current = uiState.playlist
            if (!uiState.isLoading && current != null && !isSearching) 1 else 0
        }
    }

    val actions =
        remember(
            playlist,
            songs,
            dbPlaylist,
            downloadState,
            downloads,
            selection,
            mediaMetadata,
            isPlaying,
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
                    if (!selection) {
                        if (song.item.second.id == mediaMetadata?.id) {
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
                    if (!selection) {
                        selection = true
                    }
                    wrappedSongs.forEach { it.isSelected = false }
                    song.isSelected = true
                },
                onLike = {
                    val current = playlist ?: return@OnlinePlaylistActions
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
                            val currentPlaylist = dbPlaylist!!.playlist
                            update(currentPlaylist, current)
                            update(currentPlaylist.toggleLike())
                        }
                    }
                },
                onMenu = {
                    val current = playlist ?: return@OnlinePlaylistActions
                    menuState.show {
                        YouTubePlaylistMenu(
                            playlist = current,
                            songs = songs,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                            selectAction = { selection = true },
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
                    when (downloadState) {
                        HeaderDownloadState.Completed -> {
                            sendRemoveDownloads(
                                context = context,
                                songIds = songs.map { it.id },
                            )
                        }

                        else -> {
                            downloadProgressToolbarDismissed = false
                            sendAddMissingDownloads(
                                context = context,
                                songs =
                                    songs.map { song ->
                                        HeaderDownloadItem(
                                            id = song.id,
                                            title = song.title,
                                        )
                                    },
                                downloads = downloads,
                            )
                        }
                    }
                },
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMoreSongs,
                onArtistClick = { artistId ->
                    navController.navigate("artist/$artistId")
                },
            )
        }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                songs.size >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= songs.size - 5
            ) {
                actions.onLoadMore()
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        OnlinePlaylistGradientBackground(
            gradientColors = gradientColors,
            gradientAlpha = gradientAlpha,
            surfaceColor = surfaceColor,
            disableBlur = disableBlur,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        LazyColumn(
            state = lazyListState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            if (uiState.isLoading) {
                onlinePlaylistShimmerItem(systemBarsTopPadding = systemBarsTopPadding)
            } else if (uiState.playlist != null) {
                if (!isSearching) {
                    onlinePlaylistHeaderItem(
                        playlist = uiState.playlist,
                        isBookmarked = uiState.isBookmarked,
                        downloadState = downloadState,
                        gradientColors = gradientColors,
                        systemBarsTopPadding = systemBarsTopPadding,
                        actions = actions,
                    )
                }

                if (uiState.songs.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    onlinePlaylistEmptyItem()
                }

                onlinePlaylistSongItems(
                    wrappedSongs = wrappedSongs,
                    viewCounts = uiState.viewCounts,
                    activeMediaId = mediaMetadata?.id,
                    isPlaying = isPlaying,
                    selection = selection,
                    hideExplicit = hideExplicit,
                    actions = actions,
                )

                if (uiState.hasContinuation && uiState.songs.isNotEmpty() && uiState.isLoadingMore) {
                    onlinePlaylistLoadingMoreItem()
                }
            } else {
                onlinePlaylistErrorItem(
                    error = uiState.error,
                    onRetry = actions.onRetry,
                )
            }
        }

        OnlinePlaylistScrollbar(
            scrollState = lazyListState,
            headerItems = headerItems,
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
        )

        TopAppBar(
            colors = GlassDefaults.topAppBarColors(),
            title = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
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
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.title.orEmpty())
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
                        if (!isSearching && !selection) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (selection) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    IconButton(
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == wrappedSongs.size) {
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
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map {
                                                it.item.second
                                                    .toMediaItem()
                                                    .metadata!!
                                            },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                    currentItems = emptyList(),
                                    likeSourceHint = if (playlist?.id?.startsWith("spotify:") == true) LikeSource.SPOTIFY else null,
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
                    IconButton(onClick = { isSearching = true }, onLongClick = {}) {
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
                val songIds = remember(songs) { songs.map { it.id } }
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
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime),
                    ).align(Alignment.BottomCenter),
        )
    }
}

private fun SongItem.toPlaylistPlaybackEndpoint(
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

private fun calculatePlaylistGradientAlpha(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Float =
    if (firstVisibleItemIndex == 0) {
        (1f - (firstVisibleItemScrollOffset / 600f)).coerceIn(0f, 1f)
    } else {
        0f
    }

@Composable
private fun OnlinePlaylistGradientBackground(
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
