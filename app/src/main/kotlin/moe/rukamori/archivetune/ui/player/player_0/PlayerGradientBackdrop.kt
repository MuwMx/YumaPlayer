package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.player.ambient.ToneSeamRight
import moe.rukamori.archivetune.ui.player.ambient.pictureTones

@Composable
internal fun PlayerGradientBackdrop(
    carousel: SongCarousel?,
    animatedBgColor: Color,
    glowTransition: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var slotTones by remember { mutableStateOf<Map<String, List<Color>>>(emptyMap()) }

    LaunchedEffect(carousel?.slots) {
        val slots = carousel?.slots ?: run {
            if (slotTones.isNotEmpty()) slotTones = emptyMap()
            return@LaunchedEffect
        }
        val activeUrls = slots.mapNotNull { it.artworkUri?.trim()?.takeIf(String::isNotBlank) }.toSet()

        slotTones = slotTones.filterKeys { it in activeUrls }

        for (url in activeUrls) {
            if (!slotTones.containsKey(url)) {
                launch(Dispatchers.IO) {
                    val tones = pictureTones(context, url)
                    if (tones != null) {
                        withContext(Dispatchers.Main) {
                            slotTones = slotTones + Pair(url, tones)
                        }
                    }
                }
            }
        }
    }

    if (carousel != null && carousel.slots.isNotEmpty()) {
        for (slot in carousel.slots) {
            key(slot.uid) {
                val slotUrl = slot.artworkUri?.trim()?.takeIf(String::isNotBlank)
                val slotBgColor = slotUrl?.let { slotTones[it]?.getOrNull(ToneSeamRight) } ?: animatedBgColor
                Spacer(
                    modifier = modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = carousel.crossfadeAlpha(slot)
                        }
                        .drawWithCache {
                            val currentBg = slotBgColor
                            val currentGlow = glowTransition

                            val charcoalTone = lerp(Color(0xFF080808), currentBg, 0.05f)
                            val midTone = lerp(currentBg, Color(0xFF101010), 0.50f)
                            val deepTone = lerp(currentBg, Color(0xFF060606), 0.90f)

                            val stop0 = lerp(currentBg, charcoalTone, currentGlow)
                            val stop35 = lerp(currentBg, charcoalTone, currentGlow)
                            val stop70 = lerp(midTone, currentBg.copy(alpha = 0.70f), currentGlow)
                            val stop100 = lerp(deepTone, charcoalTone, currentGlow)

                            val brush = Brush.verticalGradient(
                                0.0f to stop0,
                                0.35f to stop35,
                                0.70f to stop70,
                                1.0f to stop100,
                                startY = 0f,
                                endY = size.height
                            )

                            onDrawBehind {
                                drawRect(brush = brush)
                            }
                        }
                )
            }
        }
    } else {
        Spacer(
            modifier = modifier
                .fillMaxSize()
                .drawWithCache {
                    val currentBg = animatedBgColor
                    val currentGlow = glowTransition

                    val charcoalTone = lerp(Color(0xFF080808), currentBg, 0.05f)
                    val midTone = lerp(currentBg, Color(0xFF101010), 0.50f)
                    val deepTone = lerp(currentBg, Color(0xFF060606), 0.90f)

                    val stop0 = lerp(currentBg, charcoalTone, currentGlow)
                    val stop35 = lerp(currentBg, charcoalTone, currentGlow)
                    val stop70 = lerp(midTone, currentBg.copy(alpha = 0.70f), currentGlow)
                    val stop100 = lerp(deepTone, charcoalTone, currentGlow)

                    val brush = Brush.verticalGradient(
                        0.0f to stop0,
                        0.35f to stop35,
                        0.70f to stop70,
                        1.0f to stop100,
                        startY = 0f,
                        endY = size.height
                    )

                    onDrawBehind {
                        drawRect(brush = brush)
                    }
                }
        )
    }
}
