package moe.rukamori.archivetune.ui.component.splash

import kotlin.math.abs
import kotlin.math.sin

class SplashParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var ax: Float = 0f,
    var ay: Float = 0f,
    var targetX: Float = 0f,
    var targetY: Float = 0f,
    var radius: Float = 3f,
    var baseRadius: Float = 3f,
    var depth: Float = 0f,
    var seed: Float = 0f,
    var pulse: Float = 1f,
    var phase: Float = 0f,
    var breath: Float = 0.25f,
    var lum: Float = 1f,
    var ring: Int = 0,
    var isMember: Boolean = false,
    var isRare: Boolean = false,
    var slotIndex: Int = -1,
    var isLocked: Boolean = false
) {
    fun reset(px: Float, py: Float, r: Float) {
        x = px
        y = py
        vx = 0f
        vy = 0f
        ax = 0f
        ay = 0f
        radius = r
        baseRadius = r
        isLocked = false
    }

    companion object {
        val wD: Float get() = SplashConfig.Physics.wD
        val gD: Float get() = SplashConfig.Physics.gD
        val vD: Float get() = SplashConfig.Physics.vD
        val bD: Float get() = SplashConfig.Physics.bD
        val xD: Float get() = SplashConfig.Physics.xD
        val SD: Float get() = SplashConfig.Physics.SD
        val TD: Float get() = SplashConfig.Physics.TD
        val ED: Float get() = SplashConfig.Physics.ED
        val MD: Float get() = SplashConfig.Physics.MD

        fun baseRadiusFor(depth: Float, seed: Float = 0f, isRare: Boolean = false): Float {
            val m = abs(sin(seed * 311.7f + 74.7f) * 43758f) % 1f
            return ((0.75f + depth * 2.9f + m * 1.5f) * (if (isRare) 1.5f else 1f)) * 2.25f
        }

        fun pulseFor(seed: Float): Float {
            val m = abs(sin(seed * 311.7f + 74.7f) * 43758f) % 1f
            return 0.35f + m * 0.9f
        }

        fun breathFor(p: Float): Float = 0.15f + p * 0.5f

        fun lumFor(seed: Float, isRare: Boolean = false): Float {
            val m = abs(sin(seed * 311.7f + 74.7f) * 43758f) % 1f
            return (0.55f + m * 0.55f) * (if (isRare) 1.55f else 1f)
        }
    }
}
