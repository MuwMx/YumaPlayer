/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.SongFilter
import moe.rukamori.archivetune.constants.SongFilterKey
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibrarySongsViewModel

private const val CONTENT_TYPE_SONG = "song"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)
    val lazyListState = rememberLazyListState()

    LaunchedEffect(filter) {
        if (songs.isEmpty() && !isRefreshing) {
            viewModel.refresh(filter)
        }
    }

    val wrappedSongs = remember(songs) {
        songs.map { item -> ItemWrapper(item) }.toMutableStateList()
    }

    val hideExplicit by rememberPreference(HideExplicitKey, defaultValue = false)
    val displaySongs = remember(wrappedSongs, hideExplicit) {
        if (hideExplicit) wrappedSongs.filter { !it.item.song.explicit } else wrappedSongs
    }

    val totalDurationSec = remember(displaySongs) { displaySongs.sumOf { it.item.song.duration } }
    val totalDurationText = remember(totalDurationSec) {
        if (totalDurationSec <= 0) {
            ""
        } else {
            val days = totalDurationSec / 86400
            var remaining = totalDurationSec % 86400
            val hours = remaining / 3600
            remaining %= 3600
            val minutes = remaining / 60
            val seconds = remaining % 60

            when {
                days > 0 -> "${days}d ${hours}h ${minutes}m"
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(filter) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongSubFilterChip(
                    label = stringResource(R.string.filter_liked),
                    selected = filter == SongFilter.LIKED,
                    onClick = { filter = SongFilter.LIKED },
                )
                SongSubFilterChip(
                    label = stringResource(R.string.filter_downloaded),
                    selected = filter == SongFilter.DOWNLOADED,
                    onClick = { filter = SongFilter.DOWNLOADED },
                )
                SongSubFilterChip(
                    label = stringResource(R.string.all_songs),
                    selected = filter == SongFilter.LIBRARY,
                    onClick = { filter = SongFilter.LIBRARY },
                )

                Spacer(modifier = Modifier.width(8.dp))

                var showSortMenu by remember { mutableStateOf(false) }
                val currentSortLabel = when (sortType) {
                    SongSortType.CREATE_DATE -> {
                        if (sortDescending) {
                            stringResource(R.string.newest_first)
                        } else {
                            stringResource(R.string.oldest_first)
                        }
                    }
                    SongSortType.NAME -> {
                        if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                    }
                    SongSortType.ARTIST -> stringResource(R.string.sort_artist)
                    SongSortType.PLAY_TIME -> stringResource(R.string.most_played_sort)
                }

                Box {
                    val yumaColors = LocalYumaColors.current
                    Row(
                        modifier = Modifier
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
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            ),
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
                        SongSortType.entries.forEach { type ->
                            val label = when (type) {
                                SongSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                SongSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                SongSortType.ARTIST -> stringResource(R.string.sort_artist)
                                SongSortType.PLAY_TIME -> stringResource(R.string.most_played_sort)
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSortTypeChange(type)
                                    if (type == SongSortType.NAME) onSortDescendingChange(false)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
                val yumaColors = LocalYumaColors.current
                Box(
                    modifier = Modifier
                        .height(SettingsDimensions.LibraryChipHeight)
                        .yumaClickable { onSortDescendingChange(!sortDescending) }
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = yumaColors.glassBackground,
                            borderColor = yumaColors.glassBorder,
                            strokeWidth = SettingsDimensions.GlassBorderThickness,
                        )
                        .clip(CircleShape)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                        ),
                        contentDescription = if (sortDescending) {
                            stringResource(R.string.sort_descending)
                        } else {
                            stringResource(R.string.sort_ascending)
                        },
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "collection_spotlight") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                            .yumaGlassCard(
                                shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                                position = YumaSegmentPosition.Single,
                            )
                            .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                            .padding(20.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.your_collection),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val songsCountText = "${displaySongs.size} ${stringResource(if (displaySongs.size == 1) R.string.song_singular else R.string.songs)}"
                                    Text(
                                        text = if (totalDurationText.isNotEmpty()) {
                                            "$songsCountText • $totalDurationText"
                                        } else {
                                            songsCountText
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Button(
                                    onClick = {
                                        if (displaySongs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.queue_all_songs),
                                                    items = displaySongs.map { it.item.toMediaItem() },
                                                ),
                                            )
                                        }
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.play),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (displaySongs.isEmpty()) {
                    item(key = "empty_state") {
                        val filterNameRes = when (filter) {
                            SongFilter.LIKED -> R.string.filter_liked
                            SongFilter.DOWNLOADED -> R.string.filter_downloaded
                            SongFilter.LIBRARY -> R.string.all_songs
                        }
                        LibraryEmptyState(
                            iconRes = when (filter) {
                                SongFilter.LIKED -> R.drawable.favorite
                                SongFilter.DOWNLOADED -> R.drawable.offline
                                SongFilter.LIBRARY -> R.drawable.music_note
                            },
                            title = stringResource(R.string.no_results_found),
                            subtitle = stringResource(filterNameRes),
                            modifier = Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 24.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = displaySongs,
                        key = { index, songWrapper -> "${songWrapper.item.id}_$index" },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, songWrapper ->
                        val song = songWrapper.item
                        val isActive = song.id == mediaMetadata?.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .animateItem()
                                .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                                .then(
                                    if (isActive) {
                                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    } else {
                                        Modifier
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            val visibleSongs = displaySongs.map { it.item }
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.queue_all_songs),
                                                    items = visibleSongs.map { it.toMediaItem() },
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
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ItemThumbnail(
                                thumbnailUrl = song.song.thumbnailUrl,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                shape = RoundedCornerShape(SettingsDimensions.LibrarySmallRadius),
                                placeholderIconRes = R.drawable.music_note,
                                modifier = Modifier.size(52.dp),
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.song.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                    ),
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (isActive && isPlaying) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.graphic_eq),
                                        contentDescription = stringResource(R.string.playing_desc),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                                    )
                                }

                                val durationText = makeTimeString(song.song.duration * 1000L)
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isActive) 0.5f else 0.8f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = durationText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }

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
                                        painter = painterResource(id = R.drawable.more_vert),
                                        contentDescription = null,
                                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
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

@Composable
fun SongSubFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val yumaColors = LocalYumaColors.current
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        yumaColors.glassBackground
    }
    val borderColor = if (selected) {
        Color.Transparent
    } else {
        yumaColors.glassBorder
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .height(SettingsDimensions.LibraryChipHeight)
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(
                shape = CircleShape,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            )
            .clip(CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}
