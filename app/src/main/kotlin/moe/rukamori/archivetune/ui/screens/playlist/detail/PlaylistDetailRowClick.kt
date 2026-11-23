/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playlist.source.InnerTubePlaylistSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.playlist.spotify.isResolvedAs
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder

/**
 * The row-click entry point the screen wires into playlistDetailSongs; keeps
 * the source casts out of the screen file. [tracks] is the FULL list
 * (legacy enqueues the whole playlist with an identity-found start index,
 * LocalPlaylistWiring.kt:646-658 / TopPlaylistScreen.kt:443-454);
 * [visibleTracks] is what the row list renders (may be a search filter).
 */
internal fun onPlaylistDetailRowClick(
    viewModel: PlaylistDetailViewModel,
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    tracks: List<PlaylistTrack>,
    visibleTracks: List<PlaylistTrack>,
    playlistTitle: String,
    track: PlaylistTrack,
    index: Int,
) {
    val innerTubeSource = viewModel.source as? InnerTubePlaylistSource
    onPlaylistDetailSongClick(
        searchState = searchState,
        playerConnection = playerConnection,
        mediaMetadata = mediaMetadata,
        tracks = tracks,
        visibleTracks = visibleTracks,
        playlistTitle = playlistTitle,
        track = track,
        index = index,
        playlistId = viewModel.playlistId,
        playlistPlayParams = innerTubeSource?.playlistPlayParams,
    )
}

/**
 * Song-click behavior for the unified list, faithful to the legacy per-screen
 * semantics: toggle play/pause when the tapped track is active; toggle
 * selection while a multi-select session is active; otherwise enqueue
 * ORIGIN-accurately — SongItem origins continue streaming via
 * YouTubeQueue.playlist (OnlinePlaylistActions.kt:180-199), SpotifyTrack
 * origins play the placeholder-resolving queue with an identity-found start
 * index over the full list (SpotifyPlaylistScreen.kt:281-292), Room/cache/
 * auto origins play a local ListQueue of media items with the playlist name
 * as the queue title (Top "My Top N", Cache "Cache Songs", Local playlist name).
 */
internal fun onPlaylistDetailSongClick(
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    tracks: List<PlaylistTrack>,
    visibleTracks: List<PlaylistTrack>,
    playlistTitle: String,
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
    val resolvedTrack = visibleTracks.getOrNull(index) ?: track
    val spotifyOrigin = resolvedTrack.origin as? SpotifyTrack
    if (spotifyOrigin != null && spotifyOrigin.isResolvedAs(mediaMetadata)) {
        // Active Spotify rows toggle play/pause — placeholder ids resolve to
        // YouTube ids, so isResolvedAs (not raw id equality) is the check
        // (SpotifyPlaylistTrackRows.kt:59-70).
        playerConnection.player.togglePlayPause()
        return
    }
    // Every other origin toggles play/pause when the tapped row IS the
    // active media (legacy Local Wiring.kt:646-648, Online Actions, Top
    // 444-445, Cache 390-392, Auto TrackRows 168-170 — the unified screen
    // regressed this to a re-enqueue on the shipped flips; fixed here).
    if (resolvedTrack.id == mediaMetadata?.id) {
        playerConnection.player.togglePlayPause()
        return
    }
    val startIndex =
        tracks.indexOfFirst { it.id == resolvedTrack.id }.takeIf { it >= 0 }
            ?: index
    when (val origin = resolvedTrack.origin) {
        is SongItem -> {
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

        is SpotifyTrack -> {
            // SPOTIFY PLAYLIST SLOT — placeholder playback with the SAME
            // identity-found start index over the FULL list, verbatim the
            // legacy enqueue (SpotifyPlaylistScreen.kt:281-292): the tapped
            // track starts, the queue carries the whole playlist under the
            // playlist's name.
            playPlaylistDetailQueue(
                playerConnection = playerConnection,
                title = playlistTitle,
                tracks = tracks,
                shuffled = false,
                startIndex = startIndex,
            )
        }

        else -> {
            // Full list + identity-found start index (legacy shape); the
            // queue shows the playlist name, not the tapped song's title.
            playerConnection.playQueue(
                ListQueue(
                    title = playlistTitle,
                    items = tracks.mapNotNull { it.toPlaylistMediaItem() },
                    startIndex = startIndex,
                ),
            )
        }
    }
}

/**
 * Builds the streaming WatchEndpoint for an online row tap, verbatim the
 * legacy OnlinePlaylistActions.toPlaylistPlaybackEndpoint: base endpoint
 * with video/playlist/setVideoId/params fallbacks filled from the playlist.
 */
internal fun SongItem.toPlaylistPlaybackEndpoint(
    playlistId: String,
    playlistPlayParams: String?,
): moe.rukamori.archivetune.innertube.models.WatchEndpoint {
    val baseEndpoint = endpoint ?: moe.rukamori.archivetune.innertube.models.WatchEndpoint(videoId = id)
    return baseEndpoint.copy(
        videoId = baseEndpoint.videoId ?: id,
        playlistId = baseEndpoint.playlistId ?: playlistId,
        playlistSetVideoId = baseEndpoint.playlistSetVideoId ?: setVideoId,
        params = baseEndpoint.params ?: playlistPlayParams,
    )
}

/**
 * Builds a ListQueue item from a track's ORIGIN when the origin maps to a
 * local MediaItem (Room Song or CachedSong). SongItem origins return null
 * because they must enqueue via YouTubeQueue.playlist instead; SpotifyTrack
 * origins return null because they enqueue via the placeholder-resolving
 * [playPlaylistDetailQueue] instead.
 */
internal fun PlaylistTrack.toPlaylistMediaItem(): androidx.media3.common.MediaItem? =
    when (val origin = origin) {
        is moe.rukamori.archivetune.db.entities.Song -> origin.toMediaItem()
        is moe.rukamori.archivetune.db.entities.PlaylistSong -> origin.song.toMediaItem()
        is moe.rukamori.archivetune.playlist.cache.CachedSong -> origin.song.toMediaItem()
        // SongItem (online) enqueues via YouTubeQueue.playlist, never here;
        // SpotifyTrack enqueues via [playPlaylistDetailQueue]'s placeholders.
        else -> null
    }
