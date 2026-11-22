/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.audiodsp.StageListener
import moe.rukamori.archivetune.audiodsp.StageObject
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
data class StageCanvasPalette(
    val grid: Color,
    val edgeFront: Color,
    val edgeBack: Color,
    val guide: Color,
    val accent: Color,
    val listener: Color,
    val sub: Color,
    val muted: Color,
    val surface: Color,
    val shadow: Color,
)

@Composable
fun rememberStageCanvasPalette(): StageCanvasPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        StageCanvasPalette(
            grid = scheme.onSurfaceVariant.copy(alpha = 0.16f),
            edgeFront = scheme.onSurfaceVariant.copy(alpha = 0.45f),
            edgeBack = scheme.onSurfaceVariant.copy(alpha = 0.20f),
            guide = scheme.primary.copy(alpha = 0.45f),
            accent = scheme.primary,
            listener = scheme.tertiary,
            sub = scheme.secondary,
            muted = scheme.onSurfaceVariant.copy(alpha = 0.45f),
            surface = scheme.surface,
            shadow = scheme.onSurface.copy(alpha = 0.18f),
        )
    }
}

private sealed class DrawableItem(val depth: Float) {
    class Source(val obj: StageObject, val point: ProjectedPoint) : DrawableItem(point.depth)
    class Listener(val listener: StageListener, val point: ProjectedPoint) : DrawableItem(point.depth)
}

