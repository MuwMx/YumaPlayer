/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playlist.source.PlaylistCapabilities
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.playlist.cache.CachedSong
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistBadgeStateHolder
import sh.calvin.reorderable.ReorderableLazyListState

/**
 * The unified track list. One itemsIndexed pass renders every source's
 * tracks by delegating each [PlaylistTrack] to its ORIGIN-typed row
 * (see PlaylistDetailLocalRow / OnlineRow / SpotifyRow / SongRows) — the
 * wrapper-not-flattening decision keeps menus, badges and download icons
 * type-accurate. Keys come from [playlistDetailTrackKey].
 */
internal fun LazyListScope.playlistDetailSongs(
    tracks: List<PlaylistTrack>,
    selectedTracks: Set<PlaylistTrack>,
    capabilities: PlaylistCapabilities,
    reorderableState: ReorderableLazyListState,
    isScrollInProgress: Boolean,
    mediaMetadataId: String?,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    viewCounts: Map<String, Int>,
    sortType: PlaylistSongSortType,
    selection: Boolean,
    isSearching: Boolean,
    locked: Boolean,
    swipeToSongEnabled: Boolean,
    showTrackNumbers: Boolean,
    playlistBrowseId: String?,
    downloads: Map<String, androidx.media3.exoplayer.offline.Download> = emptyMap(),
    badges: PlaylistBadgeStateHolder? = null,
    /** Trailing row like button; OFF only on the Liked Songs auto playlist. */
    showLikeButton: Boolean = true,
    /** Legacy online rows disable taps on explicit songs when ON (hide-explicit). */
    hideExplicit: Boolean = false,
    onSongClick: (index: Int, track: PlaylistTrack) -> Unit,
    onSongLongClick: (index: Int, track: PlaylistTrack) -> Unit,
    onSwipeDelete: (index: Int) -> Unit,
    onShowMenu: (track: PlaylistTrack) -> Unit,
) {
    val canReorderNow =
        capabilities.reorderable && sortType == PlaylistSongSortType.CUSTOM &&
            !selection && !isSearching && !locked
    // Legacy swipe-delete gating (LocalPlaylistListSection.kt:252): the
    // reorder lock also freezes swipe-deletes.
    val canSwipeDeleteNow = capabilities.swipeDelete && !selection && !locked && !swipeToSongEnabled

    itemsIndexed(
        items = tracks,
        key = { index, track -> playlistDetailTrackKey(track, index) },
        contentType = { _, _ -> CONTENT_TYPE_PLAYLIST_DETAIL_SONG },
    ) { index, track ->
        when (val origin = track.origin) {
            is PlaylistSong ->
                LocalPlaylistOriginRow(
                    track = track,
                    origin = origin,
                    index = index,
                    reorderableState = reorderableState,
                    showDragHandle = canReorderNow,
                    canSwipeDelete = canSwipeDeleteNow,
                    isScrollInProgress = isScrollInProgress,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    viewCount = viewCounts[track.id],
                    selected = selection && track in selectedTracks,
                    selection = selection,
                    playlistBrowseId = playlistBrowseId,
                    showLikeButton = showLikeButton,
                    onSwipeDelete = { onSwipeDelete(index) },
                    onShowMenu = { onShowMenu(track) },
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            is SongItem ->
                OnlinePlaylistOriginRow(
                    item = origin,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    viewCount = viewCounts[track.id],
                    selected = selection && track in selectedTracks,
                    showLikeButton = showLikeButton,
                    isLiked = badges?.isLiked(track.id) == true,
                    inLibrary = badges?.isInLibrary(track.id) == true,
                    downloadState = downloads[track.id]?.state,
                    downloadProgress = downloads[track.id]?.percentDownloaded ?: -1f,
                    // Legacy rows gate explicit songs behind the hide-explicit
                    // preference (OnlinePlaylistListSection.kt:259).
                    tapEnabled = !hideExplicit || !origin.explicit,
                    onShowMenu = { onShowMenu(track) },
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            is SpotifyTrack ->
                SpotifyPlaylistOriginRow(
                    track = origin,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    showLikeButton = showLikeButton,
                    isLiked = badges?.isSpotifyLiked(origin.id) == true,
                    onShowMenu = { onShowMenu(track) },
                    onClick = { onSongClick(index, track) },
                )

            is moe.rukamori.archivetune.db.entities.Song ->
                SongPlaylistOriginRow(
                    song = origin,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    selected = selection && track in selectedTracks,
                    showTrackNumber = showTrackNumbers,
                    trackNumber = index + 1,
                    downloadState = downloads[track.id]?.state,
                    downloadProgress = downloads[track.id]?.percentDownloaded ?: -1f,
                    showLikeButton = showLikeButton,
                    onShowMenu = { onShowMenu(track) },
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            is CachedSong ->
                CachePlaylistOriginRow(
                    cachedSong = origin,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    selected = selection && track in selectedTracks,
                    showLikeButton = showLikeButton,
                    onShowMenu = { onShowMenu(track) },
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            else -> Unit
        }
    }
}
