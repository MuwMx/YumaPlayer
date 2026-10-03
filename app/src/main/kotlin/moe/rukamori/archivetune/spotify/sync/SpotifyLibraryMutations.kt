/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Spotui / ArchiveTune (2026) | Original work by © Spotui & Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.sync

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.App
import moe.rukamori.archivetune.constants.SpotifySyncLikesKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber

object SpotifyLibraryMutations {
    private const val TAG = "SpotifyLibraryMutations"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ResolvedSpotifyMatch(
        val spotifyId: String,
        val matchToPersist: SpotifyMatchEntity? = null,
    )

    fun syncLike(spotifyId: String, isLiked: Boolean) = setTrackSaved(App.instance, spotifyId, isLiked)

    fun syncLike(context: Context, spotifyId: String, isLiked: Boolean) = setTrackSaved(context, spotifyId, isLiked)

    fun setTrackSaved(context: Context, trackId: String, saved: Boolean) {
        val rawId = trackId.removePrefix("spotify:track:")
        if (rawId.isNotBlank()) setSaved(context, rawId, "spotify:track:$rawId", saved)
    }

    fun setAlbumSaved(context: Context, albumId: String, saved: Boolean) {
        val rawId = albumId.removePrefix("spotify:album:")
        if (rawId.isNotBlank()) setSaved(context, rawId, "spotify:album:$rawId", saved)
    }

    fun setArtistFollowed(context: Context, artistId: String, followed: Boolean) {
        val rawId = artistId.removePrefix("spotify:artist:")
        if (rawId.isNotBlank()) setSaved(context, rawId, "spotify:artist:$rawId", followed)
    }

    fun syncLikeForSong(
        context: Context,
        database: MusicDatabase,
        song: SongEntity,
        isLiked: Boolean,
        explicitSpotifyId: String? = null,
    ) {
        if (song.isLocal) return
        val app = context.applicationContext
        scope.launch {
            try {
                if (!(app.dataStore.data.first()[SpotifySyncLikesKey] ?: false)) {
                    Timber.tag(TAG).d("Spotify like sync disabled in settings — skipped song ${song.id}")
                    return@launch
                }
                if (!SpotifyTokenManager.ensureToken(app)) {
                    Timber.tag(TAG).w("no token — skipped syncing like for song ${song.id}")
                    return@launch
                }
                val resolved = resolveSpotifyId(database, song, explicitSpotifyId)
                if (resolved != null && resolved.spotifyId.isNotBlank()) {
                    val uri = "spotify:track:${resolved.spotifyId}"
                    if (executeSetSaved(app, uri, isLiked) && resolved.matchToPersist != null) {
                        database.insert(resolved.matchToPersist)
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.tag(TAG).w(e, "Error resolving spotifyId for song ${song.id}")
            }
        }
    }

    fun syncLikeForSongs(
        context: Context,
        database: MusicDatabase,
        songs: Collection<SongEntity>,
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (!(app.dataStore.data.first()[SpotifySyncLikesKey] ?: false)) {
                    Timber.tag(TAG).d("Spotify like sync disabled in settings — skipped batch sync")
                    return@launch
                }

                val songsWithLikes = songs.filterNot(SongEntity::isLocal).distinctBy { it.id }
                if (songsWithLikes.isEmpty()) return@launch

                val ytSongIds = songsWithLikes.filterNot { it.id.startsWith("spotify:track:") }.map { it.id }
                val matches = if (ytSongIds.isNotEmpty()) {
                    database.getSpotifyMatchesByYouTubeIds(ytSongIds).associateBy { it.youtubeId }
                } else {
                    emptyMap()
                }

                val resolvedTracks = songsWithLikes.mapNotNull { song ->
                    val spotifyId = when {
                        song.id.startsWith("spotify:track:") -> song.id.removePrefix("spotify:track:")
                        song.id.length == 22 && song.id.all { it.isLetterOrDigit() } -> song.id
                        else -> matches[song.id]?.spotifyId
                    }
                    if (!spotifyId.isNullOrBlank()) "spotify:track:$spotifyId" to song.likedSpotify else null
                }

                if (resolvedTracks.isEmpty()) return@launch
                if (!SpotifyTokenManager.ensureToken(app)) {
                    Timber.tag(TAG).w("no token — skipped batch syncing ${resolvedTracks.size} tracks")
                    return@launch
                }

                sendBatchMutations(app, resolvedTracks.filter { it.second }.map { it.first }, add = true)
                sendBatchMutations(app, resolvedTracks.filterNot { it.second }.map { it.first }, add = false)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.tag(TAG).w(e, "Error resolving spotifyIds in batch like sync")
            }
        }
    }

