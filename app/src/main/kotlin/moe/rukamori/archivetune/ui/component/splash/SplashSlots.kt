package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.min

object SplashSlots {
    const val RD = 0.5483f
    const val CROSS_T = 0.075f
    const val SQRT1_2 = 0.70710677f
    val SLOT_COUNT: Int get() = SplashConfig.getSlotCount(SHAPE_LOGO)
    const val SHAPE_BOLT = "bolt"
    const val SHAPE_CROSS = "cross"
    const val SHAPE_LOGO = "logo"
    const val SHAPE_YUMA = "yuma"

    var customVectorPath: android.graphics.Path? = null

    const val LOGO_PATH = "M353.991 673.128C341.491 673.128 151.791 605.128 113.491 586.128C75.1908 567.128 10.9905 545.499 0.490656 521.6C-10.0092 497.7 151.491 425.628 151.491 425.628C169.491 416.628 288.491 360.128 292.59 357L292.575 336.628L362.735 300.078C362.735 300.078 366.491 673.128 353.991 673.128Z M296.79 0C296.79 0 577.49 52.8 595.59 57V187.128C595.59 187.128 369.09 149.499 366.191 150.699L362.843 292.226L292.575 329.588L296.79 0Z"

    data class ShapeSlots(
        val slots: List<Offset>,
        val loops: List<IntRange>,
        val tips: List<Offset>,
        val outlinePath: android.graphics.Path
    )

    fun center(width: Float, height: Float): Offset =
        Offset(width / 2f, height / 2f)

    fun ldBox(width: Float, height: Float): Float =
        min(height * 0.35f, width * 0.52f)

    fun boxSize(width: Float, height: Float): Float =
        min(height * 0.35f, (width * 0.62f) / RD)

    fun boxSize(shape: String, width: Float, height: Float, density: Float = 1f): Float =
        when (shape) {
            SHAPE_CROSS -> ldBox(width, height)
            SHAPE_LOGO, SHAPE_YUMA -> minOf(SplashConfig.Effects.LOGO_TARGET_SIZE_DP * density, minOf(width, height) * 0.42f)
            else -> boxSize(width, height)
        }

    fun boxFrame(shape: String, width: Float, height: Float, density: Float = 1f): Pair<Offset, Float> =
        Pair(center(width, height), boxSize(shape, width, height, density))

    fun pbBolt(cx: Float, cy: Float, size: Float): List<Offset> {
        val w = size * RD
        val left = cx - w / 2f
        val top = cy - size / 2f

        val raw = listOf(
            Offset(0.72f, 0.00f),
            Offset(0.24f, 0.52f),
            Offset(0.54f, 0.52f),
            Offset(0.28f, 1.00f),
            Offset(0.76f, 0.48f),
            Offset(0.46f, 0.48f)
        )
        return raw.map { Offset(left + it.x * w, top + it.y * size) }
    }

    fun ldCross(cx: Float, cy: Float, size: Float): List<Offset> {
        val t = CROSS_T
        val raw = listOf(
            Offset(0.5f - t, 0f), Offset(0.5f + t, 0f), Offset(0.5f + t, 0.5f - t),
            Offset(1f, 0.5f - t), Offset(1f, 0.5f + t), Offset(0.5f + t, 0.5f + t),
            Offset(0.5f + t, 1f), Offset(0.5f - t, 1f), Offset(0.5f - t, 0.5f + t),
            Offset(0f, 0.5f + t), Offset(0f, 0.5f - t), Offset(0.5f - t, 0.5f - t)
        )
        val cos45 = SQRT1_2
        return raw.map { (rx, ry) ->
            val px = rx - 0.5f
            val py = ry - 0.5f
            Offset(
                cx + ((px - py) * cos45 * cos45) * size,
                cy + ((px + py) * cos45 * cos45) * size
            )
        }
    }

