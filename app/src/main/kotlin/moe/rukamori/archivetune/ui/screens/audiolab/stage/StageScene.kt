/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import moe.rukamori.archivetune.audiodsp.StageBox
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val DefaultCameraYaw = -28f
const val DefaultCameraPitch = 24f
const val CameraDistance = 3.4f
const val ListenerKey = -1

data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val depth: Float,
    val scale: Float,
) {
    val point: Offset get() = Offset(x, y)
}

class StageView(
    val size: IntSize,
    val box: StageBox,
    yawDeg: Float,
    pitchDeg: Float,
) {
    private val unit = 1f / maxOf(box.width, box.depth, box.height).coerceAtLeast(1f) * 2f
    private val halfX = box.width * 0.5f * unit
    private val halfY = box.height * 0.5f * unit
    private val halfZ = box.depth * 0.5f * unit

    private val yaw = yawDeg * (PI.toFloat() / 180f)
    private val pitch = pitchDeg * (PI.toFloat() / 180f)

    private val yawCos = cos(yaw)
    private val yawSin = sin(yaw)
    private val pitchCos = cos(pitch)
    private val pitchSin = sin(pitch)

    private val center = Offset(size.width * 0.5f, size.height * 0.5f)

    private val focal: Float = run {
        val radius = sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ).coerceAtLeast(0.1f)
        val minDim = min(size.width, size.height).coerceAtLeast(1)
        val distSq = (CameraDistance * CameraDistance - radius * radius).coerceAtLeast(0.25f)
        minDim * 0.5f * 1.08f * sqrt(distSq) / radius
    }

    val floorSquash: Float = abs(pitchSin)

    fun worldX(nx: Float): Float = nx * halfX
    fun worldY(ny: Float): Float = (ny - 0.5f) * 2f * halfY
    fun worldZ(nz: Float): Float = nz * halfZ

    fun projectWorld(wx: Float, wy: Float, wz: Float): ProjectedPoint {
        val x1 = wx * yawCos + wz * yawSin
        val z1 = -wx * yawSin + wz * yawCos
        val y2 = wy * pitchCos + z1 * pitchSin
        val z2 = -wy * pitchSin + z1 * pitchCos
        val depth = (z2 + CameraDistance).coerceAtLeast(0.15f)
        val k = focal / depth
        return ProjectedPoint(
            x = center.x + x1 * k,
            y = center.y - y2 * k,
            depth = depth,
            scale = CameraDistance / depth,
        )
    }

    fun projectNormalized(nx: Float, ny: Float, nz: Float): ProjectedPoint =
        projectWorld(worldX(nx), worldY(ny), worldZ(nz))

    fun pixels(at: ProjectedPoint, worldSize: Float): Float =
        focal * worldSize / at.depth

    fun heightPixels(nx: Float, ny: Float, nz: Float): Float {
        val a = projectNormalized(nx, ny, nz).point
        val b = projectNormalized(nx, ny + 0.5f, nz).point
        return max(1f, (a - b).getDistance() * 2f)
    }

    fun onLevel(px: Float, py: Float, fixedNy: Float): Pair<Float, Float>? {
        val planeY = worldY(fixedNy)
        val eye = toWorld(0f, 0f, -CameraDistance)
        val dir = toWorld((px - center.x) / focal, -(py - center.y) / focal, 1f)
        if (abs(dir[1]) < 1e-4f) return null
        val t = (planeY - eye[1]) / dir[1]
        if (t <= 0f) return null
        return (eye[0] + dir[0] * t) / halfX to (eye[2] + dir[2] * t) / halfZ
    }

    fun toWorld(cx: Float, cy: Float, cz: Float): FloatArray {
        val y1 = cy * pitchCos - cz * pitchSin
        val z1 = cy * pitchSin + cz * pitchCos
        val wx = cx * yawCos - z1 * yawSin
        val wz = cx * yawSin + z1 * yawCos
        return floatArrayOf(wx, y1, wz)
    }
}
