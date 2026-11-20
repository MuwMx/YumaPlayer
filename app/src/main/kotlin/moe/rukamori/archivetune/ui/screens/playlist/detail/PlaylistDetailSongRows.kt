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
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalSyncUtils
import moe.rukamori.archivetune.playlist.cache.CachedSong
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistLikeButton
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow
import moe.rukamori.archivetune.ui.screens.playlist.shared.togglePlaylistSongLike
import moe.rukamori.archivetune.utils.LikeSourceResolver

/**
 * SONG origin row (Room Song → SongListItem + SongMenu) for auto and top
 * playlist routes. Mirrors AutoPlaylistTrackRow (AutoPlaylistTrackRows.kt:
 * 58-93) minus download badge plumbing (the wiring layer owns that via
 * PlaylistDownloadStateHolder).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongPlaylistOriginRow(
    song: Song,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    selected: Boolean,
    showTrackNumber: Boolean,
    trackNumber: Int,
    downloadState: Int?,
    downloadProgress: Float,
    showLikeButton: Boolean = true,
    onShowMenu: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val likeSource = LikeSourceResolver.resolve(mediaId = song.song.id, isLocal = song.song.isLocal)

    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.ReadOnly,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SongListItem(
            song = song,
            // TOP RANK SLOT — the chart shows 1-based rank numbers
            // (TopPlaylistScreen.kt:415 albumIndex = index + 1); every other
            // Song-origin screen (auto) passes null.
            albumIndex = if (showTrackNumber) trackNumber else null,
            isActive = song.song.id == mediaMetadataId,
            isPlaying = isPlaying,
            showInLibraryIcon = true,
            // One heart per row: the trailing button replaces the subtitle
            // badge wherever it shows.
            showLikedIcon = !showLikeButton,
            // AUTO BADGE SLOT — legacy AutoPlaylistTrackRow (lines 75-81)
            // passes the per-song download state/progress into SongListItem.
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            isSelected = selected,
            trailingContent = {
                if (showLikeButton) {
                    PlaylistLikeButton(
                        isLiked = song.song.liked,
                        onToggle = {
                            togglePlaylistSongLike(
                                seed = song.song,
                                source = likeSource,
                                database = database,
                                syncUtils = syncUtils,
                                coroutineScope = coroutineScope,
                            )
                        },
                    )
                }
                IconButton(
                    onClick = onShowMenu,
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

/**
 * CACHE origin row (CachedSong → SongListItem with cache-status subtitle +
 * SongMenu fromCache). Mirrors CachePlaylistScreen.kt:351-416.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CachePlaylistOriginRow(
    cachedSong: CachedSong,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    selected: Boolean,
    showLikeButton: Boolean = true,
    onShowMenu: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val likeSource = LikeSourceResolver.resolve(mediaId = cachedSong.song.song.id, isLocal = cachedSong.song.song.isLocal)

    val partiallyLabel = stringResource(R.string.partially_cached)
    val statusText =
        if (cachedSong.isFullyCached) {
            "${cachedSong.source} • ${cachedSong.formattedSize}"
        } else {
            "${cachedSong.source} • $partiallyLabel • ${cachedSong.formattedSize}"
        }
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.ReadOnly,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SongListItem(
            song = cachedSong.song,
            isActive = cachedSong.id == mediaMetadataId,
            isPlaying = isPlaying,
            showInLibraryIcon = true,
            showLikedIcon = !showLikeButton,
            isDownloaded = cachedSong.isFullyCached,
            viewCountText = statusText,
            isSelected = selected,
            trailingContent = {
                if (showLikeButton) {
                    PlaylistLikeButton(
                        isLiked = cachedSong.song.song.liked,
                        onToggle = {
                            togglePlaylistSongLike(
                                seed = cachedSong.song.song,
                                source = likeSource,
                                database = database,
                                syncUtils = syncUtils,
                                coroutineScope = coroutineScope,
                            )
                        },
                    )
                }
                IconButton(
                    onClick = onShowMenu,
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
