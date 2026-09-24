package moe.rukamori.archivetune

import android.app.Activity
import android.content.Intent
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerLastAnchorKey
import moe.rukamori.archivetune.constants.MiniPlayerOnlyOffset
import moe.rukamori.archivetune.constants.MiniPlayerWithNavBarOffset
import moe.rukamori.archivetune.constants.NavigationBarAnimationSpec
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.COLLAPSED_ANCHOR
import moe.rukamori.archivetune.ui.component.DISMISSED_ANCHOR
import moe.rukamori.archivetune.ui.component.EXPANDED_ANCHOR
import moe.rukamori.archivetune.ui.component.FloatingNavigationToolbar
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState
import moe.rukamori.archivetune.ui.player.player_0.UnifiedPlayerSheetV2
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.state.PlayerEvent
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YdsInsets
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun rememberPlayerBottomSheetState(
    maxHeight: Dp,
    bottomInset: Dp,
    shouldShowNav: Boolean,
    useRail: Boolean,
): BottomSheetState =
    rememberBottomSheetState(
        dismissedBound = 0.dp,
        collapsedBound = bottomInset + if (shouldShowNav && !useRail) {
            MiniPlayerWithNavBarOffset
        } else {
            MiniPlayerOnlyOffset
        },
        expandedBound = maxHeight,
    )

@Composable
fun rememberPlayerAwareWindowInsets(
    useRail: Boolean,
    bottomInset: Dp,
    shouldShowNavigationBar: Boolean,
    isMiniPlayerVisible: Boolean,
    floatingToolbarBottomPadding: Dp,
    windowsInsets: WindowInsets,
): WindowInsets =
    remember(
        useRail,
        bottomInset,
        shouldShowNavigationBar,
        isMiniPlayerVisible,
        floatingToolbarBottomPadding,
        windowsInsets,
    ) {
        var bottom = bottomInset
        if (shouldShowNavigationBar && !useRail) {
            bottom = floatingToolbarBottomPadding + FloatingToolbarHeight
        }
        if (isMiniPlayerVisible) {
            bottom += MiniPlayerHeight + MiniPlayerBottomSpacing
        }
        windowsInsets
            .only(
                (
                    if (useRail) {
                        WindowInsetsSides.Right
                    } else {
                        WindowInsetsSides.Horizontal
                    }
                ) + WindowInsetsSides.Top,
            ).add(WindowInsets(top = AppBarHeight, bottom = bottom))
    }

@Composable
fun rememberPlayerAwareWindowInsets(
    playerViewModel: PlayerViewModel,
    useRail: Boolean,
    bottomInset: Dp,
    shouldShowNavigationBar: Boolean,
    floatingToolbarBottomPadding: Dp,
    windowsInsets: WindowInsets,
): WindowInsets {
    val isMiniPlayerVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.trackUrl.isNotEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    return rememberPlayerAwareWindowInsets(
        useRail = useRail,
        bottomInset = bottomInset,
        shouldShowNavigationBar = shouldShowNavigationBar,
        isMiniPlayerVisible = isMiniPlayerVisible,
        floatingToolbarBottomPadding = floatingToolbarBottomPadding,
        windowsInsets = windowsInsets,
    )
}

