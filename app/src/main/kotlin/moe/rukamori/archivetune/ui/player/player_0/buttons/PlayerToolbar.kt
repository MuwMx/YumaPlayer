package moe.rukamori.archivetune.ui.player.player_0.buttons

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily

@Composable
fun PlayerToolbar(
    onCollapseClick: () -> Unit,
    onMoreClick: () -> Unit,
    onTimerBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: PlayerUiState,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    hasUpdate: Boolean = false,
    colorScheme: ColorScheme = MaterialTheme.colorScheme
) {
    val haptics = rememberYumaHaptics()

    val isImmersiveOrBlur = (state.isImmersiveEnabled || state.isBlurBackgroundEnabled) && !state.isLyricsVisible
    val buttonBackground = if (isImmersiveOrBlur) Color.Black.copy(alpha = 0.2f) else Color.Transparent
    val buttonBorderColor = if (isImmersiveOrBlur) Color.White.copy(alpha = 0.08f) else Color.Transparent

    val collapseInteractionSource = remember { MutableInteractionSource() }
    val collapsePressed by collapseInteractionSource.collectIsPressedAsState()
    val collapseScale by animateFloatAsState(
        targetValue = if (collapsePressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "CollapseButtonBounce"
    )

    val moreInteractionSource = remember { MutableInteractionSource() }
    val morePressed by moreInteractionSource.collectIsPressedAsState()
    val moreScale by animateFloatAsState(
        targetValue = if (morePressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "MoreButtonBounce"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(
            text = "Now Playing",
            color = if (isImmersiveOrBlur) Color.White else colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .graphicsLayer { alpha = 0.6f }
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 0.dp, top = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (!state.isImmersiveEnabled) {
                SleepTimerTopBadge(
                    state = state,
                    onClick = onTimerBadgeClick,
                    colorScheme = colorScheme
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = moreScale
                        scaleY = moreScale
                    }
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(buttonBackground)
                    .border(1.dp, buttonBorderColor, RoundedCornerShape(50))
                    .clickable(
                        interactionSource = moreInteractionSource,
                        indication = null
                    ) {
                        haptics.click()
                        onMoreClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(24.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_more),
                        contentDescription = "More Options",
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.Center)
                    )
                    if (hasUpdate) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .align(Alignment.TopEnd)
                                .background(colorScheme.error, RoundedCornerShape(50))
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 0.dp, top = 0.dp)
                .graphicsLayer {
                    scaleX = collapseScale
                    scaleY = collapseScale
                }
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(buttonBackground)
                .border(1.dp, buttonBorderColor, RoundedCornerShape(50))
                .clickable(
                    interactionSource = collapseInteractionSource,
                    indication = null
                ) {
                    haptics.click()
                    onCollapseClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_collapse),
                contentDescription = "Collapse Player",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SleepTimerTopBadge(
    state: PlayerUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorScheme: ColorScheme = MaterialTheme.colorScheme
) {
    val totalSecs = state.sleepTimerRemainingSeconds
    if (totalSecs == null || totalSecs <= 0) return

    val text = remember(totalSecs) {
        val m = totalSecs / 60
        val s = totalSecs % 60
        "%02d:%02d".format(m, s)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "BadgeBounce"
    )

    val badgeColor = Color(state.vibrantColor)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.15f))
                .border(1.dp, badgeColor.copy(alpha = 0.3f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sleep_timer),
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = colorScheme.onSurface.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalArchiveTuneFontFamily.current
            )
        }
    }
}