    private suspend fun sendBatchMutations(app: Context, uris: List<String>, add: Boolean) {
        if (uris.isEmpty()) return
        val actionName = if (add) "add" else "remove"
        uris.chunked(50).forEach { chunk ->
            val op: suspend () -> Result<Unit> = {
                if (add) Spotify.addToLibrary(chunk) else Spotify.removeFromLibrary(chunk)
            }
            var result = op()
            if (result.isFailure && (result.exceptionOrNull() as? Spotify.SpotifyException)?.statusCode == 401 && SpotifyTokenManager.refreshToken(app)) {
                result = op()
            }
            result.fold(
                onSuccess = { Timber.tag(TAG).d("synced batch $actionName ${chunk.size} items") },
                onFailure = { Timber.tag(TAG).w(it, "failed batch $actionName ${chunk.size} items") },
            )
        }
    }

    private suspend fun resolveSpotifyId(
        database: MusicDatabase,
        song: SongEntity,
        explicitSpotifyId: String? = null,
    ): ResolvedSpotifyMatch? {
        if (!explicitSpotifyId.isNullOrBlank()) {
            val rawId = explicitSpotifyId.removePrefix("spotify:track:").removePrefix("spotify:")
            if (rawId.length == 22 && rawId.all { it.isLetterOrDigit() }) {
                val matchToPersist = if (song.id != rawId && !song.id.startsWith("spotify:")) {
                    val artistsText = database.getSongById(song.id)?.artists?.joinToString(" ") { it.name }.orEmpty()
                    val existingMatch = database.getSpotifyMatch(rawId)
                    SpotifyMatchEntity(
                        spotifyId = rawId,
                        youtubeId = song.id,
                        title = song.title,
                        artist = artistsText.ifBlank { song.albumName.orEmpty() },
                        matchScore = existingMatch?.matchScore ?: 1.0,
                    )
                } else null
                return ResolvedSpotifyMatch(rawId, matchToPersist)
            }
        }
        if (song.id.startsWith("spotify:track:")) return ResolvedSpotifyMatch(song.id.removePrefix("spotify:track:"))
        if (song.id.startsWith("spotify:")) return ResolvedSpotifyMatch(song.id.removePrefix("spotify:"))
        if (song.id.length == 22 && song.id.all { it.isLetterOrDigit() }) return ResolvedSpotifyMatch(song.id)
        val match = database.getSpotifyMatchesByYouTubeIds(listOf(song.id)).firstOrNull()
        if (match != null && match.spotifyId.isNotBlank()) return ResolvedSpotifyMatch(match.spotifyId)

        val artistsText = database.getSongById(song.id)?.artists?.joinToString(" ") { it.name }.orEmpty()
        val query = "${song.title} $artistsText".trim()
        if (query.isBlank() || !SpotifyTokenManager.ensureToken(App.instance)) return null

        val foundTrack = Spotify.search(query, types = listOf("track"), limit = 1).getOrNull()?.tracks?.items?.firstOrNull() ?: return null
        val spotifyId = foundTrack.id
        if (spotifyId.isNotBlank()) {
            val existingMatch = database.getSpotifyMatch(spotifyId)
            return ResolvedSpotifyMatch(
                spotifyId = spotifyId,
                matchToPersist = SpotifyMatchEntity(
                    spotifyId = spotifyId,
                    youtubeId = song.id,
                    title = foundTrack.name,
                    artist = foundTrack.artists.joinToString(" ") { it.name },
                    matchScore = existingMatch?.matchScore ?: 1.0,
                ),
            )
        }
        return null
    }

    private suspend fun executeSetSaved(context: Context, uri: String, saved: Boolean): Boolean {
        val app = context.applicationContext
        if (!(app.dataStore.data.first()[SpotifySyncLikesKey] ?: false)) {
            Timber.tag(TAG).d("Spotify like sync disabled in settings — skipped syncing $uri saved=$saved")
            return false
        }
        if (!SpotifyTokenManager.ensureToken(app)) {
            Timber.tag(TAG).w("no token — skipped syncing $uri saved=$saved")
            return false
        }
        val op: suspend () -> Result<Unit> = {
            if (saved) Spotify.addToLibrary(listOf(uri)) else Spotify.removeFromLibrary(listOf(uri))
        }
        var result = op()
        if (result.isFailure && (result.exceptionOrNull() as? Spotify.SpotifyException)?.statusCode == 401 && SpotifyTokenManager.refreshToken(app)) {
            result = op()
        }
        return result.isSuccess.also { success ->
            if (success) Timber.tag(TAG).d("synced $uri saved=$saved")
            else Timber.tag(TAG).w("failed syncing $uri saved=$saved: ${result.exceptionOrNull()}")
        }
    }

    private fun setSaved(context: Context, id: String, uri: String, saved: Boolean) {
        if (id.isBlank()) return
        val app = context.applicationContext
        scope.launch {
            try {
                executeSetSaved(app, uri, saved)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.tag(TAG).w(e, "exception syncing $uri saved=$saved")
            }
        }
    }
}
