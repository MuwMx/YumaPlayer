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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.fastSumBy
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
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
import moe.rukamori.archivetune.ui.scaffold.HeaderHazeBackdrop
import moe.rukamori.archivetune.ui.scaffold.rememberHeaderScrollProgress
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.playlist.MetadataChip
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.screens.playlist.CachePlaylistViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CachePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: CachePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptics = rememberYumaHaptics()
    val focusManager = LocalFocusManager.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            SongSortTypeKey,
            SongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val wrappedSongs =
        remember(cachedSongs, sortType, sortDescending) {
            val sortedSongs =
                when (sortType) {
                    SongSortType.CREATE_DATE -> {
                        cachedSongs.sortedBy { it.song.song.dateDownload ?: LocalDateTime.MIN }
                    }

                    SongSortType.NAME -> {
                        cachedSongs.sortedBy { it.title }
                    }

                    SongSortType.ARTIST -> {
                        cachedSongs.sortedBy { item ->
                            item.artists.joinToString(separator = "") { artist -> artist.name }
                        }
                    }

                    SongSortType.PLAY_TIME -> {
                        cachedSongs.sortedBy { it.song.song.totalPlayTime }
                    }
                }.let { if (sortDescending) it.reversed() else it }

            sortedSongs.map { song -> ItemWrapper(song) }
        }.toMutableStateList()

    var selection by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()

    val selectedCount by remember(wrappedSongs) {
        derivedStateOf { wrappedSongs.count { it.isSelected } }
    }

    LaunchedEffect(selectedCount) {
        if (selection && selectedCount == 0) {
            selection = false
        }
    }

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
        BackHandler {
            selection = false
        }
    }

    val filteredSongs =
        remember(wrappedSongs, query) {
            if (query.text.isEmpty()) {
                wrappedSongs
            } else {
                wrappedSongs.filter { wrapper ->
                    val song = wrapper.item
                    song.title.contains(query.text, true) ||
                        song.artists.any { it.name.contains(query.text, true) }
                }
            }
        }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val hazeState = remember { HazeState() }
    val headerItems = if (filteredSongs.isNotEmpty() && !isSearching) 2 else 0
    val hasHeader = headerItems > 0
    val firstSongThumbnail = filteredSongs.firstOrNull()?.item?.thumbnailUrl
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
            if (filteredSongs.isEmpty() && !isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.playlist_is_empty),
                    )
                }
            }

            if (filteredSongs.isEmpty() && isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.ic_search,
                        text = stringResource(R.string.no_results_found),
                    )
                }
            } else {
                if (filteredSongs.isNotEmpty() && !isSearching) {
                    item(key = "header") {
                        val totalDuration = remember(filteredSongs) { filteredSongs.fastSumBy { it.item.duration } }
                        val cacheTitle = stringResource(R.string.cached_playlist)
                        CachePlaylistHeroSection(
                            thumbnailUrl = filteredSongs.firstOrNull()?.item?.thumbnailUrl,
                            songsCount = filteredSongs.size,
                            totalDuration = totalDuration,
                            hasSongs = filteredSongs.isNotEmpty(),
                            systemBarsTopPadding = systemBarsTopPadding,
                            headerProgress = headerProgress,
                            disableBlur = disableBlur,
                            onPlay = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = cacheTitle,
                                        items = filteredSongs.map { it.item.toMediaItem() },
                                    ),
                                )
                            },
                            onShuffle = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = cacheTitle,
                                        items = filteredSongs.shuffled().map { it.item.toMediaItem() },
                                    ),
                                )
                            },
                            onAddToQueue = {
                                playerConnection.addToQueue(
                                    items = filteredSongs.map { it.item.toMediaItem() },
                                )
                            },
                        )
                    }
                }

                if (filteredSongs.isNotEmpty()) {
                    // Sort Header
                    item(key = "sortHeader") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Song items
                itemsIndexed(filteredSongs, key = { index, song -> "${song.item.id}_$index" }) { index, songWrapper ->
                    val cachedSong = songWrapper.item
                    val partiallyLabel = stringResource(R.string.partially_cached)
                    val cacheStatusText =
                        if (cachedSong.isFullyCached) {
                            "${cachedSong.source} • ${cachedSong.formattedSize}"
                        } else {
                            "${cachedSong.source} • $partiallyLabel • ${cachedSong.formattedSize}"
                        }
                    SongListItem(
                        song = cachedSong.song,
                        isActive = cachedSong.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        isSelected = songWrapper.isSelected && selection,
                        showInLibraryIcon = true,
                        isDownloaded = cachedSong.isFullyCached,
                        viewCountText = cacheStatusText,
                        trailingContent = {
                            androidx.compose.material3.IconButton(onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = cachedSong.song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                        isFromCache = true,
                                    )
                                }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (cachedSong.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = "Cache Songs",
                                                        items = cachedSongs.map { it.toMediaItem() },
                                                        startIndex = cachedSongs.indexOfFirst { it.id == cachedSong.id },
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
                            text = stringResource(R.string.cached_playlist),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
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
                }, onLongClick = {
                    if (!isSearching && !selection) {
                        navController.backToMain()
                    }
                }) {
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
                    androidx.compose.material3.IconButton(onClick = {
                        wrappedSongs.filter { it.isSelected }.forEach {
                            viewModel.removeSongFromCache(it.item.id)
                        }
                        selection = false
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.remove_from_cache),
                        )
                    }

                    androidx.compose.material3.IconButton(onClick = {
                        if (count == wrappedSongs.size) {
                            wrappedSongs.forEach { it.isSelected = false }
                            selection = false
                        } else {
                            wrappedSongs.forEach { it.isSelected = true }
                        }
                    }) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    androidx.compose.material3.IconButton(onClick = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = wrappedSongs.filter { it.isSelected }.map { it.item.song },
                                onDismiss = menuState::dismiss,
                                clearAction = { selection = false },
                                isFromCache = true,
                                onRemoveFromCache = { songs ->
                                    songs.forEach { viewModel.removeSongFromCache(it.id) }
                                },
                            )
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    androidx.compose.material3.IconButton(onClick = { isSearching = true }) {
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
private fun CachePlaylistHeroSection(
    thumbnailUrl: String?,
    songsCount: Int,
    totalDuration: Int,
    hasSongs: Boolean,
    systemBarsTopPadding: Dp,
    headerProgress: () -> Float,
    disableBlur: Boolean,
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
                text = stringResource(R.string.cached_playlist),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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

                if (totalDuration > 0) {
                    MetadataChip(
                        icon = R.drawable.timer,
                        text = makeTimeString(totalDuration * 1000L),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val playLabel = stringResource(R.string.play)
            val shuffleLabel = stringResource(R.string.shuffle)
            val queueLabel = stringResource(R.string.add_to_queue)

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                enabled = hasSongs,
                                onClick = onPlay,
                            )
                            .background(
                                color =
                                    if (hasSongs) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                    },
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
                            tint =
                                if (hasSongs) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                                },
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = playLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color =
                                if (hasSongs) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                                },
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
                                enabled = hasSongs,
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
                        tint =
                            if (hasSongs) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                enabled = hasSongs,
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
                        tint =
                            if (hasSongs) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