fun DrawScope.drawStageRoom(
    view: StageView,
    stage: StageSettings,
    selectedKey: Int,
    levels: FloatArray,
    palette: StageCanvasPalette,
) {
    val gridStroke = 1.dp.toPx()
    for (i in 0..4) {
        val t = -1f + i * 0.5f
        val xStart = view.projectNormalized(t, 0f, -1f)
        val xEnd = view.projectNormalized(t, 0f, 1f)
        drawLine(palette.grid, Offset(xStart.x, xStart.y), Offset(xEnd.x, xEnd.y), strokeWidth = gridStroke)

        val zStart = view.projectNormalized(-1f, 0f, t)
        val zEnd = view.projectNormalized(1f, 0f, t)
        drawLine(palette.grid, Offset(zStart.x, zStart.y), Offset(zEnd.x, zEnd.y), strokeWidth = gridStroke)
    }

    val corners = arrayOf(
        view.projectNormalized(-1f, 0f, -1f),
        view.projectNormalized(1f, 0f, -1f),
        view.projectNormalized(1f, 0f, 1f),
        view.projectNormalized(-1f, 0f, 1f),
        view.projectNormalized(-1f, 1f, -1f),
        view.projectNormalized(1f, 1f, -1f),
        view.projectNormalized(1f, 1f, 1f),
        view.projectNormalized(-1f, 1f, 1f),
    )
    val maxDepth = corners.maxOf { it.depth }
    val edges = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 0,
        4 to 5, 5 to 6, 6 to 7, 7 to 4,
        0 to 4, 1 to 5, 2 to 6, 3 to 7,
    )
    val edgeStroke = 1.25.dp.toPx()
    for ((a, b) in edges) {
        val avgDepth = (corners[a].depth + corners[b].depth) * 0.5f
        val isBack = avgDepth > maxDepth * 0.86f
        drawLine(
            color = if (isBack) palette.edgeBack else palette.edgeFront,
            start = Offset(corners[a].x, corners[a].y),
            end = Offset(corners[b].x, corners[b].y),
            strokeWidth = edgeStroke,
        )
    }

    if (selectedKey == ListenerKey) {
        drawGuides(view, stage.listener.x, stage.listener.y, stage.listener.z, palette.guide)
    } else {
        stage.objects.firstOrNull { it.id == selectedKey }?.let { obj ->
            drawGuides(view, obj.x, obj.y, obj.z, palette.guide)
        }
    }

    val items = buildList {
        stage.objects.forEach { obj ->
            add(DrawableItem.Source(obj, view.projectNormalized(obj.x, obj.y, obj.z)))
        }
        add(DrawableItem.Listener(stage.listener, view.projectNormalized(stage.listener.x, stage.listener.y, stage.listener.z)))
    }.sortedByDescending { it.depth }

    val stemDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    for (item in items) {
        when (item) {
            is DrawableItem.Source -> {
                val obj = item.obj
                val p = item.point
                val floor = view.projectNormalized(obj.x, 0f, obj.z)
                val baseColor = when {
                    !stage.enabled || obj.muted -> palette.muted
                    obj.source == StageSource.Sub -> palette.sub
                    else -> palette.accent
                }
                val level = if (stage.enabled && !obj.muted) {
                    levels.getOrElse(obj.source.ordinal) { 0f }.coerceIn(0f, 1f)
                } else {
                    0f
                }

                drawOval(
                    color = palette.shadow,
                    topLeft = Offset(floor.x - 8.dp.toPx(), floor.y - 3.5.dp.toPx()),
                    size = Size(16.dp.toPx(), 7.dp.toPx()),
                )
                drawLine(
                    color = baseColor.copy(alpha = 0.45f),
                    start = Offset(floor.x, floor.y),
                    end = Offset(p.x, p.y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = stemDash,
                )

                if (obj.source == StageSource.Ambience) {
                    val spread = obj.width * 0.42f
                    val leftP = view.projectNormalized((obj.x - spread).coerceIn(-1f, 1f), obj.y, obj.z)
                    val rightP = view.projectNormalized((obj.x + spread).coerceIn(-1f, 1f), obj.y, obj.z)
                    drawLine(
                        color = baseColor.copy(alpha = 0.55f),
                        start = Offset(leftP.x, leftP.y),
                        end = Offset(rightP.x, rightP.y),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawSourceMark(Offset(leftP.x, leftP.y), 6.dp.toPx(), baseColor, level, false, palette.surface)
                    drawSourceMark(Offset(rightP.x, rightP.y), 6.dp.toPx(), baseColor, level, false, palette.surface)
                } else {
                    val radius = (8.dp.toPx() * (0.85f + level * 0.25f))
                    drawSourceMark(
                        center = Offset(p.x, p.y),
                        radius = radius,
                        color = baseColor,
                        level = level,
                        isSub = obj.source == StageSource.Sub,
                        surface = palette.surface,
                    )
                }

                if (obj.id == selectedKey) {
                    drawCircle(
                        color = palette.accent,
                        radius = 14.dp.toPx(),
                        center = Offset(p.x, p.y),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            is DrawableItem.Listener -> {
                val l = item.listener
                val p = item.point
                val floor = view.projectNormalized(l.x, 0f, l.z)
                val color = if (stage.enabled) palette.listener else palette.muted

                drawOval(
                    color = palette.shadow,
                    topLeft = Offset(floor.x - 9.dp.toPx(), floor.y - 4.dp.toPx()),
                    size = Size(18.dp.toPx(), 8.dp.toPx()),
                )
                drawLine(
                    color = color.copy(alpha = 0.45f),
                    start = Offset(floor.x, floor.y),
                    end = Offset(p.x, p.y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = stemDash,
                )

                val yawRad = l.yaw * (PI.toFloat() / 180f)
                val coneLeft = view.projectNormalized(
                    (l.x + sin(yawRad - 0.42f) * 0.38f).coerceIn(-1f, 1f),
                    l.y,
                    (l.z + cos(yawRad - 0.42f) * 0.38f).coerceIn(-1f, 1f),
                )
                val coneRight = view.projectNormalized(
                    (l.x + sin(yawRad + 0.42f) * 0.38f).coerceIn(-1f, 1f),
                    l.y,
                    (l.z + cos(yawRad + 0.42f) * 0.38f).coerceIn(-1f, 1f),
                )
                val conePath = Path().apply {
                    moveTo(p.x, p.y)
                    lineTo(coneLeft.x, coneLeft.y)
                    lineTo(coneRight.x, coneRight.y)
                    close()
                }
                drawPath(path = conePath, color = color.copy(alpha = 0.18f))

                val headR = 9.dp.toPx()
                drawCircle(color = palette.surface, radius = headR + 1.5.dp.toPx(), center = Offset(p.x, p.y))
                drawCircle(color = color, radius = headR, center = Offset(p.x, p.y))
                drawArc(
                    color = color,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(p.x - headR - 3.dp.toPx(), p.y - headR - 3.dp.toPx()),
                    size = Size((headR + 3.dp.toPx()) * 2f, (headR + 3.dp.toPx()) * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )

                if (selectedKey == ListenerKey) {
                    drawCircle(
                        color = palette.listener,
                        radius = 16.dp.toPx(),
                        center = Offset(p.x, p.y),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGuides(
    view: StageView,
    nx: Float,
    ny: Float,
    nz: Float,
    color: Color,
) {
    val p = view.projectNormalized(nx, ny, nz)
    val xWall = view.projectNormalized(-1f, ny, nz)
    val zWall = view.projectNormalized(nx, ny, 1f)
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(p.x, p.y), Offset(xWall.x, xWall.y), strokeWidth = stroke)
    drawLine(color, Offset(p.x, p.y), Offset(zWall.x, zWall.y), strokeWidth = stroke)
}

private fun DrawScope.drawSourceMark(
    center: Offset,
    radius: Float,
    color: Color,
    level: Float,
    isSub: Boolean,
    surface: Color,
) {
    if (level > 0.02f) {
        val glowRadius = radius * (1.8f + level * 1.4f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.35f * level), Color.Transparent),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )
    }

    if (isSub) {
        val halfW = radius * 1.15f
        val halfH = radius * 0.85f
        drawRoundRect(
            color = surface,
            topLeft = Offset(center.x - halfW - 1.5.dp.toPx(), center.y - halfH - 1.5.dp.toPx()),
            size = Size((halfW + 1.5.dp.toPx()) * 2f, (halfH + 1.5.dp.toPx()) * 2f),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - halfW, center.y - halfH),
            size = Size(halfW * 2f, halfH * 2f),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    } else {
        drawCircle(color = surface, radius = radius + 1.5.dp.toPx(), center = center)
        drawCircle(color = color, radius = radius, center = center)
    }
}
