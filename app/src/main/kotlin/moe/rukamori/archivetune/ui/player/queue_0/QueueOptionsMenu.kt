package moe.rukamori.archivetune.ui.player.queue_0

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsMenuRow
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
fun QueueOptionsMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onSaveAsPlaylist: () -> Unit,
    state: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    val font = LocalArchiveTuneFontFamily.current

    if (isVisible) {
        BackHandler {
            onDismiss()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = LinearEasing),
        label = "QueueMenuAlpha",
    )

    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 300f,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "QueueMenuSlide",
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha * 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDismiss() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(320.dp)
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(20.dp),
            ) {
                val darkScheme = darkColorScheme()
                MaterialTheme(colorScheme = darkScheme) {
                    CompositionLocalProvider(
                        LocalContentColor provides Color.White,
                        LocalYumaColors provides darkYumaColorScheme(darkScheme),
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.queue),
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = font,
                                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                            )

                            val count = 3

                            SettingsMenuRow(
                                title = stringResource(R.string.clear_queue),
                                subtitle = stringResource(R.string.clear_queue_desc),
                                iconResId = R.drawable.clear_all,
                                onClick = {
                                    haptics.click()
                                    onAction(PlayerAction.ClearQueue)
                                    onDismiss()
                                },
                                index = 0,
                                count = count,
                            )

                            Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

                            SettingsMenuRow(
                                title = stringResource(R.string.shuffle),
                                subtitle = stringResource(R.string.shuffle_queue_desc),
                                iconResId = R.drawable.shuffle,
                                onClick = {
                                    haptics.click()
                                    onAction(PlayerAction.ShuffleQueue)
                                    onDismiss()
                                },
                                index = 1,
                                count = count,
                            )

                            Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

                            SettingsMenuRow(
                                title = stringResource(R.string.save_as_playlist),
                                subtitle = stringResource(R.string.save_as_playlist_desc),
                                iconResId = R.drawable.playlist_add,
                                onClick = {
                                    haptics.click()
                                    onSaveAsPlaylist()
                                },
                                index = 2,
                                count = count,
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = stringResource(R.string.close),
                                color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = font,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .yumaClickable {
                                        haptics.click()
                                        onDismiss()
                                    }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
