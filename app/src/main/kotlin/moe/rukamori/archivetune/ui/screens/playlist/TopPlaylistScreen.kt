/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import moe.rukamori.archivetune.ui.main.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.MyTopFilter
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.ProvideArtworkSurface
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.SortHeader
import moe.rukamori.archivetune.ui.component.YumaImmersiveArtworkHeader
import moe.rukamori.archivetune.ui.component.artworkBackground
import moe.rukamori.archivetune.ui.component.rememberArtworkBackground
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.ui.screens.playlist.MetadataChip
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.screens.playlist.TopPlaylistViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TopPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: TopPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val maxSize = viewModel.top

    val songs by viewModel.topSongs.collectAsState(null)
    val mutableSongs = remember { mutableStateListOf<Song>() }

    val likeLength =
        remember(songs) {
            songs?.fastSumBy { it.song.duration } ?: 0
        }

    val wrappedSongs =
        remember(songs) {
            songs?.map { item -> ItemWrapper(item) }?.toMutableStateList() ?: mutableStateListOf()
        }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var selection by remember { mutableStateOf(false) }
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

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

    val sortType by viewModel.topPeriod.collectAsState()
    val name = stringResource(R.string.my_top) + " $maxSize"

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            songs?.let { addAll(it) }
        }
        val songIds = songs?.map { it.song.id }.orEmpty()
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

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, name),
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
                        sendRemoveDownloads(
                            context = context,
                            songIds = songs.orEmpty().map { it.song.id },
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val filteredSongs =
        remember(wrappedSongs, query) {
            if (query.text.isEmpty()) {
                wrappedSongs
            } else {
                wrappedSongs.filter { wrapper ->
                    val song = wrapper.item
                    song.song.title.contains(query.text, true) ||
                        song.artists.any { it.name.contains(query.text, true) }
                }
            }
        }

    val lazyListState = rememberLazyListState()

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val hazeState = remember { HazeState() }
    val headerItems by remember {
        derivedStateOf {
            val currentSongs = songs
            if (currentSongs != null && currentSongs.isNotEmpty() && !isSearching) 2 else 0
        }
    }
    val hasHeader = headerItems > 0
    val firstSongThumbnail = songs?.firstOrNull()?.song?.thumbnailUrl
    val hasArtworkBackdrop = hasHeader && !firstSongThumbnail.isNullOrBlank()
    val headerProgress = rememberHeaderScrollProgress(
        lazyListState = lazyListState,
        hasCollapsibleHeader = hasArtworkBackdrop,
        collapseDistance = ImmersiveArtworkTokens.BackdropHeight,
        standardThreshold = 48.dp,
    )
    val playerAwareInsets = LocalPlayerAwareWindowInsets.current
    val imeInsets = WindowInsets.ime
    val contentInsets = if (hasArtworkBackdrop) {
        playerAwareInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    } else {
        playerAwareInsets
    }
    val contentPadding = contentInsets.union(imeInsets).asPaddingValues()

    val artworkBackground = rememberArtworkBackground(firstSongThumbnail)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .artworkBackground(artworkBackground),
    ) {
        ProvideArtworkSurface(artworkBackground) {
            LazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        ) {
            if (songs != null) {
                if (songs!!.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "header") {
                            TopPlaylistHeroSection(
                                thumbnailUrl = songs!!.firstOrNull()?.song?.thumbnailUrl,
                                name = name,
                                songsCount = songs!!.size,
                                likeLength = likeLength,
                                downloadState = downloadState,
                                systemBarsTopPadding = systemBarsTopPadding,
                                headerProgress = headerProgress,
                                disableBlur = disableBlur,
                                onDownloadClick = {
                                    when (downloadState) {
                                        HeaderDownloadState.Completed -> {
                                            showRemoveDownloadDialog = true
                                        }

                                        else -> {
                                            sendAddMissingDownloads(
                                                context = context,
                                                songs =
                                                    songs.orEmpty().map {
                                                        HeaderDownloadItem(
                                                            id = it.song.id,
                                                            title = it.song.title,
                                                        )
                                                    },
                                                downloads = downloads,
                                            )
                                        }
                                    }
                                },
                                onPlay = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = name,
                                            items = songs!!.map { it.toMediaItem() },
                                        ),
                                    )
                                },
                                onShuffle = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = name,
                                            items = songs!!.shuffled().map { it.toMediaItem() },
                                        ),
                                    )
                                },
                                onAddToQueue = {
                                    playerConnection.addToQueue(
                                        items = songs!!.map { it.toMediaItem() },
                                    )
                                },
                            )
                        }
                    }

                    // Sort Header (Period Filter)
                    item(key = "sortHeader") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = false,
                                onSortTypeChange = {
                                    viewModel.topPeriod.value = it
                                },
                                onSortDescendingChange = {},
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        MyTopFilter.ALL_TIME -> R.string.all_time
                                        MyTopFilter.DAY -> R.string.past_24_hours
                                        MyTopFilter.WEEK -> R.string.past_week
                                        MyTopFilter.MONTH -> R.string.past_month
                                        MyTopFilter.YEAR -> R.string.past_year
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                showDescending = false,
                            )
                        }
                    }

                    // Song items
                    itemsIndexed(
                        items = filteredSongs,
                        key = { index, song -> "${song.item.id}_$index" },
                    ) { index, songWrapper ->
                        SongListItem(
                            song = songWrapper.item,
                            albumIndex = index + 1,
                            isActive = songWrapper.item.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = songWrapper.item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            },
                            isSelected = songWrapper.isSelected && selection,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (!selection) {
                                                if (songWrapper.item.song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = name,
                                                            items = songs!!.map { it.toMediaItem() },
                                                            startIndex = songs!!.indexOfFirst { it.id == songWrapper.item.id },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                songWrapper.isSelected = !songWrapper.isSelected
                                            }
                                        },
                                        onLongClick = {
                                            haptics.longPress()
                                            if (!selection) {
                                                selection = true
                                                wrappedSongs.forEach { it.isSelected = false }
                                                songWrapper.isSelected = true
                                            }
                                        },
                                    ).animateItem(),
                        )
                    }
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

        HeaderHazeBackdrop(
            progress = headerProgress,
            hasCollapsibleHeader = hasArtworkBackdrop,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        TopAppBar(
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            title = {
                when {
                    selection -> {
                        val count = wrappedSongs.count { it.isSelected }
                        Text(
                            text = pluralStringResource(R.plurals.n_song, count, count),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    isSearching -> {
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
                    }

                    showTopBarTitle -> {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        when {
                            isSearching -> {
                                isSearching = false
                                query = TextFieldValue()
                                focusManager.clearFocus()
                            }

                            selection -> {
                                selection = false
                            }

                            else -> {
                                navController.navigateUp()
                            }
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
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    androidx.compose.material3.IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    androidx.compose.material3.IconButton(
                        onClick = { isSearching = true },
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
    }
}

@Composable
private fun TopPlaylistHeroSection(
    thumbnailUrl: String?,
    name: String,
    songsCount: Int,
    likeLength: Int,
    downloadState: HeaderDownloadState,
    systemBarsTopPadding: Dp,
    headerProgress: () -> Float,
    disableBlur: Boolean,
    onDownloadClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasArtwork = !thumbnailUrl.isNullOrBlank()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hasArtwork) {
                    Modifier.heightIn(min = ImmersiveArtworkTokens.HeaderMinHeight)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (hasArtwork) {
            YumaImmersiveArtworkHeader(
                thumbnailUrl = thumbnailUrl,
                progress = headerProgress,
                disableBlur = disableBlur,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (hasArtwork) systemBarsTopPadding + AppBarHeight else 16.dp,
                    bottom = 16.dp,
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (!hasArtwork) {
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataChip(
                    icon = R.drawable.music_note,
                    text =
                        pluralStringResource(
                            R.plurals.n_song,
                            songsCount,
                            songsCount,
                        ),
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (likeLength > 0) {
                    MetadataChip(
                        icon = R.drawable.timer,
                        text = makeTimeString(likeLength * 1000L),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val downloadLabel = stringResource(R.string.download)
            val playLabel = stringResource(R.string.play)
            val shuffleLabel = stringResource(R.string.shuffle)
            val queueLabel = stringResource(R.string.add_to_queue)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = onDownloadClick,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = downloadLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    when (val state = downloadState) {
                        HeaderDownloadState.Completed -> {
                            Icon(
                                painter = painterResource(R.drawable.offline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                            )
                        }

                        is HeaderDownloadState.Partial -> {
                            CircularProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                strokeWidth = 2.dp,
                            )
                        }

                        else -> {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                            )
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = onPlay,
                            )
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = playLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = onShuffle,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = shuffleLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = onAddToQueue,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = queueLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
