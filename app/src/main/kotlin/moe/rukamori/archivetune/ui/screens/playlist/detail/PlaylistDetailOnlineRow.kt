/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow
import moe.rukamori.archivetune.ui.utils.formatCompactCount

/**
 * ONLINE origin row (SongItem → YouTubeListItem + YouTubeSongMenu) for
 * online_playlist routes. Mirrors OnlinePlaylistSongRow
 * (OnlinePlaylistListSection.kt:216-261).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnlinePlaylistOriginRow(
    item: SongItem,
    navController: NavController,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    viewCount: Int?,
    selected: Boolean,
    menuState: MenuState,
    isFavorite: Boolean = false,
    inLibrary: Boolean = false,
    downloadState: Int? = null,
    downloadProgress: Float = -1f,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.ReadOnly,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        YouTubeListItem(
            item = item,
            viewCountText = viewCount?.let { count -> formatCompactCount(count.toLong()) },
            isActive = item.id == mediaMetadataId,
            isPlaying = isPlaying,
            isSelected = selected,
            isFavorite = isFavorite,
            inLibrary = inLibrary,
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            trailingContent = {
                IconButton(
                    onClick = {
                        menuState.show {
                            YouTubeSongMenu(
                                song = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}
