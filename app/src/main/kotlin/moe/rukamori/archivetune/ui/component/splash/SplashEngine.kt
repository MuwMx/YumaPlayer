package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class SplashEngine {
    val particles = ArrayList<SplashParticle>(MAX_MEMBERS)
    var shockwave: SplashShockwave? = null

    var currentPhase: SplashPhase = SplashPhase.Gather
    var phase: String
        get() = when (currentPhase) {
            SplashPhase.Dust -> "dust"
            SplashPhase.Gather -> "gather"
            SplashPhase.Ignite -> "ignite"
            SplashPhase.Burst -> "burst"
            SplashPhase.Idle -> "idle"
            SplashPhase.Transit -> "transit"
            SplashPhase.Success -> "success"
            SplashPhase.Error -> "error"
        }
        set(value) {
            currentPhase = when (value) {
                "dust" -> SplashPhase.Dust
                "gather" -> SplashPhase.Gather
                "ignite" -> SplashPhase.Ignite
                "burst" -> SplashPhase.Burst
                "transit" -> SplashPhase.Transit
                "success" -> SplashPhase.Success
                "error" -> SplashPhase.Error
                else -> SplashPhase.Idle
            }
        }

    var shape: String = SplashSlots.SHAPE_LOGO
    var width: Float = 0f
    var height: Float = 0f
    var density: Float = 1f

    var formStrength: Float = 0f
    var globalOpacity: Float = 0f
    var isShort: Boolean = false
    var postBurstFrames: Int = 0
    var pulseWave: Float = 0f
    var particleScale: Float = 1f
    var particleAlpha: Float = 1f
    var currentShapeData: SplashSlots.ShapeSlots =
        SplashSlots.ShapeSlots(emptyList(), listOf(0 until SplashSlots.SLOT_COUNT), emptyList(), android.graphics.Path())
    internal var slots: List<Offset> = emptyList()
    var phaseElapsedMs: Float = 0f
        private set

    var memberCount: Int = 0
        private set
    var totalMemberDist: Float = 0f
        private set
    var avgDist: Float = 0f
        private set

    fun init(w: Float, h: Float) {
        init(w, h, density)
    }

    fun init(w: Float, h: Float, d: Float) {
        spawnParticles(w, h, d)
    }

    fun rebuildSlots() {
        if (width <= 0f || height <= 0f) return
        currentShapeData = SplashSlots.build(shape, width, height, density)
        bindSlots()
    }

    fun setPhase(newPhase: SplashPhase) {
        if (currentPhase == SplashPhase.Burst && newPhase == SplashPhase.Burst) return
        currentPhase = newPhase
        phaseElapsedMs = 0f
        particleScale = 1f
        particleAlpha = if (newPhase == SplashPhase.Idle) 0f else 1f
        when (newPhase) {
            SplashPhase.Dust -> {
                formStrength = 0f
                globalOpacity = 0f
            }
            SplashPhase.Gather -> {
                formStrength = SplashConfig.Physics.FORM_GATHER
            }
            SplashPhase.Ignite -> {
                formStrength = 1f
            }
            SplashPhase.Burst -> {
                val c = SplashSlots.center(width, height)
                shock(c.x, c.y, SplashConfig.Burst.SHOCKWAVE_ALPHA_BURST)
                explode(c, power = SplashConfig.Burst.EXPLODE_POWER)
            }
            SplashPhase.Idle -> {
                formStrength = 0f
            }
            SplashPhase.Transit, SplashPhase.Success, SplashPhase.Error -> {
                formStrength = 1f
            }
        }
    }

    fun update(dt: Float, currentTimeMs: Long) {
        val step = (dt * 60f).coerceIn(0.5f, SplashPhysics.MAX_STEP)
        phaseElapsedMs += dt * 1000f

        if (postBurstFrames > 0) postBurstFrames--

        when (currentPhase) {
            SplashPhase.Dust -> {
                formStrength = 0f
                globalOpacity = min(1f, phaseElapsedMs / SplashConfig.Timings.DUST_DURATION_MS)
                if (phaseElapsedMs >= SplashConfig.Timings.DUST_DURATION_MS) {
                    setPhase(SplashPhase.Gather)
                }
            }
            SplashPhase.Gather -> {
                val gatherLimit = when {
                    shape == SplashSlots.SHAPE_CROSS -> SplashConfig.Timings.GATHER_CROSS_MS
                    shape == SplashSlots.SHAPE_LOGO || shape == SplashSlots.SHAPE_YUMA -> SplashConfig.Timings.GATHER_LOGO_MS
                    isShort -> SplashConfig.Timings.GATHER_SHORT_MS
                    else -> SplashConfig.Timings.GATHER_BOLT_MS
                }
                formStrength = min(1f, phaseElapsedMs / gatherLimit)
                var currentMemberCount = 0
                var currentTotalDist = 0f
                for (i in particles.indices) {
                    val p = particles[i]
                    if (p.isMember && p.slotIndex >= 0) {
                        currentMemberCount++
                        currentTotalDist += hypot(p.x - p.targetX, p.y - p.targetY)
                    }
                }
                memberCount = currentMemberCount
                totalMemberDist = currentTotalDist
                val currentAvgDist = if (currentMemberCount > 0) currentTotalDist / currentMemberCount else 0f
                avgDist = currentAvgDist
                val converged = currentMemberCount > 0 && currentAvgDist < SplashConfig.Settle.CONVERGE_DIST
                if (converged || phaseElapsedMs >= gatherLimit) {
                    setPhase(SplashPhase.Ignite)
                }
            }
            SplashPhase.Ignite -> {
                formStrength = 1f
                val igniteLimit = if (isShort) SplashConfig.Timings.IGNITE_SHORT_MS else SplashConfig.Timings.IGNITE_FULL_MS
                val igniteProgress = (phaseElapsedMs / igniteLimit).coerceIn(0f, 1f)
                val pinch = 1f - sin(igniteProgress * Math.PI.toFloat()) * SplashConfig.Effects.PINCH_FACTOR
                val c = SplashSlots.center(width, height)
                for (i in particles.indices) {
                    val p = particles[i]
                    if (p.isMember && p.slotIndex in slots.indices) {
                        val baseSlot = slots[p.slotIndex]
                        p.targetX = c.x + (baseSlot.x - c.x) * pinch
                        p.targetY = c.y + (baseSlot.y - c.y) * pinch
                    }
                }
                if (phaseElapsedMs >= igniteLimit) {
                    setPhase(SplashPhase.Burst)
                }
            }
            SplashPhase.Burst -> {
                val burstLimit = if (isShort) SplashConfig.Timings.BURST_SHORT_MS else SplashConfig.Timings.BURST_FULL_MS
                val progress = (phaseElapsedMs / burstLimit).coerceIn(0f, 1f)
                particleScale = 1f - progress * 0.35f
                particleAlpha = (1f - progress).coerceIn(0f, 1f)
                formStrength = 1f - progress
                if (phaseElapsedMs >= burstLimit) {
                    formStrength = 0f
                    setPhase(SplashPhase.Idle)
                }
            }
            SplashPhase.Transit -> {
                formStrength = 1f
                if (phaseElapsedMs >= SplashConfig.Timings.TRANSIT_MS) {
                    setPhase(SplashPhase.Gather)
                }
            }
            SplashPhase.Error -> {
                formStrength = 1f
                if (phaseElapsedMs >= SplashConfig.Timings.ERROR_HOLD_MS) {
                    setPhase(SplashPhase.Burst)
                }
            }
            SplashPhase.Idle, SplashPhase.Success -> {
            }
        }

        shockwave?.let { sw ->
            val newR = sw.radius + sw.maxRadius / SplashConfig.Wave.FRAMES_TO_CROSS * step
            if (newR >= sw.maxRadius) {
                shockwave = null
            } else {
                sw.radius = newR
            }
        }

        if (formStrength > 0.5f) {
            pulseWave = (pulseWave + dt * SplashConfig.Effects.PULSE_WAVE_SPEED) % 1.0f
        }

        SplashPhysics.updateParticles(this, dt, currentTimeMs, step)
    }

    fun triggerBurst() {
        setPhase(SplashPhase.Burst)
    }

    fun startGather(newShape: String = SplashSlots.SHAPE_LOGO) {
        shape = newShape
        for (i in particles.indices) {
            val p = particles[i]
            p.vx *= SplashConfig.Physics.DAMP_ON_REGATHER
            p.vy *= SplashConfig.Physics.DAMP_ON_REGATHER
        }
        rebuildSlots()
        setPhase(SplashPhase.Gather)
    }

    companion object {
        const val MAX_MEMBERS: Int = 64
        const val MEMBER_COUNT: Int = 24
    }
}
