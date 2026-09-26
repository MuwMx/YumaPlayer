/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.constants.LocalSongsExcludedFoldersKey
import moe.rukamori.archivetune.constants.LocalSongsIncludedFoldersKey
import moe.rukamori.archivetune.constants.LocalSongsMinDurationSecondsKey
import moe.rukamori.archivetune.constants.LocalSongsSortDescendingKey
import moe.rukamori.archivetune.constants.LocalSongsSortTypeKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.localmedia.SupportedLocalAudio
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.library.local.LocalSongControlsCard
import moe.rukamori.archivetune.ui.screens.library.local.LocalSongScanSheet
import moe.rukamori.archivetune.ui.screens.library.local.LocalSongSortType
import moe.rukamori.archivetune.ui.screens.library.local.toFolderEntry
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LocalSongsViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun LocalSongScreen(
    navController: NavController,
    viewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberYumaHaptics()
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val listState = rememberLazyListState()
    val scanSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showScanSheet by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    val (sortDescending, onSortDescendingChange) = rememberPreference(LocalSongsSortDescendingKey, true)
    val (sortTypeName, onSortTypeNameChange) = rememberPreference(LocalSongsSortTypeKey, LocalSongSortType.MODIFIED.name)
    val (minimumDurationSeconds, onMinimumDurationSecondsChange) =
        rememberPreference(
            LocalSongsMinDurationSecondsKey,
            0,
        )
    val (includedFolders, onIncludedFoldersChange) =
        rememberPreference(
            LocalSongsIncludedFoldersKey,
            emptySet<String>(),
        )
    val (excludedFolders, onExcludedFoldersChange) =
        rememberPreference(
            LocalSongsExcludedFoldersKey,
            emptySet<String>(),
        )
    val sortType = remember(sortTypeName) { LocalSongSortType.valueOf(sortTypeName) }
    val scanConfig =
        remember(minimumDurationSeconds, includedFolders, excludedFolders) {
            LocalSongScanConfig(
                minimumDurationSeconds = minimumDurationSeconds,
                includedFolders = includedFolders,
                excludedFolders = excludedFolders,
            )
        }

    val storagePermission =
        remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }

    var hasStoragePermission by remember(storagePermission) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasStoragePermission = granted
        }

    val includedFolderPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            val normalizedFolder = uri?.toFolderEntry() ?: return@rememberLauncherForActivityResult
            onIncludedFoldersChange(
                LocalSongScanConfig.deduplicateFolderEntries(includedFolders + normalizedFolder),
            )
            onExcludedFoldersChange(
                excludedFolders
                    .filterNot {
                        LocalSongScanConfig.normalizeFolderEntry(it).equals(normalizedFolder, ignoreCase = true)
                    }.toSet(),
            )
        }

    val excludedFolderPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            val normalizedFolder = uri?.toFolderEntry() ?: return@rememberLauncherForActivityResult
            onExcludedFoldersChange(
                LocalSongScanConfig.deduplicateFolderEntries(excludedFolders + normalizedFolder),
            )
            onIncludedFoldersChange(
                includedFolders
                    .filterNot {
                        LocalSongScanConfig.normalizeFolderEntry(it).equals(normalizedFolder, ignoreCase = true)
                    }.toSet(),
            )
        }

    val collator =
        remember {
            Collator.getInstance(Locale.getDefault()).apply {
                strength = Collator.PRIMARY
            }
        }

    val visibleSongs by remember(songs, query, sortType, sortDescending, collator) {
        derivedStateOf {
            val normalizedQuery = query.trim()
            val supportedSongs =
                songs.filter { song ->
                    SupportedLocalAudio.isSupportedMimeType(song.format?.mimeType)
                }
            val filteredSongs =
                if (normalizedQuery.isBlank()) {
                    supportedSongs
                } else {
                    supportedSongs.filter { song ->
                        song.song.title.contains(normalizedQuery, ignoreCase = true) ||
                            song.song.albumName
                                .orEmpty()
                                .contains(normalizedQuery, ignoreCase = true) ||
                            song.artists.any { artist -> artist.name.contains(normalizedQuery, ignoreCase = true) }
                    }
                }

            val sortedSongs =
                when (sortType) {
                    LocalSongSortType.MODIFIED -> {
                        filteredSongs.sortedBy { song ->
                            song.song.dateModified ?: LocalDateTime.MIN
                        }
                    }

                    LocalSongSortType.NAME -> {
                        filteredSongs.sortedWith(compareBy(collator) { song -> song.song.title })
                    }

                    LocalSongSortType.ARTIST -> {
                        filteredSongs.sortedWith(
                            compareBy(collator) { song ->
                                song.artists.joinToString(separator = "") { artist -> artist.name }
                            },
                        )
                    }

                    LocalSongSortType.ALBUM -> {
                        filteredSongs.sortedWith(
                            compareBy(collator) { song -> song.song.albumName.orEmpty() },
                        )
                    }
                }

            if (sortDescending) sortedSongs.asReversed() else sortedSongs
        }
    }

    val queueItems = remember(visibleSongs) { visibleSongs.map { it.toMediaItem() } }

    if (showScanSheet) {
        LocalSongScanSheet(
            hasStoragePermission = hasStoragePermission,
            scanState = scanState,
            minimumDurationSeconds = minimumDurationSeconds,
            onMinimumDurationSecondsChange = onMinimumDurationSecondsChange,
            includedFolders = includedFolders,
            onIncludedFoldersChange = onIncludedFoldersChange,
            onAddIncludedFolder = { includedFolderPickerLauncher.launch(null) },
            excludedFolders = excludedFolders,
            onExcludedFoldersChange = onExcludedFoldersChange,
            onAddExcludedFolder = { excludedFolderPickerLauncher.launch(null) },
            sheetState = scanSheetState,
            onDismiss = { showScanSheet = false },
            onPrimaryAction = {
                if (hasStoragePermission) {
                    viewModel.scanDevice(scanConfig)
                } else {
                    permissionLauncher.launch(storagePermission)
                }
            },
        )
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "localSongTopBar",
            ) { searching ->
                if (searching) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = { isSearchActive = false },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(text = stringResource(R.string.search_library))
                                },
                                leadingIcon = {
                                    IconButton(
                                        onClick = {
                                            query = ""
                                            isSearchActive = false
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.arrow_back),
                                            contentDescription = stringResource(R.string.back_button_desc),
                                        )
                                    }
                                },
                                trailingIcon =
                                    if (query.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { query = "" }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.close),
                                                    contentDescription = stringResource(R.string.close),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 4.dp),
                    ) {}
                } else {
                    LargeFlexibleTopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.local_history),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = navController::navigateUp) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back),
                                    contentDescription = null,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            IconButton(onClick = { showScanSheet = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings),
                                    contentDescription = stringResource(R.string.settings),
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                            ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            item(
                key = "controls",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                LocalSongControlsCard(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    visibleSongCount = visibleSongs.size,
                    shuffleEnabled = queueItems.isNotEmpty(),
                    onSortTypeChange = { onSortTypeNameChange(it.name) },
                    onSortDescendingChange = onSortDescendingChange,
                    onShuffleClick = {
                        if (queueItems.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title =
                                        if (query.isBlank()) {
                                            context.getString(R.string.local_history)
                                            } else {
                                            context.getString(R.string.queue_searched_songs)
                                        },
                                    items = queueItems.shuffled(),
                                ),
                            )
                        }
                    },
                )
            }

            if (visibleSongs.isEmpty()) {
                item(
                    key = "empty",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    LibraryEmptyState(
                        iconRes = if (query.isBlank()) R.drawable.music_note else R.drawable.ic_search,
                        titleRes = if (query.isBlank()) R.string.local_songs_empty_title else R.string.local_songs_no_matches_title,
                        subtitleRes = if (query.isBlank()) R.string.local_songs_empty_desc else R.string.local_songs_no_matches_desc,
                        modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 24.dp),
                    )
                }
            } else {
                itemsIndexed(
                    items = visibleSongs,
                    key = { index, item -> "${item.id}_$index" },
                    contentType = { _, _ -> CONTENT_TYPE_SONG },
                ) { index, song ->
                    val isActive = song.id == mediaMetadata?.id
                    SongListItem(
                        song = song,
                        showInLibraryIcon = false,
                        showDownloadIcon = false,
                        showSongIconPlaceholder = true,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                modifier = Modifier.size(SettingsDimensions.RowIconSize),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title =
                                                        if (query.isBlank()) {
                                                            context.getString(R.string.local_history)
                                                        } else {
                                                            context.getString(R.string.queue_searched_songs)
                                                        },
                                                    items = queueItems,
                                                    startIndex = index,
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptics.longPress()
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ).animateItem(),
                    )
                }
            }
        }
    }
}
