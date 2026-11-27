package moe.rukamori.archivetune.ui.player.player_0

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.theme.ExtractedColors
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import java.util.concurrent.atomic.AtomicLong

private val globalPaletteCache = object : LruCache<String, ExtractedColors>(128) {}
private val paletteCacheLock = Any()
private val prewarmGeneration = AtomicLong(0L)

private suspend fun extractAndCachePalette(
    context: Context,
    url: String,
): ExtractedColors? {
    synchronized(paletteCacheLock) { globalPaletteCache.get(url) }?.let { return it }

    val request =
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey("palette:$url")
            .diskCacheKey("palette:$url")
            .size(128, 128)
            .allowHardware(false)
            .build()

    val result = try {
        context.imageLoader.execute(request)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    val bitmap = withContext(Dispatchers.IO) {
        runCatching { result?.image?.toBitmap() }.getOrNull()
    } ?: return null

    val colors = withContext(Dispatchers.Default) {
        PlayerColorExtractor.extractColors(bitmap)
    }

    synchronized(paletteCacheLock) {
        globalPaletteCache.put(url, colors)
    }
    return colors
}

@Composable
internal fun PlayerPaletteExtractor(
    targetUrl: String?,
    trackUrl: String,
    nextUrl: String? = null,
    onColorsExtracted: (vibrant: Int, darkMuted: Int, gradient: Int) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(targetUrl, trackUrl) {
        if (targetUrl == null) return@LaunchedEffect
        val colors = extractAndCachePalette(context, targetUrl) ?: return@LaunchedEffect
        onColorsExtracted(colors.vibrant, colors.darkMuted, colors.gradient)
    }

    LaunchedEffect(targetUrl, nextUrl) {
        val warmUrl = nextUrl?.takeIf { it != targetUrl } ?: return@LaunchedEffect
        if (synchronized(paletteCacheLock) { globalPaletteCache.get(warmUrl) } != null) return@LaunchedEffect
        val generation = prewarmGeneration.incrementAndGet()
        delay(150)
        ensureActive()
        if (generation != prewarmGeneration.get()) return@LaunchedEffect
        extractAndCachePalette(context, warmUrl)
    }
}