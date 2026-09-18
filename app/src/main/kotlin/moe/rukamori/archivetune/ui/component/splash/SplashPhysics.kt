package moe.rukamori.archivetune.ui.component.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object SplashPhysics {
    val gD: Float get() = SplashConfig.Physics.gD
    val vD: Float get() = SplashConfig.Physics.vD
    val bD: Float get() = SplashConfig.Physics.bD
    val xD: Float get() = SplashConfig.Physics.xD
    val SD: Float get() = SplashConfig.Physics.SD
    val TD: Float get() = SplashConfig.Physics.TD
    val ED: Float get() = SplashConfig.Physics.ED
    val MD: Float get() = SplashConfig.Physics.MD
    val MAX_STEP: Float get() = SplashConfig.Physics.MAX_STEP

    fun noiseField(x: Float, y: Float, seed: Float): Float {
        val phase = seed * PI.toFloat() * 2f
        return sin(x * 1.37f + y * 0.71f + phase) * 0.5f +
                sin(x * 2.13f - y * 1.17f + phase * 1.83f) * 0.3f +
                sin(x * 0.61f + y * 1.93f + phase * 0.47f) * 0.2f
    }

    fun formationK(isTransit: Boolean, formStrength: Float): Float =
        (if (isTransit) SplashConfig.Physics.SPRING_K_TRANSIT else SplashConfig.Physics.SPRING_K_NORMAL) * formStrength

    fun damping(isFormingOrProcessing: Boolean, isBursting: Boolean, step: Float): Float =
        when {
            isBursting -> SplashConfig.Physics.DAMPING_BURST.pow(step)
            isFormingOrProcessing -> SplashConfig.Physics.DAMPING_FORMING.pow(step)
            else -> SplashConfig.Physics.DAMPING_FREE.pow(step)
        }

    fun driftMagnitude(depth: Float): Float =
        bD * (0.5f + depth * 0.5f)

    fun speedClamp(
        isBursting: Boolean,
        isTransit: Boolean,
        isForming: Boolean,
        isProcessing: Boolean = false,
        depth: Float = 0f
    ): Float = when {
        isBursting -> SplashConfig.Physics.SPEED_CLAMP_BURST
        isTransit -> SplashConfig.Physics.SPEED_CLAMP_TRANSIT
        isForming -> SplashConfig.Physics.SPEED_CLAMP_FORMING
        isProcessing -> SplashConfig.Physics.SPEED_CLAMP_PROCESSING
        else -> SplashConfig.Physics.SPEED_CLAMP_FREE
    }

    fun applyFormationForces(
        p: SplashParticle,
        dt: Float,
        isTransit: Boolean,
        isForming: Boolean,
        formStrength: Float,
        isBursting: Boolean = false,
        isProcessing: Boolean = false,
        time: Float = 0f,
        centerX: Float = 0f,
        centerY: Float = 0f,
        dampMember: Float = -1f,
        dampFloater: Float = -1f
    ): SplashParticle {
        val step = (dt * 60f).coerceIn(0.5f, MAX_STEP)
        val isMember = p.isMember || (p.targetX != 0f || p.targetY != 0f)

        if (!isBursting && isMember && (isForming || isProcessing || formStrength > 0f)) {
            var tx = p.targetX
            var ty = p.targetY
            if (isProcessing && (tx != 0f || ty != 0f)) {
                val c = sin(time * 0.55f) * 5f
                val f = sin(time * 0.42f) * 0.042f
                val d = 1f + sin(time * 0.72f) * 0.045f
                val cosF = cos(f)
                val sinF = sin(f)
                val dx = (tx - centerX) * d
                val dy = (ty - centerY) * d
                tx = centerX + dx * cosF - dy * sinF
                ty = centerY + dx * sinF + dy * cosF + c
            }

            val dx = tx - p.x
            val dy = ty - p.y
            val distSq = dx * dx + dy * dy

            if (distSq < 0.25f && !isBursting) {
                p.x = tx
                p.y = ty
                p.vx = 0f
                p.vy = 0f
                p.ax = 0f
                p.ay = 0f
                p.isLocked = true
                return p
            }

            p.isLocked = false
            val springForce = formationK(isTransit, formStrength)
            val friction = if (dampMember >= 0f) dampMember else damping(isFormingOrProcessing = true, isBursting = isBursting, step = step)

            p.vx = (p.vx + dx * springForce) * friction
            p.vy = (p.vy + dy * springForce) * friction

            val m = speedClamp(isBursting, isTransit, isForming, isProcessing, p.depth)
            val speedSq = p.vx * p.vx + p.vy * p.vy
            if (speedSq > m * m && speedSq > 0f) {
                val scale = m / sqrt(speedSq)
                p.vx *= scale
                p.vy *= scale
            }

            p.x += p.vx * step
            p.y += p.vy * step
        } else {
            p.isLocked = false
            val angle = noiseField((p.x + p.y * 0.7f) * vD, time * gD, p.seed) * PI.toFloat()
            val drift = driftMagnitude(p.depth)
            val noiseAx = cos(angle) * drift
            val noiseAy = sin(angle) * drift

            val damp = if (isBursting) SplashConfig.Physics.DAMPING_BURST.pow(step) else (if (dampFloater >= 0f) dampFloater else damping(isFormingOrProcessing = false, isBursting = false, step = step))
            p.vx = (p.vx + noiseAx * step) * damp
            p.vy = (p.vy + noiseAy * step) * damp

            val m = speedClamp(isBursting, isTransit, isForming, isProcessing, p.depth)
            val speedSq = p.vx * p.vx + p.vy * p.vy
            if (speedSq > m * m && speedSq > 0f) {
                val scale = m / sqrt(speedSq)
                p.vx *= scale
                p.vy *= scale
            }

            p.x += p.vx * step
            p.y += p.vy * step
        }
        return p
    }
}
