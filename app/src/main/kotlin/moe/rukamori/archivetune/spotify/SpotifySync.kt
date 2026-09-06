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
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SongEntity
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

    fun syncLikeForSong(
        context: Context,
        database: MusicDatabase,
        song: SongEntity,
        isLiked: Boolean,
    ) {
        if (song.isLocal) return
        val app = context.applicationContext
        scope.launch {
            try {
                val spotifyId = resolveSpotifyId(database, song)
                if (!spotifyId.isNullOrBlank()) {
                    setSaved(app, spotifyId, "spotify:track:$spotifyId", isLiked)
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
                val songsWithLikes = songs.filterNot(SongEntity::isLocal).distinctBy { it.id }
                if (songsWithLikes.isEmpty()) return@launch

                val ytSongIds = songsWithLikes.filterNot { it.id.startsWith("spotify:track:") }.map { it.id }
                val matches = if (ytSongIds.isNotEmpty()) {
                    database.getSpotifyMatchesByYouTubeIds(ytSongIds).associateBy { it.youtubeId }
                } else {
                    emptyMap()
                }

                songsWithLikes.forEach { song ->
                    val spotifyId = if (song.id.startsWith("spotify:track:")) {
                        song.id.removePrefix("spotify:track:")
                    } else {
                        matches[song.id]?.spotifyId
                    }
                    if (!spotifyId.isNullOrBlank()) {
                        setSaved(app, spotifyId, "spotify:track:$spotifyId", song.liked)
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.tag(TAG).w(e, "Error resolving spotifyIds in batch like sync")
            }
        }
    }

    private suspend fun resolveSpotifyId(database: MusicDatabase, song: SongEntity): String? {
        if (song.id.startsWith("spotify:track:")) {
            return song.id.removePrefix("spotify:track:")
        }
        val match = database.getSpotifyMatchesByYouTubeIds(listOf(song.id)).firstOrNull()
        return match?.spotifyId
    }

    private fun setSaved(context: Context, id: String, uri: String, saved: Boolean) {
        if (id.isBlank()) return
        val app = context.applicationContext
        scope.launch {
            try {
                if (!ensureToken(app)) {
                    Timber.tag(TAG).w("no token — skipped syncing $uri saved=$saved")
                    return@launch
                }
                val result =
                    if (saved) Spotify.addToLibrary(listOf(uri))
                    else Spotify.removeFromLibrary(listOf(uri))
                result.fold(
                    onSuccess = { Timber.tag(TAG).d("synced $uri saved=$saved") },
                    onFailure = { error ->
                        if ((error as? Spotify.SpotifyException)?.statusCode == 401) {
                            if (refreshToken(app)) {
                                val retry =
                                    if (saved) Spotify.addToLibrary(listOf(uri))
                                    else Spotify.removeFromLibrary(listOf(uri))
                                retry.fold(
                                    onSuccess = { Timber.tag(TAG).d("synced $uri saved=$saved (after retry)") },
                                    onFailure = { Timber.tag(TAG).w(it, "failed syncing $uri saved=$saved after retry") },
                                )
                            }
                        } else {
                            Timber.tag(TAG).w(error, "failed syncing $uri saved=$saved")
                        }
                    },
                )
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
