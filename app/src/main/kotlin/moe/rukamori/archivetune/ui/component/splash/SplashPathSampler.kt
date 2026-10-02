package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

object SplashPathSampler {
    private val pos = FloatArray(2)
    private var segLengths = FloatArray(32)

    fun fromSvgPath(
        pathData: String,
        count: Int,
        cx: Float,
        cy: Float,
        targetSize: Float
    ): List<Offset> = synchronized(this) {
        if (pathData.isEmpty() || count <= 0 || targetSize <= 0f) return@synchronized emptyList()
        return try {
            val androidPath = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
            val bounds = android.graphics.RectF()
            androidPath.computeBounds(bounds, true)
            if (bounds.width() <= 0f || bounds.height() <= 0f) return emptyList()
            val scale = targetSize / maxOf(bounds.width(), bounds.height())
            val matrix = android.graphics.Matrix().apply {
                postTranslate(-bounds.centerX(), -bounds.centerY())
                postScale(scale, scale)
                postTranslate(cx, cy)
            }
            androidPath.transform(matrix)
            val measure = android.graphics.PathMeasure(androidPath, false)
            val length = measure.length
            if (length <= 0f) return emptyList()
            val step = length / count
            List(count) { i ->
                measure.getPosTan(i * step, pos, null)
                Offset(pos[0], pos[1])
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun ndResample(pts: List<Offset>, count: Int): List<Offset> = synchronized(this) {
        if (pts.isEmpty() || count <= 0) return@synchronized emptyList()
        val n = pts.size
        var lengths = segLengths
        if (lengths.size < n) {
            lengths = FloatArray(n)
            segLengths = lengths
        }
        var perimeter = 0f
        for (i in 0 until n) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val len = hypot(p2.x - p1.x, p2.y - p1.y)
            lengths[i] = len
            perimeter += len
        }
        if (perimeter <= 0f) return@synchronized List(count) { pts.first() }

        val step = perimeter / count
        val res = ArrayList<Offset>(count)
        var segIdx = 0
        var segStartDist = 0f

        for (i in 0 until count) {
            val targetDist = i * step
            while (segIdx < n - 1 && targetDist >= segStartDist + lengths[segIdx]) {
                segStartDist += lengths[segIdx]
                segIdx++
            }
            val segLen = lengths[segIdx]
            val t = if (segLen > 0f) ((targetDist - segStartDist) / segLen).coerceIn(0f, 1f) else 0f
            val p1 = pts[segIdx]
            val p2 = pts[(segIdx + 1) % n]
            res.add(
                Offset(
                    x = p1.x + (p2.x - p1.x) * t,
                    y = p1.y + (p2.y - p1.y) * t
                )
            )
        }
        res
    }

    fun parseContourPath(path: android.graphics.Path, totalSlots: Int): SplashSlots.ShapeSlots = synchronized(this) {
        val contourLengths = ArrayList<Float>()
        val measure = android.graphics.PathMeasure(path, false)
        do {
            val len = measure.length
            if (len > 0f) {
                contourLengths.add(len)
            }
        } while (measure.nextContour())

        if (contourLengths.isEmpty() || totalSlots <= 0) {
            return@synchronized SplashSlots.ShapeSlots(emptyList(), listOf(0 until totalSlots), emptyList(), path)
        }

        val totalLength = contourLengths.sum()
        if (totalLength <= 0f) {
            return@synchronized SplashSlots.ShapeSlots(emptyList(), listOf(0 until totalSlots), emptyList(), path)
        }

        val counts = contourLengths.map { len ->
            ((len / totalLength * totalSlots).toInt()).coerceAtLeast(3)
        }.toMutableList()

        val longestIndex = contourLengths.indices.maxByOrNull { contourLengths[it] } ?: 0
        val diff = totalSlots - counts.sum()
        counts[longestIndex] += diff

        val samplingMeasure = android.graphics.PathMeasure(path, false)
        val slots = ArrayList<Offset>(totalSlots)
        val loops = ArrayList<IntRange>(contourLengths.size)
        var contourIdx = 0

        do {
            val len = samplingMeasure.length
            if (len <= 0f) continue
            if (contourIdx >= counts.size) break
            val count = counts[contourIdx]
            val startIndex = slots.size
            if (count > 0) {
                val step = len / count
                for (i in 0 until count) {
                    samplingMeasure.getPosTan(i * step, pos, null)
                    slots.add(Offset(pos[0], pos[1]))
                }
            }
            loops.add(startIndex until slots.size)
            contourIdx++
        } while (samplingMeasure.nextContour())

        var sumX = 0f
        var sumY = 0f
        for (s in slots) {
            sumX += s.x
            sumY += s.y
        }
        val centroid = if (slots.isNotEmpty()) Offset(sumX / slots.size, sumY / slots.size) else Offset.Zero

        val third = slots.size / 3
        val tips = if (third > 0) {
            (0..2).mapNotNull { i ->
                val from = i * third
                val to = if (i == 2) slots.size else (i + 1) * third
                slots.subList(from, to).maxByOrNull {
                    val dx = it.x - centroid.x
                    val dy = it.y - centroid.y
                    dx * dx + dy * dy
                }
            }
        } else {
            slots.take(3)
        }

        SplashSlots.ShapeSlots(
            slots = slots,
            loops = loops,
            tips = tips,
            outlinePath = path
        )
    }
}
