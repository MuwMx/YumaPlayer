package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState

// Состояния суб-навигации
enum class PlayerMenuScreen { SETTINGS, CUSTOMIZATION, SLEEP_TIMER, ABOUT }

@Composable
fun FullPlayerOptionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    state: PlayerUiState,
    updateState: UpdateState,
    onAction: (PlayerAction) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialScreen: PlayerMenuScreen = PlayerMenuScreen.SETTINGS
) {

    val GoogleSans = FontFamily(
        Font(R.font.google_sans_regular, FontWeight.Normal),
        Font(R.font.google_sans_bold, FontWeight.Bold)
    )

    // Стейт текущего экрана внутри меню
    var currentScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }

    // Сбрасываем или выставляем экран при открытии меню
    LaunchedEffect(expanded) {
        if (expanded) {
            currentScreen = initialScreen
        }
    }

    // Обработка системной кнопки "Назад"
    if (expanded) {
        BackHandler {
            if (currentScreen != PlayerMenuScreen.SETTINGS) {
                currentScreen = PlayerMenuScreen.SETTINGS
            } else {
                onDismissRequest()
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
            val cardGradient = Brush.verticalGradient(
                colors = listOf(Color(state.darkMutedColor), Color(0xFF161616))
            )

            Box(
                modifier = Modifier
                    .width(320.dp)
                    .graphicsLayer {
                        this.translationY = translateY
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(28.dp))
                    .background(cardGradient)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(20.dp)
            ) {
                Column {
                    // ==========================================
                    // АНИМИРОВАННЫЙ КОНТЕНТ (Суб-навигация)
                    // ==========================================
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
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ==========================================
                    // АДАПТИВНЫЙ ПОДВАЛ (Footer)
                    // ==========================================
                    Text(
                        text = if (currentScreen == PlayerMenuScreen.SETTINGS) "Close" else "Back",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSans,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // ФИКС: теперь корректно возвращает из Любого подэкрана назад в настройки
                                if (currentScreen != PlayerMenuScreen.SETTINGS) {
                                    currentScreen = PlayerMenuScreen.SETTINGS
                                } else {
                                    onDismissRequest()
                                }
                            }
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}
