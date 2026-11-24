package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.random.Random

fun SplashEngine.explode(
    center: Offset,
    power: Float = SplashConfig.Burst.EXPLODE_POWER,
    memberBoost: Boolean = true
) {
    postBurstFrames = SplashConfig.Burst.POST_BURST_FRAMES
    val box = SplashSlots.boxSize(shape, width, height, density).coerceAtLeast(1f)
    for (i in particles.indices) {
        val p = particles[i]
        val dx = center.x - p.x
        val dy = center.y - p.y
        val dist = hypot(dx, dy) + 0.1f
        val nx = dx / dist
        val ny = dy / dist
        val centerRatio = (dist / (box * 0.5f)).coerceIn(0f, 1f)
        val dir = if (centerRatio < 0.28f) -0.35f else 1f
        val mult = if (memberBoost && p.isMember) 0.35f else 0.2f
        val m = power * mult * (0.6f + Random.nextFloat() * 0.6f) * (0.4f + centerRatio * 0.6f)
        val swirl = (Random.nextFloat() - 0.5f) * 1.8f
        p.vx = nx * m * dir - ny * swirl
        p.vy = ny * m * dir + nx * swirl
    }
}

fun SplashEngine.shock(cx: Float, cy: Float, alpha: Float = 1f) {
    val maxR = hypot(width, height) * SplashConfig.Burst.SHOCKWAVE_RADIUS_FACTOR
    shockwave = SplashShockwave(
        x = cx,
        y = cy,
        maxRadius = maxR,
        radius = 0f,
        alpha = alpha
    )
}
