/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.ChipSortTypeKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.constants.ShowTagsInLibraryKey
import moe.rukamori.archivetune.db.entities.TagEntity
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.ui.component.LibraryFilterChipBar
import moe.rukamori.archivetune.ui.component.TagsManagementDialog
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun LibraryScreen(
    navController: NavController,
    spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel(),
) {
    val defaultFilter by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val database = LocalDatabase.current
    val (selectedTagIds, onSelectedTagIdsChange) = rememberPlaylistTagFilterState(database)
    val allTags by database.allTags().collectAsState(initial = emptyList())
    val (showTagsInLibrary) = rememberPreference(ShowTagsInLibraryKey, defaultValue = true)
    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, defaultValue = true)
    var showTagsManagementDialog by rememberSaveable { mutableStateOf(false) }
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()
    val activeSelectedTagIds = if (showTagsInLibrary) selectedTagIds else emptySet()
    val libraryFilters =
        remember(showSpotifyPlaylists) {
            if (showSpotifyPlaylists) {
                listOf(
                    LibraryFilter.LIBRARY,
                    LibraryFilter.PLAYLISTS,
                    LibraryFilter.SPOTIFY,
                    LibraryFilter.SONGS,
                    LibraryFilter.ARTISTS,
                    LibraryFilter.ALBUMS,
                )
            } else {
                listOf(
                    LibraryFilter.LIBRARY,
                    LibraryFilter.PLAYLISTS,
                    LibraryFilter.SONGS,
                    LibraryFilter.ARTISTS,
                    LibraryFilter.ALBUMS,
                )
            }
        }

    if (showTagsManagementDialog) {
        TagsManagementDialog(
            onDismiss = { showTagsManagementDialog = false },
        )
    }

    val pagerState =
        rememberPagerState(
            initialPage = libraryFilters.indexOf(defaultFilter).takeIf { it >= 0 } ?: 0,
        ) { libraryFilters.size }

    val coroutineScope = rememberCoroutineScope()

    val currentFilter = libraryFilters.getOrElse(pagerState.currentPage) { LibraryFilter.LIBRARY }

    val density = LocalDensity.current
    val headerState = rememberLibraryCollapsingHeaderState(LibraryHeaderHeight)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = AppBarHeight)
                    .nestedScroll(headerState.nestedScrollConnection),
        ) {
            LibraryCollapsingHeader(currentFilter = currentFilter, state = headerState)

            LaunchedEffect(defaultFilter, libraryFilters) {
                val selectedFilter = defaultFilter.takeIf { it in libraryFilters } ?: LibraryFilter.LIBRARY
                val selectedPage = libraryFilters.indexOf(selectedFilter).takeIf { it >= 0 } ?: 0
                if (pagerState.currentPage != selectedPage) {
                    pagerState.scrollToPage(selectedPage)
                }
            }

            LaunchedEffect(pagerState.currentPage) {
                headerState.reset()
            }

            LibraryFilterChipBar(
                selected = currentFilter,
                onSelected = { filter ->
                    val page = libraryFilters.indexOf(filter)
                    if (page >= 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    }
                },
                chips = libraryFilters,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) { page ->
                when (libraryFilters.getOrElse(page) { LibraryFilter.LIBRARY }) {
                    LibraryFilter.LIBRARY -> {
                        LibraryMixScreen(
                            navController = navController,
                            filterContent =
                                if (showTagsInLibrary) {
                                    {
                                        PlaylistTagFilterRow(
                                            tags = allTags,
                                            selectedTagIds = selectedTagIds,
                                            onSelectedTagIdsChange = onSelectedTagIdsChange,
                                            onManageTagsClick = { showTagsManagementDialog = true },
                                        )
                                    }
                                } else {
                                    null
                                },
                            selectedTagIds = activeSelectedTagIds,
                            onTabSelected = { targetFilter ->
                                coroutineScope.launch {
                                    val targetPage = libraryFilters.indexOf(targetFilter)
                                    pagerState.animateScrollToPage(targetPage.takeIf { it >= 0 } ?: 0)
                                }
                            },
                        )
                    }

                    LibraryFilter.PLAYLISTS -> {
                        LibraryPlaylistsScreen(
                            navController = navController,
                            filterContent =
                                if (showTagsInLibrary) {
                                    {
                                        PlaylistTagFilterRow(
                                            tags = allTags,
                                            selectedTagIds = selectedTagIds,
                                            onSelectedTagIdsChange = onSelectedTagIdsChange,
                                            onManageTagsClick = { showTagsManagementDialog = true },
                                        )
                                    }
                                } else {
                                    null
                                },
                            selectedTagIds = activeSelectedTagIds,
                        )
                    }

                    LibraryFilter.SPOTIFY -> {
                        LibrarySpotifyPlaylistsScreen(navController = navController)
                    }

                    LibraryFilter.SONGS -> {
                        LibrarySongsScreen(
                            navController = navController,
                            onDeselect = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        )
                    }

                    LibraryFilter.ARTISTS -> {
                        LibraryArtistsScreen(
                            navController = navController,
                            onDeselect = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        )
                    }

                    LibraryFilter.ALBUMS -> {
                        LibraryAlbumsScreen(
                            navController = navController,
                            onDeselect = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTagFilterRow(
    tags: List<TagEntity>,
    selectedTagIds: Set<String>,
    onSelectedTagIdsChange: (Set<String>) -> Unit,
    onManageTagsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all_playlist_tags", contentType = "playlist_tag_filter_action") {
            PlaylistTagFilterChip(
                label = stringResource(R.string.filter_all),
                selected = selectedTagIds.isEmpty(),
                iconRes = R.drawable.filter_alt,
                onClick = { onSelectedTagIdsChange(emptySet()) },
            )
        }

        items(
            items = tags,
            key = TagEntity::id,
            contentType = { "playlist_tag_filter" },
        ) { tag ->
            PlaylistTagFilterChip(
                label = tag.name,
                selected = tag.id in selectedTagIds,
                accentColor =
                    remember(tag.color) {
                        runCatching { Color(tag.color.toColorInt()) }.getOrDefault(Color.Unspecified)
                    },
                onClick = {
                    val nextSelection =
                        if (tag.id in selectedTagIds) {
                            selectedTagIds - tag.id
                        } else {
                            selectedTagIds + tag.id
                        }
                    onSelectedTagIdsChange(nextSelection)
                },
            )
        }

        item(key = "manage_playlist_tags", contentType = "playlist_tag_filter_action") {
            PlaylistTagFilterChip(
                label = stringResource(R.string.manage_tags),
                selected = false,
                iconRes = R.drawable.add,
                onClick = onManageTagsClick,
            )
        }
    }
}

@Composable
private fun PlaylistTagFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val resolvedAccentColor =
        if (accentColor == Color.Unspecified) {
            MaterialTheme.colorScheme.primary
        } else {
            accentColor
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue =
            if (isPressed) {
                0.92f
            } else if (selected) {
                1.05f
            } else {
                1.0f
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PlaylistTagFilterChipScale",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "PlaylistTagFilterChipContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "PlaylistTagFilterChipContentColor",
    )

    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (selected) contentColor else resolvedAccentColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

private val LibraryHeaderHeight = 90.dp

@Stable
private class LibraryCollapsingHeaderState(
    private val maxHeaderOffsetPx: Float,
) {
    var headerOffsetPx by mutableFloatStateOf(0f)
        private set

    val progress: Float
        get() = 1f + (headerOffsetPx / maxHeaderOffsetPx)

    val nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                // Scrolling down the page (dragging finger up, delta < 0): collapse header first
                if (delta < 0) {
                    val newOffset = headerOffsetPx + delta
                    val oldOffset = headerOffsetPx
                    headerOffsetPx = newOffset.coerceIn(-maxHeaderOffsetPx, 0f)
                    val consumedY = headerOffsetPx - oldOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                // Scrolling up the page (dragging finger down, delta > 0): expand header ONLY if list is at top
                if (delta > 0) {
                    val newOffset = headerOffsetPx + delta
                    val oldOffset = headerOffsetPx
                    headerOffsetPx = newOffset.coerceIn(-maxHeaderOffsetPx, 0f)
                    val consumedY = headerOffsetPx - oldOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }

    fun reset() {
        headerOffsetPx = 0f
    }
}

@Composable
private fun rememberLibraryCollapsingHeaderState(maxHeaderHeight: Dp): LibraryCollapsingHeaderState {
    val density = LocalDensity.current
    val maxHeaderOffsetPx = with(density) { maxHeaderHeight.toPx() }
    return remember(maxHeaderOffsetPx) {
        LibraryCollapsingHeaderState(maxHeaderOffsetPx = maxHeaderOffsetPx)
    }
}

@Composable
private fun LibraryCollapsingHeader(
    currentFilter: LibraryFilter,
    state: LibraryCollapsingHeaderState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val headerHeight = LibraryHeaderHeight + with(density) { state.headerOffsetPx.toDp() }

    val headerTitle =
        when (currentFilter) {
            LibraryFilter.LIBRARY -> stringResource(R.string.library_title)
            LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
            LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_playlists)
            LibraryFilter.SONGS -> stringResource(R.string.songs)
            LibraryFilter.ARTISTS -> stringResource(R.string.artists)
            LibraryFilter.ALBUMS -> stringResource(R.string.albums)
            else -> stringResource(R.string.library_title)
        }

    val headerSubtitle =
        when (currentFilter) {
            LibraryFilter.LIBRARY -> stringResource(R.string.library_subtitle)
            LibraryFilter.PLAYLISTS -> stringResource(R.string.library_playlists_subtitle)
            LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_show_playlist_desc)
            LibraryFilter.SONGS -> stringResource(R.string.library_songs_subtitle)
            LibraryFilter.ARTISTS -> stringResource(R.string.library_artists_subtitle)
            LibraryFilter.ALBUMS -> stringResource(R.string.library_albums_subtitle)
            else -> stringResource(R.string.library_subtitle)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(headerHeight)
                .clipToBounds()
                .graphicsLayer { alpha = state.progress }
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = headerTitle,
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = headerSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}