@Composable
fun PlayerOverlayHost(
    navController: NavHostController,
    maxHeight: Dp,
    bottomInset: Dp,
    shouldShowNav: Boolean,
    isYearInMusic: Boolean,
    modifier: Modifier = Modifier,
    useRail: Boolean = false,
    hazeState: HazeState? = null,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    pureBlack: Boolean = false,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    playerConnection: PlayerConnection? = null,
    database: MusicDatabase? = null,
    systemBarController: SystemBarController? = null,
    window: Window? = (LocalContext.current as? Activity)?.window,
    sheetState: BottomSheetState = rememberPlayerBottomSheetState(
        maxHeight = maxHeight,
        bottomInset = bottomInset,
        shouldShowNav = shouldShowNav,
        useRail = useRail,
    ),
    aodModeLaunchRequestCount: Int = 0,
    onResetAodLaunchRequestCount: () -> Unit = {},
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    shouldShowHomeShuffleButton: Boolean = false,
    navigationItems: List<Screens> = Screens.MainScreens,
    navBackStackEntry: NavBackStackEntry? = null,
    handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { _, _ -> },
    onSearchItemDoubleClick: () -> Unit = {},
    disableAnimations: Boolean = false,
    navVisibleHeight: Dp = FloatingToolbarHeight,
    floatingToolbarBottomPadding: Dp = YdsInsets.floatingToolbarBottomPadding(),
    onExpansionFraction: (() -> Float) -> Unit = {},
    onExpansionState: (State<Float>) -> Unit = {},
) {
    var playerExpansionFraction by remember { mutableFloatStateOf(0f) }
    val expansionFraction: () -> Float = remember { { playerExpansionFraction } }
    val expansionState: State<Float> = remember { derivedStateOf { playerExpansionFraction } }

    SideEffect {
        onExpansionFraction(expansionFraction)
        onExpansionState(expansionState)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(playerViewModel) {
        playerViewModel.event.collect { event ->
            when (event) {
                is PlayerEvent.ShareTrack -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.url)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
                is PlayerEvent.Navigate -> {
                    navController.navigate(event.route) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val isPlayerLyricsVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.isLyricsVisible }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val isPlayerQueueVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.isQueueVisible }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    BackHandler(enabled = playerExpansionFraction > 0.5f) {
        when {
            isPlayerLyricsVisible -> {
                playerViewModel.setLyricsVisible(false)
            }
            isPlayerQueueVisible -> {
                playerViewModel.setQueueVisible(false)
            }
            else -> {
                playerViewModel.requestSheetCollapse()
            }
        }
    }

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT,
    )
    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V4,
    )

    val aodModeEnabled by remember(playerConnection) {
        playerConnection?.aodModeEnabled ?: MutableStateFlow(false)
    }.collectAsStateWithLifecycle()

    LaunchedEffect(aodModeLaunchRequestCount, playerConnection) {
        val launchRequestCount = aodModeLaunchRequestCount
        if (launchRequestCount == 0) return@LaunchedEffect
        val connection = playerConnection ?: return@LaunchedEffect
        if (!awaitRestorablePlayback(connection)) return@LaunchedEffect
        if (!sheetState.isExpandedOrExpanding) {
            sheetState.expandSoft()
        }
        connection.aodModeEnabled.value = true
        if (aodModeLaunchRequestCount == launchRequestCount) {
            onResetAodLaunchRequestCount()
        }
    }

    LaunchedEffect(aodModeEnabled) {
        window?.let { win ->
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            if (aodModeEnabled) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(useDarkTheme, sheetState.isExpanded, playerBackground, aodModeEnabled) {
        if (aodModeEnabled) return@LaunchedEffect
        val isDarkStatusBar =
            if (sheetState.isExpanded &&
                playerBackground != PlayerBackgroundStyle.DEFAULT
            ) {
                true
            } else {
                useDarkTheme
            }
        if (systemBarController != null) {
            systemBarController.setSystemBarAppearance(isDarkStatusBar)
        } else if (window != null) {
            setSystemBarAppearance(window, isDarkStatusBar)
        }
    }

    val miniPlayerAnchor by remember {
        derivedStateOf {
            when {
                sheetState.isExpanded -> EXPANDED_ANCHOR
                sheetState.isDismissed -> DISMISSED_ANCHOR
                else -> COLLAPSED_ANCHOR
            }
        }
    }

    val (savedMiniPlayerAnchor, setSavedMiniPlayerAnchor) =
        rememberPreference(
            MiniPlayerLastAnchorKey,
            defaultValue = COLLAPSED_ANCHOR,
        )

    var miniPlayerAnchorPersistenceEnabled by remember(playerConnection) {
        mutableStateOf(false)
    }

    LaunchedEffect(miniPlayerAnchor, isYearInMusic, miniPlayerAnchorPersistenceEnabled) {
        if (!isYearInMusic && miniPlayerAnchorPersistenceEnabled) {
            setSavedMiniPlayerAnchor(miniPlayerAnchor)
        }
    }

    var yearInMusicSavedPlayerAnchor by rememberSaveable { mutableStateOf(-1) }

    val shouldHideStatusBars =
        isYearInMusic ||
            (sheetState.isExpanded && playerDesignStyle == PlayerDesignStyle.V7)

    LaunchedEffect(shouldHideStatusBars, aodModeEnabled) {
        if (aodModeEnabled) return@LaunchedEffect
        if (systemBarController != null) {
            systemBarController.setStatusBarsHidden(shouldHideStatusBars)
        } else if (window != null) {
            setStatusBarsHidden(window, shouldHideStatusBars)
        }
    }

    LaunchedEffect(isYearInMusic, playerConnection) {
        val connection = playerConnection ?: return@LaunchedEffect

        if (isYearInMusic) {
            if (yearInMusicSavedPlayerAnchor == -1) {
                yearInMusicSavedPlayerAnchor =
                    when {
                        sheetState.isExpanded -> EXPANDED_ANCHOR
                        sheetState.isCollapsed -> COLLAPSED_ANCHOR
                        sheetState.isDismissed -> DISMISSED_ANCHOR
                        else -> COLLAPSED_ANCHOR
                    }
            }

            if (!sheetState.isDismissed) {
                sheetState.dismiss()
            }
        } else if (yearInMusicSavedPlayerAnchor != -1) {
            val anchorToRestore = yearInMusicSavedPlayerAnchor
            yearInMusicSavedPlayerAnchor = -1

            if (!awaitRestorablePlayback(connection)) {
                sheetState.dismiss()
            } else {
                when (anchorToRestore) {
                    EXPANDED_ANCHOR -> sheetState.expandSoft()
                    COLLAPSED_ANCHOR -> sheetState.collapseSoft()
                    DISMISSED_ANCHOR -> sheetState.collapseSoft()
                    else -> sheetState.collapseSoft()
                }
            }
        }
    }

    var restoredMiniPlayerAnchor by remember(playerConnection) { mutableStateOf(false) }

    LaunchedEffect(playerConnection, savedMiniPlayerAnchor, isYearInMusic) {
        if (restoredMiniPlayerAnchor) return@LaunchedEffect
        val connection = playerConnection ?: return@LaunchedEffect
        connection.queueRestoreCompleted.first { it }
        if (!awaitRestorablePlayback(connection)) {
            if (!sheetState.isDismissed) {
                sheetState.dismiss()
            }
        } else {
            if (!isYearInMusic) {
                when (savedMiniPlayerAnchor) {
                    EXPANDED_ANCHOR -> sheetState.expandSoft()
                    COLLAPSED_ANCHOR -> sheetState.collapseSoft()
                    DISMISSED_ANCHOR -> sheetState.collapseSoft()
                    else -> sheetState.collapseSoft()
                }
            }
        }
        restoredMiniPlayerAnchor = true
        miniPlayerAnchorPersistenceEnabled = true
    }

    val currentPlayerBottomSheetState = rememberUpdatedState(sheetState)
    val currentIsYearInMusicScreen = rememberUpdatedState(isYearInMusic)

    DisposableEffect(playerConnection) {
        val player =
            playerConnection?.player ?: return@DisposableEffect onDispose { }
        val listener =
            object : Player.Listener {
                private fun collapseDismissedMiniPlayerForActivePlayback() {
                    if (
                        player.mediaItemCount > 0 &&
                        player.currentMediaItem != null &&
                        player.playWhenReady &&
                        player.playbackState != Player.STATE_IDLE &&
                        player.playbackState != Player.STATE_ENDED &&
                        currentPlayerBottomSheetState.value.isDismissed &&
                        !currentIsYearInMusicScreen.value
                    ) {
                        currentPlayerBottomSheetState.value.collapseSoft()
                    }
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }

                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int,
                ) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    val bottomNavigationBarHeight by animateDpAsState(
        targetValue = if (shouldShowNav && !useRail) navVisibleHeight else 0.dp,
        animationSpec = if (disableAnimations) snap() else NavigationBarAnimationSpec,
        label = "",
    )

    val isMiniPlayerActive by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.trackUrl.isNotEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    Box(modifier = modifier) {
        ScopedPlayerSheet(
            playerViewModel = playerViewModel,
            playerConnection = playerConnection,
            navController = navController,
            bottomNavigationBarHeight = bottomNavigationBarHeight,
            hazeState = hazeState,
            pureBlack = pureBlack,
            blurRadius = blurRadius,
            onExpansionFractionChanged = { fraction ->
                playerExpansionFraction = fraction
            },
        )

        if (useRail) return@Box

        val navSlideDistance =
            floatingToolbarBottomPadding + navVisibleHeight

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .height(navSlideDistance)
                    .offset {
                        if (bottomNavigationBarHeight == 0.dp) {
                            IntOffset(
                                x = 0,
                                y = navSlideDistance.roundToPx(),
                            )
                        } else {
                            val slideOffset =
                                navSlideDistance.toPx() *
                                    playerExpansionFraction.coerceIn(
                                        0f,
                                        1f,
                                    )
                            val hideOffset =
                                navSlideDistance.toPx() *
                                    (
                                        1f -
                                            bottomNavigationBarHeight.coerceAtMost(navVisibleHeight) /
                                            navVisibleHeight
                                    )
                            IntOffset(
                                x = 0,
                                y = (slideOffset + hideOffset).roundToInt(),
                            )
                        }
                    },
        ) {
            FloatingNavigationToolbar(
                items = navigationItems,
                pureBlack = pureBlack,
                hazeState = hazeState,
                blurRadius = blurRadius,
                showBorder = !isMiniPlayerActive || playerExpansionFraction >= SettingsDimensions.FullyExpandedThreshold,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = FloatingToolbarHorizontalPadding,
                            end = FloatingToolbarHorizontalPadding,
                            bottom = floatingToolbarBottomPadding,
                        ).height(navVisibleHeight),
                onShuffleClick =
                    if (shouldShowHomeShuffleButton) {
                        {
                            val localItems = homeViewModel.allLocalItems.value
                            val ytItems = homeViewModel.allYtItems.value
                            val useLocalSource =
                                when {
                                    localItems.isNotEmpty() && ytItems.isNotEmpty() -> {
                                        Random.nextFloat() < 0.5f
                                    }

                                    localItems.isNotEmpty() -> {
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }

                            coroutineScope.launch(Dispatchers.Main) {
                                if (useLocalSource) {
                                    when (val luckyItem = localItems.random()) {
                                        is Song -> {
                                            playerConnection?.playQueue(
                                                if (luckyItem.song.isLocal) {
                                                    ListQueue(items = listOf(luckyItem.toMediaItem()))
                                                } else {
                                                    YouTubeQueue.radio(luckyItem.toMediaMetadata())
                                                },
                                            )
                                        }

                                        is Album -> {
                                            val albumWithSongs =
                                                if (database != null) {
                                                    withContext(Dispatchers.IO) {
                                                        database.albumWithSongs(luckyItem.id).first()
                                                    }
                                                } else {
                                                    null
                                                }

                                            albumWithSongs?.let {
                                                playerConnection?.playQueue(LocalAlbumRadio(it))
                                            }
                                        }

                                        is Artist, is Playlist -> {}
                                    }
                                } else {
                                    when (val luckyItem = ytItems.random()) {
                                        is SongItem -> {
                                            playerConnection?.playQueue(
                                                YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                                            )
                                        }

                                        is AlbumItem -> {
                                            playerConnection?.playQueue(
                                                YouTubeAlbumRadio(luckyItem.playlistId),
                                            )
                                        }

                                        is ArtistItem -> {
                                            luckyItem.radioEndpoint?.let {
                                                playerConnection?.playQueue(YouTubeQueue(it))
                                            }
                                        }

                                        is PlaylistItem -> {
                                            luckyItem.playEndpoint?.let {
                                                playerConnection?.playQueue(YouTubeQueue.playlist(it))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    },
                shuffleIconRes = if (shouldShowHomeShuffleButton) R.drawable.shuffle else null,
                shuffleContentDescription =
                    if (shouldShowHomeShuffleButton) {
                        stringResource(
                            R.string.shuffle,
                        )
                    } else {
                        ""
                    },
                onMusicRecognitionClick =
                    if (shouldShowHomeShuffleButton) {
                        { navController.navigate(MusicRecognitionRoute) }
                    } else {
                        null
                    },
                musicRecognitionContentDescription =
                    if (shouldShowHomeShuffleButton) {
                        stringResource(
                            R.string.music_recognition,
                        )
                    } else {
                        ""
                    },
                onMusicTogetherClick =
                    if (shouldShowHomeShuffleButton) {
                        { navController.navigate("settings/music_together") }
                    } else {
                        null
                    },
                isSelected = { screen ->
                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } ==
                        true
                },
                onItemClick = { screen, isSelected ->
                    handlePrimaryNavigationClick(screen, isSelected)
                },
                onSearchItemDoubleClick = onSearchItemDoubleClick,
            )
        }
    }
}

@Composable
private fun ScopedPlayerSheet(
    playerViewModel: PlayerViewModel,
    playerConnection: PlayerConnection?,
    navController: NavController,
    bottomNavigationBarHeight: Dp,
    hazeState: HazeState?,
    pureBlack: Boolean,
    blurRadius: Float,
    onExpansionFractionChanged: (Float) -> Unit,
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()
    UnifiedPlayerSheetV2(
        state = uiState,
        queueState = queueState,
        progressMsProvider = playerViewModel.progressMsProvider,
        onAction = { action ->
            when (action) {
                is PlayerAction.StartRadio -> {
                    playerConnection?.startRadioSeamlessly()
                }
                is PlayerAction.OpenArtist -> {
                    playerConnection?.service?.currentMediaMetadata?.value?.artists?.firstOrNull()?.id?.let { artistId ->
                        playerViewModel.requestSheetCollapse()
                        navController.navigate("artist/$artistId")
                    }
                }
                is PlayerAction.OpenAlbum -> {
                    playerConnection?.service?.currentMediaMetadata?.value?.album?.id?.let { albumId ->
                        playerViewModel.requestSheetCollapse()
                        navController.navigate("album/$albumId")
                    }
                }
                else -> playerViewModel.handleAction(action)
            }
        },
        onLyricsClick = { playerViewModel.setLyricsVisible(true) },
        onCloseLyricsClick = { playerViewModel.setLyricsVisible(false) },
        onOpenQueue = { playerViewModel.setQueueVisible(true) },
        onCloseQueueClick = { playerViewModel.setQueueVisible(false) },
        onSearchLyricsClick = { playerViewModel.fetchLyrics() },
        onSeek = { position -> playerViewModel.seekTo(position.toLong()) },
        onSeekStarted = { playerViewModel.onSeekStarted() },
        onBackgroundStyleChanged = { playerViewModel.setBlurBackgroundEnabled(it) },
        onImmersiveChanged = { playerViewModel.setImmersiveEnabled(it) },
        bottomBarHeight = bottomNavigationBarHeight,
        hazeState = hazeState,
        pureBlack = pureBlack,
        blurRadius = blurRadius,
        onExpansionFractionChanged = onExpansionFractionChanged,
    )
}
