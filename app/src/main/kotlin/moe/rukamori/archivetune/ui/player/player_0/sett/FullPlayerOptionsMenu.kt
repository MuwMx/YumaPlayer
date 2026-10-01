package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier,
    initialScreen: PlayerMenuScreen = PlayerMenuScreen.SETTINGS
) {
    var currentScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }

    LaunchedEffect(expanded) {
        if (expanded) {
            currentScreen = initialScreen
        }
    }

    if (expanded) {
        BackHandler {
            when {
                currentScreen == PlayerMenuScreen.DETAILS ||
                currentScreen == PlayerMenuScreen.CUSTOMIZATION ||
                currentScreen == PlayerMenuScreen.SLEEP_TIMER ||
                currentScreen == PlayerMenuScreen.DOWNLOAD ||
                currentScreen == PlayerMenuScreen.ABOUT -> currentScreen = PlayerMenuScreen.SETTINGS
                else -> onDismissRequest()
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(300, easing = LinearEasing),
        label = "MenuAlpha"
    )

    val translateY by animateFloatAsState(
        targetValue = if (expanded) 0f else 300f,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
        label = "MenuSlide"
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha * 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(340.dp)
                    .graphicsLayer {
                        this.translationY = translateY
                        this.alpha = alpha
                    }
                    .yumaGlassCard(
                        shape = RoundedCornerShape(28.dp),
                        backgroundColor = Color(state.darkMutedColor).copy(alpha = 1f),
                        borderColor = LocalYumaColors.current.glassBorder,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(20.dp)
            ) {
                val darkScheme = darkColorScheme()
                MaterialTheme(colorScheme = darkScheme) {
                    CompositionLocalProvider(
                        LocalContentColor provides Color.White,
                        LocalYumaColors provides darkYumaColorScheme(darkScheme),
                    ) {
                        Column {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    if (targetState != PlayerMenuScreen.SETTINGS) {
                                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                                    } else {
                                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                                    }.using(SizeTransform(clip = false))
                                },
                                label = "MenuScreenTransition"
                            ) { screen ->
                                when (screen) {
                                    PlayerMenuScreen.SETTINGS -> {
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
                                            onAction = onAction
                                        )
                                    }

                                    PlayerMenuScreen.CUSTOMIZATION -> {
                                        CustomizationMenuContent(
                                            state = state,
                                            onBackgroundStyleChanged = onBackgroundStyleChanged,
                                            onImmersiveChanged = onImmersiveChanged,
                                            onAction = onAction
                                        )
                                    }
                                    PlayerMenuScreen.SLEEP_TIMER -> {
                                        SleepTimerMenuContent(
                                            state = state,
                                            onAction = onAction,
                                            onBackClick = { currentScreen = PlayerMenuScreen.SETTINGS }
                                        )
                                    }
                                    PlayerMenuScreen.ABOUT -> {
                                        AboutMenuSection(
                                            state = state,
                                            updateState = updateState,
                                            onAction = onAction,
                                            onDismissRequest = onDismissRequest
                                        )
                                    }
                                    PlayerMenuScreen.DOWNLOAD -> {
                                        DownloadMenuContent(
                                            state = state,
                                            onDismissRequest = onDismissRequest
                                        )
                                    }
                                    PlayerMenuScreen.DETAILS -> {
                                        val songId = state.trackUrl
                                        if (songId.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(460.dp)
                                            ) {
                                                ShowMediaInfo(videoId = songId)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = if (currentScreen == PlayerMenuScreen.SETTINGS) stringResource(R.string.close) else stringResource(R.string.back_button_desc),
                                color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = LocalArchiveTuneFontFamily.current,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .yumaClickable {
                                        when {
                                            currentScreen != PlayerMenuScreen.SETTINGS -> currentScreen = PlayerMenuScreen.SETTINGS
                                            else -> onDismissRequest()
                                        }
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
