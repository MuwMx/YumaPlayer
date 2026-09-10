/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import moe.rukamori.archivetune.ui.component.horizontalFadingEdge
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.AlbumGridItem
import moe.rukamori.archivetune.ui.component.ArtistGridItem
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongGridItem
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.SpeedDialGridItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import moe.rukamori.archivetune.ui.utils.SnapLayoutInfoProvider as buildSnapLayoutInfoProvider

private const val SpeedDialGridRows = 3
private const val SpeedDialGridColumns = 3
private const val SpeedDialItemsPerPage = SpeedDialGridRows * SpeedDialGridColumns

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedDialSection(
    speedDialItems: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberYumaHaptics()

    data class SpeedDialTile(
        val key: String,
        val localItem: LocalItem?,
        val ytItem: YTItem?,
    )

    val distinctSpeedDial =
        remember(speedDialItems) {
            speedDialItems
                .distinctBy {
                    when (it) {
                        is Song -> "song_${it.id}"
                        is Album -> "album_${it.id}"
                        is Artist -> "artist_${it.id}"
                        is Playlist -> "playlist_${it.id}"
                    }
                }.take(24)
        }
    val speedDialSongs = remember(distinctSpeedDial) { distinctSpeedDial.filterIsInstance<Song>() }
    val speedDialSongIndexById =
        remember(speedDialSongs) {
            speedDialSongs.mapIndexed { index, song -> song.id to index }.toMap()
        }
    val spacing = 10.dp

    val tiles =
        remember(distinctSpeedDial) {
            buildList {
                distinctSpeedDial.forEach { localItem ->
                    val key =
                        when (localItem) {
                            is Song -> "song_${localItem.id}"
                            is Album -> "album_${localItem.id}"
                            is Artist -> "artist_${localItem.id}"
                            is Playlist -> "playlist_${localItem.id}"
                        }
                    val ytItem =
                        when (localItem) {
                            is Song -> {
                                SongItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rukamori.archivetune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    thumbnail = localItem.song.thumbnailUrl.orEmpty(),
                                    explicit = localItem.song.explicit,
                                )
                            }

                            is Album -> {
                                AlbumItem(
                                    browseId = localItem.id,
                                    playlistId = localItem.album.playlistId.orEmpty(),
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rukamori.archivetune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    year = localItem.album.year,
                                    thumbnail = localItem.album.thumbnailUrl.orEmpty(),
                                )
                            }

                            is Artist -> {
                                ArtistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    thumbnail = localItem.artist.thumbnailUrl,
                                    channelId = localItem.artist.channelId,
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )
                            }

                            is Playlist -> {
                                PlaylistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    author = null,
                                    songCountText = localItem.songCount.toString(),
                                    thumbnail = localItem.thumbnails.firstOrNull(),
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                    isEditable = localItem.playlist.isEditable,
                                )
                            }
                        }
                    add(SpeedDialTile(key = key, localItem = localItem, ytItem = ytItem))
                }
                add(SpeedDialTile(key = "random", localItem = null, ytItem = null))
            }
        }
    val tilePages =
        remember(tiles) {
            tiles.chunked(SpeedDialItemsPerPage)
        }
    val visibleGridRows =
        remember(tilePages) {
            if (tilePages.size == 1) {
                ((tilePages.first().size + SpeedDialGridColumns - 1) / SpeedDialGridColumns)
                    .coerceIn(1, SpeedDialGridRows)
            } else {
                SpeedDialGridRows
            }
        }
    val pagerState =
        rememberPagerState(
            pageCount = { tilePages.size },
        )

    fun playSpeedDialQueue(startIndex: Int) {
        if (speedDialSongs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = context.getString(R.string.speed_dial),
                items = speedDialSongs.map { it.toMediaItem() },
                startIndex = startIndex,
            ),
        )
    }

    val selectedDotIndex by
        remember(pagerState, tilePages) {
            derivedStateOf {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt()
                    .coerceIn(0, (tilePages.size - 1).coerceAtLeast(0))
            }
        }
    val motionScheme = MaterialTheme.motionScheme

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier =
            modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
            ) {
                val tileSize = (maxWidth - spacing * (SpeedDialGridColumns - 1)) / SpeedDialGridColumns
                val gridHeight = (tileSize * visibleGridRows) + (spacing * (visibleGridRows - 1))

                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fill,
                    pageSpacing = spacing,
                    key = { page -> tilePages[page].firstOrNull()?.key ?: "speed_dial_page_$page" },
                    verticalAlignment = Alignment.Top,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                ) { page ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        tilePages[page]
                            .chunked(SpeedDialGridColumns)
                            .forEach { rowTiles ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    rowTiles.forEach { tile ->
                                        val localItem = tile.localItem
                                        val ytItem = tile.ytItem
                                        if (localItem == null || ytItem == null) {
                                            SpeedDialRandomTile(
                                                onClick = {
                                                    if (speedDialSongs.isNotEmpty()) {
                                                        playSpeedDialQueue(Random.nextInt(speedDialSongs.size))
                                                    }
                                                },
                                                modifier = Modifier.size(tileSize),
                                            )
                                        } else {
                                            val isActive =
                                                when (localItem) {
                                                    is Song -> localItem.id == mediaMetadata?.id
                                                    is Album -> localItem.id == mediaMetadata?.album?.id
                                                    is Artist -> false
                                                    is Playlist -> false
                                                }
                                            val songIndex =
                                                if (localItem is Song) speedDialSongIndexById[localItem.id] ?: 0 else 0

                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(tileSize)
                                                        .clip(MaterialTheme.shapes.large)
                                                        .focusable()
                                                        .combinedClickable(
                                                            onClick = {
                                                                when (localItem) {
                                                                    is Song -> {
                                                                        if (isActive) {
                                                                            playerConnection.player.togglePlayPause()
                                                                        } else {
                                                                            playSpeedDialQueue(songIndex)
                                                                        }
                                                                    }

                                                                    is Album -> {
                                                                        navController.navigate("album/${localItem.id}")
                                                                    }

                                                                    is Artist -> {
                                                                        navController.navigate("artist/${localItem.id}")
                                                                    }

                                                                    is Playlist -> {
                                                                        navController.navigate("local_playlist/${localItem.id}")
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptics.longPress()
                                                                menuState.show {
                                                                    when (localItem) {
                                                                        is Song -> {
                                                                            SongMenu(
                                                                                originalSong = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Album -> {
                                                                            AlbumMenu(
                                                                                originalAlbum = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Artist -> {
                                                                            ArtistMenu(
                                                                                originalArtist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Playlist -> {
                                                                            PlaylistMenu(
                                                                                playlist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                        ),
                                            ) {
                                                SpeedDialGridItem(
                                                    item = ytItem,
                                                    isPinned = true,
                                                    isActive = isActive,
                                                    isPlaying = isPlaying,
                                                )
                                            }
                                        }
                                    }
                                    repeat(SpeedDialGridColumns - rowTiles.size) {
                                        Spacer(modifier = Modifier.size(tileSize))
                                    }
                                }
                            }
                    }
                }
            }

            if (tilePages.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    repeat(tilePages.size) { index ->
                        val isSelected = index == selectedDotIndex
                        val dotColor by animateColorAsState(
                            targetValue =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            animationSpec = motionScheme.defaultEffectsSpec(),
                            label = "speedDialDotColor",
                        )
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 8.dp,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            label = "speedDialDotWidth",
                        )
                        Surface(
                            color = dotColor,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier =
                                Modifier
                                    .width(dotWidth)
                                    .height(8.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialRandomTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier =
            modifier
                .aspectRatio(1f)
                .combinedClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp),
                    ) {}
                }
            }
        }
    }
}

