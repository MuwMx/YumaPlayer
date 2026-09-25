/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.ui.component.HeaderType
import moe.rukamori.archivetune.ui.component.HideOnScrollFAB
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.rememberCollapseFraction
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ArtistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val loadedArtistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val loadedLibrarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val loadedLibraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val blockState by viewModel.blockState.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }

    val uiState =
        rememberArtistUiState(
            loadedArtistPage = loadedArtistPage,
            libraryArtist = libraryArtist,
            loadedLibrarySongs = loadedLibrarySongs,
            loadedLibraryAlbums = loadedLibraryAlbums,
            blockState = blockState,
            showLocal = showLocal,
        )

    val actions =
        rememberArtistActions(
            viewModel = viewModel,
            onShowLocalChange = { showLocal = it },
        )

    ArtistEffects(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        context = context,
        libraryArtist = libraryArtist,
        onShowLocalChange = { showLocal = it },
    )

    val screenWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val expandedHeight = screenWidthDp * HeaderType.ARTIST.heightRatio
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val topPadding = systemBarsTopPadding + AppBarHeight

    val thumbnail = uiState.artistPage?.artist?.thumbnail ?: uiState.libraryArtist?.artist?.thumbnailUrl
    val (gradientColors, gradientAlpha) =
        rememberArtistGradientState(
            thumbnailUrl = thumbnail,
            context = context,
            fallbackColor = MaterialTheme.colorScheme.surface.toArgb(),
            lazyListState = lazyListState,
        )

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    val collapseFraction by rememberCollapseFraction(lazyListState)
    val fabHazeState = remember { HazeState() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        ArtistGradientBackground(
            gradientColors = gradientColors,
            gradientAlpha = gradientAlpha,
            surfaceColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        ArtistMorphingHeader(
            imageUrl = thumbnail,
            collapseFraction = collapseFraction,
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize().hazeSource(fabHazeState),
        ) {
            if (uiState.artistPage == null && !uiState.showLocal) {
                artistShimmerItem(
                    expandedHeight = expandedHeight,
                    topPadding = topPadding,
                )
            } else {
                item(key = ARTIST_KEY_HEADER, contentType = "header") {
                    ArtistHeroContent(
                        artistPage = uiState.artistPage,
                        libraryArtist = uiState.libraryArtist,
                        librarySongs = uiState.librarySongs,
                        libraryAlbums = uiState.libraryAlbums,
                        showLocal = uiState.showLocal,
                        expandedHeight = expandedHeight,
                        topPadding = topPadding,
                        playerConnection = playerConnection,
                        database = database,
                    )
                }

                if (uiState.showLocal) {
                    artistLibrarySections(
                        artistId = viewModel.artistId,
                        libraryArtist = uiState.libraryArtist,
                        librarySongs = uiState.librarySongs,
                        libraryAlbums = uiState.libraryAlbums,
                        hideExplicit = hideExplicit,
                        navController = navController,
                        playerConnection = playerConnection,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        menuState = menuState,
                        haptics = haptics,
                        coroutineScope = coroutineScope,
                    )
                } else {
                    artistOnlineSections(
                        artistPage = uiState.artistPage,
                        artistId = viewModel.artistId,
                        navController = navController,
                        playerConnection = playerConnection,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        menuState = menuState,
                        haptics = haptics,
                        coroutineScope = coroutineScope,
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            scrollBehavior = scrollBehavior,
            title = {
                val animatedAlpha by animateFloatAsState(
                    targetValue = if (!transparentAppBar) 1f else 0f,
                    animationSpec = tween(200),
                    label = "titleAlpha",
                )
                Text(
                    text = uiState.artistPage?.artist?.title ?: uiState.libraryArtist?.artist?.name ?: "",
                    modifier = Modifier.alpha(animatedAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                TopAppBarBackButton(navController = navController)
            },
            actions = {
                IconButton(
                    onClick = {
                        showArtistOverflowMenu(
                            menuState = menuState,
                            uiState = uiState,
                            onAction = viewModel::onAction,
                        )
                    },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            },
        )

        HideOnScrollFAB(
            visible = uiState.librarySongs.isNotEmpty() && uiState.libraryArtist?.artist?.isLocal != true,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            label = if (showLocal) stringResource(R.string.together_online) else stringResource(R.string.filter_library),
            hazeState = fabHazeState,
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && uiState.artistPage == null) viewModel.fetchArtistsFromYTM()
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .align(Alignment.BottomCenter),
        )
    }
}
