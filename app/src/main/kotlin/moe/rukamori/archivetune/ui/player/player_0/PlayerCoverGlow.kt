/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.ui.component.BlurEffectCache

private const val GLOW_TEXTURE_SIZE = 80
private val GlowRadiusDp = 32.dp

@Composable
fun PlayerCoverGlow(
    coverUrl: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled || coverUrl.isNullOrBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val blurRadiusPx = remember(density) { with(density) { GlowRadiusDp.toPx() } }

    val glowRequest = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .memoryCacheKey("cover-glow:$coverUrl")
            .diskCacheKey("palette:$coverUrl")
            .size(GLOW_TEXTURE_SIZE, GLOW_TEXTURE_SIZE)
            .allowHardware(true)
            .build()
    }

    val glowShape = remember { RoundedCornerShape(26.dp) }

    val glowColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.25f, 0f, 0f, 0f, 0f,
                    0f, 1.25f, 0f, 0f, 0f,
                    0f, 0f, 1.25f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                renderEffect = BlurEffectCache.get(blurRadiusPx)
                alpha = 0.95f
                scaleX = 1.04f
                scaleY = 1.04f
            }
    ) {
        AsyncImage(
            model = glowRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = glowColorFilter,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(glowShape),
        )
    }
}