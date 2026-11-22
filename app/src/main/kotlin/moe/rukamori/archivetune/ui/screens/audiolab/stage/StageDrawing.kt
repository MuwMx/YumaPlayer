/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.audiodsp.StageListener
import moe.rukamori.archivetune.audiodsp.StageObject
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSource
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val HeadSize = 0.085f

private val RoomEdges = arrayOf(
    intArrayOf(0, 1), intArrayOf(1, 3), intArrayOf(3, 2), intArrayOf(2, 0),
    intArrayOf(4, 5), intArrayOf(5, 7), intArrayOf(7, 6), intArrayOf(6, 4),
    intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
)

private class StageMark(
    val obj: StageObject,
    val point: ProjectedPoint,
    val label: TextLayoutResult?,
    val level: Float,
    val quiet: Boolean,
    val spread: Float,
)

private class HeadPart(
    val point: Offset,
    val depth: Float,
    val radius: Float,
    val color: Color,
)

fun DrawScope.drawStageRoom(
    view: StageView,
    stage: StageSettings,
    selectedKey: Int?,
    levels: FloatArray,
    palette: StageCanvasPalette,
    on: Float = if (stage.enabled) 1f else 0f,
    labels: Map<Int, TextLayoutResult> = emptyMap(),
    compact: Boolean = false,
) {
    drawRoomBox(view, palette, on)

    if (selectedKey != null && !compact) {
        val selectedObj = stage.objects.firstOrNull { it.id == selectedKey }
        if (selectedObj != null) {
            drawGuides(view, selectedObj.x, selectedObj.z, palette.colorFor(selectedObj.source))
        } else if (selectedKey == ListenerKey) {
            drawGuides(view, stage.listener.x, stage.listener.z, palette.listener)
        }
    }

    drawFacing(view, stage.listener, palette, on)

    val soloing = stage.objects.any { it.solo && !it.muted }
    val box = stage.box
    val marks = stage.objects.map { obj ->
        val quiet = obj.muted || (soloing && !obj.solo)
        val level = if (stage.enabled && !quiet) {
            levels.getOrElse(obj.source.ordinal) { 0f }.coerceIn(0f, 1f)
        } else {
            0f
        }
        val spread = if (obj.source == StageSource.Ambience) {
            obj.width * minOf(3f, box.width * 0.35f) / (box.width * 0.5f)
        } else {
            0f
        }
        StageMark(
            obj = obj,
            point = view.projectNormalized(obj.x, obj.y, obj.z),
            label = labels[obj.id],
            level = level,
            quiet = quiet,
            spread = spread,
        )
    }.sortedByDescending { it.point.depth }

    val headDepth = view.projectNormalized(stage.listener.x, stage.listener.y, stage.listener.z).depth
    var headDrawn = false
    for (mark in marks) {
        if (!headDrawn && mark.point.depth < headDepth) {
            drawHead(view, stage.listener, selectedKey == ListenerKey, palette, on, compact)
            headDrawn = true
        }
        drawMark(view, mark, selectedKey == mark.obj.id, palette, on, compact)
    }
    if (!headDrawn) {
        drawHead(view, stage.listener, selectedKey == ListenerKey, palette, on, compact)
    }
}

