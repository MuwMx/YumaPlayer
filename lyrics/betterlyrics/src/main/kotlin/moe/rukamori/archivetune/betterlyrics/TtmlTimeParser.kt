/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

import org.w3c.dom.Element
import kotlin.math.roundToLong

internal data class TimingContext(
    val tickRate: Double,
    val frameRate: Double,
)

internal object TtmlTimeParser {
    const val MILLIS_PER_SECOND = 1000.0
    const val DEFAULT_FINAL_LINE_DURATION_MS = 4000L

    private val OFFSET_TIME_REGEX = Regex("""^([0-9]+(?:\.[0-9]+)?)(ms|h|m|s|f|t)$""", RegexOption.IGNORE_CASE)

    fun readTimingContext(root: Element): TimingContext {
        val baseFrameRate = root.attribute("frameRate")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 30.0
        val multiplier =
            root.attribute("frameRateMultiplier")
                ?.split(WHITESPACE_REGEX)
                ?.mapNotNull(String::toDoubleOrNull)
                ?.takeIf { it.size == 2 && it[0] > 0.0 && it[1] > 0.0 }
                ?.let { it[0] / it[1] }
                ?: 1.0
        val frameRate = (baseFrameRate * multiplier).coerceAtLeast(1.0)
        val tickRate = root.attribute("tickRate")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: frameRate
        return TimingContext(tickRate = tickRate, frameRate = frameRate)
    }

    fun parseTime(
        rawValue: String,
        context: TimingContext,
    ): Long? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null

        OFFSET_TIME_REGEX.matchEntire(value)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
            val seconds =
                when (match.groupValues[2].lowercase()) {
                    "h" -> amount * 3600.0
                    "m" -> amount * 60.0
                    "s" -> amount
                    "ms" -> amount / 1000.0
                    "f" -> amount / context.frameRate
                    "t" -> amount / context.tickRate
                    else -> return null
                }
            return seconds.toMillisecondsOrNull()
        }

        val parts = value.replace(';', ':').split(':')
        if (parts.size !in 1..4) return null
        val numeric = parts.map { it.toDoubleOrNull()?.takeIf { number -> number.isFinite() && number >= 0.0 } ?: return null }
        if (numeric.size >= 2 && numeric.drop(1).dropLast(if (numeric.size == 4) 1 else 0).any { it >= 60.0 }) return null
        val seconds =
            when (numeric.size) {
                1 -> numeric[0]
                2 -> numeric[0] * 60.0 + numeric[1]
                3 -> numeric[0] * 3600.0 + numeric[1] * 60.0 + numeric[2]
                4 -> numeric[0] * 3600.0 + numeric[1] * 60.0 + numeric[2] + numeric[3] / context.frameRate
                else -> return null
            }
        return seconds.toMillisecondsOrNull()
    }

    private fun Double.toMillisecondsOrNull(): Long? {
        val milliseconds = this * 1000.0
        if (!milliseconds.isFinite() || milliseconds < 0.0 || milliseconds > Long.MAX_VALUE.toDouble()) return null
        return milliseconds.roundToLong()
    }
}

internal fun Long.saturatedPlus(value: Long): Long =
    if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
