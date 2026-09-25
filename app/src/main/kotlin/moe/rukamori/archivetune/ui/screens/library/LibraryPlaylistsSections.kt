/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun ControlRow(
    sortType: PlaylistSortType,
    sortDescending: Boolean,
    isGridView: Boolean,
    locked: Boolean,
    showHidden: Boolean,
    onSortTypeChange: (PlaylistSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onGridViewChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    filterContent: (@Composable () -> Unit)? = null,
) {
    val yumaColors = LocalYumaColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Sort dropdown
            var showSortMenu by remember { mutableStateOf(false) }
            val currentSortLabel =
                when (sortType) {
                    PlaylistSortType.CREATE_DATE -> {
                        stringResource(R.string.recently_added)
                    }

                    PlaylistSortType.NAME -> {
                        if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                    }

                    PlaylistSortType.SONG_COUNT -> {
                        stringResource(R.string.tracks_count_label)
                    }

                    PlaylistSortType.LAST_UPDATED -> {
                        stringResource(R.string.recently_updated)
                    }

                    PlaylistSortType.CUSTOM -> {
                        stringResource(R.string.custom_order)
                    }
                }

            val showSortDirection = sortType != PlaylistSortType.CUSTOM
            val sortDirectionRotation by animateFloatAsState(
                targetValue = if (sortDescending) 0f else 180f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "PlaylistSortDirectionRotation",
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        PlaylistSortType.entries.forEach { type ->
                            val label =
                                when (type) {
                                    PlaylistSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                    PlaylistSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                    PlaylistSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                    PlaylistSortType.LAST_UPDATED -> stringResource(R.string.recently_updated)
                                    PlaylistSortType.CUSTOM -> stringResource(R.string.custom_order)
                                }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSortTypeChange(type)
                                    if (type == PlaylistSortType.NAME) {
                                        onSortDescendingChange(false)
                                    }
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }

                if (showSortDirection) {
                    Spacer(modifier = Modifier.width(6.dp))
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
                            painter = painterResource(id = R.drawable.arrow_downward),
                            contentDescription =
                                stringResource(
                                    if (sortDescending) {
                                        R.string.sort_order_descending
                                    } else {
                                        R.string.sort_order_ascending
                                    },
                                ),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = sortDirectionRotation },
                        )
                    }
                }
            }

            // Right: list/grid toggle & add button
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sortType == PlaylistSortType.CUSTOM) {
                    Box(
                        modifier =
                            Modifier
                                .size(SettingsDimensions.LibraryChipHeight)
                                .yumaClickable { onLockedChange(!locked) }
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
                            painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // List/Grid Toggle
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
                                .yumaClickable { onGridViewChange(false) },
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
                                .yumaClickable { onGridViewChange(true) },
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

                Spacer(modifier = Modifier.width(8.dp))

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
                                .background(if (showHidden) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .yumaClickable { onShowHiddenChange(!showHidden) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.visibility_off),
                            contentDescription = stringResource(R.string.show_hidden_playlists),
                            tint = if (showHidden) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .yumaClickable { onCreatePlaylistClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.add),
                            contentDescription = stringResource(R.string.create_playlist),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        val playlistTagFilterContent = filterContent
        if (playlistTagFilterContent != null) {
            playlistTagFilterContent()
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun PlaylistsControlRow(
    sortType: PlaylistSortType,
    sortDescending: Boolean,
    isGridView: Boolean,
    locked: Boolean,
    showHidden: Boolean,
    onSortTypeChange: (PlaylistSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onGridViewChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    filterContent: (@Composable () -> Unit)? = null,
) = ControlRow(
    sortType = sortType,
    sortDescending = sortDescending,
    isGridView = isGridView,
    locked = locked,
    showHidden = showHidden,
    onSortTypeChange = onSortTypeChange,
    onSortDescendingChange = onSortDescendingChange,
    onLockedChange = onLockedChange,
    onGridViewChange = onGridViewChange,
    onShowHiddenChange = onShowHiddenChange,
    onCreatePlaylistClick = onCreatePlaylistClick,
    modifier = modifier,
    filterContent = filterContent,
)

fun mergeVisiblePlaylistOrder(
    currentOrder: List<Playlist>,
    visibleOrder: List<Playlist>,
): List<Playlist> {
    if (visibleOrder.isEmpty()) return currentOrder

    val visibleIds = visibleOrder.mapTo(HashSet(visibleOrder.size)) { playlist -> playlist.id }
    val reorderedVisible = visibleOrder.iterator()
    return currentOrder.map { playlist ->
        if (playlist.id in visibleIds) {
            reorderedVisible.next()
        } else {
            playlist
        }
    }
}

fun openPlaylist(
    navController: NavController,
    playlist: Playlist,
) {
    if (!playlist.playlist.isEditable && playlist.playlist.browseId?.startsWith("VL") == true && (playlist.songCount == 0 || playlist.playlist.remoteSongCount == 0)) {
        navController.navigate("online_playlist/${playlist.playlist.browseId}")
    } else {
        navController.navigate("local_playlist/${playlist.id}")
    }
}

@Composable
fun triggerPlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    menuState: MenuState,
) {
    if (playlist.playlist.isEditable || playlist.songCount != 0) {
        PlaylistMenu(
            playlist = playlist,
            coroutineScope = coroutineScope,
            onDismiss = menuState::dismiss,
        )
    } else {
        playlist.playlist.browseId?.let { browseId ->
            YouTubePlaylistMenu(
                playlist =
                    PlaylistItem(
                        id = browseId,
                        title = playlist.playlist.name,
                        author = null,
                        songCountText = null,
                        thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                        playEndpoint =
                            WatchEndpoint(
                                playlistId = browseId,
                                params = playlist.playlist.playEndpointParams,
                            ),
                        shuffleEndpoint =
                            WatchEndpoint(
                                playlistId = browseId,
                                params = playlist.playlist.shuffleEndpointParams,
                            ),
                        radioEndpoint =
                            WatchEndpoint(
                                playlistId = "RDAMPL$browseId",
                                params = playlist.playlist.radioEndpointParams,
                            ),
                        isEditable = false,
                    ),
                coroutineScope = coroutineScope,
                onDismiss = menuState::dismiss,
            )
        }
    }
}

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

@Composable
fun PlaylistListCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: YumaSegmentPosition = YumaSegmentPosition.Single,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    val yumaColors = LocalYumaColors.current
    val hiddenAlpha = if (playlist.playlist.isHidden) 0.45f else 1f

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = hiddenAlpha
                }
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                )
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.LibrarySheetRadius),
                    backgroundColor = yumaColors.glassBackground,
                    borderColor = yumaColors.glassBorder,
                    position = position,
                )
                .clip(RoundedCornerShape(SettingsDimensions.LibrarySheetRadius))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = playlist.thumbnails.getOrNull(0),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val tagText =
                    if (playlist.playlist.isEditable) {
                        stringResource(
                            R.string.personal_label,
                        )
                    } else {
                        stringResource(R.string.youtube_synced)
                    }
                val tagColor = if (playlist.playlist.isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tagText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tagColor,
                    )
                }

                if (playlist.playlist.isHidden) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.visibility_off),
                        contentDescription = stringResource(R.string.hide_playlist),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // Play Button
        IconButton(
            onClick = onPlay,
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

        Spacer(modifier = Modifier.width(4.dp))

        // Options Button
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(SettingsDimensions.LibraryChipHeight),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.more_vert),
                contentDescription = stringResource(R.string.options_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDragHandle) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = dragHandleModifier.size(SettingsDimensions.LibraryChipHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.drag_handle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yumaColors = LocalYumaColors.current
    val hiddenAlpha = if (playlist.playlist.isHidden) 0.45f else 1f

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = hiddenAlpha
                }
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
                .padding(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius)),
        ) {
            AsyncImage(
                model = playlist.thumbnails.getOrNull(0),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Play overlay on bottom right of grid cover
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (playlist.playlist.isHidden) {
                Icon(
                    painter = painterResource(id = R.drawable.visibility_off),
                    contentDescription = stringResource(R.string.hide_playlist),
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
