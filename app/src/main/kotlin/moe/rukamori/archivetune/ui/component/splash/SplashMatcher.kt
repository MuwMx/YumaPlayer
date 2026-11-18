package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

object SplashMatcher {
    fun zdGreedyCompile(members: List<Offset>, slots: List<Offset>): List<Int> {
        val memberCount = members.size
        val slotCount = slots.size
        if (memberCount == 0 || slotCount == 0) return List(memberCount) { -1 }

        val distances = FloatArray(memberCount * slotCount)
        for (i in 0 until memberCount) {
            val m = members[i]
            val row = i * slotCount
            for (j in 0 until slotCount) {
                val dx = m.x - slots[j].x
                val dy = m.y - slots[j].y
                distances[row + j] = dx * dx + dy * dy
            }
        }

        val usedSlots = BooleanArray(slotCount)
        val assign = IntArray(memberCount) { -1 }
        val targetCount = min(memberCount, slotCount)

        repeat(targetCount) {
            var bestIndex = -1
            var bestDist = Float.MAX_VALUE
            for (i in 0 until memberCount) {
                if (assign[i] != -1) continue
                val row = i * slotCount
                for (j in 0 until slotCount) {
                    if (usedSlots[j]) continue
                    val d = distances[row + j]
                    if (d < bestDist) {
                        bestDist = d
                        bestIndex = row + j
                    }
                }
            }
            if (bestIndex < 0) return@repeat
            assign[bestIndex / slotCount] = bestIndex % slotCount
            usedSlots[bestIndex % slotCount] = true
        }
        return assign.toList()
    }
}
