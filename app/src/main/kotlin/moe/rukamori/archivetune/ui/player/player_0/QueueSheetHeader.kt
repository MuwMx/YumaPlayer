package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.theme.SoftTextShadow
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun QueueSheetHeader(
    queueState: QueueUiState,
    queueFractionProvider: () -> Float,
    onCloseClick: () -> Unit,
    onMoreQueueClick: () -> Unit,
    onToggleAutoMix: () -> Unit,
    isAutoMixEnabled: Boolean = false,
    state: PlayerUiState,
    isVisible: Boolean
) {
    val haptics = rememberYumaHaptics()

    val closeInteractionSource = remember { MutableInteractionSource() }
    val closePressed by closeInteractionSource.collectIsPressedAsState()
    val closeScale by animateFloatAsState(if (closePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    val autoMixInteractionSource = remember { MutableInteractionSource() }
    val autoMixPressed by autoMixInteractionSource.collectIsPressedAsState()
    val autoMixScale by animateFloatAsState(if (autoMixPressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    val autoMixTint by animateColorAsState(
        targetValue = if (isAutoMixEnabled) Color(state.vibrantColor) else Color.White.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "AutoMixTint"
    )

    val moreInteractionSource = remember { MutableInteractionSource() }
    val morePressed by moreInteractionSource.collectIsPressedAsState()
    val moreScale by animateFloatAsState(if (morePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    val songCount = queueState.songCount.takeIf { it != 0 } ?: queueState.queueWindows.size
    // remember inside the elvis would shift Compose's positional slots whenever
    // queueDurationMs flips between zero and non-zero.
    val windowsDurationMs = remember(queueState.queueWindows) {
        queueState.queueWindows.sumOf { (it.mediaItem.metadata?.duration ?: 0).toLong() } * 1000L
    }
    val queueDurationMs = queueState.queueDurationMs.takeIf { it != 0L } ?: windowsDurationMs
    val queueTitle = queueState.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.queue)
    val songPlural = pluralStringResource(R.plurals.n_song, songCount, songCount)
    val subtitle = remember(songPlural, queueDurationMs) { "$songPlural  •  ${makeTimeString(queueDurationMs)}" }

    val capsuleShape = RoundedCornerShape(24.dp)
    val capsuleColor = if (state.isBlurBackgroundEnabled) Color.Black else Color(state.darkMutedColor)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarTop > 0.dp) statusBarTop + 8.dp else 44.dp

    Box(
        modifier = Modifier
            .padding(top = topPadding, start = 24.dp, end = 24.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(elevation = 12.dp, shape = capsuleShape, clip = false)
            .graphicsLayer {
                val progress = queueFractionProvider()
                alpha = progress
                scaleX = 0.8f + (0.2f * progress)
                scaleY = 0.8f + (0.2f * progress)
            }
            .background(capsuleColor, capsuleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(capsuleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = closeScale; scaleY = closeScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = closeInteractionSource, indication = null) {
                        onCloseClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.ic_collapse), contentDescription = stringResource(R.string.collapse), modifier = Modifier.size(20.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = queueTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = SoftTextShadow)
                )
                Text(
                    text = subtitle,
                    color = Color(0xD9FFFFFF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = SoftTextShadow)
                )
            }
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = autoMixScale; scaleY = autoMixScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = autoMixInteractionSource, indication = null) {
                        haptics.click()
                        onToggleAutoMix()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.auto_awesome),
                    contentDescription = stringResource(R.string.automix),
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(autoMixTint)
                )
            }
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = moreScale; scaleY = moreScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = moreInteractionSource, indication = null) {
                        haptics.click()
                        onMoreQueueClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.ic_more), contentDescription = stringResource(R.string.more_label), modifier = Modifier.size(20.dp))
            }
        }
        if (state.isBlurBackgroundEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(BorderStroke(1.dp, Color(0x22FFFFFF)), capsuleShape)
            )
        }
    }
}
