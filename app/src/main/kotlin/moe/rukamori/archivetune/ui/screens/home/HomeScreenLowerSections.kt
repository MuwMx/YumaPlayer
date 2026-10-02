/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.screens.HomeCategoryChips
import moe.rukamori.archivetune.ui.screens.HomeSectionHeader
import moe.rukamori.archivetune.ui.screens.QuickPicksSection
import moe.rukamori.archivetune.ui.screens.SpeedDialSection
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider

internal val HomeFeedMaxWidth = 1_200.dp
internal val HomeSectionSpacing = 18.dp

@Immutable
internal data class HomeFeedMediaContext(
    val mediaMetadata: MediaMetadata?,
    val isPlaying: Boolean,
    val navController: NavController,
    val playerConnection: PlayerConnection,
    val menuState: MenuState,
    val scope: CoroutineScope,
)

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
internal fun LazyListScope.homeSimilarAndRemoteSections(
    uiState: HomeUiState,
    mediaContext: HomeFeedMediaContext,
) {
    uiState.similarRecommendations.forEach { recommendation ->
        sectionSpacer("similar_${recommendation.title.id}")
        item(
            key = "home_similar_header_${recommendation.title.id}",
            contentType = "section_header",
        ) {
            SimilarRecommendationsTitle(
                recommendation = recommendation,
                navController = mediaContext.navController,
                modifier = Modifier.animateItem(),
            )
        }
        item(
            key = "home_similar_${recommendation.title.id}",
            contentType = "media_shelf",
        ) {
            SimilarRecommendationsSection(
                recommendation = recommendation,
                mediaMetadata = mediaContext.mediaMetadata,
                isPlaying = mediaContext.isPlaying,
                navController = mediaContext.navController,
                playerConnection = mediaContext.playerConnection,
                menuState = mediaContext.menuState,
                scope = mediaContext.scope,
                modifier = Modifier.animateItem(),
            )
        }
    }

    uiState.homePage?.sections.orEmpty().forEachIndexed { index, section ->
        val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
        sectionSpacer("remote_$sectionKey")
        item(
            key = "home_remote_header_$sectionKey",
            contentType = "section_header",
        ) {
            HomePageSectionTitle(
                section = section,
                navController = mediaContext.navController,
                modifier = Modifier.animateItem(),
            )
        }
        item(
            key = "home_remote_$sectionKey",
            contentType = "media_shelf",
        ) {
            HomePageSectionContent(
                section = section,
                mediaMetadata = mediaContext.mediaMetadata,
                isPlaying = mediaContext.isPlaying,
                navController = mediaContext.navController,
                playerConnection = mediaContext.playerConnection,
                menuState = mediaContext.menuState,
                scope = mediaContext.scope,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

internal fun LazyListScope.homeLoadingMoreItem(
    isLoadingMore: Boolean,
) {
    if (isLoadingMore) {
        item(
            key = "home_loading_more",
            contentType = "loading",
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .animateItem(),
            ) {
                LoadingIndicator()
            }
        }
    }
}

internal fun LazyListScope.sectionSpacer(key: String) {
    item(
        key = "home_section_spacer_$key",
        contentType = "section_spacer",
    ) {
        Spacer(Modifier.height(HomeSectionSpacing))
    }
}

internal fun LazyListScope.homeForgottenFavoritesSection(
    uiState: HomeUiState,
    mediaContext: HomeFeedMediaContext,
    forgottenItemWidth: Dp,
    forgottenFavoritesGridState: LazyGridState,
    forgottenSnapLayoutInfoProvider: SnapLayoutInfoProvider,
) {
    if (uiState.forgottenFavorites.isNotEmpty()) {
        sectionSpacer("forgotten_favorites")
        item(
            key = "home_forgotten_favorites_header",
            contentType = "section_header",
        ) {
            HomeSectionHeader(
                title = stringResource(R.string.forgotten_favorites),
                modifier = Modifier.animateItem(),
            )
        }
        item(
            key = "home_forgotten_favorites",
            contentType = "song_shelf",
        ) {
            ForgottenFavoritesSection(
                forgottenFavorites = uiState.forgottenFavorites,
                mediaMetadata = mediaContext.mediaMetadata,
                isPlaying = mediaContext.isPlaying,
                horizontalLazyGridItemWidth = forgottenItemWidth,
                lazyGridState = forgottenFavoritesGridState,
                snapLayoutInfoProvider = forgottenSnapLayoutInfoProvider,
                navController = mediaContext.navController,
                playerConnection = mediaContext.playerConnection,
                menuState = mediaContext.menuState,
                modifier = Modifier.animateItem(),
            )
        }
    }
}
