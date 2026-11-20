/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playlist.source.InnerTubePlaylistSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playlist.source.PlaylistTracksSource
import moe.rukamori.archivetune.playlist.source.SpotifyPlaylistSource
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.spotifyAwareQueue
import moe.rukamori.archivetune.spotify.toPlaceholderId
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata

/**
 * Hero PLAY — origin-accurate, mirroring each legacy hero button:
 * InnerTube sources stream the playlist via YouTubeQueue (playlist
 * playEndpoint, OnlinePlaylistActions.kt:146-152); Spotify sources enqueue
 * the placeholder-resolving queue (legacy playPlaylist,
 * SpotifyPlaylistScreen.kt:178-198); every other source plays a ListQueue
 * of origin-mapped media items (Local Wiring onPlay, Auto Header, Top/Cache
 * heroes).
 */
internal fun onPlaylistDetailPlay(
    source: PlaylistTracksSource,
    playerConnection: PlayerConnection,
    title: String,
    tracks: List<PlaylistTrack>,
) {
    val innerTubeSource = source as? InnerTubePlaylistSource
    val playQueue = innerTubeSource?.createPlayQueue()
    if (playQueue != null) {
        playerConnection.playQueue(playQueue)
        return
    }
    if (source is SpotifyPlaylistSource) {
        playPlaylistDetailQueue(
            playerConnection = playerConnection,
            title = title,
            tracks = tracks,
            shuffled = false,
            startIndex = 0,
        )
        return
    }
    playerConnection.playQueue(
        ListQueue(
            title = title,
            items = tracks.mapNotNull { it.toPlaylistMediaItem() },
        ),
    )
}

/**
 * Hero SHUFFLE — same dispatch as [onPlaylistDetailPlay] with the
 * shuffleEndpoint for InnerTube and the shuffled queue otherwise.
 */
internal fun onPlaylistDetailShuffle(
    source: PlaylistTracksSource,
    playerConnection: PlayerConnection,
    title: String,
    tracks: List<PlaylistTrack>,
) {
    val innerTubeSource = source as? InnerTubePlaylistSource
    val shuffleQueue = innerTubeSource?.createShuffleQueue()
    if (shuffleQueue != null) {
        playerConnection.playQueue(shuffleQueue)
        return
    }
    if (source is SpotifyPlaylistSource) {
        playPlaylistDetailQueue(
            playerConnection = playerConnection,
            title = title,
            tracks = tracks,
            shuffled = true,
            startIndex = 0,
        )
        return
    }
    playerConnection.playQueue(
        ListQueue(
            title = title,
            items = tracks.shuffled().mapNotNull { it.toPlaylistMediaItem() },
        ),
    )
}

/**
 * SPOTIFY PLAYLIST SLOT — the unified playback core for placeholder-bearing
 * lists. Exactly the legacy SpotifyPlaylistScreen.playPlaylist (lines
 * 178-198): build placeholder metadata for every SpotifyTrack, find the
 * start index by placeholder id, then play the spotify-aware queue under a
 * beginPlayQueueRequest/playQueueIfCurrent pair so a late resolve can't
 * start a stale queue. Non-Spotify tracks drop out of a Spotify session.
 */
internal fun playPlaylistDetailQueue(
    playerConnection: PlayerConnection,
    title: String,
    tracks: List<PlaylistTrack>,
    shuffled: Boolean,
    startIndex: Int,
) {
    val queueTracks = if (shuffled) tracks.shuffled() else tracks
    if (queueTracks.isEmpty()) return
    val startId = queueTracks.getOrNull(startIndex)?.origin?.let { it as? SpotifyTrack }?.toPlaceholderId() ?: return
    val items = queueTracks.mapNotNull { track -> track.toPlaylistQueueMetadata() }
    val queueStart = items.indexOfFirst { it.id == startId }.takeIf { it >= 0 } ?: return

    val requestToken = playerConnection.beginPlayQueueRequest()
    playerConnection.playQueueIfCurrent(
        spotifyAwareQueue(
            title = title,
            items = items,
            startIndex = queueStart,
        ),
        generation = requestToken,
    )
}

/**
 * Queue item for [playPlaylistDetailQueue]: SpotifyTrack origins become
 * placeholder metadata (resolved lazily by the queue itself); any other
 * origin has no business in a Spotify playback session and drops out.
 */
internal fun PlaylistTrack.toPlaylistQueueMetadata(): MediaMetadata? =
    when (val origin = origin) {
        is SpotifyTrack -> origin.toPlaceholderMetadata()
        else -> null
    }

/**
 * Hero ADD-TO-QUEUE — mirror of the Auto/Top/Cache hero queue buttons:
 * origin-mapped media items appended to the playing queue.
 */
internal fun onPlaylistDetailAddToQueue(
    playerConnection: PlayerConnection,
    tracks: List<PlaylistTrack>,
) {
    playerConnection.addToQueue(
        items = tracks.mapNotNull { it.toPlaylistMediaItem() },
    )
}
