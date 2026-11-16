/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.ambient

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val FadeMs = 1_200
private const val MinChange = 0.035f
private const val PublishMs = 33L
private const val PicturePx = 32

internal const val ToneSeamLeft = 0
internal const val ToneSeamRight = 1
internal const val ToneDeep = 2
internal const val ToneAccent = 3
internal const val ToneCount = 4

private val DefaultBackgroundBase = Color(0xFF2B2A2D)

@Stable
class AmbientColors internal constructor(private val scope: CoroutineScope) {
    private var shownTones by mutableStateOf(List(ToneCount) { DefaultBackgroundBase })
    private var target: List<Color>? = null
    private var picture: List<Color>? = null
    var isLive by mutableStateOf(false)
        private set
    private var fade: Job? = null

    fun tone(index: Int): Color = shownTones.getOrElse(index) { DefaultBackgroundBase }

    fun seam(): Color {
        val a = shownTones.getOrElse(ToneSeamLeft) { DefaultBackgroundBase }
        val b = shownTones.getOrElse(ToneSeamRight) { DefaultBackgroundBase }
        return Color((a.red + b.red) / 2f, (a.green + b.green) / 2f, (a.blue + b.blue) / 2f)
    }

    fun picture(colors: List<Color>) {
        picture = colors
        if (!isLive) ease(colors)
    }

    fun live(colors: List<Color>) {
        isLive = true
        ease(colors)
    }

    fun forgetLive() {
        isLive = false
    }

    fun endLive() {
        if (!isLive) return
        isLive = false
        picture?.let(::ease)
    }

    private fun ease(next: List<Color>) {
        if (next.size < ToneCount) return
        val last = target
        if (last != null && last.indices.all { distance(last[it], next[it]) < MinChange }) return
        target = next
        val from = shownTones
        fade?.cancel()
        fade = scope.launch {
            val start = withFrameMillis { it }
            var handedOut = start
            while (true) {
                val now = withFrameMillis { it }
                val t = ((now - start).toFloat() / FadeMs).coerceIn(0f, 1f)
                if (t >= 1f) break
                if (now - handedOut >= PublishMs) {
                    handedOut = now
                    val k = FastOutSlowInEasing.transform(t)
                    shownTones = List(ToneCount) { lerp(from[it], next[it], k) }
                }
            }
            shownTones = next
        }
    }

    private fun distance(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
}

@Composable
fun rememberAmbientColors(): AmbientColors {
    val scope = rememberCoroutineScope()
    return remember(scope) { AmbientColors(scope) }
}

@Composable
fun FollowPicture(colors: AmbientColors, url: String?) {
    val context = LocalContext.current
    LaunchedEffect(colors, url) {
        colors.forgetLive()
        if (url.isNullOrBlank()) return@LaunchedEffect
        val tones = pictureTones(context, url) ?: return@LaunchedEffect
        colors.picture(tones)
    }
}

private val pictureCache = LruCache<String, List<Color>>(48)

suspend fun pictureTones(context: Context, url: String): List<Color>? {
    pictureCache.get(url)?.let { return it }
    val bitmap = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(PicturePx, PicturePx)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).image?.toBitmap()
        }.getOrNull()
    } ?: return null

    return withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return@withContext null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        extractAmbientTones(pixels, w, h)
    }?.also { pictureCache.put(url, it) }
}

internal fun extractAmbientTones(pixels: IntArray, width: Int, height: Int): List<Color> {
    if (pixels.isEmpty() || width <= 0 || height <= 0) {
        return List(ToneCount) { DefaultBackgroundBase }
    }

    val midX = width / 2
    val seamY = (height * 0.6f).toInt().coerceIn(0, height - 1)

    fun averageRgb(x0: Int, x1: Int, y0: Int, y1: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in y0 until y1) {
            val row = y * width
            for (x in x0 until x1) {
                val c = pixels[row + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
            }
        }
        if (count == 0) return Color.Black
        return Color(red = (r / count).toInt(), green = (g / count).toInt(), blue = (b / count).toInt())
    }

    val seamLeftRaw = averageRgb(0, midX, seamY, height)
    val seamLeftHsl = FloatArray(3)
    ColorUtils.colorToHSL(seamLeftRaw.toArgb(), seamLeftHsl)
    seamLeftHsl[1] = (seamLeftHsl[1] * 1.25f).coerceIn(0.15f, 0.85f)
    seamLeftHsl[2] = seamLeftHsl[2].coerceIn(0.22f, 0.58f)
    val seamLeft = Color(ColorUtils.HSLToColor(seamLeftHsl))

    val seamRightRaw = averageRgb(midX, width, seamY, height)
    val seamRightHsl = FloatArray(3)
    ColorUtils.colorToHSL(seamRightRaw.toArgb(), seamRightHsl)
    seamRightHsl[1] = (seamRightHsl[1] * 1.25f).coerceIn(0.15f, 0.85f)
    seamRightHsl[2] = seamRightHsl[2].coerceIn(0.22f, 0.58f)
    val seamRight = Color(ColorUtils.HSLToColor(seamRightHsl))

    val deepRaw = averageRgb(0, width, 0, height)
    val deepHsl = FloatArray(3)
    ColorUtils.colorToHSL(deepRaw.toArgb(), deepHsl)
    deepHsl[1] = (deepHsl[1] * 1.1f).coerceAtMost(0.75f)
    deepHsl[2] = deepHsl[2].coerceIn(0.06f, 0.20f)
    val deep = Color(ColorUtils.HSLToColor(deepHsl))

    var bestPixel = 0
    var bestScore = -1f
    var maxSat = 0f
    val hslTemp = FloatArray(3)

    for (c in pixels) {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val maxChannel = maxOf(r, maxOf(g, b))

        ColorUtils.colorToHSL(c, hslTemp)
        val sat = hslTemp[1]
        if (sat > maxSat) maxSat = sat
        val score = sat * sat * (1f - abs(maxChannel - 0.75f))
        if (score > bestScore) {
            bestScore = score
            bestPixel = c
        }
    }

    val accentBase = if (maxSat < 0.08f) {
        val topThirdY = (height / 3).coerceAtLeast(1)
        averageRgb(0, width, 0, topThirdY).toArgb()
    } else {
        bestPixel
    }

    val accentHsl = FloatArray(3)
    ColorUtils.colorToHSL(accentBase, accentHsl)
    accentHsl[1] = (accentHsl[1] * 1.35f).coerceIn(0.25f, 0.95f)
    accentHsl[2] = accentHsl[2].coerceIn(0.35f, 0.72f)
    val accent = Color(ColorUtils.HSLToColor(accentHsl))

    return listOf(seamLeft, seamRight, deep, accent)
}
