/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AlbumFilter
import moe.rukamori.archivetune.constants.AlbumFilterKey
import moe.rukamori.archivetune.constants.AlbumSortDescendingKey
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.constants.AlbumSortTypeKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryAlbumsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val yumaColors = LocalYumaColors.current

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            AlbumSortTypeKey,
            AlbumSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    var isGridView by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val featuredAlbum = albums.firstOrNull()

    val filteredAlbums =
        if (hideExplicit) {
            albums.filter { !it.album.explicit }
        } else {
            albums
        }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.sync() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-header controls (Sort dropdown, genres/filters, list/grid toggle)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var showSortMenu by remember { mutableStateOf(false) }
                val currentSortLabel =
                    if (filter == AlbumFilter.DOWNLOADED_FULL) {
                        stringResource(R.string.filter_downloaded)
                    } else {
                        when (sortType) {
                            AlbumSortType.CREATE_DATE -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.newest_first,
                                    )
                                } else {
                                    stringResource(R.string.oldest_first)
                                }
                            }

                            AlbumSortType.NAME -> {
                                if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                            }

                            AlbumSortType.ARTIST -> {
                                stringResource(R.string.sort_artist)
                            }

                            AlbumSortType.YEAR -> {
                                if (sortDescending) stringResource(R.string.newest_year) else stringResource(R.string.oldest_year)
                            }

                            AlbumSortType.SONG_COUNT -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.most_tracks,
                                    )
                                } else {
                                    stringResource(R.string.least_tracks)
                                }
                            }

                            AlbumSortType.LENGTH -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.longest_duration,
                                    )
                                } else {
                                    stringResource(R.string.shortest_duration)
                                }
                            }

                            AlbumSortType.PLAY_TIME -> {
                                stringResource(R.string.most_played_sort)
                            }
                        }
                    }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Row(
                            modifier =
                                Modifier
                                    .height(SettingsDimensions.LibraryChipHeight)
                                    .yumaClickable { showSortMenu = true }
                                    .yumaGlassCard(
                                        shape = CircleShape,
                                        backgroundColor = yumaColors.glassBackground,
                                        borderColor = yumaColors.glassBorder,
                                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                                    )
                                    .clip(CircleShape)
                                    .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentSortLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.expand_more),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            AlbumSortType.entries.forEach { type ->
                                val label =
                                    when (type) {
                                        AlbumSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                        AlbumSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                        AlbumSortType.ARTIST -> stringResource(R.string.sort_artist)
                                        AlbumSortType.YEAR -> stringResource(R.string.year_sort)
                                        AlbumSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                        AlbumSortType.LENGTH -> stringResource(R.string.duration_sort)
                                        AlbumSortType.PLAY_TIME -> stringResource(R.string.most_played_sort)
                                    }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        filter = AlbumFilter.LIKED
                                        onSortTypeChange(type)
                                        if (type == AlbumSortType.NAME) onSortDescendingChange(false)
                                        showSortMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_downloaded)) },
                                onClick = {
                                    filter = AlbumFilter.DOWNLOADED_FULL
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(SettingsDimensions.LibraryChipHeight)
                                .yumaClickable { onSortDescendingChange(!sortDescending) }
                                .yumaGlassCard(
                                    shape = CircleShape,
                                    backgroundColor = yumaColors.glassBackground,
                                    borderColor = yumaColors.glassBorder,
                                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                                )
                                .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    id = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                                ),
                            contentDescription =
                                if (sortDescending) {
                                    stringResource(
                                        R.string.sort_descending,
                                    )
                                } else {
                                    stringResource(R.string.sort_ascending)
                                },
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Grid / List Toggle layout controls
                Row(
                    modifier =
                        Modifier
                            .height(SettingsDimensions.LibraryChipHeight)
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = yumaColors.glassBackground,
                                borderColor = yumaColors.glassBorder,
                                strokeWidth = SettingsDimensions.GlassBorderThickness,
                            )
                            .clip(CircleShape)
                            .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (!isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .yumaClickable { isGridView = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.queue_music),
                            contentDescription = stringResource(R.string.list_view),
                            tint = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .yumaClickable { isGridView = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.album),
                            contentDescription = stringResource(R.string.grid_view),
                            tint = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main albums list or grid layout
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                ) {
                    // Featured Album spotlight card span all 4 columns
                    item(span = { GridItemSpan(4) }, key = "featured_album_card") {
                        featuredAlbum?.let { album ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .yumaClickable(
                                            pressedScale = SettingsAnimations.PressScale,
                                            onClick = {
                                                navController.navigate("album/${album.id}")
                                            },
                                        )
                                        .yumaGlassCard(
                                            shape = RoundedCornerShape(SettingsDimensions.LibrarySheetRadius),
                                            backgroundColor = yumaColors.glassBackground,
                                            borderColor = yumaColors.glassBorder,
                                            position = YumaSegmentPosition.Single,
                                        )
                                        .clip(RoundedCornerShape(SettingsDimensions.LibrarySheetRadius))
                                        .padding(20.dp),
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AsyncImage(
                                            model = album.album.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier =
                                                Modifier
                                                    .size(80.dp)
                                                    .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius)),
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.featured_album_badge),
                                                style =
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp,
                                                    ),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = album.album.title,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = album.artists.joinToString(", ") { it.name },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                                                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                                    }
                                                }
                                            },
                                            shape = CircleShape,
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.play),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.play),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = album,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.more_vert),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredAlbums.isEmpty()) {
                        item(span = { GridItemSpan(4) }, key = "empty_albums_grid") {
                            LibraryEmptyState(
                                iconRes = R.drawable.album,
                                titleRes = R.string.no_results_found,
                                subtitleRes = R.string.library_albums_subtitle,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        items(
                            items = filteredAlbums,
                            key = { it.id },
                            contentType = { "album_grid_item" },
                        ) { album ->
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                                        .yumaCombinedClickable(
                                            pressedScale = SettingsAnimations.PressScale,
                                            onClick = { navController.navigate("album/${album.id}") },
                                            onLongClick = {
                                                haptics.longPress()
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = album,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius)),
                                ) {
                                    AsyncImage(
                                        model = album.album.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .clickable {
                                                    coroutineScope.launch {
                                                        database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                                                            playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                                        }
                                                    }
                                                },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.play),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = album.album.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = album.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else {
                // List View
                LazyColumn(
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(SettingsDimensions.LibraryItemGap),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                ) {
                    if (filteredAlbums.isEmpty()) {
                        item(key = "empty_albums_list") {
                            LibraryEmptyState(
                                iconRes = R.drawable.album,
                                titleRes = R.string.no_results_found,
                                subtitleRes = R.string.library_albums_subtitle,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredAlbums,
                            key = { _, it -> it.id },
                            contentType = { _, _ -> "album_list_item" },
                        ) { index, album ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .yumaCombinedClickable(
                                            pressedScale = SettingsAnimations.PressScale,
                                            onClick = { navController.navigate("album/${album.id}") },
                                            onLongClick = {
                                                haptics.longPress()
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = album,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                        .yumaGlassCard(
                                            shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                                            backgroundColor = yumaColors.glassBackground,
                                            borderColor = yumaColors.glassBorder,
                                            position = YumaSegmentPosition.Single
                                        )
                                        .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                                        .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = album.album.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius)),
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.album.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = album.artists.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                                                playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                            }
                                        }
                                    },
                                    colors =
                                        IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    modifier = Modifier.size(SettingsDimensions.LibraryChipHeight),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.play),
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
