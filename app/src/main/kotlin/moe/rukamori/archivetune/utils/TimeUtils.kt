package moe.rukamori.archivetune.utils

import java.util.Locale

object TimeUtils {
    fun formatMs(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000).toInt()
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
