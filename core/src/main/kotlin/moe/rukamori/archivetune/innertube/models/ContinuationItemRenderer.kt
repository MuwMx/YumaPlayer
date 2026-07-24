/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint?,
) {
    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand?,
    ) {
        @Serializable
        data class ContinuationCommand(
            val token: String?,
        )
    }
}
