/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.ui.unit.IntSize
import moe.rukamori.archivetune.audiodsp.StageBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

const val DefaultCameraYaw = -28f
const val DefaultCameraPitch = 24f
const val CameraDistance = 3.4f
const val ListenerKey = -1

data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val depth: Float,
    val scale: Float,
)

class StageView(
    val size: IntSize,
    val box: StageBox,
    yawDeg: Float,
    pitchDeg: Float,
) {
    private val yaw = yawDeg * (PI.toFloat() / 180f)
    private val pitch = pitchDeg * (PI.toFloat() / 180f)

    private val sinY = sin(yaw)
    private val cosY = cos(yaw)
    private val sinP = sin(pitch)
    private val cosP = cos(pitch)

    private val halfW = box.width * 0.5f
    private val halfD = box.depth * 0.5f
    private val centerY = box.height * 0.38f

    private val maxSpan = maxOf(box.width, box.depth, box.height * 1.4f).coerceAtLeast(1f)
    private val camDist = maxSpan * CameraDistance * 0.45f
    private val fovRad = 46f * (PI.toFloat() / 180f)
    private val focal = (minOf(size.width, size.height).coerceAtLeast(1) * 0.5f) / tan(fovRad * 0.5f)

    private val camX = camDist * cosP * sinY
    private val camY = centerY + camDist * sinP
    private val camZ = -camDist * cosP * cosY

    private val fwdX: Float
    private val fwdY: Float
    private val fwdZ: Float

    private val rightX: Float
    private val rightZ: Float

    private val upX: Float
    private val upY: Float
    private val upZ: Float

    init {
        val dx = -camX
        val dy = centerY - camY
        val dz = -camZ
        val len = hypot(hypot(dx, dy), dz).coerceAtLeast(1e-5f)
        fwdX = dx / len
        fwdY = dy / len
        fwdZ = dz / len

        val rx = -fwdZ
        val rz = fwdX
        val rLen = hypot(rx, rz).coerceAtLeast(1e-5f)
        rightX = rx / rLen
        rightZ = rz / rLen

        upX = rightZ * fwdY
        upY = rightZ * fwdX - rightX * fwdZ
        upZ = -rightX * fwdY
    }

    fun worldX(nx: Float): Float = nx * halfW
    fun worldY(ny: Float): Float = ny * box.height
    fun worldZ(nz: Float): Float = nz * halfD

    fun projectWorld(wx: Float, wy: Float, wz: Float): ProjectedPoint {
        val vx = wx - camX
        val vy = wy - camY
        val vz = wz - camZ

        val cx = vx * rightX + vz * rightZ
        val cy = vx * upX + vy * upY + vz * upZ
        val cz = (vx * fwdX + vy * fwdY + vz * fwdZ).coerceAtLeast(0.15f)

        val scale = focal / cz
        return ProjectedPoint(
            x = size.width * 0.5f + cx * scale,
            y = size.height * 0.5f - cy * scale,
            depth = cz,
            scale = scale,
        )
    }

    fun projectNormalized(nx: Float, ny: Float, nz: Float): ProjectedPoint =
        projectWorld(worldX(nx), worldY(ny), worldZ(nz))

    fun pixels(meters: Float, depth: Float): Float =
        meters * (focal / depth.coerceAtLeast(0.15f))

    fun heightPixels(depth: Float): Float =
        pixels(box.height, depth)

    fun onLevel(px: Float, py: Float, fixedNy: Float): Pair<Float, Float> {
        val sx = (px - size.width * 0.5f) / focal
        val sy = -(py - size.height * 0.5f) / focal

        val dirX = fwdX + sx * rightX + sy * upX
        val dirY = fwdY + sy * upY
        val dirZ = fwdZ + sx * rightZ + sy * upZ

        val targetY = worldY(fixedNy)
        val t = if (kotlin.math.abs(dirY) > 1e-4f) {
            ((targetY - camY) / dirY).coerceIn(0.2f, camDist * 4f)
        } else {
            camDist
        }

        val wx = camX + dirX * t
        val wz = camZ + dirZ * t
        return (wx / halfW).coerceIn(-1f, 1f) to (wz / halfD).coerceIn(-1f, 1f)
    }
}
