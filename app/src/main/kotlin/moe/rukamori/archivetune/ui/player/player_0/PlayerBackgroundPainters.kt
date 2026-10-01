package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade

internal data class PlayerBackgroundPainters(
    val currentClearPainter: Painter?,
    val currentBlurPainter: Painter?
)

@Composable
internal fun rememberPlayerBackgroundPainters(
    targetUrl: String?,
    needsBlur: Boolean
): PlayerBackgroundPainters {
    val context = LocalContext.current

    val clearImageRequest = remember(targetUrl) {
        ImageRequest.Builder(context)
            .data(targetUrl)
            .apply {
                if (targetUrl != null) {
                    memoryCacheKey(targetUrl)
                    diskCacheKey(targetUrl)
                }
            }
            .crossfade(500)
            .build()
    }

    val clearPainter = rememberAsyncImagePainter(model = clearImageRequest)
    var currentClearPainter by remember { mutableStateOf<Painter?>(null) }

    LaunchedEffect(clearPainter, targetUrl) {
        if (targetUrl == null) {
            currentClearPainter = null
            return@LaunchedEffect
        }
        clearPainter.state.collect { s ->
            when (s) {
                is AsyncImagePainter.State.Success -> {
                    if (s.result.request.data == targetUrl) {
                        currentClearPainter = s.painter
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    currentClearPainter = null
                }
                else -> {}
            }
        }
    }

    var activeBlurPainter by remember { mutableStateOf<Painter?>(null) }

    val blurPainter = if (needsBlur) {
        val blurImageRequest = remember(targetUrl) {
            ImageRequest.Builder(context)
                .data(targetUrl)
                .apply {
                    if (targetUrl != null) {
                        memoryCacheKey("blur:$targetUrl")
                        diskCacheKey(targetUrl)
                    }
                }
                .size(300)
                .allowHardware(true)
                .crossfade(400)
                .build()
        }
        rememberAsyncImagePainter(model = blurImageRequest)
    } else {
        null
    }

    LaunchedEffect(blurPainter) {
        blurPainter?.state?.collect { s ->
            if (s is AsyncImagePainter.State.Success) {
                activeBlurPainter = s.painter
            }
        }
    }

    val currentBlurPainter = activeBlurPainter ?: currentClearPainter

    return remember(currentClearPainter, currentBlurPainter) {
        PlayerBackgroundPainters(
            currentClearPainter = currentClearPainter,
            currentBlurPainter = currentBlurPainter
        )
    }
}
