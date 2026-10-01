package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.theme.ExtractedColors
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor

@Composable
internal fun PlayerPaletteExtractor(
    targetUrl: String?,
    trackUrl: String,
    onColorsExtracted: (vibrant: Int, darkMuted: Int, gradient: Int) -> Unit
) {
    val context = LocalContext.current
    val colorCache = remember { ConcurrentHashMap<String, ExtractedColors>() }

    val paletteImageRequest = remember(targetUrl) {
        ImageRequest.Builder(context)
            .data(targetUrl)
            .apply {
                if (targetUrl != null) {
                    memoryCacheKey("palette:$targetUrl")
                    diskCacheKey(targetUrl)
                }
            }
            .size(128, 128)
            .allowHardware(false)
            .build()
    }

    LaunchedEffect(targetUrl, trackUrl) {
        if (targetUrl == null) {
            return@LaunchedEffect
        }

        val requestedTargetUrl = targetUrl
        val requestedTrackUrl = trackUrl

        val cached = colorCache[requestedTargetUrl]
        if (cached != null) {
            onColorsExtracted(cached.vibrant, cached.darkMuted, cached.gradient)
        } else {
            launch {
                val result = runCatching {
                    context.imageLoader.execute(paletteImageRequest)
                }.getOrNull()
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { result?.image?.toBitmap() }.getOrNull()
                }
                if (bitmap != null &&
                    targetUrl == requestedTargetUrl &&
                    trackUrl == requestedTrackUrl
                ) {
                    val colors = withContext(Dispatchers.Default) {
                        PlayerColorExtractor.extractColors(bitmap)
                    }
                    if (targetUrl == requestedTargetUrl &&
                        trackUrl == requestedTrackUrl
                    ) {
                        colorCache[requestedTargetUrl] = colors
                        onColorsExtracted(colors.vibrant, colors.darkMuted, colors.gradient)
                    }
                }
            }
        }
    }
}
