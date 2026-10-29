/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.mapping.SpotifyEntityMapper
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Id prefix of a Spotify track that has not been matched to YouTube yet: `spotify:<trackId>`. */
const val SPOTIFY_PLACEHOLDER_PREFIX = "spotify:"

/** The placeholder id for this track, or null for a track without a Spotify id. */
fun SpotifyTrack.toPlaceholderId(): String? {
    val spotifyId =
        id
            .removePrefix("spotify:track:")
            .removePrefix(SPOTIFY_PLACEHOLDER_PREFIX)
            .trim()
    return spotifyId.takeIf { it.isNotBlank() }?.let { SPOTIFY_PLACEHOLDER_PREFIX + it }
}

/** Display metadata for this track under its placeholder id; null for local files and id-less tracks. */
fun SpotifyTrack.toPlaceholderMetadata(): MediaMetadata? {
    if (isLocal) return null
    val placeholderId = toPlaceholderId() ?: return null
    return MediaMetadata(
        id = placeholderId,
        title = name,
        artists =
            artists.map { artist ->
                MediaMetadata.Artist(id = artist.id ?: "spotify:artist:${artist.name}", name = artist.name)
            },
        duration = if (durationMs > 0) durationMs / 1000 else -1,
        thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(this),
        album =
            album?.takeIf { it.name.isNotBlank() }?.let {
                MediaMetadata.Album(id = it.id.ifBlank { "spotify:album:$placeholderId" }, title = it.name)
            },
        explicit = explicit,
        isrc = externalIds?.isrc?.takeIf { it.isNotBlank() },
    )
}

/** The Spotify track a placeholder stands for, as much as its display metadata says. */
fun MediaMetadata.toPlaceholderSpotifyTrack(): SpotifyTrack =
    SpotifyTrack(
        id = id,
        name = title,
        artists = artists.map { artist -> SpotifySimpleArtist(id = artist.id, name = artist.name) },
        album = album?.let { SpotifySimpleAlbum(id = it.id, name = it.title) },
        durationMs = if (duration > 0) duration * 1000 else 0,
        explicit = explicit,
    )

/**
 * A Spotify track that has not been matched to YouTube yet is represented by a
 * placeholder id. Placeholders can sit in playlists and in the player queue like
 * any other song; the YouTube video is looked up only when the track is about to
 * play.
 */
object SpotifyPlaceholders {
    private const val RESOLVE_TIMEOUT_MS = 15_000L
    private const val UNMATCHED_TTL_MS = 10 * 60 * 1000L

    private val metadataById = ConcurrentHashMap<String, MediaMetadata>()

    // A lookup never waits behind a lower-priority one for the same id.
    private val inFlight =
        MatchPriority.entries.associateWith { ConcurrentHashMap<String, CompletableDeferred<MediaMetadata?>>() }
    private val unmatchedAt = ConcurrentHashMap<String, Long>()

    /** Set by the player service. A failed lookup only counts as "no match" while online. */
    @Volatile
    var isOnline: () -> Boolean = { true }

    fun isPlaceholder(id: String?): Boolean =
        id != null &&
            id.startsWith(SPOTIFY_PLACEHOLDER_PREFIX) &&
            id.length > SPOTIFY_PLACEHOLDER_PREFIX.length &&
            !id.startsWith("spotify:track:")

    /** Remembers display metadata so a placeholder can be matched even if it has no song row. */
    fun register(items: Iterable<MediaMetadata>) {
        items.forEach { metadata ->
            if (isPlaceholder(metadata.id)) metadataById[metadata.id] = metadata
        }
    }

    /** Records that [id] could not be matched. Ignored while offline. */
    fun markUnmatched(id: String) {
        if (isPlaceholder(id) && isOnline()) unmatchedAt[id] = System.currentTimeMillis()
    }

    /** True when a recent search found no YouTube version of this track. */
    fun isKnownUnmatched(id: String?): Boolean {
        if (id == null) return false
        val at = unmatchedAt[id] ?: return false
        if (System.currentTimeMillis() - at < UNMATCHED_TTL_MS) return true
        unmatchedAt.remove(id, at)
        return false
    }

    /**
     * Returns the YouTube metadata for [placeholderId], using the stored match
     * when there is one and searching otherwise. Concurrent callers for the same
     * placeholder share one lookup. [priority] says who is waiting for it.
     */
    suspend fun resolve(
        placeholderId: String,
        database: MusicDatabase,
        priority: MatchPriority = MatchPriority.PLAYING,
    ): MediaMetadata? {
        if (!isPlaceholder(placeholderId) || isKnownUnmatched(placeholderId)) return null
        val owners = inFlight.getValue(priority)
        while (true) {
            // Reuse a lookup that is already running at the same or a higher priority.
            val running =
                MatchPriority.entries
                    .filter { it.ordinal <= priority.ordinal }
                    .firstNotNullOfOrNull { inFlight.getValue(it)[placeholderId] }
            if (running != null) {
                try {
                    return running.await()
                } catch (e: CancellationException) {
                    // Cancelled because the caller that started the lookup was; only give up if this caller was too.
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }
            val owner = CompletableDeferred<MediaMetadata?>()
            if (owners.putIfAbsent(placeholderId, owner) != null) continue
            return try {
                withContext(Dispatchers.IO) { resolveUncached(placeholderId, database, priority) }
                    .also { owner.complete(it) }
            } catch (e: Throwable) {
                owner.completeExceptionally(e)
                throw e
            } finally {
                owners.remove(placeholderId, owner)
            }
        }
    }

    /**
     * The YouTube video id to use where a real video id is required (radio,
     * autoplay). Other ids are returned unchanged, and so is a placeholder that
     * cannot be matched.
     */
    suspend fun youtubeIdFor(mediaId: String): String {
        if (!isPlaceholder(mediaId)) return mediaId
        val database = SpotifyPlaybackResolver.database() ?: return mediaId
        return try {
            resolve(mediaId, database, MatchPriority.UPCOMING)?.id ?: mediaId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mediaId
        }
    }

    /**
     * For the player's loader thread, which cannot suspend. Returns null when there is no match
     * and throws an [IOException] when the lookup takes too long, which is not "no match".
     */
    fun resolveBlocking(
        placeholderId: String,
        database: MusicDatabase,
    ): MediaMetadata? =
        try {
            runBlocking { withTimeout(RESOLVE_TIMEOUT_MS) { resolve(placeholderId, database) } }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Matching $placeholderId took longer than ${RESOLVE_TIMEOUT_MS / 1000} s", e)
        }

    private suspend fun resolveUncached(
        placeholderId: String,
        database: MusicDatabase,
        priority: MatchPriority,
    ): MediaMetadata? {
        val source =
            metadataById[placeholderId]
                ?: database.getSongByIdBlocking(placeholderId)?.toMediaMetadata()
                ?: run {
                    Timber.tag("SpotifyMatching").w("No metadata for placeholder %s", placeholderId)
                    return null
                }
        val resolved =
            SpotifyPlaybackResolver.resolveToMetadata(
                track = source.toPlaceholderSpotifyTrack(),
                database = database,
                priority = priority,
                throwOnSearchFailure = true,
            )
        if (resolved == null) markUnmatched(placeholderId)
        return resolved?.copy(thumbnailUrl = source.thumbnailUrl ?: resolved.thumbnailUrl)
    }
}
