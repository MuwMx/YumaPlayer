/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.album

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState

fun LazyListScope.albumHeaderSection(
    albumWithSongs: AlbumWithSongs,
    songCount: Int,
    downloadState: HeaderDownloadState,
    systemBarsTopPadding: Dp,
    actions: AlbumActions,
    headerProgress: () -> Float,
    disableBlur: Boolean,
    onArtistClick: (String) -> Unit,
) {
    item(key = ALBUM_KEY_HEADER, contentType = CONTENT_TYPE_ALBUM_HEADER) {
        AlbumHeroHeader(
            albumWithSongs = albumWithSongs,
            songCount = songCount,
            downloadState = downloadState,
            systemBarsTopPadding = systemBarsTopPadding,
            actions = actions,
            headerProgress = headerProgress,
            disableBlur = disableBlur,
            onArtistClick = onArtistClick,
            modifier = Modifier.animateItem(),
        )
    }
}
