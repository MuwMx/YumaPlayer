/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.MenuState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPlaylistsSection(
    accountPlaylists: List<PlaylistItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val distinctPlaylists = remember(accountPlaylists) { accountPlaylists.distinctBy { it.id } }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = HomeLayoutConfig.cardWidth(maxWidth)
        val artistSize = HomeLayoutConfig.artistSize(maxWidth)
        val horizontalInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val startPadding = horizontalInsets.calculateStartPadding(layoutDirection) + HomeLayoutConfig.shelfHorizontalPadding
        val endPadding = horizontalInsets.calculateEndPadding(layoutDirection) + HomeLayoutConfig.shelfHorizontalPadding

        LazyRow(
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(HomeLayoutConfig.shelfSpacing),
        ) {
            itemsIndexed(
                items = distinctPlaylists,
                key = { index, item -> "${item.id}_$index" },
                contentType = { _, _ -> "account_playlist" },
            ) { index, item ->
                YouTubeGridItemWrapper(
                    item = item,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    scope = scope,
                    cardWidth = cardWidth,
                    artistSize = artistSize,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimilarRecommendationsSection(
    recommendation: SimilarRecommendation,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = HomeLayoutConfig.cardWidth(maxWidth)
        val artistSize = HomeLayoutConfig.artistSize(maxWidth)
        val horizontalInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val startPadding = horizontalInsets.calculateStartPadding(layoutDirection) + HomeLayoutConfig.shelfHorizontalPadding
        val endPadding = horizontalInsets.calculateEndPadding(layoutDirection) + HomeLayoutConfig.shelfHorizontalPadding

        LazyRow(
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(HomeLayoutConfig.shelfSpacing),
        ) {
            itemsIndexed(
                items = recommendation.items,
                key = { index, item -> "${item.id}_$index" },
                contentType = { _, item -> item::class },
            ) { index, item ->
                YouTubeGridItemWrapper(
                    item = item,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    scope = scope,
                    visibleItems = recommendation.items,
                    clickedIndex = index,
                    sectionTitle = recommendation.title.title,
                    cardWidth = cardWidth,
                    artistSize = artistSize,
                )
            }
        }
    }
}

@Composable
fun AccountPlaylistsTitle(
    accountName: String,
    accountImageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        label = stringResource(R.string.your_youtube_playlists),
        title = accountName.ifBlank { stringResource(R.string.account) },
        thumbnail = {
            if (accountImageUrl != null) {
                val context = LocalContext.current
                val avatarSizePx =
                    with(LocalDensity.current) {
                        ListThumbnailSize.roundToPx().coerceAtLeast(1)
                    }
                val imageRequest =
                    remember(accountImageUrl, avatarSizePx) {
                        ImageRequest
                            .Builder(context)
                            .data(accountImageUrl)
                            .size(Size(avatarSizePx, avatarSizePx))
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(accountImageUrl)
                            .crossfade(true)
                            .build()
                    }
                AsyncImage(
                    model = imageRequest,
                    placeholder = painterResource(id = R.drawable.person),
                    error = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier.size(ListThumbnailSize),
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun SimilarRecommendationsTitle(
    recommendation: SimilarRecommendation,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        label = stringResource(R.string.similar_to),
        title = recommendation.title.title,
        thumbnail =
            recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                {
                    val shape =
                        if (recommendation.title is Artist) {
                            CircleShape
                        } else {
                            RoundedCornerShape(ThumbnailCornerRadius)
                        }
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(shape),
                    )
                }
            },
        onClick = {
            when (recommendation.title) {
                is Song -> {
                    navController.navigate("album/${recommendation.title.album!!.id}")
                }

                is Album -> {
                    navController.navigate("album/${recommendation.title.id}")
                }

                is Artist -> {
                    navController.navigate("artist/${recommendation.title.id}")
                }

                is Playlist -> {}
            }
        },
        modifier = modifier,
    )
}
