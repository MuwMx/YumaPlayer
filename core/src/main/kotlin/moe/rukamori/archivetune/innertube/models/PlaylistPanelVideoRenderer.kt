/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelVideoRenderer(
    val title: Runs?,
    val lengthText: Runs?,
    val longBylineText: Runs?,
    val shortBylineText: Runs?,
    val badges: List<Badges>?,
    val videoId: String?,
    val playlistSetVideoId: String?,
    val selected: Boolean,
    val thumbnail: Thumbnails,
    val unplayableText: Runs?,
    val menu: Menu?,
    val navigationEndpoint: NavigationEndpoint,
)
