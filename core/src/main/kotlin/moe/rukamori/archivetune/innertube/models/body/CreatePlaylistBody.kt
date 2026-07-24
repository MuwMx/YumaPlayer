/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models.body

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.Context

@Serializable
data class CreatePlaylistBody(
    val context: Context,
    val title: String,
    val videoIds: List<String>,
    val privacyStatus: String = PrivacyStatus.PRIVATE,
) {
    object PrivacyStatus {
        const val PRIVATE = "PRIVATE"
        const val PUBLIC = "PUBLIC"
        const val UNLISTED = "UNLISTED"
    }
}
