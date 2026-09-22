/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicEditablePlaylistDetailHeaderRenderer(
    val header: Header,
    val editHeader: EditHeader,
) {
    @Serializable
    data class Header(
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer?,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer?,
    )

    @Serializable
    data class EditHeader(
        val musicPlaylistEditHeaderRenderer: MusicPlaylistEditHeaderRenderer?,
    )
}

@Serializable
data class MusicDetailHeaderRenderer(
    val title: Runs,
    val subtitle: Runs,
    val secondSubtitle: Runs,
    val description: Runs?,
    val thumbnail: ThumbnailRenderer,
    val menu: Menu,
)

@Serializable
data class MusicPlaylistEditHeaderRenderer(
    val editTitle: Runs?,
)