private fun DrawScope.drawRoomBox(view: StageView, palette: StageCanvasPalette, on: Float) {
    val gridColor = palette.grid.copy(alpha = palette.grid.alpha * (0.6f + 0.4f * on))
    val gridStroke = 1.dp.toPx()
    for (i in 0..4) {
        val t = -1f + i * 0.5f
        drawLine(gridColor, view.projectNormalized(t, 0f, -1f).point, view.projectNormalized(t, 0f, 1f).point, gridStroke)
        drawLine(gridColor, view.projectNormalized(-1f, 0f, t).point, view.projectNormalized(1f, 0f, t).point, gridStroke)
    }

    val corners = Array(8) { i ->
        view.projectNormalized(
            if (i and 1 == 0) -1f else 1f,
            if (i and 4 == 0) 0f else 1f,
            if (i and 2 == 0) -1f else 1f,
        )
    }
    val farthest = corners.maxOf { it.depth }
    val edgeStroke = 1.dp.toPx()
    for ((a, b) in RoomEdges) {
        val back = corners[a].depth == farthest || corners[b].depth == farthest
        val base = if (back) palette.edgeBack else palette.edgeFront
        drawLine(
            color = base.copy(alpha = base.alpha * (0.6f + 0.4f * on)),
            start = corners[a].point,
            end = corners[b].point,
            strokeWidth = edgeStroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawGuides(view: StageView, x: Float, z: Float, color: Color) {
    val line = color.copy(alpha = 0.22f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    val stroke = 1.dp.toPx()
    drawLine(line, view.projectNormalized(-1f, 0f, z).point, view.projectNormalized(1f, 0f, z).point, stroke, pathEffect = dash)
    drawLine(line, view.projectNormalized(x, 0f, -1f).point, view.projectNormalized(x, 0f, 1f).point, stroke, pathEffect = dash)
}

private fun DrawScope.drawFacing(
    view: StageView,
    listener: StageListener,
    palette: StageCanvasPalette,
    on: Float,
) {
    val base = Math.toRadians(listener.yaw.toDouble())
    val reach = 0.55f
    val path = Path()
    val startProj = view.projectNormalized(listener.x, 0f, listener.z)
    val start = startProj.point
    val wx0 = view.worldX(listener.x)
    val wy0 = view.worldY(0f)
    val wz0 = view.worldZ(listener.z)
    path.moveTo(start.x, start.y)
    for (step in -4..4) {
        val angle = base + Math.toRadians(step * 6.0)
        val wx = wx0 + sin(angle).toFloat() * reach
        val wz = wz0 + cos(angle).toFloat() * reach
        val p = view.projectWorld(wx, wy0, wz).point
        path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(palette.facing.copy(alpha = 0.14f * (0.5f + 0.5f * on)), Color.Transparent),
            center = start,
            radius = view.pixels(startProj, reach) + 1f,
        ),
    )
}

private fun DrawScope.drawStem(
    view: StageView,
    x: Float,
    y: Float,
    z: Float,
    color: Color,
    alpha: Float,
    compact: Boolean,
) {
    val top = view.projectNormalized(x, y, z)
    val floor = view.projectNormalized(x, 0f, z)
    val rx = (if (compact) 5f else 8f).dp.toPx() * floor.scale
    val ry = rx * (0.2f + 0.7f * view.floorSquash)
    drawOval(
        color = color.copy(alpha = 0.2f * alpha),
        topLeft = Offset(floor.x - rx, floor.y - ry),
        size = Size(rx * 2f, ry * 2f),
    )
    drawLine(
        color = color.copy(alpha = 0.32f * alpha),
        start = floor.point,
        end = top.point,
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

private fun DrawScope.drawMark(
    view: StageView,
    mark: StageMark,
    selected: Boolean,
    palette: StageCanvasPalette,
    on: Float,
    compact: Boolean,
) {
    val obj = mark.obj
    val color = palette.colorFor(obj.source)
    val alpha = (if (mark.quiet) 0.35f else 1f) * (0.55f + 0.45f * on)
    val p = mark.point
    drawStem(view, obj.x, obj.y, obj.z, color, alpha, compact)

    if (mark.spread > 0f) {
        val left = view.projectNormalized((obj.x - mark.spread).coerceIn(-1f, 1f), obj.y, obj.z).point
        val right = view.projectNormalized((obj.x + mark.spread).coerceIn(-1f, 1f), obj.y, obj.z).point
        drawLine(color.copy(alpha = 0.3f * alpha), left, right, 1.5.dp.toPx(), cap = StrokeCap.Round)
        val endR = (if (compact) 1.6f else 2.5f).dp.toPx() * p.scale
        drawCircle(color.copy(alpha = 0.5f * alpha), endR, left)
        drawCircle(color.copy(alpha = 0.5f * alpha), endR, right)
    }

    val base = (if (compact) 3.5f else 6f).dp.toPx() * p.scale
    val r = base * (1f + 0.6f * mark.level * on)
    val glow = r * (2.4f + 1.4f * mark.level * on)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.42f * alpha), Color.Transparent),
            center = p.point,
            radius = glow,
        ),
        radius = glow,
        center = p.point,
    )

    if (obj.source == StageSource.Sub) {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(p.x - r, p.y - r),
            size = Size(r * 2f, r * 2f),
            cornerRadius = CornerRadius(r * 0.4f),
        )
    } else {
        drawCircle(color.copy(alpha = alpha), r, p.point)
    }

    drawCircle(
        color = Color.White.copy(alpha = 0.55f * alpha),
        radius = r * 0.32f,
        center = p.point + Offset(-r * 0.3f, -r * 0.3f),
    )

    if (selected && !compact) {
        drawCircle(
            color = palette.selectionRing,
            radius = r + 4.dp.toPx(),
            center = p.point,
            style = Stroke(1.5.dp.toPx()),
        )
    }

    if (!compact && mark.label != null) {
        drawText(
            textLayoutResult = mark.label,
            color = palette.label.copy(alpha = palette.label.alpha * alpha),
            topLeft = Offset(p.x - mark.label.size.width * 0.5f, p.y + r + 5.dp.toPx()),
        )
    }
}

