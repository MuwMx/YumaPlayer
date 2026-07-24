/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelRenderer(
    val title: String?,
    val titleText: Runs?,
    val shortBylineText: Runs?,
    val contents: List<Content>,
    val isInfinite: Boolean?,
    val numItemsToShow: Int?,
    val playlistId: String?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Content(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer?,
        val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer?,
    )
}
