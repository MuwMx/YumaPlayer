package moe.rukamori.archivetune.home.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun ParticlesBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
    isVisible: Boolean = true,
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current

    val disableAnimations = LocalAnimationsDisabled.current

    val animatedPrimaryColor by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "particlesPrimaryColor",
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "particlesSecondaryColor",
    )
    val animatedTertiaryColor by animateColorAsState(
        targetValue = tertiaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "particlesTertiaryColor",
    )

    val alphaScale = brightness.coerceIn(0.1f, 2f)

    val parallaxState = rememberParallaxState(
        enableParallax = parallaxEnabled && !disableAnimations,
        sensitivity = parallaxSensitivity,
        context = context
    )

    // Particle state - mutable so physics loop can update positions each frame
    val particles = remember {
        mutableStateListOf<Particle>().apply {
            repeat(65) {
                // Create 3 particle size groups for more natural depth
                val baseRadius = when (Random.nextFloat()) {
                    in 0f..0.6f -> 2.5f + Random.nextFloat() * 3f // small particles
                    in 0.6f..0.9f -> 4f + Random.nextFloat() * 4f // medium particles
                    else -> 7f + Random.nextFloat() * 5f // large particles
                }

                add(
                    Particle(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        vx = (Random.nextFloat() - 0.5f) * 0.00018f,
                        vy = (Random.nextFloat() - 0.5f) * 0.00018f,
                        baseRadius = baseRadius,
                        colorIndex = it % 3
                    )
                )
            }
        }
    }

    val speedMultiplier = if (disableAnimations) 0f else 1f
    // targetSpeedState updated via SideEffect so the physics loop stays reactive
    val targetSpeedState = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeedState.floatValue = speedMultiplier }

    val isVisibleState by rememberUpdatedState(isVisible)

    // Physics loop - runs every display frame independently of Compose animation clock
    LaunchedEffect(Unit) {
        var lastFrameMs = -1L
        var currentSpeed = targetSpeedState.floatValue
        while (true) {
            if (!isVisibleState) {
                snapshotFlow { isVisibleState }.first { it }
                lastFrameMs = -1L
            }

            withInfiniteAnimationFrameMillis { frameMs ->
                if (lastFrameMs == -1L) lastFrameMs = frameMs
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs

                // Lerp speed for smooth transitions
                currentSpeed += (targetSpeedState.floatValue - currentSpeed) * (delta / 1000f) * 2.5f
                val speedScale = currentSpeed * (delta / 16.67f)

                particles.forEachIndexed { index, p ->
                    var nx  = p.x  + p.vx * speedScale
                    var ny  = p.y  + p.vy * speedScale
                    var nvx = p.vx
                    var nvy = p.vy

                    // Soft edge bounce - reverse velocity and nudge back inside
                    if (nx < 0.02f) { nvx = abs(nvx); nx = 0.02f }
                    if (nx > 0.98f) { nvx = -abs(nvx); nx = 0.98f }
                    if (ny < 0.02f) { nvy = abs(nvy); ny = 0.02f }
                    if (ny > 0.98f) { nvy = -abs(nvy); ny = 0.98f }

                    // Tiny random drift to avoid completely straight paths
                    nvx += (Random.nextFloat() - 0.5f) * 0.000004f
                    nvy += (Random.nextFloat() - 0.5f) * 0.000004f

                    // Speed cap so particles never rocket across the screen
                    val speed = sqrt(nvx * nvx + nvy * nvy)
                    val maxSpeed = 0.00025f
                    if (speed > maxSpeed) {
                        nvx = nvx / speed * maxSpeed
                        nvy = nvy / speed * maxSpeed
                    }

                    particles[index] = p.copy(x = nx, y = ny, vx = nvx, vy = nvy)
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value

        val unit = minOf(size.width, size.height)
        val connectDist   = size.width * 0.22f
        val connectDistSq = connectDist * connectDist

        val colors = arrayOf(animatedPrimaryColor, animatedSecondaryColor, animatedTertiaryColor)
        val parallaxStrength = unit * 0.035f

        for (i in particles.indices) {
            val p1 = particles[i]
            val x1 = p1.x * size.width  + tiltX * parallaxStrength
            val y1 = p1.y * size.height + tiltY * parallaxStrength

            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                val x2 = p2.x * size.width  + tiltX * parallaxStrength
                val y2 = p2.y * size.height + tiltY * parallaxStrength

                val dx     = x1 - x2
                val dy     = y1 - y2
                val distSq = dx * dx + dy * dy
                if (distSq < connectDistSq) {
                    val proximity = 1f - sqrt(distSq) / connectDist
                    val alpha = proximity * proximity * 0.10f
                    val color = colors[(p1.colorIndex + p2.colorIndex) % 3]

                    drawLine(
                        color       = color.copy(alpha = alpha.coerceIn(0f, 0.18f) * alphaScale),
                        start       = Offset(x1, y1),
                        end         = Offset(x2, y2),
                        strokeWidth = 1.2f
                    )
                }
            }
        }

        // Draw particles
        particles.forEach { p ->
            val pos   = Offset(
                p.x * size.width  + tiltX * parallaxStrength,
                p.y * size.height + tiltY * parallaxStrength
            )
            val color = colors[p.colorIndex]
            val alpha  = 0.55f
            val radius = p.baseRadius * unit / 720f

            drawCircle(color = color.copy(alpha = alpha * 0.2f * alphaScale), radius = radius * 2f, center = pos)
            drawCircle(color = color.copy(alpha = alpha * alphaScale),        radius = radius,      center = pos)
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val baseRadius: Float,
    val colorIndex: Int
)
