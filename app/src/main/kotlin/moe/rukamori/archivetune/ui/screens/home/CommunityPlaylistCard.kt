/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.toPlaylistItem
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.PlaylistThumbnail
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.navigation.containerTransformSource
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews

// --- Community / Featured Card Geometry ---
private const val CommunityCardWidthRatio = 0.88f
private val CommunityCoverSize = 80.dp
private val CommunityCoverCornerRadius = 12.dp
private val CommunityActionButtonSize = 40.dp
private val CommunityActionIconSize = 20.dp
private val CommunityShelfSpacing = 12.dp
private val CommunityShelfHorizontalPadding = 16.dp

@Composable
internal fun CommunityPlaylistSection(
    section: HomePage.Section,
    mediaContext: HomeFeedMediaContext,
    modifier: Modifier = Modifier,
) {
    if (section.featuredCards.isEmpty()) return

    val songsById = remember(section.items) {
        section.items.filterIsInstance<SongItem>().associateBy(SongItem::id)
    }
    val horizontalInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = horizontalInsets.calculateStartPadding(layoutDirection) + CommunityShelfHorizontalPadding
    val endPadding = horizontalInsets.calculateEndPadding(layoutDirection) + CommunityShelfHorizontalPadding

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - (CommunityShelfHorizontalPadding * 2)) * CommunityCardWidthRatio
        LazyRow(
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(CommunityShelfSpacing),
        ) {
            items(
                items = section.featuredCards,
                key = HomePage.Section.FeaturedCard::id,
                contentType = { "featured_playlist_card" },
            ) { card ->
                val songs = remember(card.itemIds, songsById) {
                    card.itemIds.mapNotNull(songsById::get).take(3)
                }
                val playlist = remember(card) { card.toPlaylistItem() }
                val openPlaylist = remember(playlist.id, mediaContext.navController) {
                    { mediaContext.navController.navigate("online_playlist/${playlist.id}") }
                }
                val sequentialEndpoint = remember(card.playEndpoint, card.id) {
                    card.playEndpoint ?: WatchEndpoint(playlistId = card.id)
                }
                val playPlaylist = remember(sequentialEndpoint, mediaContext.playerConnection) {
                    { mediaContext.playerConnection.playQueue(YouTubeQueue.playlist(sequentialEndpoint)) }
                }
                val secondaryEndpoint = card.radioEndpoint ?: card.shuffleEndpoint
                val playSecondary = remember(secondaryEndpoint, mediaContext.playerConnection) {
                    secondaryEndpoint?.let { endpoint ->
                        { mediaContext.playerConnection.playQueue(YouTubeQueue.playlist(endpoint)) }
                    }
                }
                val showPlaylistMenu = remember(playlist, songs, mediaContext.menuState, mediaContext.scope) {
                    {
                        mediaContext.menuState.show {
                            YouTubePlaylistMenu(
                                playlist = playlist,
                                songs = songs,
                                coroutineScope = mediaContext.scope,
                                onDismiss = mediaContext.menuState::dismiss,
                            )
                        }
                    }
                }
                val onPlaySong: (SongItem, Int) -> Unit = remember(card.id, mediaContext.playerConnection, mediaContext.mediaMetadata?.id) {
                    { song, index ->
                        if (song.id == mediaContext.mediaMetadata?.id) {
                            mediaContext.playerConnection.player.togglePlayPause()
                        } else {
                            val songEndpoint = song.endpoint?.let { endpoint ->
                                endpoint.copy(playlistId = endpoint.playlistId ?: card.id, index = endpoint.index ?: index)
                            } ?: WatchEndpoint(videoId = song.id, playlistId = card.id, index = index)
                            mediaContext.playerConnection.playQueue(YouTubeQueue.playlist(songEndpoint))
                        }
                    }
                }
                val onSongMenu: (SongItem) -> Unit = remember(mediaContext.menuState, mediaContext.navController) {
                    { song ->
                        mediaContext.menuState.show {
                            YouTubeSongMenu(
                                song = song,
                                navController = mediaContext.navController,
                                onDismiss = mediaContext.menuState::dismiss,
                            )
                        }
                    }
                }

                CommunityPlaylistCard(
                    card = card,
                    songs = songs,
                    isPlaying = mediaContext.isPlaying,
                    activeSongId = mediaContext.mediaMetadata?.id,
                    onOpen = openPlaylist,
                    onPlay = playPlaylist,
                    onSecondaryPlay = playSecondary,
                    isSecondaryRadio = card.radioEndpoint != null,
                    onMore = showPlaylistMenu,
                    onPlaySong = onPlaySong,
                    onSongMenu = onSongMenu,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommunityPlaylistCard(
    card: HomePage.Section.FeaturedCard,
    songs: List<SongItem>,
    isPlaying: Boolean,
    activeSongId: String?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onSecondaryPlay: (() -> Unit)?,
    isSecondaryRadio: Boolean,
    onMore: () -> Unit,
    onPlaySong: (SongItem, Int) -> Unit,
    onSongMenu: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
        modifier =
            modifier.containerTransformSource(
                key = "playlist-${card.id}",
                cornerRadius = 28.dp,
                coverUrl = card.thumbnail,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            CommunityPlaylistCardHeader(
                card = card,
                songs = songs,
                onOpen = onOpen,
                onLongClick = onMore,
            )

            if (songs.isNotEmpty()) {
                songs.forEachIndexed { index, song ->
                    YouTubeListItem(
                        item = song,
                        isActive = song.id == activeSongId,
                        isPlaying = isPlaying,
                        isSwipeable = false,
                        trailingContent = {
                            androidx.compose.material3.IconButton(onClick = { onSongMenu(song) }) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onPlaySong(song, index) },
                                onLongClick = { onSongMenu(song) },
                            ),
                    )
                }
            } else if (card.itemIds.isEmpty()) {
                CommunityPlaylistCardPlaceholderRows()
            }

            CommunityPlaylistCardActionsRow(
                onPlay = onPlay,
                onSecondaryPlay = onSecondaryPlay,
                isSecondaryRadio = isSecondaryRadio,
                onMore = onMore,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommunityPlaylistCardHeader(
    card: HomePage.Section.FeaturedCard,
    songs: List<SongItem>,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
) {
    val coverThumbnails = remember(card.thumbnail, songs) {
        val isAvatar = card.thumbnail?.contains("yt3.") == true
        val songThumbs = songs.mapNotNull { it.thumbnail }.distinct()
        when {
            isAvatar && songThumbs.size >= 4 -> songThumbs.take(4)
            isAvatar && songThumbs.isNotEmpty() -> listOf(songThumbs.first())
            !card.thumbnail.isNullOrBlank() -> listOfNotNull(card.thumbnail)
            songThumbs.size >= 4 -> songThumbs.take(4)
            songThumbs.isNotEmpty() -> listOf(songThumbs.first())
            else -> emptyList()
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CommunityCoverSize)
            .then(
                Modifier.combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongClick,
                ),
            ),
    ) {
        PlaylistThumbnail(
            thumbnails = coverThumbnails,
            size = CommunityCoverSize,
            placeHolder = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            },
            shape = RoundedCornerShape(CommunityCoverCornerRadius),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            card.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CommunityPlaylistCardPlaceholderRows() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(3) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ListItemHeight)
                    .padding(horizontal = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(ListThumbnailSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.65f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.38f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityPlaylistCardActionsRow(
    onPlay: () -> Unit,
    onSecondaryPlay: (() -> Unit)?,
    isSecondaryRadio: Boolean,
    onMore: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        FilledIconButton(
            onClick = onPlay,
            modifier = Modifier.size(CommunityActionButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(CommunityActionIconSize),
            )
        }

        if (onSecondaryPlay != null) {
            FilledTonalIconButton(
                onClick = onSecondaryPlay,
                modifier = Modifier.size(CommunityActionButtonSize),
            ) {
                Icon(
                    painter = painterResource(if (isSecondaryRadio) R.drawable.radio else R.drawable.shuffle),
                    contentDescription = stringResource(if (isSecondaryRadio) R.string.start_radio else R.string.shuffle),
                    modifier = Modifier.size(CommunityActionIconSize),
                )
            }
        }

        FilledTonalIconButton(
            onClick = onMore,
            modifier = Modifier.size(CommunityActionButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more_label),
                modifier = Modifier.size(CommunityActionIconSize),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun CommunityPlaylistCardPreview() {
    TestThemeWrapper {
        val sampleCard = HomePage.Section.FeaturedCard(
            id = "sample_playlist",
            title = "Late Night Lo-Fi Beats",
            subtitle = "Chillhop Music • 48 songs",
            thumbnail = null,
            endpoint = null,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
            itemIds = listOf("s1", "s2"),
        )
        val sampleSongs = listOf(
            SongItem(id = "s1", title = "Midnight Coffee", artists = emptyList(), thumbnail = "thumb1"),
            SongItem(id = "s2", title = "Rainy Window", artists = emptyList(), thumbnail = "thumb2"),
        )
        Box(modifier = Modifier.padding(16.dp)) {
            CommunityPlaylistCard(
                card = sampleCard,
                songs = sampleSongs,
                isPlaying = false,
                activeSongId = null,
                onOpen = {},
                onPlay = {},
                onSecondaryPlay = {},
                isSecondaryRadio = false,
                onMore = {},
                onPlaySong = { _, _ -> },
                onSongMenu = {},
                modifier = Modifier.width(360.dp),
            )
        }
    }
}