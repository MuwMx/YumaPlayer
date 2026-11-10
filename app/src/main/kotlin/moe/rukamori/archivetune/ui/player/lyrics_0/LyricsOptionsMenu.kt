package moe.rukamori.archivetune.ui.player.lyrics_0

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.player.lyrics_0.menu.LyricsMenuDragHandle
import moe.rukamori.archivetune.ui.player.lyrics_0.menu.LyricsMenuEdit
import moe.rukamori.archivetune.ui.player.lyrics_0.menu.LyricsMenuMain
import moe.rukamori.archivetune.ui.player.lyrics_0.menu.LyricsMenuProviders
import moe.rukamori.archivetune.ui.player.lyrics_0.menu.LyricsMenuSyncOffset
import moe.rukamori.archivetune.ui.player.lyrics_0.menu.LyricsMenuTranslate
import moe.rukamori.archivetune.ui.player.lyrics_0.menu.rememberLyricsMenuState
import moe.rukamori.archivetune.ui.player.player_0.buttons.LyricsMenuScreen
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

private val LyricsMenuMaxWidth = 560.dp

@Composable
fun LyricsOptionsMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    state: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    var currentScreen by remember { mutableStateOf(LyricsMenuScreen.MAIN) }
    val menuState = rememberLyricsMenuState(
        isVisible = isVisible,
        isMainScreen = currentScreen == LyricsMenuScreen.MAIN,
        onDismiss = onDismiss,
    )

    LaunchedEffect(isVisible) {
        Log.d("SpotLyrics", "LyricsOptionsMenu: isVisible = $isVisible")
        if (!isVisible) {
            currentScreen = LyricsMenuScreen.MAIN
        }
        menuState.onVisibilityChanged(isVisible)
    }

    if (isVisible) {
        BackHandler {
            if (currentScreen == LyricsMenuScreen.MAIN) {
                menuState.requestDismiss()
            } else {
                currentScreen = LyricsMenuScreen.MAIN
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = LinearEasing),
        label = "LyricsMenuAlpha"
    )

    val panelShape = remember { RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius) }

    // Автоматический возврат на главный экран при успешном окончании перевода
    val isTranslating = state.isAiTranslating || state.isStandardTranslating
    LaunchedEffect(isTranslating) {
        if (!isTranslating && state.aiTranslationError == null && currentScreen == LyricsMenuScreen.TRANSLATE) {
            currentScreen = LyricsMenuScreen.MAIN
        }
    }

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha * 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (currentScreen == LyricsMenuScreen.MAIN) {
                            menuState.requestDismiss()
                        } else {
                            currentScreen = LyricsMenuScreen.MAIN
                        }
                    }
                ),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = SettingsDimensions.BottomSheetHorizontalPadding)
                    .padding(bottom = SettingsDimensions.BottomSheetBottomPadding),
                contentAlignment = Alignment.BottomCenter
            ) {
                val panelMaxHeight = maxHeight * 0.75f
                Box(
                    modifier = Modifier
                        .widthIn(max = LyricsMenuMaxWidth)
                        .fillMaxWidth()
                        .heightIn(max = panelMaxHeight)
                        .graphicsLayer {
                            this.translationY = menuState.panelOffsetPx.value
                            this.alpha = alpha
                        }
                        .yumaGlassCard(
                            shape = panelShape,
                            backgroundColor = Color(state.darkMutedColor).copy(alpha = 1f),
                            borderColor = LocalYumaColors.current.glassBorder,
                            strokeWidth = SettingsDimensions.GlassBorderThickness,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                        .padding(
                            top = SettingsDimensions.BottomSheetContentPaddingTop,
                            bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                        ),
                ) {
                    val darkScheme = darkColorScheme()
                    MaterialTheme(colorScheme = darkScheme) {
                        CompositionLocalProvider(
                            LocalContentColor provides Color.White,
                            LocalYumaColors provides darkYumaColorScheme(darkScheme),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LyricsMenuDragHandle(
                                    enabled = isVisible && currentScreen == LyricsMenuScreen.MAIN,
                                    onDragStart = menuState::onDragStart,
                                    onDrag = menuState::onDrag,
                                    onDragEnd = menuState::onDragEnd,
                                    onDragCancel = menuState::onDragCancel,
                                )

                                AnimatedContent(
                                    targetState = currentScreen,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false),
                                    transitionSpec = {
                                        if (targetState != LyricsMenuScreen.MAIN) {
                                            (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                                        } else {
                                            (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                                        }.using(SizeTransform(clip = true))
                                    },
                                    label = "LyricsMenuTransition"
                                ) { screen ->
                                    when (screen) {
                                        LyricsMenuScreen.MAIN -> LyricsMenuMain(
                                            state = state,
                                            onAction = onAction,
                                            onDismiss = menuState::requestDismiss,
                                            onNavigateTo = { currentScreen = it }
                                        )
                                        LyricsMenuScreen.EDIT -> LyricsMenuEdit(
                                            state = state,
                                            onAction = onAction,
                                            onBack = { currentScreen = LyricsMenuScreen.MAIN }
                                        )
                                        LyricsMenuScreen.TRANSLATE -> LyricsMenuTranslate(
                                            state = state,
                                            onAction = onAction,
                                            onBack = { currentScreen = LyricsMenuScreen.MAIN }
                                        )
                                        LyricsMenuScreen.SYNC_OFFSET -> LyricsMenuSyncOffset(
                                            state = state,
                                            onAction = onAction,
                                            onBack = { currentScreen = LyricsMenuScreen.MAIN }
                                        )
                                        LyricsMenuScreen.PROVIDERS -> LyricsMenuProviders(
                                            state = state,
                                            onAction = onAction,
                                            onBack = { currentScreen = LyricsMenuScreen.MAIN }
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
