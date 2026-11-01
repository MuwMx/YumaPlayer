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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.navigation.buildBrowseRoute

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageSectionContent(
    section: HomePage.Section,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    if (section.featuredCards.isNotEmpty()) {
        CommunityPlaylistSection(
            section = section,
            mediaContext =
                HomeFeedMediaContext(
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    scope = scope,
                ),
            modifier = modifier,
        )
        return
    }

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
                items = section.items,
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
                    visibleItems = section.items,
                    clickedIndex = index,
                    sectionTitle = section.title,
                    cardWidth = cardWidth,
                    artistSize = artistSize,
                )
            }
        }
    }
}

internal fun resolveHomeSectionRoute(endpoint: BrowseEndpoint?): String? {
    val browseId = endpoint?.browseId ?: return null
    return if (browseId == "FEmusic_moods_and_genres") {
        Screens.MoodAndGenres.route
    } else {
        buildBrowseRoute(browseId, endpoint.params)
    }
}

@Composable
fun HomePageSectionTitle(
    section: HomePage.Section,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        title = section.title,
        label = section.label,
        thumbnail =
            section.thumbnail?.let { thumbnailUrl ->
                {
                    val shape =
                        if (section.endpoint?.isArtistEndpoint == true) {
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
        onClick =
            resolveHomeSectionRoute(section.endpoint)?.let { route ->
                { navController.navigate(route) }
            },
        modifier = modifier,
    )
}
