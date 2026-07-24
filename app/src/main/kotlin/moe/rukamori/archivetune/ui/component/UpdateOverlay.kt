package moe.rukamori.archivetune.ui.components.update

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.state.UpdateState

// ─── DESIGN TOKENS (Размеры карточки) ────────────────────────────────────────
private val CardWidth = 340.dp
private val CardCornerRadius = 28.dp
private val CardPadding = 24.dp
private val MascotSize = 140.dp
private val ChangelogMaxHeight = 240.dp
// ─────────────────────────────────────────────────────────────────────────────

// 👈 НАСТРОЙКА: Размеры шрифтов (увеличены для читаемости)
private val TitleFontSize = 24.sp
private val VersionFontSize = 16.sp
private val ChangelogFontSize = 14.sp
private val ChangelogLineHeight = 20.sp
private val ButtonFontSize = 16.sp

@Composable
fun UpdateOverlay(
    state: UpdateState,
    onDismiss: () -> Unit,
    onUpdateClick: (String) -> Unit
) {
    val isCritical = state is UpdateState.CriticalUpdate

    // Блокируем системную кнопку "Назад", если обновление критическое
    if (isCritical) {
        BackHandler { }
    }

    val versionName = when (state) {
        is UpdateState.CriticalUpdate -> state.versionName
        is UpdateState.SoftUpdate -> state.versionName
        else -> ""
    }
    val updateUrl = when (state) {
        is UpdateState.CriticalUpdate -> state.updateUrl
        is UpdateState.SoftUpdate -> state.updateUrl
        else -> ""
    }
    val changelog = when (state) {
        is UpdateState.CriticalUpdate -> state.changelog
        is UpdateState.SoftUpdate -> state.changelog
        else -> ""
    }

    // Пружинная анимация сжатия кнопки Download
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.96f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    )

    // Фон всего экрана (полупрозрачный черный, затемняет контент под оверлеем)
    // 👈 НАСТРОЙКА: Степень затемнения фона (Увеличена до 0.98f для критического, 0.92f для софт)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isCritical) 0.98f else 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isCritical) onDismiss() }
            ),
        contentAlignment = Alignment.Center
    ) {

        val cardGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2A2A2A),
                Color(0xFF161616)
            )
        )

        Box(
            modifier = Modifier
                .width(CardWidth)
                .clip(RoundedCornerShape(CardCornerRadius))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(CardCornerRadius))
                .background(cardGradient)
                .padding(CardPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ─── 1. МАСКОТ (С радиальной маской растворения) ───────────────
                Image(
                    painter = painterResource(id = R.drawable.ic_update_chara),
                    contentDescription = null,
                    modifier = Modifier
                        .size(MascotSize)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.radialGradient(
                                    0.0f to Color.White,
                                    0.75f to Color.White,
                                    1.0f to Color.Transparent,
                                    center = center,
                                    radius = size.width * 0.5f
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )

                // ─── 2. РАЗДЕЛИТЕЛЬ ────────────────────────────────────────────
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.1f)
                )

                // ─── 3. ЗАГОЛОВКИ ──────────────────────────────────────────────
                Text(
                    text = stringResource(
                        if (isCritical) R.string.update_critical_title
                        else R.string.update_soft_title
                    ),
                    color = Color.White,
                    fontSize = TitleFontSize,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.update_version_format, versionName),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = VersionFontSize,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // ─── 4. ЧЕНЖЛОГ (Компактный, без серого бокса) ─────────────────
                if (changelog.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = ChangelogMaxHeight)
                            .padding(top = 16.dp, bottom = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = changelog,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = ChangelogFontSize,
                            lineHeight = ChangelogLineHeight,
                            textAlign = TextAlign.Start
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ─── 5. КНОПКИ ─────────────────────────────────────────────────

                // Акцентная кнопка Download
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onUpdateClick(updateUrl) }
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.update_action_download),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = ButtonFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Кнопка "Позже" (только для Soft Update)
                if (!isCritical) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.update_action_later),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = ButtonFontSize,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}