package moe.rukamori.archivetune.home.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun SpaceBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
    isVisible: Boolean = true,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val starColor = if (isDarkTheme) Color.White else Color(0xFF1A2530)
    
    val disableAnimations = LocalAnimationsDisabled.current
    val context = LocalContext.current
    val parallaxState = rememberParallaxState(
        enableParallax = parallaxEnabled && !disableAnimations,
        sensitivity = parallaxSensitivity,
        context = context
    )

    val stars = remember(isDarkTheme) {
        mutableStateListOf<StarData>().apply { addAll(generateStarPool()) }
    }

    var baseProgress by remember { mutableFloatStateOf(0f) }

    val speedMultiplier = if (disableAnimations) 0f else 1f
    val targetSpeedState = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeedState.floatValue = speedMultiplier }

    val isVisibleState by rememberUpdatedState(isVisible)

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

                val speedBoost = 3f
                currentSpeed += (1f + (targetSpeedState.floatValue - 1f) * speedBoost - currentSpeed) * (delta / 1000f) * 5.0f

                baseProgress += 0.0025f * currentSpeed * (delta / 16.67f)

                stars.forEachIndexed { index, star ->
                    val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
                    if (adjustedProgress !in 0.01f..0.98f) {
                        if (star.lastRegen != baseProgress.toInt()) {
                            var newX: Float; var newY: Float; var newDistance: Float
                            do {
                                val newAngle = Random.nextFloat() * 360f
                                newDistance = sqrt(Random.nextFloat()) * 1.5f
                                val newAngleRad = newAngle * (Math.PI / 180f).toFloat()
                                newX = cos(newAngleRad) * newDistance
                                newY = sin(newAngleRad) * newDistance
                            } while (newDistance < 0.15f)
                            stars[index] = star.copy(x = newX, y = newY, lastRegen = baseProgress.toInt())
                        }
                    }
                }
            }
        }
    }

    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    val meteorProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(40000, 60000))
            if (!isVisibleState) {
                snapshotFlow { isVisibleState }.first { it }
            }
            val direction = Random.nextInt(2)
            val angle = when (direction) {
                0    -> 130f + Random.nextFloat() * 20f
                else -> 30f  + Random.nextFloat() * 20f
            }
            meteor = MeteorState(
                startX    = Random.nextFloat(),
                startY    = Random.nextFloat() * 0.3f,
                angle     = angle,
                length    = 200f + Random.nextFloat() * 150f,
                depth     = 0.4f + Random.nextFloat() * 0.6f,
                thickness = 4f
            )
            meteorProgress.snapTo(0f)
            meteorProgress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
            meteor = null
        }
    }

    val alphaScale = brightness.coerceIn(0.1f, 2f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width   = size.width
        val height  = size.height
        val centerX = width  / 2f
        val centerY = height / 2f
        val tiltX   = parallaxState.tiltX.value
        val tiltY   = parallaxState.tiltY.value
        val unit    = minOf(width, height)

        stars.forEach { star ->
            val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
            val z = (1f - adjustedProgress).coerceAtLeast(0.01f)
            if (z !in 0.05f..1.5f) return@forEach

            val perspectiveFactor = 1f / z
            val baseX = star.x * width  * 0.5f
            val baseY = star.y * height * 0.5f

            val projectedX = baseX * perspectiveFactor
            val projectedY = baseY * perspectiveFactor

            val parallaxStrength = star.depth * unit * 0.2f
            val finalX = centerX + projectedX + tiltX * parallaxStrength
            val finalY = centerY + projectedY + tiltY * parallaxStrength

            if (finalX < -150 || finalX > width + 150 || finalY < -150 || finalY > height + 150) return@forEach

            val sizeFactor  = perspectiveFactor * 0.45f
            val finalSize   = star.size * sizeFactor

            val fadeIn    = if (z > 1.3f) ((1.5f - z) / 0.2f).coerceIn(0f, 1f) else 1f
            val fadeOut   = if (z < 0.15f) (z / 0.15f).coerceIn(0f, 1f) else 1f
            val distAlpha = when {
                z > 0.6f -> ((1f - z) / 0.4f).coerceIn(0f, 1f)
                z < 0.3f -> (z / 0.3f).coerceIn(0f, 1f)
                else     -> 1f
            }
            val baseAlpha = (star.baseAlpha * distAlpha * fadeIn * fadeOut).coerceIn(0f, 1f) * alphaScale

            drawCircle(color = starColor, radius = finalSize * 1.8f, center = Offset(finalX, finalY), alpha = (baseAlpha * 0.2f).coerceIn(0f, 1f))
            drawCircle(color = starColor, radius = finalSize * 1.1f, center = Offset(finalX, finalY), alpha = baseAlpha.coerceIn(0f, 1f))
        }

        meteor?.let { m ->
            val p        = meteorProgress.value
            val angleRad = m.angle * (Math.PI / 180f).toFloat()
            val parallaxStrength = m.depth * unit * 0.16f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength
            val travelDist = width * 2f * p
            val cosA = cos(angleRad); val sinA = sin(angleRad)
            val curX = m.startX * width  + travelDist * cosA + parallaxX
            val curY = m.startY * height + travelDist * sinA + parallaxY
            val tailX = curX - m.length * cosA
            val tailY = curY - m.length * sinA

            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = (0.3f * alphaScale).coerceIn(0f, 1f)),
                    0.6f to starColor.copy(alpha = (0.15f * alphaScale).coerceIn(0f, 1f)),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY), end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY), end = Offset(tailX, tailY),
                strokeWidth = m.thickness * 4f, cap = StrokeCap.Round
            )
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = (0.95f * alphaScale).coerceIn(0f, 1f)),
                    0.5f to starColor.copy(alpha = (0.6f * alphaScale).coerceIn(0f, 1f)),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY), end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY), end = Offset(tailX, tailY),
                strokeWidth = m.thickness, cap = StrokeCap.Round
            )
        }
    }
}

private fun generateStarPool(): List<StarData> = List(300) { index ->
    val depthLayer = index / 300f
    var x: Float; var y: Float; var distance: Float
    do {
        val angle = (index * 137.5f) % 360f
        distance = sqrt(Random.nextFloat()) * 1.5f
        val angleRad = angle * (Math.PI / 180f).toFloat()
        x = cos(angleRad) * distance
        y = sin(angleRad) * distance
    } while (distance < 0.15f)

    StarData(
        x             = x,
        y             = y,
        size          = 2f + Random.nextFloat() * 3.5f,
        baseAlpha     = 0.6f + Random.nextFloat() * 0.4f,
        depth         = depthLayer,
        speed         = 0.3f + depthLayer * 2.2f,
        initialOffset = Random.nextFloat(),
        lastRegen     = -1
    )
}

private data class StarData(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float,
    val depth: Float,
    val speed: Float,
    val initialOffset: Float,
    val lastRegen: Int
)

private data class MeteorState(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val length: Float,
    val depth: Float,
    val thickness: Float
)
