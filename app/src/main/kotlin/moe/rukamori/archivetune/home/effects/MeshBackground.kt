package moe.rukamori.archivetune.home.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
    isVisible: Boolean = true,
) {
    val disableAnimations = LocalAnimationsDisabled.current
    val context = LocalContext.current
    val parallaxState = rememberParallaxState(
        enableParallax = parallaxEnabled && !disableAnimations,
        sensitivity = parallaxSensitivity,
        context = context
    )
    val time = rememberAnimatedTime(
        speedMultiplier = if (disableAnimations) 0f else 1f,
        isVisible = isVisible,
        label = "MeshBackground",
    )

    val animatedPrimaryColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "meshPrimaryColor",
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.secondary,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "meshSecondaryColor",
    )
    val animatedTertiaryColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.tertiary,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "meshTertiaryColor",
    )
    val alphaScale = brightness.coerceIn(0.1f, 2f)

    // Generate mesh grid - random offsets and Z amplitudes per node
    val meshNodes = remember { generateMeshGrid() }

    val reusablePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width  = size.width
        val height = size.height
        val tiltX  = parallaxState.tiltX.value
        val tiltY  = parallaxState.tiltY.value
        val unit   = minOf(width, height)

        val rows = 12
        val cols = 12

        // 3D projection parameters
        val cameraZ  = 1.6f
        val gridTilt = 10f

        // Map accumulated time → [0, PI] with a 20 000 ms period and smooth reverse.
        val t = (time.value % 40000f).let { raw ->
            if (raw < 20000f) raw * PI.toFloat() / 20000f          // forward
            else (40000f - raw) * PI.toFloat() / 20000f            // reverse
        }

        fun projectNode(node: MeshNode): Offset {
            // Unique frequencies and phases for each node - creates chaotic, non-repeating motion
            val xFreq  = 1.0f + node.baseX * 0.5f
            val yFreq  = 1.2f + node.baseY * 0.6f
            val zFreq  = 0.8f + (node.baseX + node.baseY) * 0.4f
            val xPhase = node.baseX * 2f * PI.toFloat()
            val yPhase = node.baseY * 3f * PI.toFloat()
            val zPhase = (node.baseX + node.baseY) * 1.5f * PI.toFloat()

            // Calculate position with sine/cosine for smooth looping
            val x = node.baseX + node.offsetX * sin(t * xFreq + xPhase)
            val y = node.baseY + node.offsetY * cos(t * yFreq + yPhase)

            val z = node.zAmplitude * sin(t * zFreq + zPhase)

            // Normalize coordinates
            val normalizedX = (x - 0.5f) * 3.8f
            val normalizedY = (y - 0.5f) * 2.8f

            // Apply tilt rotation
            val tiltRad  = Math.toRadians(gridTilt.toDouble())
            val rotatedY = normalizedY * cos(tiltRad).toFloat() - z * sin(tiltRad).toFloat()
            val rotatedZ = normalizedY * sin(tiltRad).toFloat() + z * cos(tiltRad).toFloat()

            // Parallax effect
            val parallaxStrength = node.baseDepth * unit * 0.1f

            // Perspective projection
            val perspective = cameraZ / (cameraZ - rotatedZ)

            // Convert to screen coordinates
            return Offset(
                normalizedX * perspective * width  * 0.48f + width  * 0.5f + tiltX * parallaxStrength,
                rotatedY    * perspective * height * 0.48f + height * 0.5f + tiltY * parallaxStrength
            )
        }

        // Draw triangular polygons
        meshNodes.forEachIndexed { index, node ->
            val row = index / cols
            val col = index % cols

            if (row >= rows - 1 || col >= cols - 1) return@forEachIndexed

            val p1 = projectNode(meshNodes[index])
            val p2 = projectNode(meshNodes[index + 1])
            val p3 = projectNode(meshNodes[index + cols])
            val p4 = projectNode(meshNodes[index + cols + 1])

            // Select color based on grid position
            val color = when ((row + col) % 3) {
                0    -> animatedPrimaryColor
                1    -> animatedSecondaryColor
                else -> animatedTertiaryColor
            }

            val alpha = (0.14f + node.baseDepth * 0.05f) * alphaScale

            reusablePath.reset()
            reusablePath.moveTo(p1.x, p1.y)
            reusablePath.lineTo(p2.x, p2.y)
            reusablePath.lineTo(p3.x, p3.y)
            reusablePath.close()
            drawPath(
                reusablePath,
                color.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx())
            )

            reusablePath.reset()
            reusablePath.moveTo(p2.x, p2.y)
            reusablePath.lineTo(p4.x, p4.y)
            reusablePath.lineTo(p3.x, p3.y)
            reusablePath.close()
            drawPath(
                reusablePath,
                color.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * Generate mesh grid with varying depth.
 * Random offsets and Z amplitudes create unique motion per node.
 */
private fun generateMeshGrid(): List<MeshNode> {
    val rows = 12
    val cols = 12
    val nodes = mutableListOf<MeshNode>()

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val baseX = col / (cols - 1f)
            val baseY = row / (rows - 1f)

            // Random offset for chaotic movement
            val offsetX    = (Random.nextFloat() - 0.5f) * 0.04f
            val offsetY    = (Random.nextFloat() - 0.5f) * 0.04f
            val zAmplitude = Random.nextFloat() * 0.15f

            // Calculate depth based on distance from center
            val centerDistX = (baseX - 0.5f) * 2f
            val centerDistY = (baseY - 0.5f) * 2f
            val baseDepth   = sqrt(centerDistX * centerDistX + centerDistY * centerDistY) / sqrt(2f)

            nodes.add(MeshNode(
                baseX      = baseX,
                baseY      = baseY,
                offsetX    = offsetX,
                offsetY    = offsetY,
                baseDepth  = baseDepth,
                zAmplitude = zAmplitude
            ))
        }
    }

    return nodes
}

private data class MeshNode(
    val baseX: Float,
    val baseY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val baseDepth: Float, // For parallax effect
    val zAmplitude: Float // For wave animation
)
