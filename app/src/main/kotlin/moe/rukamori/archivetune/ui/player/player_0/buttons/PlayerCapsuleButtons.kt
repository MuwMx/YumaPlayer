package moe.rukamori.archivetune.ui.player.player_0.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.state.PlayerUiState

// =================================================================
// БЛОК НАСТРОЕК: МЕНЯЙ ЦИФРЫ ТУТ, И ВСЯ КАПСУЛА ПЕРЕПИШЕТСЯ САМА
// =================================================================
private val CapsuleHeight = 52.dp             // Высота всей таблетки
private val CapsuleHorizontalPadding = 8.dp  // Внутренние боковые отступы капсулы
private val ButtonClickAreaSize = 48.dp       // Размер кликабельной зоны кнопок
private val ButtonIconSize = 26.dp           // Размер самой иконки внутри кнопки
private val SpaceBetweenButtons = 6.dp        // Расстояние между кнопками внутри
private val InactiveButtonColor = Color.White.copy(alpha = 0.8f)

@Composable
fun PlayerControlCapsule(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit, /* Одна лямбда вместо кучи пробросов */
    modifier: Modifier = Modifier
) {
    var lastLikeClickTime by remember { mutableLongStateOf(0L) }

    val heartColor by animateColorAsState(
        targetValue = if (state.isLiked) Color(state.vibrantColor) else InactiveButtonColor,
        animationSpec = tween(300),
        label = "HeartColorAnimation"
    )

    val repeatIcon = when (state.repeatState) {
        "one" -> R.drawable.ic_repeat_one
        else -> R.drawable.ic_repeat
    }

    val shuffleColor = if (state.shuffleState != "off") Color(0xFF1DB954) else InactiveButtonColor
    val repeatColor = if (state.repeatState != "off") Color(0xFF1DB954) else InactiveButtonColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val capsuleShape = RoundedCornerShape(50)

        Row(
            modifier = Modifier
                .height(CapsuleHeight)
                .clip(capsuleShape)
                .background(Color(0x14FFFFFF))
                .border(BorderStroke(1.dp, Color(0x22FFFFFF)), capsuleShape)
                .padding(horizontal = CapsuleHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. ШАФЛ
            CapsuleIconButton(
                iconRes = R.drawable.ic_shuffle,
                tint = shuffleColor,
                onClick = { onAction(PlayerAction.Shuffle) }
            )

            Spacer(modifier = Modifier.width(SpaceBetweenButtons))

            // 2. SHARE
            CapsuleIconButton(
                iconRes = R.drawable.ic_share,
                tint = InactiveButtonColor,
                onClick = { onAction(PlayerAction.Share) }
            )

            Spacer(modifier = Modifier.width(SpaceBetweenButtons))

            // 3. LIKE
            CapsuleIconButton(
                iconRes = if (state.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                tint = heartColor,
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastLikeClickTime > 450L) {
                        lastLikeClickTime = now
                        onAction(PlayerAction.Like)
                    }
                }
            )

            Spacer(modifier = Modifier.width(SpaceBetweenButtons))

            // 4. LYRICS
            CapsuleIconButton(
                iconRes = R.drawable.ic_lyrics,
                tint = InactiveButtonColor,
                onClick = { onAction(PlayerAction.Lyrics) }
            )

            Spacer(modifier = Modifier.width(SpaceBetweenButtons))

            // 5. ПОВТОР
            CapsuleIconButton(
                iconRes = repeatIcon,
                tint = repeatColor,
                onClick = { onAction(PlayerAction.Repeat) }
            )
        }
    }
}

@Composable
private fun CapsuleIconButton(
    iconRes: Int,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "CapsuleButtonBounce"
    )

    Box(
        modifier = Modifier
            .size(ButtonClickAreaSize) // Используем константу
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ButtonIconSize) // Используем константу
        )
    }
}
