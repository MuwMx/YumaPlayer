package moe.rukamori.archivetune.ui.player.update_0

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.state.PlayerUiState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy

@Composable
fun WelcomeOverlay(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onTelegramClick: () -> Unit,
) {
    val GoogleSans = FontFamily(
        Font(R.font.google_sans_regular, FontWeight.Normal),
        Font(R.font.google_sans_bold, FontWeight.Bold)
    )

    // Анимация для кнопки Телеграма
    val tgInteraction = remember { MutableInteractionSource() }
    val isTgPressed by tgInteraction.collectIsPressedAsState()
    val tgScale by animateFloatAsState(
        targetValue = if (isTgPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    )

    val buttonInteraction = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteraction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F).copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Блокируем клики по заднему фону
            ),
        contentAlignment = Alignment.Center
    ) {
        val cardGradient = Brush.verticalGradient(
            colors = listOf(Color(state.darkMutedColor), Color(0xFF161616))
        )

        Box(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(28.dp))
                .background(cardGradient)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ТВОЯ ТЯН: Замени этот Text на свой Image с аниме-артом, когда закинешь в res/drawable
//                Text(
//                    text = "(｡♥‿♥｡)",
//                    fontSize = 32.sp,
//                    modifier = Modifier.padding(vertical = 8.dp)
//                )

                Image(
                    painter = painterResource(id = R.drawable.ic_welcome_chara),
                    contentDescription = "Welcome Character",
                    modifier = Modifier
                        .size(220.dp)
                        .padding(vertical = 12.dp)
                        // 1. Включаем изолированный слой для блендинга
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        // 2. Накладываем маску плавного кругового растворения
                        .drawWithContent {
                            drawContent() // Рисуем саму тянку

                            drawRect(
                                brush = Brush.radialGradient(
                                    0.0f to Color.White,        // В самом центре — полная видимость
                                    0.9f to Color.White,        // До половины радиуса картинка чёткая и плотная
                                    1.0f to Color.Transparent,  // От 0.5 до 1.0 ПЛАВНО растворяется в ноль
                                    center = center,
                                    radius = size.width * 0.45f  // Радиус равен половине ширины — аккурат до краёв квадрата
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Welcome to Yuma!",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSans
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Thank you for choosing Yuma Player! Enjoy your music! \uD83C\uDFB5",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = GoogleSans,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Кнопка закрытия
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(state.vibrantColor))
                        .clickable(
                            interactionSource = buttonInteraction,
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Let's go!",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSans
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // КНОПКА 2: Наш Телеграм канал
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = tgScale; scaleY = tgScale }
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp)) // Тонкая серая рамка
                        .background(Color(0x0AFFFFFF)) // Едва заметный внутренний тон
                        .clickable(
                            interactionSource = tgInteraction,
                            indication = null,
                            onClick = onTelegramClick
                        )
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_telegram), // Сюда подставь точное имя файла твоего значка телеги
                        contentDescription = "Telegram Link",
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Our Telegram Channel",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = GoogleSans
                    )
                }
            }
        }
    }
}
