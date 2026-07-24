package moe.rukamori.archivetune.ui.player.player_0.buttons

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.state.PlayerUiState

// =================================================================
// БЛОК НАСТРОЕК НИЖНЕЙ ПАНЕЛИ
// =================================================================
private val ButtonClickAreaSize = 48.dp
private val StandardIconSize = 26.dp
private val LyricsIconSize = 32.dp
private val InactiveButtonColor = Color.White.copy(alpha = 0.7f)

@Composable
fun PlayerBottomBar(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Неактивные кнопки делаем тусклее, чтобы активные на их фоне «горели»
    val inactiveColor = Color.White.copy(alpha = 0.35f)
    val rawActiveColor = Color(state.vibrantColor)

    // ФИКС БОЛОТНОГО ЦВЕТА: Если цвет из Palette слишком темный, осветляем его на 45% белизной
    val activeColor = remember(rawActiveColor) {
        if (rawActiveColor.luminance() < 0.3f) {
            lerp(rawActiveColor, Color.White, 0.45f)
        } else {
            rawActiveColor
        }
    }

    // Определяем статусы активности кнопок
    val isShuffleActive = state.shuffleState != "off"
    val isRepeatActive = state.repeatState != "off"
    val isLyricsActive = state.isLyricsVisible // Берем из твоего стейта видимость лирики

    val shuffleColor = if (isShuffleActive) activeColor else inactiveColor
    val repeatColor = if (isRepeatActive) activeColor else inactiveColor
    val lyricsColor = if (isLyricsActive) activeColor else inactiveColor

    // ДИНАМИЧЕСКИЙ ВЫБОР ИКОНОК:
    // Если пришел стейт "smart" — подставляем твой новый XML со звездой, иначе обычный шаффл
    val shuffleIcon = if (state.shuffleState == "smart") R.drawable.ic_shuffle_mix else R.drawable.ic_shuffle
    val repeatIcon = if (state.repeatState == "one") R.drawable.ic_repeat_one else R.drawable.ic_repeat

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. ШАФЛ (Тепер подставляет динамический shuffleIcon вместо хардкода)
        AiryIconButton(iconRes = shuffleIcon, tint = shuffleColor, size = StandardIconSize) {
            onAction(PlayerAction.Shuffle)
        }

        // 2. ЛИРИКА (Центральная, берет увеличенный размер)
        AiryIconButton(iconRes = R.drawable.ic_lyrics, tint = InactiveButtonColor, size = LyricsIconSize) {
            onAction(PlayerAction.Lyrics)
        }

        // 3. ПОВТОР
        AiryIconButton(iconRes = repeatIcon, tint = repeatColor, size = StandardIconSize) {
            onAction(PlayerAction.Repeat)
        }
    }
}

@Composable
private fun AiryIconButton(
    iconRes: Int,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "AiryButtonBounce"
    )

    // Box заменен на Column, чтобы отцентровать иконку и точку
    Column(
        modifier = Modifier
            .size(ButtonClickAreaSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
