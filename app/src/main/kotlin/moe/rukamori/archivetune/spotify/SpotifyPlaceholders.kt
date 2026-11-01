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
import moe.rukamori.archivetune.spotify.matching.SpotifySearchScorer
import moe.rukamori.archivetune.spotify.matching.TrackMatcher
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.parser.SpotifyLocalFile
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

const val SPOTIFY_PLACEHOLDER_PREFIX = "spotify:"

const val SPOTIFY_LOCAL_FILE_PLACEHOLDER_PREFIX = SPOTIFY_PLACEHOLDER_PREFIX + SpotifyLocalFile.ID_PREFIX

fun SpotifyTrack.toPlaceholderId(): String? {
    val spotifyId =
        id
            .removePrefix("spotify:track:")
            .removePrefix(SPOTIFY_PLACEHOLDER_PREFIX)
            .trim()
    return spotifyId.takeIf { it.isNotBlank() }?.let { SPOTIFY_PLACEHOLDER_PREFIX + it }
}

fun SpotifyTrack.toPlaceholderMetadata(): MediaMetadata? {
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

fun MediaMetadata.toPlaceholderSpotifyTrack(): SpotifyTrack =
    SpotifyTrack(
        id = id,
        name = title,
        artists = artists.map { artist -> SpotifySimpleArtist(id = artist.id, name = artist.name) },
        album = album?.let { SpotifySimpleAlbum(id = it.id, name = it.title) },
        durationMs = if (duration > 0) duration * 1000 else 0,
        explicit = explicit,
    )

object SpotifyPlaceholders {
    private const val RESOLVE_TIMEOUT_MS = 15_000L
    private const val UNMATCHED_TTL_MS = 10 * 60 * 1000L

    private val metadataById = ConcurrentHashMap<String, MediaMetadata>()

    private val inFlight =
        MatchPriority.entries.associateWith { ConcurrentHashMap<String, CompletableDeferred<MediaMetadata?>>() }
    private val unmatchedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    var isOnline: () -> Boolean = { true }

    fun isPlaceholder(id: String?): Boolean =
        id != null &&
            id.startsWith(SPOTIFY_PLACEHOLDER_PREFIX) &&
            id.length > SPOTIFY_PLACEHOLDER_PREFIX.length &&
            !id.startsWith("spotify:track:")

    fun register(items: Iterable<MediaMetadata>) {
        items.forEach { metadata ->
            if (isPlaceholder(metadata.id)) metadataById[metadata.id] = metadata
        }
    }

    fun markUnmatched(id: String) {
        if (isPlaceholder(id) && isOnline()) unmatchedAt[id] = System.currentTimeMillis()
    }

    fun isKnownUnmatched(id: String?): Boolean {
        if (id == null) return false
        val at = unmatchedAt[id] ?: return false
        if (System.currentTimeMillis() - at < UNMATCHED_TTL_MS) return true
        unmatchedAt.remove(id, at)
        return false
    }

    suspend fun resolve(
        placeholderId: String,
        database: MusicDatabase,
        priority: MatchPriority = MatchPriority.PLAYING,
        force: Boolean = false,
    ): MediaMetadata? {
        if (!isPlaceholder(placeholderId)) return null
        if (force) {
            unmatchedAt.remove(placeholderId)
        } else if (isKnownUnmatched(placeholderId)) {
            val hasStoredMatch = withContext(Dispatchers.IO) {
                database.getSpotifyMatch(placeholderId.removePrefix(SPOTIFY_PLACEHOLDER_PREFIX)) != null
            }
            if (!hasStoredMatch) return null
            unmatchedAt.remove(placeholderId)
        }
        val owners = inFlight.getValue(priority)
        while (true) {
            val running =
                MatchPriority.entries
                    .filter { it.ordinal <= priority.ordinal }
                    .firstNotNullOfOrNull { inFlight.getValue(it)[placeholderId] }
            if (running != null) {
                try {
                    return running.await()
                } catch (_: CancellationException) {
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
    suspend fun youtubeIdFor(mediaId: String): String {
        if (!isPlaceholder(mediaId)) return mediaId
        val database = SpotifyPlaybackResolver.database() ?: return mediaId
        return try {
            resolve(mediaId, database, MatchPriority.UPCOMING)?.id ?: mediaId
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            mediaId
        }
    }
    fun resolveBlocking(
        placeholderId: String,
        database: MusicDatabase,
        force: Boolean = false,
    ): MediaMetadata? =
        try {
            runBlocking { withTimeout(RESOLVE_TIMEOUT_MS.milliseconds) { resolve(placeholderId, database, force = force) } }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Matching $placeholderId took longer than ${RESOLVE_TIMEOUT_MS / 1000} s", e)
        }

    private fun findOnDevice(
        source: MediaMetadata,
        database: MusicDatabase,
    ): MediaMetadata? {
        val artists = source.artists.map { it.name }.filter { it.isNotBlank() }
        return database
            .localSongsByTitle(source.title)
            .firstOrNull { song ->
                artists.isEmpty() || localFileScorer.hasArtistEvidence(artists, song.song.title, song.artists.map { it.name })
            }?.toMediaMetadata()
    }

    private val localFileScorer = SpotifySearchScorer(TrackMatcher())

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
        if (placeholderId.startsWith(SPOTIFY_LOCAL_FILE_PLACEHOLDER_PREFIX)) {
            findOnDevice(source, database)?.let { return it }
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
