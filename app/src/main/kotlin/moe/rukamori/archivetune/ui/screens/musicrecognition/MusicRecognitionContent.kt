/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.viewmodels.MusicRecognitionScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicRecognitionContent(
    state: MusicRecognitionScreenState,
    onNavigateBack: () -> Unit,
    onShowHistory: () -> Unit,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAllowPermission: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val scrollBehavior = appBarScrollBehavior()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useWideLayout =
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )
    val maximumContentWidth = if (useWideLayout) 1_040.dp else 680.dp

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.music_recognition),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onShowHistory) {
                        Icon(
                            painter = painterResource(R.drawable.history),
                            contentDescription = stringResource(R.string.music_recognition_history),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .widthIn(max = maximumContentWidth)
                        .fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        start = if (useWideLayout) 24.dp else 16.dp,
                        top = 24.dp,
                        end = if (useWideLayout) 24.dp else 16.dp,
                        bottom = 40.dp,
                    ),
            ) {
                item(
                    key = MusicRecognitionStateItemKey,
                    contentType = MusicRecognitionStateItemContentType,
                ) {
                    val motionScheme = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = state,
                        contentKey = MusicRecognitionScreenState::contentKey,
                        transitionSpec = {
                            (
                                fadeIn(motionScheme.defaultEffectsSpec()) +
                                    scaleIn(
                                        animationSpec = motionScheme.defaultSpatialSpec(),
                                        initialScale = StateTransitionInitialScale,
                                    )
                            ).togetherWith(
                                fadeOut(motionScheme.fastEffectsSpec()) +
                                    scaleOut(
                                        animationSpec = motionScheme.fastSpatialSpec(),
                                        targetScale = StateTransitionTargetScale,
                                    ),
                            )
                        },
                        label = "MusicRecognitionState",
                    ) { animatedState ->
                        when (animatedState) {
                            is MusicRecognitionScreenState.Empty -> {
                                RecognitionTaskState(
                                    phase = null,
                                    onListen = onListen,
                                    onCancel = onCancel,
                                )
                            }

                            is MusicRecognitionScreenState.Loading -> {
                                RecognitionTaskState(
                                    phase = animatedState.phase,
                                    onListen = onListen,
                                    onCancel = onCancel,
                                )
                            }

                            is MusicRecognitionScreenState.Error -> {
                                RecognitionErrorState(
                                    error = animatedState.error,
                                    onListen = onListen,
                                    onAllowPermission = onAllowPermission,
                                )
                            }

                            is MusicRecognitionScreenState.Success -> {
                                val searchResult =
                                    remember(animatedState.track.searchQuery, onSearch) {
                                        { onSearch(animatedState.track.searchQuery) }
                                    }
                                RecognitionResultContent(
                                    track = animatedState.track,
                                    useWideLayout = useWideLayout,
                                    onListenAgain = onListen,
                                    onSearch = searchResult,
                                    onOpenUri = onOpenUri,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
