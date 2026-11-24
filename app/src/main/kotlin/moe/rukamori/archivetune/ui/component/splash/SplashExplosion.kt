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
    for (i in particles.indices) {
        val p = particles[i]
        val dx = p.x - center.x
        val dy = p.y - center.y
        val dist = hypot(dx, dy) + 0.1f
        val nx = dx / dist
        val ny = dy / dist
        val mult = if (memberBoost && p.isMember) SplashConfig.Burst.MEMBER_BOOST else SplashConfig.Burst.FLOATER_BOOST
        val depthScale = 0.8f + p.depth * 0.45f
        val m = power * mult * depthScale * (0.7f + Random.nextFloat() * 0.7f)
        val swirl = (Random.nextFloat() - 0.5f) * m * 0.12f
        p.vx = nx * m - ny * swirl
        p.vy = ny * m + nx * swirl
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
