/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Spotui / ArchiveTune (2026) | Original work by © Spotui & Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.App
import moe.rukamori.archivetune.constants.SpotifyAccessTokenExpiresAtKey
import moe.rukamori.archivetune.constants.SpotifyAccessTokenKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.constants.SpotifySyncLikesKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber

object SpotifySync {

    private const val TAG = "SpotifySync"
    private const val TOKEN_EXPIRY_GRACE_MS = 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenMutex = Mutex()

    fun syncLike(spotifyId: String, isLiked: Boolean) {
        setTrackSaved(App.instance, spotifyId, isLiked)
    }

    fun syncLike(context: Context, spotifyId: String, isLiked: Boolean) {
        setTrackSaved(context, spotifyId, isLiked)
    }

    fun setTrackSaved(context: Context, trackId: String, saved: Boolean) {
        val rawId = trackId.removePrefix("spotify:track:")
        if (rawId.isBlank()) return
        setSaved(context, rawId, "spotify:track:$rawId", saved)
    }

    fun setAlbumSaved(context: Context, albumId: String, saved: Boolean) {
        val rawId = albumId.removePrefix("spotify:album:")
        if (rawId.isBlank()) return
        setSaved(context, rawId, "spotify:album:$rawId", saved)
    }

    fun setArtistFollowed(context: Context, artistId: String, followed: Boolean) {
        val rawId = artistId.removePrefix("spotify:artist:")
        if (rawId.isBlank()) return
        setSaved(context, rawId, "spotify:artist:$rawId", followed)
    }

    data class ResolvedSpotifyMatch(
        val spotifyId: String,
        val matchToPersist: SpotifyMatchEntity? = null,
    )

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
                val isSyncLikesEnabled = app.dataStore.data.first()[SpotifySyncLikesKey] ?: false
                if (!isSyncLikesEnabled) {
                    Timber.tag(TAG).d("Spotify like sync disabled in settings — skipped song ${song.id}")
                    return@launch
                }
                if (!ensureToken(app)) {
                    Timber.tag(TAG).w("no token — skipped syncing like for song ${song.id}")
                    return@launch
                }
                val resolved = resolveSpotifyId(database, song, explicitSpotifyId)
                if (resolved != null && resolved.spotifyId.isNotBlank()) {
                    val uri = "spotify:track:${resolved.spotifyId}"
                    val success = executeSetSaved(app, uri, isLiked)
                    if (success && resolved.matchToPersist != null) {
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
                val isSyncLikesEnabled = app.dataStore.data.first()[SpotifySyncLikesKey] ?: false
                if (!isSyncLikesEnabled) {
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
                    val spotifyId = if (song.id.startsWith("spotify:track:")) {
                        song.id.removePrefix("spotify:track:")
                    } else if (song.id.length == 22 && song.id.all { it.isLetterOrDigit() }) {
                        song.id
                    } else {
                        matches[song.id]?.spotifyId
                    }
                    if (!spotifyId.isNullOrBlank()) {
                        "spotify:track:$spotifyId" to song.likedSpotify
                    } else {
                        null
                    }
                }

                if (resolvedTracks.isEmpty()) return@launch

                if (!ensureToken(app)) {
                    Timber.tag(TAG).w("no token — skipped batch syncing ${resolvedTracks.size} tracks")
                    return@launch
                }

                val toAddUris = resolvedTracks.filter { it.second }.map { it.first }
                val toRemoveUris = resolvedTracks.filterNot { it.second }.map { it.first }

                if (toAddUris.isNotEmpty()) {
                    toAddUris.chunked(50).forEach { chunk ->
                        val result = Spotify.addToLibrary(chunk)
                        result.fold(
                            onSuccess = { Timber.tag(TAG).d("synced batch add ${chunk.size} items") },
                            onFailure = { error ->
                                if ((error as? Spotify.SpotifyException)?.statusCode == 401 && refreshToken(app)) {
                                    Spotify.addToLibrary(chunk).fold(
                                        onSuccess = { Timber.tag(TAG).d("synced batch add ${chunk.size} items (after retry)") },
                                        onFailure = { Timber.tag(TAG).w(it, "failed batch add ${chunk.size} items after retry") },
                                    )
                                } else {
                                    Timber.tag(TAG).w(error, "failed batch add ${chunk.size} items")
                                }
                            },
                        )
                    }
                }

                if (toRemoveUris.isNotEmpty()) {
                    toRemoveUris.chunked(50).forEach { chunk ->
                        val result = Spotify.removeFromLibrary(chunk)
                        result.fold(
                            onSuccess = { Timber.tag(TAG).d("synced batch remove ${chunk.size} items") },
                            onFailure = { error ->
                                if ((error as? Spotify.SpotifyException)?.statusCode == 401 && refreshToken(app)) {
                                    Spotify.removeFromLibrary(chunk).fold(
                                        onSuccess = { Timber.tag(TAG).d("synced batch remove ${chunk.size} items (after retry)") },
                                        onFailure = { Timber.tag(TAG).w(it, "failed batch remove ${chunk.size} items after retry") },
                                    )
                                } else {
                                    Timber.tag(TAG).w(error, "failed batch remove ${chunk.size} items")
                                }
                            },
                        )
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.tag(TAG).w(e, "Error resolving spotifyIds in batch like sync")
            }
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
                    val songWithArtists = database.getSongById(song.id)
                    val artistsText = songWithArtists?.artists?.joinToString(" ") { it.name }.orEmpty()
                    SpotifyMatchEntity(
                        spotifyId = rawId,
                        youtubeId = song.id,
                        title = song.title,
                        artist = artistsText.ifBlank { song.albumName.orEmpty() },
                        matchScore = 1.0,
                    )
                } else {
                    null
                }
                return ResolvedSpotifyMatch(spotifyId = rawId, matchToPersist = matchToPersist)
            }
        }
        if (song.id.startsWith("spotify:track:")) {
            return ResolvedSpotifyMatch(spotifyId = song.id.removePrefix("spotify:track:"))
        }
        if (song.id.startsWith("spotify:")) {
            return ResolvedSpotifyMatch(spotifyId = song.id.removePrefix("spotify:"))
        }
        if (song.id.length == 22 && song.id.all { it.isLetterOrDigit() }) {
            return ResolvedSpotifyMatch(spotifyId = song.id)
        }
        val match = database.getSpotifyMatchesByYouTubeIds(listOf(song.id)).firstOrNull()
        if (match != null && match.spotifyId.isNotBlank()) {
            return ResolvedSpotifyMatch(spotifyId = match.spotifyId)
        }

