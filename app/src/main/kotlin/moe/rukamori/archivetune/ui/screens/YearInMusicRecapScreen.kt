/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.yearinmusic.RecapBackdrop
import moe.rukamori.archivetune.ui.screens.yearinmusic.RecapCardPager
import moe.rukamori.archivetune.utils.ComposeToImage
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.YearInMusicUiState

@Composable
internal fun YearInMusicRecapScreen(
    navController: NavController,
    uiState: YearInMusicUiState,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val coroutineScope = rememberCoroutineScope()
    val content = uiState as YearInMusicUiState.Content
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    var isGeneratingImage by remember { mutableStateOf(false) }
    var isShareCaptureMode by remember { mutableStateOf(false) }
    var currentCardBounds by remember { mutableStateOf<Rect?>(null) }

    val cards = rememberYearInMusicCards(content)
    val pagerState = rememberPagerState(pageCount = { cards.size })
    val currentPage by remember(cards, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, cards.lastIndex.coerceAtLeast(0)) }
    }
    val canShare by remember(content, isShareCaptureMode, cards, currentPage) {
        derivedStateOf { content.hasData && !isShareCaptureMode && cards.getOrNull(currentPage) != null }
    }

    LaunchedEffect(content.selectedYear) {
        pagerState.scrollToPage(0)
    }

    LaunchedEffect(cards) {
        if (pagerState.currentPage > cards.lastIndex) {
            pagerState.scrollToPage(cards.lastIndex.coerceAtLeast(0))
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(RecapBlack),
    ) {
        RecapBackdrop(
            enabled = !disableBlur && !isShareCaptureMode,
            modifier = Modifier.fillMaxSize(),
        )

        RecapCardPager(
            cards = cards,
            pagerState = pagerState,
            isShareCaptureMode = isShareCaptureMode,
            onCardBoundsChanged = { currentCardBounds = it },
            onTopSongLongClick = { song ->
                haptics.longPress()
                menuState.show {
                    SongMenu(
                        originalSong = song,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            onTopArtistLongClick = { artist ->
                haptics.longPress()
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxSize(),
        )

        if (canShare) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                RecapShareButton(
                    isGenerating = isGeneratingImage,
                    onClick = {
                        if (isGeneratingImage) return@RecapShareButton
                        isGeneratingImage = true
                        coroutineScope.launch {
                            try {
                                isShareCaptureMode = true
                                awaitNextPreDraw(view)
                                awaitNextPreDraw(view)

                                val raw =
                                    ComposeToImage.captureViewBitmap(
                                        view = view,
                                        backgroundColor = RecapBlack.toArgb(),
                                    )
                                val bounds = currentCardBounds
                                val cardBitmap =
                                    if (bounds != null && bounds.width > 0f && bounds.height > 0f) {
                                        ComposeToImage.cropBitmap(
                                            source = raw,
                                            left = bounds.left.toInt().coerceAtLeast(0),
                                            top = bounds.top.toInt().coerceAtLeast(0),
                                            width = bounds.width.toInt().coerceAtLeast(1),
                                            height = bounds.height.toInt().coerceAtLeast(1),
                                        )
                                    } else {
                                        raw
                                    }
                                val fitted =
                                    ComposeToImage.fitBitmap(
                                        source = cardBitmap,
                                        targetWidth = 1080,
                                        targetHeight = 1920,
                                        backgroundColor = RecapBlack.toArgb(),
                                    )
                                val uri =
                                    ComposeToImage.saveBitmapAsFile(
                                        context = context,
                                        bitmap = fitted,
                                        fileName = "ArchiveTune_YearInMusic_${content.selectedYear}_${currentPage + 1}",
                                    )
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        context.getString(R.string.share_summary),
                                    ),
                                )
                            } finally {
                                isShareCaptureMode = false
                                isGeneratingImage = false
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                            ).padding(end = 16.dp, bottom = 16.dp)
                            .widthIn(max = 168.dp),
                )
            }
        }
    }
}
