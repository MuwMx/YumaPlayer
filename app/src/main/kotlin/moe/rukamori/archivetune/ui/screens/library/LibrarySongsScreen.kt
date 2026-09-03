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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu
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


    val playerAwareBottomPadding = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 12.dp

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
                    .padding(horizontal = 24.dp, vertical = 8.dp),
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
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentSortLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.expand_more),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSortDescendingChange(!sortDescending) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
            val isDark = isSystemInDarkTheme()

            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = playerAwareBottomPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "collection_spotlight") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                    ),
                                ),
                            )
                            .padding(20.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.your_collection),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    )
                                }

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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                itemsIndexed(
                    items = displaySongs,
                    key = { index, songWrapper -> "${songWrapper.item.id}_$index" },
                    contentType = { _, _ -> CONTENT_TYPE_SONG },
                ) { index, songWrapper ->
                    val song = songWrapper.item
                    val isActive = song.id == mediaMetadata?.id

                    val activeCardColor = ArtworkColorUtils.rememberArtworkCardColor(
                        thumbnailUrl = song.song.thumbnailUrl,
                        fallbackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    val inactiveCardColor = MaterialTheme.colorScheme.surfaceContainerLow

                    val showDivider = isDark && pureBlack && index > 0
                    if (showDivider) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            thickness = 0.5.dp,
                        )
                    }

                    val cornerRadius = if (isActive) 36.dp else 24.dp
                    val topPadding = if (index == 0 || showDivider) 0.dp else 8.dp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topPadding, bottom = 0.dp)
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(
                                if (isActive) activeCardColor else inactiveCardColor,
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val thumbCorner = if (isActive) 26.dp else 10.dp
                        ItemThumbnail(
                            thumbnailUrl = song.song.thumbnailUrl,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            shape = RoundedCornerShape(thumbCorner),
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
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
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
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            val durationText = makeTimeString(song.song.duration * 1000L)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isActive) 0.5f else 0.8f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
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
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.more_vert),
                                    contentDescription = null,
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

@Composable
fun SongSubFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
            color = contentColor,
        )
    }
}
private object ArtworkColorUtils {
    @Composable
    fun rememberArtworkGradient(
        thumbnailUrl: String?,
        fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    ): List<Color> {
        val context = LocalContext.current
        var colors by remember(thumbnailUrl) { mutableStateOf(listOf(fallbackColor, fallbackColor.copy(alpha = 0.5f))) }

        LaunchedEffect(thumbnailUrl) {
            if (thumbnailUrl == null) return@LaunchedEffect
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
                            fallbackColor = fallbackColor.toArgb(),
                        )
                    if (extractedColors.size >= 2) {
                        colors = extractedColors
                    } else if (extractedColors.isNotEmpty()) {
                        colors = listOf(extractedColors[0], extractedColors[0].copy(alpha = 0.5f))
                    }
                }
            }
        }
        return colors
    }

    @Composable
    fun rememberArtworkCardColor(
        thumbnailUrl: String?,
        fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    ): Color {
        val gradientColors =
            rememberArtworkGradient(
                thumbnailUrl = thumbnailUrl,
                fallbackColor = fallbackColor,
            )
        val surfaceColor = MaterialTheme.colorScheme.surface
        val useDarkTheme = remember(surfaceColor) { ColorUtils.calculateLuminance(surfaceColor.toArgb()) < 0.5 }
        val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

        return remember(gradientColors, useDarkTheme, pureBlack) {
            val baseColor = gradientColors.firstOrNull() ?: fallbackColor
            val baseArgb = baseColor.toArgb()
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(baseArgb, hsv)
            val hue = hsv[0]

            if (useDarkTheme) {
                // Issue 6/3 fix: increased brightness for visibility in pure black mode
                val s = (hsv[1] * 0.45f).coerceIn(0.06f, 0.20f)
                val v = if (pureBlack) 0.18f else 0.12f
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
            } else {
                val s = (hsv[1] * 0.30f).coerceIn(0.03f, 0.12f)
                val v = 0.95f
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
            }
        }
    }
}
