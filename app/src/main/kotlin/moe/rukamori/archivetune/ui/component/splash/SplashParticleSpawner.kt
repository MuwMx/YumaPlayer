package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
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
        val tangentSign = if (Random.nextBoolean()) 1f else -1f
        val swirlSpeed = (SplashConfig.Spawn.SWIRL_BASE + Random.nextFloat() * SplashConfig.Spawn.SWIRL_VAR) * tangentSign
        val startVx = cosA * speed - sinA * swirlSpeed
        val startVy = sinA * speed + cosA * swirlSpeed

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

