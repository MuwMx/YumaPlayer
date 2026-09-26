/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import android.content.Intent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.yearinmusic.RecapBackdrop
import moe.rukamori.archivetune.ui.screens.yearinmusic.RecapCardPager
import moe.rukamori.archivetune.utils.ComposeToImage
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.YearInMusicUiState
import moe.rukamori.archivetune.viewmodels.YearInMusicViewModel

internal val RecapBlack = Color(0xFF070707)
internal val RecapSurfaceHigh = Color(0xFF1D1D1D)
internal val RecapRed = Color(0xFFFF0033)
internal val RecapRedDeep = Color(0xFFB60024)
internal val RecapCream = Color(0xFFFFF7EF)
internal val RecapYellow = Color(0xFFFFD447)
internal val RecapGreen = Color(0xFF1ED760)
internal val RecapPurple = Color(0xFF8A2CFF)
internal val RecapBlue = Color(0xFF7CB7FF)
internal val RecapSurface = Color(0xFF121212)
internal val RecapPink = Color(0xFFFF8BDE)
internal val RecapLime = Color(0xFFDFFF3E)
internal val RecapInk = Color(0xFF151515)

internal object RecapTokens {
    val SectionRadius = 24.dp
    val ItemRadius = 18.dp
    val ShareVerticalPadding = 14.dp
}

@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) {
    YearInMusicRoute(
        navController = navController,
        viewModel = viewModel,
        initialYear = initialYear,
    )
}

@Composable
private fun YearInMusicRoute(
    navController: NavController,
    viewModel: YearInMusicViewModel,
    initialYear: Int?,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialYear) {
        if (initialYear != null) {
            viewModel.selectYear(initialYear)
        }
    }

    YearInMusicRecapScreen(
        navController = navController,
        uiState = uiState,
    )
}

@Composable
private fun YearInMusicRecapScreen(
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

@Composable
private fun rememberYearInMusicCards(content: YearInMusicUiState.Content): List<YearInMusicRecapCard> {
    val introLabel = stringResource(R.string.year_in_music_recap)
    val totalsLabel = stringResource(R.string.total_listening_time)
    val topTrackLabel = stringResource(R.string.year_in_music_top_track)
    val artistsLabel = stringResource(R.string.year_in_music_ranked_artists)
    val albumsLabel = stringResource(R.string.year_in_music_ranked_albums)
    val summaryLabel = stringResource(R.string.share_summary)
    val emptyLabel = stringResource(R.string.no_listening_data)

    return remember(
        content.selectedYear,
        content.totalListeningTime,
        content.totalSongsPlayed,
        content.topSongsStats,
        content.topSongs,
        content.topArtists,
        content.topAlbums,
        introLabel,
        totalsLabel,
        topTrackLabel,
        artistsLabel,
        albumsLabel,
        summaryLabel,
        emptyLabel,
    ) {
        if (!content.hasData) {
            listOf(YearInMusicRecapCard.Empty(content.selectedYear, emptyLabel))
        } else {
            buildList {
                add(
                    YearInMusicRecapCard.Intro(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        label = introLabel,
                    ),
                )
                add(
                    YearInMusicRecapCard.Totals(
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        topSong = content.topSongsStats.firstOrNull(),
                        topArtist = content.topArtists.firstOrNull(),
                        label = totalsLabel,
                    ),
                )
                content.topSongsStats.firstOrNull()?.let { topSong ->
                    add(
                        YearInMusicRecapCard.TopSong(
                            song = topSong,
                            originalSong = content.topSongs.firstOrNull { it.id == topSong.id } ?: content.topSongs.firstOrNull(),
                            label = topTrackLabel,
                        ),
                    )
                }
                if (content.topArtists.isNotEmpty()) {
                    add(
                        YearInMusicRecapCard.RankedArtists(
                            artists = content.topArtists,
                            label = artistsLabel,
                        ),
                    )
                }
                if (content.topAlbums.isNotEmpty()) {
                    add(
                        YearInMusicRecapCard.RankedAlbums(
                            albums = content.topAlbums,
                            label = albumsLabel,
                        ),
                    )
                }
                add(
                    YearInMusicRecapCard.Summary(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        topSongs = content.topSongsStats,
                        topArtists = content.topArtists,
                        topAlbums = content.topAlbums,
                        label = summaryLabel,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RecapShareButton(
    isGenerating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = RecapRed,
        contentColor = RecapCream,
        shape = MaterialTheme.shapes.large,
        icon = {
            AnimatedContent(
                targetState = isGenerating,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "shareProgress",
            ) { generating ->
                if (generating) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = RecapCream,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                    )
                }
            }
        },
        text = {
            Text(
                text = stringResource(R.string.year_in_music_current_card),
                fontWeight = FontWeight.Black,
            )
        },
    )
}

@Composable
internal fun rememberShareSafeImageRequest(data: Any?): Any? {
    val context = LocalContext.current
    return remember(data, context) {
        data?.let {
            ImageRequest
                .Builder(context)
                .data(it)
                .allowHardware(false)
                .build()
        }
    }
}

private suspend fun awaitNextPreDraw(view: View) {
    suspendCancellableCoroutine { cont ->
        val vto = view.viewTreeObserver
        val listener =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (vto.isAlive) vto.removeOnPreDrawListener(this)
                    cont.resume(Unit)
                    return true
                }
            }
        vto.addOnPreDrawListener(listener)
        cont.invokeOnCancellation {
            if (vto.isAlive) vto.removeOnPreDrawListener(listener)
        }
        view.invalidate()
    }
}

@Immutable
internal sealed interface YearInMusicRecapCard {
    val id: String
    val label: String

    data class Empty(
        val year: Int,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "empty_$year"
    }

    data class Intro(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "intro_$year"
    }

    data class Totals(
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        val topSong: SongWithStats?,
        val topArtist: Artist?,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "totals"
    }

    data class TopSong(
        val song: SongWithStats,
        val originalSong: Song?,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "top_song_${song.id}"
    }

    data class RankedArtists(
        val artists: List<Artist>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "artists_${artists.joinToString("_") { it.id }}"
    }

    data class RankedAlbums(
        val albums: List<Album>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "albums_${albums.joinToString("_") { it.id }}"
    }

    data class Summary(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        val topSongs: List<SongWithStats>,
        val topArtists: List<Artist>,
        val topAlbums: List<Album>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "summary_$year"
    }
}
