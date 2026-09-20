/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AlbumFilter
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
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

@Composable
fun AlbumsControlsRow(
    filter: AlbumFilter,
    sortType: AlbumSortType,
    sortDescending: Boolean,
    isGridView: Boolean,
    onFilterChange: (AlbumFilter) -> Unit,
    onSortTypeChange: (AlbumSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onViewModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val yumaColors = LocalYumaColors.current

    Row(
        modifier =
            modifier
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
                            stringResource(R.string.newest_first)
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
                            stringResource(R.string.most_tracks)
                        } else {
                            stringResource(R.string.least_tracks)
                        }
                    }

                    AlbumSortType.LENGTH -> {
                        if (sortDescending) {
                            stringResource(R.string.longest_duration)
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
                                onFilterChange(AlbumFilter.LIKED)
                                onSortTypeChange(type)
                                if (type == AlbumSortType.NAME) onSortDescendingChange(false)
                                showSortMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_downloaded)) },
                        onClick = {
                            onFilterChange(AlbumFilter.DOWNLOADED_FULL)
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
                            stringResource(R.string.sort_descending)
                        } else {
                            stringResource(R.string.sort_ascending)
                        },
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

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
                        .yumaClickable { onViewModeChange(false) },
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
                        .yumaClickable { onViewModeChange(true) },
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
}

@Composable
fun FeaturedAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yumaColors = LocalYumaColors.current

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
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
                    onClick = onPlayClick,
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
                    onClick = onMenuClick,
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

@Composable
fun FeaturedAlbumCard(
    album: Album,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    FeaturedAlbumCard(
        album = album,
        onClick = { navController.navigate("album/${album.id}") },
        onPlayClick = {
            coroutineScope.launch {
                database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                    playerConnection?.playQueue(LocalAlbumRadio(albumWithSongs))
                }
            }
        },
        onMenuClick = {
            menuState.show {
                AlbumMenu(
                    originalAlbum = album,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                .yumaCombinedClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                    onLongClick = onLongClick,
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
                        .clickable(onClick = onPlayClick),
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

@Composable
fun AlbumGridItem(
    album: Album,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    AlbumGridItem(
        album = album,
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
        onPlayClick = {
            coroutineScope.launch {
                database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                    playerConnection?.playQueue(LocalAlbumRadio(albumWithSongs))
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun AlbumListRow(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yumaColors = LocalYumaColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaCombinedClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                    backgroundColor = yumaColors.glassBackground,
                    borderColor = yumaColors.glassBorder,
                    position = YumaSegmentPosition.Single,
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
            onClick = onPlayClick,
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

@Composable
fun AlbumListRow(
    album: Album,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    AlbumListRow(
        album = album,
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
        onPlayClick = {
            coroutineScope.launch {
                database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                    playerConnection?.playQueue(LocalAlbumRadio(albumWithSongs))
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun AlbumsEmptySection(modifier: Modifier = Modifier) {
    LibraryEmptyState(
        iconRes = R.drawable.album,
        titleRes = R.string.no_results_found,
        subtitleRes = R.string.library_albums_subtitle,
        modifier = modifier.padding(vertical = 24.dp),
    )
}
