package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.PathParser
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

object Fu {
    data class ColorScheme(val r: Int, val g: Int, val b: Int, val coreHex: Long) {
        val color: Color = Color(r, g, b)
        val coreColor: Color = Color(coreHex)
    }

    val ok: ColorScheme = ColorScheme(255, 236, 203, 0xFFFFFAF0)
    val fail: ColorScheme = ColorScheme(255, 120, 110, 0xFFFFDEDB)
}

class SplashEngine {
    val particles = ArrayList<SplashParticle>(MAX_MEMBERS + FLOATER_COUNT)
    var shockwave: SplashShockwave? = null
    var screenFlash: Float = 0f

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
    private var slots: List<Offset> = emptyList()
    var phaseElapsedMs: Float = 0f
        private set

    fun init(w: Float, h: Float) {
        init(w, h, density)
    }

    fun init(w: Float, h: Float, d: Float) {
        width = w
        height = h
        density = d
        particles.clear()

        rebuildSlots()

        val scatter = 20f
        val slotList = slots
        val center = SplashSlots.center(w, h)
        val activeMembers = SplashConfig.getSlotCount(shape).coerceIn(12, MAX_MEMBERS)
        val totalParticles = MAX_MEMBERS + FLOATER_COUNT

        for (i in 0 until totalParticles) {
            val isMember = i < activeMembers
            val target = if (slotList.isNotEmpty()) slotList[i % slotList.size] else center
            val depth = Random.nextFloat()
            val seed = Random.nextFloat()
            val isRare = Random.nextFloat() < SplashParticle.wD
            val baseRadius = SplashParticle.baseRadiusFor(depth, seed, isRare)
            val pulse = SplashParticle.pulseFor(seed)
            val breath = SplashParticle.breathFor(pulse)
            val lum = SplashParticle.lumFor(seed, isRare)
            val phaseAngle = Random.nextFloat() * (Math.PI.toFloat() * 2f)

            particles.add(
                SplashParticle(
                    x = target.x + (Random.nextFloat() - 0.5f) * scatter,
                    y = target.y + (Random.nextFloat() - 0.5f) * scatter,
                    vx = (Random.nextFloat() - 0.5f) * 0.5f,
                    vy = (Random.nextFloat() - 0.5f) * 0.5f,
                    baseRadius = baseRadius,
                    radius = baseRadius,
                    depth = depth,
                    seed = seed,
                    pulse = pulse,
                    phase = phaseAngle,
                    breath = breath,
                    lum = lum,
                    ring = i % 2,
                    isMember = isMember,
                    isRare = isRare
                )
            )
        }
        bindSlots()
        setPhase(SplashPhase.Gather)
    }

    fun rebuildSlots() {
        if (width <= 0f || height <= 0f) return
        currentShapeData = SplashSlots.build(shape, width, height, density)
        bindSlots()
    }

    fun setShapeFromPathData(pathData: String) {
        if (pathData.isBlank() || width <= 0f || height <= 0f) return
        val size = SplashSlots.boxSize(shape, width, height)
        val center = SplashSlots.center(width, height)
        val svgSlots = SplashSlots.fromSvgPath(pathData, SplashSlots.SLOT_COUNT, center.x, center.y, size)
        if (svgSlots.isEmpty()) return

        val path = try {
            PathParser.createPathFromPathData(pathData)
        } catch (_: Exception) {
            android.graphics.Path()
        }

        currentShapeData = SplashSlots.ShapeSlots(
            slots = svgSlots,
            loops = listOf(0 until SplashSlots.SLOT_COUNT),
            tips = emptyList(),
            outlinePath = path
        )

        for (i in particles.indices) {
            val p = particles[i]
            p.vx *= 0.65f
            p.vy *= 0.65f
        }
        bindSlots()
        setPhase(SplashPhase.Gather)
    }

