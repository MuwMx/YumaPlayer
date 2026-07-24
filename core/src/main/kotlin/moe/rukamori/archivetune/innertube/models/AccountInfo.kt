/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models

data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)

data class AccountChannel(
    val name: String,
    val byline: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
    val dataSyncId: String,
    val isSelected: Boolean,
)