        val songWithArtists = database.getSongById(song.id)
        val artistsText = songWithArtists?.artists?.joinToString(" ") { it.name }.orEmpty()
        val query = "${song.title} $artistsText".trim()
        if (query.isBlank()) return null

        if (!ensureToken(App.instance)) return null
        val searchResult = Spotify.search(query, types = listOf("track"), limit = 1).getOrNull()
        val foundTrack = searchResult?.tracks?.items?.firstOrNull() ?: return null
        val spotifyId = foundTrack.id
        if (spotifyId.isNotBlank()) {
            return ResolvedSpotifyMatch(
                spotifyId = spotifyId,
                matchToPersist = SpotifyMatchEntity(
                    spotifyId = spotifyId,
                    youtubeId = song.id,
                    title = foundTrack.name,
                    artist = foundTrack.artists.joinToString(" ") { it.name },
                    matchScore = 1.0,
                ),
            )
        }
        return null
    }

    private suspend fun executeSetSaved(context: Context, uri: String, saved: Boolean): Boolean {
        val app = context.applicationContext
        val isSyncLikesEnabled = app.dataStore.data.first()[SpotifySyncLikesKey] ?: false
        if (!isSyncLikesEnabled) {
            Timber.tag(TAG).d("Spotify like sync disabled in settings — skipped syncing $uri saved=$saved")
            return false
        }
        if (!ensureToken(app)) {
            Timber.tag(TAG).w("no token — skipped syncing $uri saved=$saved")
            return false
        }
        val result =
            if (saved) Spotify.addToLibrary(listOf(uri))
            else Spotify.removeFromLibrary(listOf(uri))
        return result.fold(
            onSuccess = {
                Timber.tag(TAG).d("synced $uri saved=$saved")
                true
            },
            onFailure = { error ->
                if ((error as? Spotify.SpotifyException)?.statusCode == 401) {
                    if (refreshToken(app)) {
                        val retry =
                            if (saved) Spotify.addToLibrary(listOf(uri))
                            else Spotify.removeFromLibrary(listOf(uri))
                        retry.fold(
                            onSuccess = {
                                Timber.tag(TAG).d("synced $uri saved=$saved (after retry)")
                                true
                            },
                            onFailure = {
                                Timber.tag(TAG).w(it, "failed syncing $uri saved=$saved after retry")
                                false
                            },
                        )
                    } else {
                        false
                    }
                } else {
                    Timber.tag(TAG).w(error, "failed syncing $uri saved=$saved")
                    false
                }
            },
        )
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

    suspend fun ensureToken(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        val token = prefs[SpotifyAccessTokenKey].orEmpty()
        val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
        if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
            Spotify.accessToken = token
            return true
        }
        return refreshToken(context)
    }

    private suspend fun refreshToken(context: Context): Boolean =
        tokenMutex.withLock {
            val prefs = context.dataStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return true
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) return false
            val spKey = prefs[SpotifySpKeyKey].orEmpty()

            SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey)
                .mapCatching { internalToken ->
                    Spotify.accessToken = internalToken.accessToken
                    context.dataStore.edit { editPrefs ->
                        editPrefs[SpotifyAccessTokenKey] = internalToken.accessToken
                        editPrefs[SpotifyAccessTokenExpiresAtKey] = internalToken.accessTokenExpirationTimestampMs
                    }
                    true
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    Timber.tag(TAG).w(error, "Failed to refresh Spotify token in background sync")
                    false
                }
        }
}
