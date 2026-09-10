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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeCategoryChips(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onChipSelected: (HomePage.Chip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val scrollState = rememberScrollState()
    var containerWidth by remember { mutableIntStateOf(0) }
    val chipBoundsMap = remember { mutableStateMapOf<HomePage.Chip, Pair<Int, Int>>() }

    LaunchedEffect(selectedChip, containerWidth) {
        selectedChip?.let { chip ->
            chipBoundsMap[chip]?.let { (xInParent, width) ->
                if (containerWidth > 0) {
                    val targetScroll = (xInParent + width / 2 - containerWidth / 2).coerceIn(0, scrollState.maxValue)
                    scrollState.animateScrollTo(targetScroll)
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .yumaGlassCard(
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { containerWidth = it.width }
                    .horizontalFadingEdge(scrollState = scrollState, length = 20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
            ) {
                chips.forEach { chip ->
                    val selected = chip == selectedChip

                    val chipBgColor by animateColorAsState(
                        targetValue =
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                colors.glassBorder.copy(alpha = 0.10f)
                            },
                        animationSpec = tween(durationMillis = 250),
                        label = "chipBgColorAnimation",
                    )
                    val chipTextColor by animateColorAsState(
                        targetValue =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                colors.textPrimary
                            },
                        animationSpec = tween(durationMillis = 250),
                        label = "chipTextColorAnimation",
                    )

                    Box(
                        modifier =
                            Modifier
                                .onGloballyPositioned { coordinates ->
                                    chipBoundsMap[chip] = Pair(coordinates.positionInParent().x.toInt(), coordinates.size.width)
                                }
                                .yumaClickable(pressedScale = 0.94f) { onChipSelected(chip) }
                                .yumaGlassCard(
                                    shape = CircleShape,
                                    backgroundColor = chipBgColor,
                                    borderColor = Color.Transparent,
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(animationSpec = tween(200)) + expandHorizontally(animationSpec = tween(200)),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally(animationSpec = tween(200)),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.done),
                                    contentDescription = null,
                                    tint = chipTextColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = chip.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = chipTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        thumbnail?.invoke()
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

