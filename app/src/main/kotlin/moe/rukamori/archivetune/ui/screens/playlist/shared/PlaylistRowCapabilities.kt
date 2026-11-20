/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.shared

import androidx.compose.runtime.Immutable

/**
 * Capability flags for a playlist song row, resolved per playlist source.
 *
 * A row is only wrapped by the behavior its source supports: Local (Room)
 * playlists get swipe-to-delete, drag-reorder handles, selection and menus;
 * Online/Spotify/Auto/Cache/Top rows render without those wrappers today and
 * gain capabilities here as the unified playlist work proceeds.
 *
 * NOTE: `swipeDelete` (swipe-to-remove from the playlist, Local only) is
 * distinct from the global "swipe to next/queue" preference that lives inside
 * [moe.rukamori.archivetune.ui.component.SongListItem] /
 * [moe.rukamori.archivetune.ui.component.YouTubeListItem] via
 * [moe.rukamori.archivetune.constants.SwipeToSongKey]; the two never nest,
 * Local rows disable that preference before enabling this.
 */
@Immutable
data class PlaylistRowCapabilities(
    val swipeDelete: Boolean = false,
    val selection: Boolean = false,
    val trailingMenu: Boolean = true,
    val downloadBadges: Boolean = true,
) {
    companion object {
        /** Superset used by editable local (Room-backed) playlists. */
        val Local = PlaylistRowCapabilities(
            swipeDelete = true,
            selection = true,
            trailingMenu = true,
            downloadBadges = true,
        )

        /** Read-only list rows (online, auto, cache, top). */
        val ReadOnly = PlaylistRowCapabilities(
            swipeDelete = false,
            selection = true,
            trailingMenu = true,
            downloadBadges = true,
        )

        /** Spotify rows: no download badges (no download pipeline for them yet). */
        val Spotify = PlaylistRowCapabilities(
            swipeDelete = false,
            selection = false,
            trailingMenu = false,
            downloadBadges = false,
        )
    }
}
