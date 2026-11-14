/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.navigation.NavController
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playlist.source.PlaylistCapabilities
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.screens.playlist.CachedSong
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
    isPlaying: Boolean,
    viewCounts: Map<String, Int>,
    sortType: PlaylistSongSortType,
    selection: Boolean,
    isSearching: Boolean,
    locked: Boolean,
    swipeToSongEnabled: Boolean,
    navController: NavController,
    menuState: MenuState,
    playlistBrowseId: String?,
    onSongClick: (index: Int, track: PlaylistTrack) -> Unit,
    onSongLongClick: (index: Int, track: PlaylistTrack) -> Unit,
    onSwipeDelete: (index: Int) -> Unit,
) {
    val canReorderNow =
        capabilities.reorderable && sortType == PlaylistSongSortType.CUSTOM &&
            !selection && !isSearching && !locked
    val canSwipeDeleteNow = capabilities.swipeDelete && !selection && !swipeToSongEnabled

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
                    navController = navController,
                    menuState = menuState,
                    playlistBrowseId = playlistBrowseId,
                    onSwipeDelete = { onSwipeDelete(index) },
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            is SongItem ->
                OnlinePlaylistOriginRow(
                    item = origin,
                    navController = navController,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    viewCount = viewCounts[track.id],
                    selected = selection && track in selectedTracks,
                    menuState = menuState,
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            is SpotifyTrack ->
                SpotifyPlaylistOriginRow(
                    track = origin,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(index, track) },
                )

            is moe.rukamori.archivetune.db.entities.Song ->
                SongPlaylistOriginRow(
                    song = origin,
                    navController = navController,
                    menuState = menuState,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    selected = selection && track in selectedTracks,
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            is CachedSong ->
                CachePlaylistOriginRow(
                    cachedSong = origin,
                    navController = navController,
                    menuState = menuState,
                    mediaMetadataId = mediaMetadataId,
                    isPlaying = isPlaying,
                    selected = selection && track in selectedTracks,
                    onClick = { onSongClick(index, track) },
                    onLongClick = { onSongLongClick(index, track) },
                )

            else -> Unit
        }
    }
}
