/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playlist.source.InnerTubePlaylistSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.playlist.source.PlaylistTracksSource
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.toPlaylistPlaybackEndpoint

/**
 * The row-click entry point the screen wires into playlistDetailSongs; keeps
 * the InnerTube source cast out of the screen file.
 */
internal fun onPlaylistDetailRowClick(
    viewModel: PlaylistDetailViewModel,
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    playerConnection: PlayerConnection,
    mediaMetadataId: String?,
    tracks: List<PlaylistTrack>,
    track: PlaylistTrack,
    index: Int,
) {
    val innerTubeSource = viewModel.source as? InnerTubePlaylistSource
    onPlaylistDetailSongClick(
        searchState = searchState,
        playerConnection = playerConnection,
        mediaMetadataId = mediaMetadataId,
        tracks = tracks,
        track = track,
        index = index,
        playlistId = viewModel.playlistId,
        playlistPlayParams = innerTubeSource?.playEndpoint?.params,
    )
}

/**
 * Song-click behavior for the unified list, faithful to the legacy per-screen
 * semantics: toggle play/pause when the tapped track is active; toggle
 * selection while a multi-select session is active; otherwise enqueue
 * ORIGIN-accurately — SongItem origins continue streaming via
 * YouTubeQueue.playlist (OnlinePlaylistActions.kt:180-199), Room/cache/auto
 * origins play a local ListQueue of media items.
 */
internal fun onPlaylistDetailSongClick(
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    playerConnection: PlayerConnection,
    mediaMetadataId: String?,
    tracks: List<PlaylistTrack>,
    track: PlaylistTrack,
    index: Int,
    playlistId: String,
    playlistPlayParams: String?,
) {
    if (searchState.selection) {
        // Identity lookup, never positional: the rendered list may be the
        // filtered view while the wrapped list is unfiltered (the legacy
        // Online screen passed the wrapper itself for exactly this reason).
        val wrapper =
            searchState.filteredItems.firstOrNull { it.item === track }
                ?: searchState.wrappedItems.firstOrNull { it.item === track }
                ?: return
        wrapper.isSelected = !wrapper.isSelected
        return
    }
    val resolvedTrack = tracks.getOrNull(index) ?: track
    if (resolvedTrack.id == mediaMetadataId) {
        playerConnection.player.togglePlayPause()
        return
    }
    when (val origin = resolvedTrack.origin) {
        is moe.rukamori.archivetune.innertube.models.SongItem -> {
            // ONLINE PLAYLIST SLOT — streaming continuation, exactly the
            // legacy enqueue (OnlinePlaylistActions.kt:180-199): the playlist
            // play params come from the InnerTube source's playEndpoint.
            val endpoint =
                origin.toPlaylistPlaybackEndpoint(
                    playlistId = playlistId,
                    playlistPlayParams = playlistPlayParams,
                )
            playerConnection.playQueue(
                YouTubeQueue.playlist(
                    endpoint = endpoint,
                    preloadItem = origin.toMediaMetadata(),
                ),
            )
        }

        else -> {
            val mediaItems =
                tracks.mapNotNull { trackItem -> trackItem.toPlaylistMediaItem() }
            playerConnection.playQueue(
                ListQueue(
                    title = resolvedTrack.title,
                    items = mediaItems,
                    startIndex = index,
                ),
            )
        }
    }
}

/**
 * Builds a ListQueue item from a track's ORIGIN when the origin maps to a
 * local MediaItem (Room Song or CachedSong). SongItem origins return null
 * because they must enqueue via YouTubeQueue.playlist instead; SpotifyTrack
 * origins never reach this (the Spotify flow resolves and enqueues Room
 * songs before playback).
 */
internal fun PlaylistTrack.toPlaylistMediaItem(): androidx.media3.common.MediaItem? =
    when (val origin = origin) {
        is moe.rukamori.archivetune.db.entities.Song -> origin.toMediaItem()
        is moe.rukamori.archivetune.db.entities.PlaylistSong -> origin.song.toMediaItem()
        is moe.rukamori.archivetune.ui.screens.playlist.CachedSong -> origin.song.toMediaItem()
        // SongItem (online) enqueues via YouTubeQueue.playlist, never here.
        else -> null
    }