private fun DrawScope.drawHead(
    view: StageView,
    listener: StageListener,
    selected: Boolean,
    palette: StageCanvasPalette,
    on: Float,
    compact: Boolean,
) {
    val alpha = 0.7f + 0.3f * on
    drawStem(view, listener.x, listener.y, listener.z, palette.listener, alpha, compact)

    val cx = view.worldX(listener.x)
    val cy = view.worldY(listener.y)
    val cz = view.worldZ(listener.z)
    val center = view.projectWorld(cx, cy, cz)
    val size = if (compact) HeadSize * 0.8f else HeadSize
    val radius = max(view.pixels(center, size), (if (compact) 5 else 9).dp.toPx())
    val yaw = Math.toRadians(listener.yaw.toDouble())
    val fx = sin(yaw).toFloat()
    val fz = cos(yaw).toFloat()
    val rx = fz
    val rz = -fx

    fun part(forward: Float, right: Float, up: Float, partRadius: Float, color: Color): HeadPart {
        val p = view.projectWorld(
            cx + (fx * forward + rx * right) * size,
            cy + up * size,
            cz + (fz * forward + rz * right) * size,
        )
        return HeadPart(p.point, p.depth, radius * partRadius, color)
    }

    val cup = palette.headCup.copy(alpha = alpha)
    val parts = mutableListOf(
        part(0f, -1.02f, 0f, 0.36f, cup),
        part(0f, 1.02f, 0f, 0.36f, cup),
        part(1.02f, 0f, -0.05f, 0.2f, palette.headNose.copy(alpha = alpha)),
    )
    if (!compact) {
        val eyeColor = palette.headEye.copy(alpha = alpha)
        parts += part(0.86f, -0.36f, 0.22f, 0.09f, eyeColor)
        parts += part(0.86f, 0.36f, 0.22f, 0.09f, eyeColor)
    }

    val (behind, front) = parts.partition { it.depth > center.depth }
    behind.forEach { drawCircle(it.color, it.radius, it.point) }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.headLight.copy(alpha = alpha), palette.headShadow.copy(alpha = alpha)),
            center = center.point + Offset(-radius * 0.35f, -radius * 0.4f),
            radius = radius * 1.7f,
        ),
        radius = radius,
        center = center.point,
    )

    front.forEach { drawCircle(it.color, it.radius, it.point) }

    val left = part(0f, -1.02f, 0.25f, 0f, cup).point
    val right = part(0f, 1.02f, 0.25f, 0f, cup).point
    val top = part(0f, 0f, 1.25f, 0f, cup).point
    val control = top * 2f - (left + right) * 0.5f
    drawPath(
        path = Path().apply {
            moveTo(left.x, left.y)
            quadraticTo(control.x, control.y, right.x, right.y)
        },
        color = palette.headBand.copy(alpha = 0.9f * alpha),
        style = Stroke(max(1.5.dp.toPx(), radius * 0.16f), cap = StrokeCap.Round),
    )

    if (selected && !compact) {
        drawCircle(
            color = palette.selectionRing,
            radius = radius + 6.dp.toPx(),
            center = center.point,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}
