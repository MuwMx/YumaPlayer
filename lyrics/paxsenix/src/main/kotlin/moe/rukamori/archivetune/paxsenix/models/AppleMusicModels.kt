/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.paxsenix.models

import kotlinx.serialization.Serializable

@Serializable
data class AppleMusicLyricsResponse(
    val type: String? = null,
    val content: List<AppleMusicLine> = emptyList(),
)

@Serializable
data class AppleMusicLine(
    val timestamp: Long = 0,
    val text: List<AppleMusicWord> = emptyList(),
)

@Serializable
data class AppleMusicWord(
    val text: String,
    val timestamp: Long? = null,
)