    fun fromSvgPath(
        pathData: String,
        count: Int = SLOT_COUNT,
        cx: Float,
        cy: Float,
        targetSize: Float
    ): List<Offset> {
        if (pathData.isEmpty() || count <= 0 || targetSize <= 0f) return emptyList()
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
            val pos = FloatArray(2)
            List(count) { i ->
                measure.getPosTan(i * step, pos, null)
                Offset(pos[0], pos[1])
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun tips(shape: String, width: Float, height: Float, density: Float = 1f): List<Offset> {
        val (c, size) = boxFrame(shape, width, height, density)
        return if (shape == SHAPE_CROSS) {
            val offset = size * 0.45f
            listOf(
                Offset(c.x, c.y - offset),
                Offset(c.x + offset, c.y),
                Offset(c.x, c.y + offset),
                Offset(c.x - offset, c.y)
            )
        } else {
            val w = size * RD
            listOf(
                Offset(c.x + w * 0.22f, c.y - size * 0.5f),
                Offset(c.x - w * 0.22f, c.y + size * 0.5f)
            )
        }
    }

    fun contour(shape: String, cx: Float, cy: Float, size: Float): List<Offset> =
        if (shape == SHAPE_CROSS) ldCross(cx, cy, size) else pbBolt(cx, cy, size)

    fun ndResample(pts: List<Offset>, count: Int = SLOT_COUNT): List<Offset> {
        if (pts.isEmpty() || count <= 0) return emptyList()
        val n = pts.size
        val segLengths = FloatArray(n) { i ->
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            hypot(p2.x - p1.x, p2.y - p1.y)
        }
        val perimeter = segLengths.sum()
        if (perimeter <= 0f) return List(count) { pts.first() }

        val step = perimeter / count
        val res = ArrayList<Offset>(count)
        var segIdx = 0
        var segStartDist = 0f

        for (i in 0 until count) {
            val targetDist = i * step
            while (segIdx < n - 1 && targetDist >= segStartDist + segLengths[segIdx]) {
                segStartDist += segLengths[segIdx]
                segIdx++
            }
            val segLen = segLengths[segIdx]
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
        return res
    }

    fun parseContourPath(path: android.graphics.Path, totalSlots: Int = SLOT_COUNT): ShapeSlots {
        val contourLengths = ArrayList<Float>()
        val measure = android.graphics.PathMeasure(path, false)
        do {
            val len = measure.length
            if (len > 0f) {
                contourLengths.add(len)
            }
        } while (measure.nextContour())

        if (contourLengths.isEmpty() || totalSlots <= 0) {
            return ShapeSlots(emptyList(), listOf(0 until totalSlots), emptyList(), path)
        }

        val totalLength = contourLengths.sum()
        if (totalLength <= 0f) {
            return ShapeSlots(emptyList(), listOf(0 until totalSlots), emptyList(), path)
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
        val pos = FloatArray(2)

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

        val centroid = if (slots.isNotEmpty()) {
            var sumX = 0f
            var sumY = 0f
            for (s in slots) {
                sumX += s.x
                sumY += s.y
            }
            Offset(sumX / slots.size, sumY / slots.size)
        } else {
            Offset.Zero
        }

        val tips = slots.sortedByDescending {
            (it.x - centroid.x) * (it.x - centroid.x) + (it.y - centroid.y) * (it.y - centroid.y)
        }.take(3)

        return ShapeSlots(
            slots = slots,
            loops = loops,
            tips = tips,
            outlinePath = path
        )
    }

    fun build(shape: String, width: Float, height: Float, density: Float = 1f): ShapeSlots {
        val totalSlots = SplashConfig.getSlotCount(shape)
        if (width <= 0f || height <= 0f) return ShapeSlots(emptyList(), listOf(0 until totalSlots), emptyList(), android.graphics.Path())
        val (c, size) = boxFrame(shape, width, height, density)
        if (shape == SHAPE_LOGO || shape == SHAPE_YUMA) {
            val sourcePath = customVectorPath ?: try {
                androidx.core.graphics.PathParser.createPathFromPathData(LOGO_PATH)
            } catch (_: Exception) {
                null
            }
            if (sourcePath != null) {
                val p = android.graphics.Path(sourcePath)
                val bounds = android.graphics.RectF()
                p.computeBounds(bounds, true)
                if (bounds.width() > 0f && bounds.height() > 0f) {
                    val scale = size / maxOf(bounds.width(), bounds.height())
                    val matrix = android.graphics.Matrix().apply {
                        postTranslate(-bounds.centerX(), -bounds.centerY())
                        postScale(scale, scale)
                        postTranslate(c.x, c.y)
                    }
                    p.transform(matrix)
                    return parseContourPath(p, totalSlots)
                }
            }
        }
        val pts = ndResample(contour(shape, c.x, c.y, size), totalSlots)
        val raw = contour(shape, c.x, c.y, size)
        val outline = android.graphics.Path().apply {
            if (raw.isNotEmpty()) {
                moveTo(raw[0].x, raw[0].y)
                for (k in 1 until raw.size) lineTo(raw[k].x, raw[k].y)
                close()
            }
        }
        return ShapeSlots(pts, listOf(0 until totalSlots), tips(shape, width, height, density), outline)
    }

    private data class PairDist(val member: Int, val slot: Int, val d: Float)

    fun zdGreedyCompile(members: List<Offset>, slots: List<Offset>): List<Int> {
        if (members.isEmpty() || slots.isEmpty()) return List(members.size) { -1 }

        val pairs = ArrayList<PairDist>(members.size * slots.size)
        for (i in members.indices) {
            val m = members[i]
            for (j in slots.indices) {
                val s = slots[j]
                val d = hypot(m.x - s.x, m.y - s.y)
                pairs.add(PairDist(i, j, d))
            }
        }
        pairs.sortBy { it.d }

        val usedSlots = BooleanArray(slots.size)
        val assignedMembers = BooleanArray(members.size)
        val assign = IntArray(members.size) { -1 }
        var assignedCount = 0
        val targetCount = min(members.size, slots.size)

        for (p in pairs) {
            if (!assignedMembers[p.member] && !usedSlots[p.slot]) {
                assignedMembers[p.member] = true
                usedSlots[p.slot] = true
                assign[p.member] = p.slot
                assignedCount++
                if (assignedCount == targetCount) break
            }
        }
        return assign.toList()
    }
}
