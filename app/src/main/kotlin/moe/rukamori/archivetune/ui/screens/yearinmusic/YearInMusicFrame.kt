/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.screens.RecapBlack
import moe.rukamori.archivetune.ui.screens.RecapInk
import moe.rukamori.archivetune.ui.screens.RecapLime
import moe.rukamori.archivetune.ui.screens.RecapPurple
import moe.rukamori.archivetune.ui.screens.RecapRed
import moe.rukamori.archivetune.ui.screens.RecapRedDeep
import moe.rukamori.archivetune.ui.screens.RecapSurface
import moe.rukamori.archivetune.ui.screens.RecapSurfaceHigh
import moe.rukamori.archivetune.ui.screens.RecapTokens
import moe.rukamori.archivetune.ui.screens.YearInMusicRecapCard
import moe.rukamori.archivetune.ui.screens.rememberShareSafeImageRequest

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

@Composable
internal fun PassportStamp(
    label: String,
    imageData: Any?,
    count: Int,
) {
    val imageModel = rememberShareSafeImageRequest(imageData)

    Column(
        modifier = Modifier.width(92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = RecapInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(width = 82.dp, height = 62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .border(
                        width = 2.dp,
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(Color.White, RecapLime, Color.White),
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = RecapInk,
        )
    }
}

@Composable
internal fun SummaryGuideLine(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(64.dp)
                    .height(2.dp)
                    .background(RecapInk.copy(alpha = 0.42f)),
        )
        Box(
            modifier =
                Modifier
                    .width(64.dp)
                    .height(2.dp)
                    .background(RecapInk.copy(alpha = 0.42f)),
        )
    }
}

@Composable
internal fun ArchiveTuneBrand(
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon_small),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = contentColor,
        )
    }
}
