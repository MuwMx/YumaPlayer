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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalSyncUtils
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistLikeButton
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow
import moe.rukamori.archivetune.ui.screens.playlist.shared.togglePlaylistSongLike
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.utils.LikeSourceResolver

/**
 * ONLINE origin row (SongItem → YouTubeListItem + YouTubeSongMenu) for
 * online_playlist routes. Mirrors OnlinePlaylistSongRow
 * (OnlinePlaylistListSection.kt:216-261) — plus the trailing like button
 * left of the 3-dot menu (user spec 2026-11-15). One heart per row: the
 * badge next to the subtitle is suppressed wherever the button shows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnlinePlaylistOriginRow(
    item: SongItem,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    viewCount: Int?,
    selected: Boolean,
    showLikeButton: Boolean = true,
    isLiked: Boolean = false,
    inLibrary: Boolean = false,
    downloadState: Int? = null,
    downloadProgress: Float = -1f,
    /** Legacy gates explicit rows' taps when hide-explicit is ON (ListSection.kt:259). */
    tapEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()

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
            isFavorite = showLikeButton && isLiked,
            inLibrary = inLibrary,
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            trailingContent = {
                if (showLikeButton) {
                    PlaylistLikeButton(
                        isLiked = isLiked,
                        onToggle = {
                            val source = LikeSourceResolver.resolve(mediaId = item.id)
                            togglePlaylistSongLike(
                                seed = item.toMediaMetadata().toSongEntity(),
                                source = source,
                                database = database,
                                syncUtils = syncUtils,
                                coroutineScope = coroutineScope,
                            )
                        },
                    )
                }
                IconButton(
                    onClick = onShowMenu,
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
                    .combinedClickable(
                        enabled = tapEnabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
        )
    }
}
