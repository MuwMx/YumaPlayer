@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player.lyrics_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.ShowLyricsPlayerControlsKey
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.player.player_0.PlayerSeekBar
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.ui.theme.glassBorder
import moe.rukamori.archivetune.ui.theme.transparentIconShadow
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.bounceClick
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsContentCard(
    state: PlayerUiState,
    animateProgressProvider: () -> Float,
    progressMsProvider: () -> Long,
    modifier: Modifier = Modifier,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    onSearchClick: () -> Unit = {},
    lazyListState: LazyListState = rememberLazyListState(),
    onLineClick: (Long) -> Unit = {},
    isReadyToParse: Boolean = true,
) {
    val (showPlayerControls) = rememberPreference(ShowLyricsPlayerControlsKey, defaultValue = true)

    Box(modifier = modifier.fillMaxSize()) {
        LyricsEnhanced(
            sliderPositionProvider = progressMsProvider,
            lyricsSyncOffset = state.lyricsSyncOffset,
            textColorOverride = Color.White,
            isReadyToParse = isReadyToParse,
            lazyListState = lazyListState,
            modifier = Modifier.fillMaxSize(),
        )

        if (showPlayerControls) {
            val animatedAccentColor by animateColorAsState(
                targetValue = Color(state.vibrantColor),
                animationSpec = tween(500),
                label = "LyricsTransportAccent",
            )

            val darkScheme = darkColorScheme()
            CompositionLocalProvider(
                LocalContentColor provides Color.White,
                LocalYumaColors provides darkYumaColorScheme(darkScheme),
            ) {
                MaterialTheme(colorScheme = darkScheme) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                if (animateProgressProvider() >= 0.05f) {
                                    layout(placeable.width, placeable.height) {
                                        placeable.placeRelative(0, 0)
                                    }
                                } else {
                                    layout(0, 0) {}
                                }
                            }
                            .graphicsLayer {
                                val progress = animateProgressProvider()
                                alpha = progress
                                scaleX = 0.88f + (0.12f * progress)
                                scaleY = 0.88f + (0.12f * progress)
                                translationY = 40f * (1f - progress)
                            }
                            .yumaGlassCard(shape = RoundedCornerShape(32.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlayerSeekBar(
                                state = state,
                                progressProvider = progressMsProvider,
                                durationMs = state.durationMs,
                                vibrantColor = Color(state.vibrantColor),
                                slideOffset = { 1f },
                                onSeek = onSeek,
                                onSeekStarted = onSeekStarted,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                        val playPauseIcon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow

                        Box(
                            modifier = Modifier
                                .bounceClick(pressedScale = 0.90f) {
                                    onAction(PlayerAction.Previous)
                                }
                                .size(48.dp)
                                .transparentIconShadow(alpha = 0.1f, shadowRadius = 15.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = rememberVectorPainter(Icons.Rounded.SkipPrevious),
                                contentDescription = "Previous Track",
                                modifier = Modifier.size(36.dp),
                                colorFilter = ColorFilter.tint(Color.White),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .bounceClick(pressedScale = 0.92f) {
                                    if (!state.isLoading) onAction(PlayerAction.PlayPause)
                                }
                                .size(68.dp)
                                .clip(CircleShape)
                                .drawBehind {
                                    drawCircle(animatedAccentColor)
                                }
                                .padding(0.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.isLoading) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(42.dp),
                                    color = Color(0xFF121212),
                                )
                            } else {
                                Image(
                                    painter = rememberVectorPainter(playPauseIcon),
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(48.dp),
                                    colorFilter = ColorFilter.tint(Color(0xFF121212)),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .bounceClick(pressedScale = 0.90f) {
                                    onAction(PlayerAction.Next)
                                }
                                .size(48.dp)
                                .transparentIconShadow(alpha = 0.1f, shadowRadius = 15.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = rememberVectorPainter(Icons.Rounded.SkipNext),
                                contentDescription = "Next Track",
                                modifier = Modifier.size(36.dp),
                                colorFilter = ColorFilter.tint(Color.White),
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