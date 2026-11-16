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
private const val CacheSize = 48

private const val SeamRegionStartY = 0.60f

// Левый/нижний тёмный подтон
private const val SeamLeftSatBoost = 1.20f
private const val SeamLeftMinSat = 0.20f
private const val SeamLeftMaxSat = 0.85f
private const val SeamLeftMinLight = 0.08f
private const val SeamLeftMaxLight = 0.24f

// Правый/центральный основной насыщенный цвет
private const val SeamRightSatBoost = 1.35f
private const val SeamRightMinSat = 0.35f
private const val SeamRightMaxSat = 0.95f
private const val SeamRightMinLight = 0.28f
private const val SeamRightMaxLight = 0.48f

// Глубокая тёмная база
private const val DeepSatBoost = 1.15f
private const val DeepMaxSat = 0.80f
private const val DeepMinLight = 0.03f
private const val DeepMaxLight = 0.11f

// Верхний светлый/акцентный блик
private const val GrayscaleSatThreshold = 0.08f
private const val AccentTargetBrightness = 0.82f
private const val AccentSatWeight = 0.70f
private const val AccentLumaWeight = 0.60f
private const val AccentSatBoost = 1.15f
private const val AccentMinSat = 0.15f
private const val AccentMaxSat = 0.90f
private const val AccentMinLight = 0.55f
private const val AccentMaxLight = 0.86f

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

private val pictureCache = LruCache<String, List<Color>>(CacheSize)

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
    val seamY = (height * SeamRegionStartY).toInt().coerceIn(0, height - 1)

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
        return Color(
            red = (r / count).toInt(),
            green = (g / count).toInt(),
            blue = (b / count).toInt()
        )
    }

    val seamLeftHsl = FloatArray(3)
    ColorUtils.colorToHSL(averageRgb(0, midX, seamY, height).toArgb(), seamLeftHsl)
    seamLeftHsl[1] = (seamLeftHsl[1] * SeamLeftSatBoost).coerceIn(SeamLeftMinSat, SeamLeftMaxSat)
    seamLeftHsl[2] = seamLeftHsl[2].coerceIn(SeamLeftMinLight, SeamLeftMaxLight)
    val seamLeft = Color(ColorUtils.HSLToColor(seamLeftHsl))

    val seamRightHsl = FloatArray(3)
    ColorUtils.colorToHSL(averageRgb(midX, width, 0, height).toArgb(), seamRightHsl)
    seamRightHsl[1] =
        (seamRightHsl[1] * SeamRightSatBoost).coerceIn(SeamRightMinSat, SeamRightMaxSat)
    seamRightHsl[2] = seamRightHsl[2].coerceIn(SeamRightMinLight, SeamRightMaxLight)
    val seamRight = Color(ColorUtils.HSLToColor(seamRightHsl))

    val deepHsl = FloatArray(3)
    ColorUtils.colorToHSL(averageRgb(0, width, 0, height).toArgb(), deepHsl)
    deepHsl[1] = (deepHsl[1] * DeepSatBoost).coerceAtMost(DeepMaxSat)
    deepHsl[2] = deepHsl[2].coerceIn(DeepMinLight, DeepMaxLight)
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
        val score = (sat * AccentSatWeight + maxChannel * AccentLumaWeight) *
                (1f - abs(maxChannel - AccentTargetBrightness))
        if (score > bestScore) {
            bestScore = score
            bestPixel = c
        }
    }

    val accentBase = if (maxSat < GrayscaleSatThreshold) {
        averageRgb(0, width, 0, (height / 3).coerceAtLeast(1)).toArgb()
    } else {
        bestPixel
    }
    val accentHsl = FloatArray(3)
    ColorUtils.colorToHSL(accentBase, accentHsl)
    accentHsl[1] = (accentHsl[1] * AccentSatBoost).coerceIn(AccentMinSat, AccentMaxSat)
    accentHsl[2] = accentHsl[2].coerceIn(AccentMinLight, AccentMaxLight)
    val accent = Color(ColorUtils.HSLToColor(accentHsl))

    return listOf(seamLeft, seamRight, deep, accent)
}
