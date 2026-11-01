/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.HideOnScrollFAB
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.asScrollContract
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.screens.stats.StatsViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val indexChips by viewModel.indexChips.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()
    val listeningByHour by viewModel.listeningByHour.collectAsStateWithLifecycle()
    val listeningByDayOfWeek by viewModel.listeningByDayOfWeek.collectAsStateWithLifecycle()
    val listeningSummary by viewModel.listeningSummary.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val fabHazeState = remember { HazeState() }
    val currentDate = remember { LocalDateTime.now() }
    var isYearPickerOpen by remember { mutableStateOf(false) }

    val availableYears =
        remember(currentDate, firstEvent) {
            val startYear = firstEvent?.event?.timestamp?.year ?: currentDate.year
            (currentDate.year downTo startYear).toList()
        }

    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer

    Box(modifier = Modifier.fillMaxSize()) {
        if (!disableBlur) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .align(Alignment.TopCenter)
                        .drawWithCache {
                            val brush =
                                Brush.verticalGradient(
                                    0f to tonalStart.copy(alpha = 0.30f),
                                    0.42f to tonalMiddle.copy(alpha = 0.14f),
                                    1f to Color.Transparent,
                                )
                            onDrawBehind { drawRect(brush) }
                        },
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .asPaddingValues(),
            modifier =
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                ).hazeSource(fabHazeState),
        ) {
            item(contentType = "chips") {
                StatsPeriodSelector(
                    selectedOption = selectedOption,
                    indexChips = indexChips,
                    firstEvent = firstEvent,
                    onSelectionChange = viewModel::onOptionSelected,
                    onValueUpdate = viewModel::onChipIndexChanged,
                    currentDate = currentDate,
                )
            }

            item(contentType = "summary") {
                AnimatedContent(
                    targetState = listeningSummary,
                    transitionSpec = {
                        slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(300)) togetherWith
                            slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(200))
                    },
                    modifier = Modifier.animateItem(),
                    label = "summary",
                ) { summary ->
                    StatsSummarySection(summary = summary)
                }
            }

            item(contentType = "highlights") {
                AnimatedContent(
                    targetState = mostPlayedArtists.firstOrNull() to mostPlayedSongsStats.firstOrNull(),
                    transitionSpec = {
                        slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(300)) togetherWith
                            slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(200))
                    },
                    modifier = Modifier.animateItem(),
                    label = "highlights",
                ) { (topArtist, topSong) ->
                    StatsHighlightsSection(
                        topArtist = topArtist,
                        topSong = topSong,
                        topSongEntity = mostPlayedSongs.firstOrNull(),
                        navController = navController,
                    )
                }
            }

            statsChartsSection(
                mostPlayedArtists = mostPlayedArtists,
                listeningSummary = listeningSummary,
                listeningByDayOfWeek = listeningByDayOfWeek,
                listeningByHour = listeningByHour,
            )

            statsSongsSection(
                mostPlayedSongsStats = mostPlayedSongsStats,
                mostPlayedSongs = mostPlayedSongs,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                navController = navController,
                menuState = menuState,
                haptics = haptics,
            )

            statsArtistsSection(
                mostPlayedArtists = mostPlayedArtists,
                navController = navController,
                menuState = menuState,
                coroutineScope = coroutineScope,
                haptics = haptics,
            )

            statsAlbumsSection(
                mostPlayedAlbums = mostPlayedAlbums,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                menuState = menuState,
                haptics = haptics,
            )
        }

        if (mostPlayedSongs.isNotEmpty()) {
            HideOnScrollFAB(
                visible = true,
                scrollContract = lazyListState.asScrollContract(),
                icon = R.drawable.shuffle,
                label = stringResource(R.string.shuffle),
                hazeState = fabHazeState,
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.most_played_songs),
                            items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled(),
                        ),
                    )
                },
            )
        }

        StatsTopAppBar(
            navController = navController,
            onOpenYearPicker = { isYearPickerOpen = true },
        )

        if (isYearPickerOpen) {
            StatsYearPickerDialog(
                availableYears = availableYears,
                selectedYear = currentDate.year,
                onSelectYear = { year ->
                    isYearPickerOpen = false
                    navController.navigate("year_in_music?year=$year")
                },
                onDismiss = { isYearPickerOpen = false },
            )
        }
    }
}
