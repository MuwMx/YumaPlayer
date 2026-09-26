/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.unit.Dp
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState

fun LazyListScope.albumHeaderSection(
    albumWithSongs: AlbumWithSongs,
    songCount: Int,
    downloadState: HeaderDownloadState,
    actions: AlbumActions,
    topPadding: Dp,
    heroSpacerHeight: Dp,
    onArtistClick: (String) -> Unit,
) {
    item(key = ALBUM_KEY_HEADER, contentType = CONTENT_TYPE_ALBUM_HEADER) {
        AlbumHeroHeader(
            albumWithSongs = albumWithSongs,
            songCount = songCount,
            downloadState = downloadState,
            actions = actions,
            topPadding = topPadding,
            heroSpacerHeight = heroSpacerHeight,
            onArtistClick = onArtistClick,
        )
    }
}
