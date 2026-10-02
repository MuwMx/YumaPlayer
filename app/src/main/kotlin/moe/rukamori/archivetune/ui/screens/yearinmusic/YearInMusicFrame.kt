/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.screens.RecapBlack
import moe.rukamori.archivetune.ui.screens.RecapPurple
import moe.rukamori.archivetune.ui.screens.RecapRed
import moe.rukamori.archivetune.ui.screens.RecapRedDeep
import moe.rukamori.archivetune.ui.screens.RecapSurface
import moe.rukamori.archivetune.ui.screens.RecapSurfaceHigh
import moe.rukamori.archivetune.ui.screens.RecapTokens
import moe.rukamori.archivetune.ui.screens.YearInMusicRecapCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecapCardPager(
    cards: List<YearInMusicRecapCard>,
    pagerState: PagerState,
    isShareCaptureMode: Boolean,
    onCardBoundsChanged: (Rect) -> Unit,
    onTopSongLongClick: (Song) -> Unit,
    onTopArtistLongClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val verticalPadding = if (isShareCaptureMode) RecapTokens.ShareVerticalPadding else 0.dp

    HorizontalPager(
        state = pagerState,
        key = { index -> cards.getOrNull(index)?.id ?: "stale_year_in_music_page_$index" },
        userScrollEnabled = !isShareCaptureMode,
        modifier = modifier.padding(vertical = verticalPadding),
    ) { page ->
        val card = cards.getOrNull(page)
        if (card == null) {
            Box(modifier = Modifier.fillMaxSize())
            return@HorizontalPager
        }
        val canAdvance = !isShareCaptureMode && page == pagerState.currentPage && page < cards.lastIndex
        RecapCardFrame(
            card = card,
            applySafeContentInsets = !isShareCaptureMode,
            onCardClick = {
                if (canAdvance) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page + 1)
                    }
                }
            },
            canAdvance = canAdvance,
            onTopSongLongClick = onTopSongLongClick,
            onTopArtistLongClick = onTopArtistLongClick,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        if (page == pagerState.currentPage) {
                            onCardBoundsChanged(coordinates.boundsInRoot())
                        }
                    },
        )
    }
}
@Composable
internal fun RecapCardFrame(
    card: YearInMusicRecapCard,
    applySafeContentInsets: Boolean,
    onCardClick: () -> Unit,
    canAdvance: Boolean,
    onTopSongLongClick: (Song) -> Unit,
    onTopArtistLongClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient =
        remember(card.id) {
            when (card) {
                is YearInMusicRecapCard.Empty -> listOf(RecapSurfaceHigh, RecapBlack)
                is YearInMusicRecapCard.Intro -> listOf(RecapRedDeep, RecapBlack, RecapSurface)
                is YearInMusicRecapCard.Totals -> listOf(RecapRed, RecapBlack, RecapPurple.copy(alpha = 0.72f))
                is YearInMusicRecapCard.TopSong -> listOf(RecapBlack, RecapRedDeep, RecapBlack)
                is YearInMusicRecapCard.RankedArtists -> listOf(RecapBlack, RecapSurface, RecapRedDeep)
                is YearInMusicRecapCard.RankedAlbums -> listOf(RecapRedDeep, RecapBlack, RecapSurface)
                is YearInMusicRecapCard.Summary -> listOf(RecapBlack, RecapRedDeep, RecapPurple.copy(alpha = 0.78f))
            }
        }

    Surface(
        modifier = modifier.clickable(enabled = canAdvance, onClick = onCardClick),
        color = RecapBlack,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(gradient)),
        ) {
            RecapNoiseOverlay(modifier = Modifier.fillMaxSize())

            when (card) {
                is YearInMusicRecapCard.Empty -> {
                    EmptyRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.Intro -> {
                    IntroRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.Totals -> {
                    TotalsRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.TopSong -> {
                    TopSongRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                        onClick = onCardClick,
                        onLongClick = { card.originalSong?.let(onTopSongLongClick) },
                    )
                }

                is YearInMusicRecapCard.RankedArtists -> {
                    RankedArtistsRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                        onArtistLongClick = onTopArtistLongClick,
                    )
                }

                is YearInMusicRecapCard.RankedAlbums -> {
                    RankedAlbumsRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.Summary -> {
                    SummaryRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }
            }
        }
    }
}
@Composable
internal fun RecapBackdrop(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.background(
                Brush.radialGradient(
                    colors =
                        if (enabled) {
                            listOf(
                                RecapRed.copy(alpha = 0.38f),
                                RecapPurple.copy(alpha = 0.22f),
                                RecapBlack,
                            )
                        } else {
                            listOf(RecapBlack, RecapBlack)
                        },
                    radius = 1200f,
                ),
            ),
    )
}
@Composable
internal fun RecapNoiseOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                        ),
                ),
            ),
    )
}
