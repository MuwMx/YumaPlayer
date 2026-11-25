package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo

enum class PlayerMenuScreen { SETTINGS, CUSTOMIZATION, SLEEP_TIMER, ABOUT, DETAILS, DOWNLOAD }

private val OptionsMenuMaxWidth = 560.dp
private val MenuDismissThreshold = 64.dp
private val MenuHeaderDragHeight = 24.dp
private const val MenuExitTranslatePx = 300f

@Composable
fun FullPlayerOptionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    state: PlayerUiState,
    updateState: UpdateState,
    onAction: (PlayerAction) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenPlaybackSpeed: () -> Unit = {},
    onOpenAddToPlaylist: () -> Unit = {},
    hasArtistId: Boolean = false,
    hasAlbumId: Boolean = false,
    modifier: Modifier = Modifier,
    initialScreen: PlayerMenuScreen = PlayerMenuScreen.SETTINGS
) {
    var currentScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }
    var dismissRequested by remember { mutableStateOf(false) }

    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentExpanded by rememberUpdatedState(expanded)
    val requestDismiss: () -> Unit = remember {
        {
            if (!dismissRequested) {
                dismissRequested = true
                currentOnDismissRequest()
            }
        }
    }

    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { MenuDismissThreshold.toPx() } }
    val scope = rememberCoroutineScope()
    var panelOffsetPx by remember {
        mutableFloatStateOf(if (expanded) 0f else MenuExitTranslatePx)
    }
    var offsetAnimationJob by remember { mutableStateOf<Job?>(null) }
    val panelSpring = remember { spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessLow) }

    fun animatePanelOffsetTo(target: Float) {
        offsetAnimationJob?.cancel()
        if (panelOffsetPx == target) return
        offsetAnimationJob = scope.launch {
            animate(
                initialValue = panelOffsetPx,
                targetValue = target,
                animationSpec = panelSpring,
            ) { value, _ ->
                panelOffsetPx = value
            }
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            currentScreen = initialScreen
            dismissRequested = false
        } else {
            dismissRequested = true
        }
        animatePanelOffsetTo(
            if (expanded) 0f else maxOf(panelOffsetPx, MenuExitTranslatePx)
        )
    }

    if (expanded) {
        BackHandler {
            when {
                currentScreen == PlayerMenuScreen.DETAILS ||
                currentScreen == PlayerMenuScreen.CUSTOMIZATION ||
                currentScreen == PlayerMenuScreen.SLEEP_TIMER ||
                currentScreen == PlayerMenuScreen.DOWNLOAD ||
                currentScreen == PlayerMenuScreen.ABOUT -> currentScreen = PlayerMenuScreen.SETTINGS
                else -> requestDismiss()
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(300, easing = LinearEasing),
        label = "MenuAlpha"
    )

    val panelShape = remember { RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius) }

    if (expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                currentScreen = PlayerMenuScreen.SETTINGS
                requestDismiss()
            },
            sheetState = sheetState,
            sheetMaxWidth = OptionsMenuMaxWidth,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            dragHandle = null,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            modifier = modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 48.dp),
        ) {
            BackHandler {
                when {
                    currentScreen == PlayerMenuScreen.DETAILS ||
                    currentScreen == PlayerMenuScreen.CUSTOMIZATION ||
                    currentScreen == PlayerMenuScreen.SLEEP_TIMER ||
                    currentScreen == PlayerMenuScreen.DOWNLOAD ||
                    currentScreen == PlayerMenuScreen.ABOUT -> currentScreen = PlayerMenuScreen.SETTINGS
                    else -> {
                        currentScreen = PlayerMenuScreen.SETTINGS
                        requestDismiss()
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.BottomSheetHorizontalPadding),
                shape = panelShape,
                color = Color(state.darkMutedColor).copy(alpha = 1f),
                shadowElevation = 10.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                        .padding(
                            top = SettingsDimensions.BottomSheetContentPaddingTop,
                            bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                        )
                ) {
                    val darkScheme = darkColorScheme()
                    MaterialTheme(colorScheme = darkScheme) {
                        CompositionLocalProvider(
                            LocalContentColor provides Color.White,
                            LocalYumaColors provides darkYumaColorScheme(darkScheme),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(
                                                width = SettingsDimensions.BottomSheetDragHandleWidth,
                                                height = SettingsDimensions.BottomSheetDragHandleHeight,
                                            )
                                            .clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = SettingsDimensions.YumaRowArrowAlpha
                                                )
                                            )
                                    )
                                }

                                AnimatedContent(
                                    targetState = currentScreen,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false),
                                    transitionSpec = {
                                        if (targetState != PlayerMenuScreen.SETTINGS) {
                                            (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                                        } else {
                                            (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                                        }.using(SizeTransform(clip = true))
                                    },
                                    label = "MenuScreenTransition"
                                ) { screen ->
                                    when (screen) {
                                        PlayerMenuScreen.SETTINGS -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                SettingsMenuContent(
                                                    state = state,
                                                    updateState = updateState,
                                                    onNavigateToAbout = { currentScreen = PlayerMenuScreen.ABOUT },
                                                    onNavigateToCustomization = { currentScreen = PlayerMenuScreen.CUSTOMIZATION },
                                                    onNavigateToSleepTimer = { currentScreen = PlayerMenuScreen.SLEEP_TIMER },
                                                    onNavigateToDetails = { currentScreen = PlayerMenuScreen.DETAILS },
                                                    onNavigateToDownload = { currentScreen = PlayerMenuScreen.DOWNLOAD },
                                                    onOpenEqualizer = onOpenEqualizer,
                                                    onOpenPlaybackSpeed = onOpenPlaybackSpeed,
                                                    onOpenAddToPlaylist = onOpenAddToPlaylist,
                                                    onDismissRequest = requestDismiss,
                                                    hasArtistId = hasArtistId,
                                                    hasAlbumId = hasAlbumId,
                                                    onAction = onAction
                                                )
                                            }
                                        }

                                        PlayerMenuScreen.CUSTOMIZATION -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                CustomizationMenuContent(
                                                    state = state,
                                                    onBackgroundStyleChanged = onBackgroundStyleChanged,
                                                    onImmersiveChanged = onImmersiveChanged,
                                                    onAction = onAction
                                                )
                                            }
                                        }
                                        PlayerMenuScreen.SLEEP_TIMER -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                SleepTimerMenuContent(
                                                    state = state,
                                                    onAction = onAction,
                                                    onBackClick = { currentScreen = PlayerMenuScreen.SETTINGS }
                                                )
                                            }
                                        }
                                        PlayerMenuScreen.ABOUT -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(vertical = SettingsDimensions.SectionSpacing)
                                            ) {
                                                AboutMenuSection(
                                                    state = state,
                                                    updateState = updateState,
                                                    onAction = onAction,
                                                    onDismissRequest = requestDismiss
                                                )
                                            }
                                        }
                                        PlayerMenuScreen.DOWNLOAD -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                DownloadMenuContent(
                                                    state = state,
                                                    onDismissRequest = requestDismiss
                                                )
                                            }
                                        }
                                        PlayerMenuScreen.DETAILS -> {
                                            val songId = state.trackUrl
                                            if (songId.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 460.dp)
                                                ) {
                                                    ShowMediaInfo(
                                                        videoId = songId,
                                                        onDismiss = requestDismiss,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
