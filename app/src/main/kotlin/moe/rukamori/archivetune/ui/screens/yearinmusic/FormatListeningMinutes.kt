/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import java.text.NumberFormat

internal fun formatListeningMinutes(duration: Long): String {
    val minutes = (duration / 60_000L).coerceAtLeast(if (duration > 0L) 1L else 0L)
    return NumberFormat.getIntegerInstance().format(minutes)
}
