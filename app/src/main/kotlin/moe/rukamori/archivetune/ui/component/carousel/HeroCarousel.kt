/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun HeroCarousel(
    items: List<HeroItem>,
    rollCandidates: List<HeroItem>,
    rollState: HeroRollState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    isCarouselActive: Boolean,
    onTrackClick: (String) -> Unit,
    onTrackLongClick: (String) -> Unit,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItems = remember(items, rollCandidates.size) {
        buildList {
            addAll(items.map { HeroDisplayItem.Track(it) })
            if (rollCandidates.size >= HeroCarouselTokens.GachaMinPool) {
                add(HeroDisplayItem.RollCard)
            }
        }
    }

    if (displayItems.isEmpty()) return

    val isSingleItem = displayItems.size == 1
    val pagerState = rememberPagerState(
        initialPage = if (isSingleItem) 0 else (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % displayItems.size),
        pageCount = { if (isSingleItem) 1 else Int.MAX_VALUE },
    )

    val context = LocalContext.current
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurNavBar) = rememberPreference(BlurNavBarKey, true)
    val (blurRadius) = rememberPreference(BlurRadiusKey, SettingsDimensions.BlurRadiusDefault)
    val animationsDisabled = LocalAnimationsDisabled.current
    val edgeBlur = blurNavBar && !disableBlur && !animationsDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurFactor = (blurRadius / SettingsDimensions.BlurRadiusDefault).coerceIn(0.1f, 3.0f)

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userSwipe by remember { mutableStateOf(false) }
    LaunchedEffect(isDragged) { if (isDragged) userSwipe = true }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) userSwipe = false
        }
    }

    val wipeCurveState = animateFloatAsState(
        targetValue = if (userSwipe && !animationsDisabled) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(260),
        label = "heroWipeCurve",
    )
    val wipeCurveProvider = remember(wipeCurveState) { { wipeCurveState.value } }

    if (!isSingleItem && !animationsDisabled) {
        LaunchedEffect(pagerState, displayItems.size, isDragged, rollState.phase, isCarouselActive) {
            if (isDragged || !isCarouselActive) return@LaunchedEffect
            while (true) {
                val currentItem = displayItems[pagerState.currentPage.mod(displayItems.size)]
                val onRoll = currentItem is HeroDisplayItem.RollCard
                if (onRoll && rollState.phase != HeroRollPhase.Idle) return@LaunchedEffect
                delay((if (onRoll) HeroCarouselTokens.HeroRollIdleAdvanceMillis else HeroCarouselTokens.HeroAdvanceMillis).milliseconds)
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(
                        HeroCarouselTokens.HeroSlideMillis,
                        easing = HeroCarouselTokens.HeroSlideEasing,
                    ),
                )
            }
        }

        LaunchedEffect(pagerState.currentPage, displayItems) {
            val nextItem = displayItems[(pagerState.currentPage + 1).mod(displayItems.size)]
            if (nextItem is HeroDisplayItem.Track) {
                nextItem.item.thumbnailUrl?.let { url ->
                    context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSingleItem && rollState.phase != HeroRollPhase.Rolling,
            key = { page ->
                val item = displayItems[page.mod(displayItems.size)]
                "${item.key}_$page"
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    val fadeStart = HeroCarouselTokens.BottomShadeStart
                    val fadeMid = fadeStart + (1f - fadeStart) * 0.55f
                    val mask = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black,
                            fadeStart to Color.Black,
                            fadeMid to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = mask,
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        ) { page ->
            when (val displayItem = displayItems[page.mod(displayItems.size)]) {
                is HeroDisplayItem.RollCard -> {
                    HeroRollCard(
                        state = rollState,
                        pageOffset = { pagerState.offsetOf(page) },
                        wipeCurve = wipeCurveProvider,
                        isResultPlaying = isPlaying && rollState.result != null && mediaMetadata?.id == rollState.result?.id,
                        edgeBlur = edgeBlur,
                        blurFactor = blurFactor,
                        isCarouselActive = isCarouselActive,
                        animationsDisabled = animationsDisabled,
                        onRoll = onRoll,
                        onPlayResult = onTrackClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is HeroDisplayItem.Track -> {
                    HeroMediaCard(
                        item = displayItem.item,
                        pageOffset = { pagerState.offsetOf(page) },
                        wipeCurve = wipeCurveProvider,
                        isThisPlaying = isPlaying && mediaMetadata?.id == displayItem.item.id,
                        edgeBlur = edgeBlur,
                        blurFactor = blurFactor,
                        onPrimaryClick = { onTrackClick(displayItem.item.id) },
                        onLongClick = { onTrackLongClick(displayItem.item.id) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}



private fun PagerState.offsetOf(page: Int): Float =
    (page - currentPage) - currentPageOffsetFraction
