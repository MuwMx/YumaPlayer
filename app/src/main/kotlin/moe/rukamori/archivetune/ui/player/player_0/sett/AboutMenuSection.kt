package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.setValue

// На самый верх файла настроек, сразу под импортами:
private val localFont = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

// ==========================================
// ЭКРАН 1: ОСНОВНЫЕ НАСТРОЙКИ
// ==========================================
@Composable
fun SettingsMenuContent(
    state: PlayerUiState,
    updateState: UpdateState,
    onNavigateToAbout: () -> Unit,
    onNavigateToCustomization: () -> Unit,
    onNavigateToSleepTimer: () -> Unit,
    onAction: (PlayerAction) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val softUpdateInteraction = remember { MutableInteractionSource() }
    val isSoftUpdatePressed by softUpdateInteraction.collectIsPressedAsState()
    val softUpdateScale by animateFloatAsState(if (isSoftUpdatePressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Player Settings",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = localFont,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        // Ряд разделен на три аккуратные кнопки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Поделиться
            MenuRowButton(
                iconRes = R.drawable.ic_share,
                onClick = { onAction(PlayerAction.Share) },
                modifier = Modifier.weight(1f)
            )

            // Умный derivedStateOf для форматирования тиков без перерисовки соседних слоев
            val sleepTimerText by remember(state.sleepTimerRemainingSeconds) {
                derivedStateOf {
                    val totalSecs = state.sleepTimerRemainingSeconds
                    if (totalSecs != null && totalSecs > 0) {
                        val m = totalSecs / 60
                        val s = totalSecs % 60
                        "%02d:%02d".format(m, s)
                    } else null
                }
            }

            // 2. Таймер сна
            MenuRowButton(
                iconRes = R.drawable.ic_sleep_timer,
                timerText = sleepTimerText,
                isActive = sleepTimerText != null,
                vibrantColor = Color(state.vibrantColor),
                onClick = onNavigateToSleepTimer,
                modifier = Modifier.weight(1f)
            )
        }


        // Возвращаем твой оригинальный рабочий Box для обновлений
        if (updateState is UpdateState.SoftUpdate) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .graphicsLayer { scaleX = softUpdateScale; scaleY = softUpdateScale }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFB33A3A).copy(alpha = 0.8f))
                    .clickable(interactionSource = softUpdateInteraction, indication = null) {
                        uriHandler.openUri(updateState.updateUrl)
                    }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.download),
                        contentDescription = "Update",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val formattedVer = if (updateState.versionName.startsWith("v", ignoreCase = true)) updateState.versionName else "v${updateState.versionName}"
                        Text(text = "Update Available ($formattedVer)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = localFont)
                        Text(text = "Tap to download from Telegram", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = localFont)
                    }
                }
            }
        }

        // Переход в Кастомизацию
        SettingsMenuRow(
            title = "Interface & Visuals",
            subtitle = "Blur, Glow, and background styles",
            iconResId = R.drawable.ic_palette,
            onClick = onNavigateToCustomization,
            showArrow = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Кнопка About & Support
        SettingsMenuRow(
            title = "About & Support",
            subtitle = "App info and links",
            iconResId = R.drawable.ic_about,
            onClick = onNavigateToAbout,
            showArrow = true
        )
    }
}

@Composable
fun AboutMenuSection(
    state: PlayerUiState,
    updateState: UpdateState,
    onAction: (PlayerAction) -> Unit,
    onDismissRequest: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "About & Support",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = localFont,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun MenuRowButton(
    iconRes: Int,
    timerText: String? = null,
    isActive: Boolean = false,
    vibrantColor: Color = Color.Transparent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "MenuButtonBounce"
    )

    val contentColor = if (isActive && vibrantColor.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) vibrantColor else Color(0x12FFFFFF))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isActive && timerText != null) {
            Text(
                text = timerText,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = localFont
            )
        } else {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (isActive) contentColor else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SleepTimerMenuContent(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBackClick: () -> Unit
) {
    val isTimerActive = state.sleepTimerRemainingSeconds != null

    var selectedMinutes by remember { mutableIntStateOf(15) }
    val GoogleSans = localFont

    val displayMinutes = if (isTimerActive) {
        ((state.sleepTimerRemainingSeconds ?: 0) + 59) / 60
    } else {
        selectedMinutes
    }

    val startStopInteraction = remember { MutableInteractionSource() }
    val isStartStopPressed by startStopInteraction.collectIsPressedAsState()
    val startStopScale by animateFloatAsState(
        targetValue = if (isStartStopPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "StartStopButtonBounce"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sleep Timer",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSans,
            modifier = Modifier.padding(start = 4.dp, bottom = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val minusInteraction = remember { MutableInteractionSource() }
            val isMinusPressed by minusInteraction.collectIsPressedAsState()
            val minusScale by animateFloatAsState(if (isMinusPressed) 0.85f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = minusScale; scaleY = minusScale }
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x12FFFFFF))
                    .clickable(interactionSource = minusInteraction, indication = null) {
                        if (isTimerActive) {
                            onAction(PlayerAction.AdjustSleepTimer(-5))
                        } else {
                            if (selectedMinutes > 5) selectedMinutes -= 5
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("-5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
            }

            Column(
                modifier = Modifier.width(140.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$displayMinutes",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSans
                )
                Text(
                    text = "minutes",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = GoogleSans
                )
            }

            val plusInteraction = remember { MutableInteractionSource() }
            val isPlusPressed by plusInteraction.collectIsPressedAsState()
            val plusScale by animateFloatAsState(if (isPlusPressed) 0.85f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = plusScale; scaleY = plusScale }
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x12FFFFFF))
                    .clickable(interactionSource = plusInteraction, indication = null) {
                        if (isTimerActive) {
                            // Если запущен — просто накидываем 5 минут к текущим секундам без перезапуска
                            onAction(PlayerAction.AdjustSleepTimer(5))
                        } else {
                            if (selectedMinutes < 120) selectedMinutes += 5
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val actionColor = if (isTimerActive) Color(0xFFB33A3A).copy(alpha = 0.8f) else Color(state.vibrantColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer {
                    scaleX = startStopScale
                    scaleY = startStopScale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(actionColor)
                .clickable(
                    interactionSource = startStopInteraction,
                    indication = null
                ) {
                    if (isTimerActive) {
                        onAction(PlayerAction.StopSleepTimer)
                    } else {
                        onAction(PlayerAction.StartSleepTimer(selectedMinutes))
                    }
                    onBackClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isTimerActive) "Stop Timer" else "Start Timer",
                color = if (isTimerActive) Color.White else (if (Color(state.vibrantColor).luminance() > 0.5f) Color.Black else Color.White),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSans
            )
        }
    }
}
