package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun PlayerBlurBackdrop(
    carousel: SongCarousel?,
    targetUrl: String?,
    needsBlur: Boolean,
    blurOverlayAlpha: Float,
    modifier: Modifier = Modifier,
    playerHazeState: HazeState = remember { HazeState() },
) {
    if (!needsBlur) return

    val painters = rememberPlayerBackgroundPainters(
        targetUrl = targetUrl,
        needsBlur = needsBlur
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = blurOverlayAlpha
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(playerHazeState)
        ) {
            if (carousel != null && carousel.slots.isNotEmpty()) {
                for (slot in carousel.slots) {
                    key(slot.uid) {
                        val slotUrl = slot.artworkUri?.trim()?.takeIf(String::isNotBlank)
                        val slotPainters = rememberPlayerBackgroundPainters(
                            targetUrl = slotUrl,
                            needsBlur = needsBlur
                        )
                        val isSlotVisible by remember(carousel, slot) {
                            derivedStateOf { carousel.crossfadeAlpha(slot) > 0f }
                        }
                        val blurPainter = slotPainters.currentBlurPainter
                        if (isSlotVisible && blurPainter != null) {
                            Image(
                                painter = blurPainter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = carousel.crossfadeAlpha(slot)
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            } else {
                val blurPainter = painters.currentBlurPainter
                if (blurPainter != null) {
                    Image(
                        painter = blurPainter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeBlur(
                    input = HazeInput.Sources(playerHazeState),
                    style = HazeBlurStyle {
                        val tintEffect = HazeColorEffect.tint(Color.Black.copy(alpha = 0.35f))
                        backgroundColor(Color.Transparent)
                        colorEffects(listOf(tintEffect))
                        fallbackColorEffect(tintEffect)
                        blurRadius(56.dp)
                        noiseFactor(SettingsDimensions.HazeNoiseFactor)
                    },
                    performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                )
        )
    }
}
