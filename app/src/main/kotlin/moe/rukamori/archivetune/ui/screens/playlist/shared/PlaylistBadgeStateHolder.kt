/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.spotify.SPOTIFY_PLACEHOLDER_PREFIX

/**
 * App-wide liked / in-library song-id sets for row BADGES, hoisted to the
 * screen (the 0412b6f7 pattern: one subscription per list instead of one
 * per row — `YouTubeListItem` used to self-subscribe before that hoist).
 *
 * [spotifyLikedIds] carries ids under BOTH shapes the writers use: the
 * resolved YouTube id (SpotifyLikedSync persists likedSpotify there) and
 * the "spotify:<raw>" placeholder id (a menu like on a placeholder row
 * updates the song row in place). [isSpotifyLiked] bridges the raw
 * playlist-track id through [matchBySpotifyId].
 */
@Stable
class PlaylistBadgeStateHolder(
    likedIds: Set<String>,
    inLibraryIds: Set<String>,
    spotifyLikedIds: Set<String>,
    matchBySpotifyId: Map<String, String> = emptyMap(),
) {
    var likedIds by mutableStateOf(likedIds)
        internal set
    var inLibraryIds by mutableStateOf(inLibraryIds)
        internal set
    var spotifyLikedIds by mutableStateOf(spotifyLikedIds)
        internal set
    var matchBySpotifyId by mutableStateOf(matchBySpotifyId)
        internal set

    /** Union like flag (`song.liked`) — what pre-0412b6f7 online rows showed. */
    fun isLiked(songId: String): Boolean = songId in likedIds

    fun isInLibrary(songId: String): Boolean = songId in inLibraryIds

    /**
     * SPOTIFY-side like state (likedSpotify), NOT the generic app like —
     * per the user's spec, a Spotify playlist row hearts iff the song is
     * liked on Spotify. [rawSpotifyId] is the playlist track's raw base62
     * id; matches may be keyed raw or "spotify:track:"-prefixed depending
     * on which writer persisted them, so both shapes are checked.
     */
    fun isSpotifyLiked(rawSpotifyId: String): Boolean {
        if (rawSpotifyId.isBlank()) return false
        if ("$SPOTIFY_PLACEHOLDER_PREFIX$rawSpotifyId" in spotifyLikedIds) return true
        if ("spotify:track:$rawSpotifyId" in spotifyLikedIds) return true
        val youtubeId =
            matchBySpotifyId[rawSpotifyId]
                ?: matchBySpotifyId["spotify:track:$rawSpotifyId"]
        return youtubeId != null && youtubeId in spotifyLikedIds
    }
}

/**
 * Subscribes the badge id-flows once per screen and keeps the holder in
 * sync — the badge twin of [PlaylistDownloadSyncEffect]. Room invalidation
 * drives every emit, so a like/unlike (menu, sync, playback resolution)
 * updates the rows live. The spotify_match observation is what lets a
 * never-played playlist row learn its YouTube id after the liked-sync
 * resolves it.
 */
@Composable
fun rememberPlaylistBadgeStateHolder(database: MusicDatabase): PlaylistBadgeStateHolder {
    val holder = remember { PlaylistBadgeStateHolder(emptySet(), emptySet(), emptySet()) }
    LaunchedEffect(database) {
        database.likedSongIds()
            .map { it.toSet() }
            .distinctUntilChanged()
            .collect { holder.likedIds = it }
    }
    LaunchedEffect(database) {
        database.librarySongIds()
            .map { it.toSet() }
            .distinctUntilChanged()
            .collect { holder.inLibraryIds = it }
    }
    LaunchedEffect(database) {
        database.spotifyLikedSongIds()
            .map { it.toSet() }
            .distinctUntilChanged()
            .collect { holder.spotifyLikedIds = it }
    }
    LaunchedEffect(database) {
        // spotify_match rows change (playback resolution / liked-sync /
        // manual matches persist them) without the song table changing;
        // observe the small id-pair table so hearts follow the bridge.
        database.spotifyMatchesFlow()
            .map { matches: List<SpotifyMatchEntity> ->
                matches.associate { it.spotifyId to it.youtubeId }
            }
            .distinctUntilChanged()
            .collect { holder.matchBySpotifyId = it }
    }
    return holder
}