    private fun bindSlots() {
        slots = currentShapeData.slots
        if (slots.isEmpty()) return

        val targetMemberCount = minOf(slots.size, MAX_MEMBERS)
        for (i in 0 until minOf(particles.size, MAX_MEMBERS)) {
            particles[i].isMember = (i < targetMemberCount)
            if (i >= targetMemberCount) {
                particles[i].slotIndex = -1
            }
        }

        val memberIndices = (0 until minOf(particles.size, targetMemberCount)).toList()
        if (memberIndices.isEmpty()) return

        val memberPositions = memberIndices.map { Offset(particles[it].x, particles[it].y) }
        val assignment = SplashSlots.zdGreedyCompile(memberPositions, slots)

        for (i in memberIndices.indices) {
            val pIdx = memberIndices[i]
            val slotIdx = assignment.getOrElse(i) { -1 }
            val target = if (slotIdx in slots.indices) {
                slots[slotIdx]
            } else {
                SplashSlots.center(width, height)
            }
            val p = particles[pIdx]
            p.targetX = target.x
            p.targetY = target.y
            p.slotIndex = slotIdx
        }
    }

    fun setPhase(newPhase: SplashPhase) {
        if (currentPhase == SplashPhase.Burst && newPhase == SplashPhase.Burst) return
        currentPhase = newPhase
        phaseElapsedMs = 0f
        particleScale = 1f
        particleAlpha = 1f
        when (newPhase) {
            SplashPhase.Dust -> {
                formStrength = 0f
                globalOpacity = 0f
            }
            SplashPhase.Gather -> {
                formStrength = if (shape == SplashSlots.SHAPE_CROSS) 0.85f else 0.15f
            }
            SplashPhase.Ignite -> {
                formStrength = 1f
                screenFlash = SplashConfig.Burst.SCREEN_FLASH_IGNITE
            }
            SplashPhase.Burst -> {
                screenFlash = SplashConfig.Burst.SCREEN_FLASH_BURST
                val c = SplashSlots.center(width, height)
                shock(c.x, c.y, SplashConfig.Burst.SHOCKWAVE_ALPHA_BURST)
                explode(c, power = SplashConfig.Burst.EXPLODE_POWER)
            }
            SplashPhase.Idle -> {
                formStrength = 0f
            }
            SplashPhase.Transit, SplashPhase.Success -> {
                formStrength = 1f
            }
            SplashPhase.Error -> {
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
                if (phaseElapsedMs >= gatherLimit) {
                    if (shape == SplashSlots.SHAPE_CROSS) {
                        setPhase(SplashPhase.Error)
                    } else {
                        setPhase(SplashPhase.Ignite)
                    }
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
                    if (!p.isMember || p.slotIndex !in slots.indices) continue
                    val baseSlot = slots[p.slotIndex]
                    p.targetX = c.x + (baseSlot.x - c.x) * pinch
                    p.targetY = c.y + (baseSlot.y - c.y) * pinch
                }
                if (phaseElapsedMs >= igniteLimit) {
                    setPhase(SplashPhase.Burst)
                }
            }
            SplashPhase.Burst -> {
                formStrength = max(0f, 1f - phaseElapsedMs / 200f)
                val burstLimit = if (isShort) SplashConfig.Timings.BURST_SHORT_MS else SplashConfig.Timings.BURST_FULL_MS
                val progress = (phaseElapsedMs / burstLimit).coerceIn(0f, 1f)
                particleScale = max(0.6f, 1f - progress * 0.4f)
                particleAlpha = (1f - progress * progress).coerceIn(0f, 1f)
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
            SplashPhase.Idle, SplashPhase.Success, SplashPhase.Error -> {
            }
        }

        if (screenFlash > 0f) {
            screenFlash = max(0f, screenFlash - SplashConfig.Burst.SCREEN_FLASH_DECAY * step)
        }

        // Стало: синхронизация с фазой Burst или динамическая скорость
        shockwave?.let { sw ->
            if (currentPhase == SplashPhase.Burst || currentPhase == SplashPhase.Idle) {
                val burstLimit = if (isShort) SplashConfig.Timings.BURST_SHORT_MS else SplashConfig.Timings.BURST_FULL_MS
                val progress = (phaseElapsedMs / burstLimit).coerceIn(0f, 1f)
                particleAlpha = (1f - progress * progress).coerceIn(0f, 1f)

                if (progress >= 1f) {
                    shockwave = null
                } else {
                    val e = progress * progress * (3f - 2f * progress)
                    sw.radius = sw.maxRadius * e
                }
            } else {
                // Фоновый долет, если фаза сменилась чуть раньше
                val speed = (sw.maxRadius / 20f) * step
                val newR = sw.radius + speed
                if (newR >= sw.maxRadius) {
                    shockwave = null
                } else {
                    sw.radius = newR
                }
            }
        }

        if (formStrength > 0.5f) {
            pulseWave = (pulseWave + dt * SplashConfig.Effects.PULSE_WAVE_SPEED) % 1.0f
        }

        val isTransit = currentPhase == SplashPhase.Transit
        val isForming = currentPhase == SplashPhase.Gather || currentPhase == SplashPhase.Ignite
        val isProcessing = currentPhase == SplashPhase.Error || currentPhase == SplashPhase.Transit
        val isBursting = currentPhase == SplashPhase.Burst || postBurstFrames > 0
        val timeSec = currentTimeMs / 1000f
        val center = SplashSlots.center(width, height)

        val dampMember = SplashConfig.Physics.DAMPING_FORMING.pow(step)
        val dampFloater = SplashConfig.Physics.DAMPING_FREE.pow(step)

        for (i in particles.indices) {
            val p = particles[i]
            SplashPhysics.applyFormationForces(
                p = p,
                dt = dt,
                isTransit = isTransit,
                isForming = isForming,
                formStrength = formStrength,
                isBursting = isBursting,
                isProcessing = isProcessing,
                time = timeSec,
                centerX = center.x,
                centerY = center.y,
                dampMember = dampMember,
                dampFloater = dampFloater
            )

            p.radius = p.baseRadius * (1f + sin(timeSec * p.pulse + p.phase) * p.breath) * particleScale

            if ((!isForming && !isProcessing) || !p.isMember) {
                val pad = 40f
                if (p.x < -pad) p.x = width + pad
                if (p.x > width + pad) p.x = -pad
                if (p.y < -pad) p.y = height + pad
                if (p.y > height + pad) p.y = -pad
            }
        }
    }

    fun explode(center: Offset, power: Float = SplashConfig.Burst.EXPLODE_POWER, memberBoost: Boolean = true) {
        postBurstFrames = SplashConfig.Burst.POST_BURST_FRAMES
        for (i in particles.indices) {
            val p = particles[i]
            val dx = p.x - center.x
            val dy = p.y - center.y
            val dist = hypot(dx, dy) + 0.1f
            val mult = if (memberBoost && p.isMember) SplashConfig.Burst.MEMBER_BOOST else SplashConfig.Burst.FLOATER_BOOST
            val m = power * mult * (0.5f + Random.nextFloat() * 1.1f)
            p.vx = (dx / dist) * m + (Random.nextFloat() - 0.5f)
            p.vy = (dy / dist) * m + (Random.nextFloat() - 0.5f)
        }
    }

    fun shock(cx: Float, cy: Float, alpha: Float = 1f) {
        val maxR = hypot(width, height) * SplashConfig.Burst.SHOCKWAVE_RADIUS_FACTOR
        shockwave = SplashShockwave(
            x = cx,
            y = cy,
            maxRadius = maxR,
            radius = 0f,
            alpha = alpha
        )
    }

    fun triggerBurst() {
        setPhase(SplashPhase.Burst)
    }

    fun startGather(newShape: String = SplashSlots.SHAPE_LOGO) {
        shape = newShape
        for (i in particles.indices) {
            val p = particles[i]
            p.vx *= 0.5f
            p.vy *= 0.5f
        }
        rebuildSlots()
        setPhase(SplashPhase.Gather)
    }

    companion object {
        const val MAX_MEMBERS: Int = 64
        const val MEMBER_COUNT: Int = 24
        const val FLOATER_COUNT: Int = 28
    }
}
