package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.PathParser
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun SplashEngine.spawnParticles(w: Float, h: Float, d: Float) {
    width = w
    height = h
    density = d
    particles.clear()

    rebuildSlots()

    val center = SplashSlots.center(w, h)
    val activeMembers = SplashConfig.getSlotCount(shape).coerceIn(12, SplashEngine.MAX_MEMBERS)

    for (i in 0 until activeMembers) {
        val r = SplashConfig.Spawn.RING_INNER + Random.nextFloat() * SplashConfig.Spawn.RING_WIDTH
        val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
        val cosA = cos(angle)
        val sinA = sin(angle)
        val startX = center.x + cosA * r
        val startY = center.y + sinA * r

        val speed = SplashConfig.Spawn.SPEED_BASE + Random.nextFloat() * SplashConfig.Spawn.SPEED_VAR
        val startVx = cosA * speed
        val startVy = sinA * speed

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
                x = startX,
                y = startY,
                vx = startVx,
                vy = startVy,
                baseRadius = baseRadius,
                radius = baseRadius,
                depth = depth,
                seed = seed,
                pulse = pulse,
                phase = phaseAngle,
                breath = breath,
                lum = lum,
                ring = i % 2,
                isMember = true,
                isRare = isRare
            )
        )
    }
    bindSlots()
    setPhase(SplashPhase.Gather)
}

fun SplashEngine.bindSlots() {
    val currentSlots = currentShapeData.slots
    slots = currentSlots
    if (currentSlots.isEmpty()) return

    val targetMemberCount = minOf(currentSlots.size, SplashEngine.MAX_MEMBERS)
    for (i in 0 until minOf(particles.size, SplashEngine.MAX_MEMBERS)) {
        particles[i].isMember = (i < targetMemberCount)
        if (i >= targetMemberCount) {
            particles[i].slotIndex = -1
        }
    }

    val memberIndices = (0 until minOf(particles.size, targetMemberCount)).toList()
    if (memberIndices.isEmpty()) return

    val memberPositions = memberIndices.map { Offset(particles[it].x, particles[it].y) }
    val assignment = SplashSlots.zdGreedyCompile(memberPositions, currentSlots)

    for (i in memberIndices.indices) {
        val pIdx = memberIndices[i]
        val slotIdx = assignment.getOrElse(i) { -1 }
        val target = if (slotIdx in currentSlots.indices) {
            currentSlots[slotIdx]
        } else {
            SplashSlots.center(width, height)
        }
        val p = particles[pIdx]
        p.targetX = target.x
        p.targetY = target.y
        p.slotIndex = slotIdx
    }
}

fun SplashEngine.setShapeFromPathData(pathData: String) {
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
        p.vx *= SplashConfig.Physics.DAMP_ON_RESHAPE
        p.vy *= SplashConfig.Physics.DAMP_ON_RESHAPE
    }
    bindSlots()
    setPhase(SplashPhase.Gather)
}
