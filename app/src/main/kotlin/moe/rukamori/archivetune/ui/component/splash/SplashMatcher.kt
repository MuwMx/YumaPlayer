package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.min

object SplashMatcher {
    private data class PairDist(val member: Int, val slot: Int, val d: Float)

    fun zdGreedyCompile(members: List<Offset>, slots: List<Offset>): List<Int> {
        if (members.isEmpty() || slots.isEmpty()) return List(members.size) { -1 }

        val pairs = ArrayList<PairDist>(members.size * slots.size)
        for (i in members.indices) {
            val m = members[i]
            for (j in slots.indices) {
                val s = slots[j]
                val d = hypot(m.x - s.x, m.y - s.y)
                pairs.add(PairDist(i, j, d))
            }
        }
        pairs.sortBy { it.d }

        val usedSlots = BooleanArray(slots.size)
        val assignedMembers = BooleanArray(members.size)
        val assign = IntArray(members.size) { -1 }
        var assignedCount = 0
        val targetCount = min(members.size, slots.size)

        for (p in pairs) {
            if (!assignedMembers[p.member] && !usedSlots[p.slot]) {
                assignedMembers[p.member] = true
                usedSlots[p.slot] = true
                assign[p.member] = p.slot
                assignedCount++
                if (assignedCount == targetCount) break
            }
        }
        return assign.toList()
    }
}
