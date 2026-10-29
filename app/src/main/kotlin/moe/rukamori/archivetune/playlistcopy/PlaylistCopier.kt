/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.MatchPriority
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata
import moe.rukamori.archivetune.spotify.toPlaceholderSpotifyTrack
import timber.log.Timber
import javax.inject.Inject

/**
 * Copies one playlist to a Personal or YouTube playlist.
 *
 * Copies of Spotify playlists to a Personal playlist are written as placeholder rows, so they
 * are complete immediately and each song is matched to YouTube when it is played. A YouTube
 * playlist needs a real video for every song, so Spotify tracks, and placeholders that are
 * already in a Personal playlist, are matched first.
 */
class PlaylistCopier internal constructor(
    private val database: MusicDatabase,
    private val spotifyReader: SpotifyPlaylistReader,
    private val personalReader: PersonalPlaylistReader,
    private val youTubeReader: YouTubePlaylistReader,
    private val writer: PlaylistCopyWriter,
    private val resolveSpotifyTrack: suspend (SpotifyTrack) -> MediaMetadata?,
) {
    @Inject
    constructor(
        database: MusicDatabase,
        spotifyReader: SpotifyPlaylistReader,
        personalReader: PersonalPlaylistReader,
        youTubeReader: YouTubePlaylistReader,
        writer: PlaylistCopyWriter,
    ) : this(
        database,
        spotifyReader,
        personalReader,
        youTubeReader,
        writer,
        { track ->
            SpotifyPlaybackResolver.resolveToMetadata(
                track = track,
                database = database,
                priority = MatchPriority.UPCOMING,
                throwOnSearchFailure = true,
            )
        },
    )

    /** Runs the copy and returns [PlaylistCopyState.Success] or [PlaylistCopyState.Failure]. */
    suspend fun copy(
        request: PlaylistCopyRequest,
        onProgress: (PlaylistCopyProgress) -> Unit = {},
    ): PlaylistCopyState =
        try {
            copyOrThrow(request, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Copy failed")
            PlaylistCopyState.Failure()
        }

    private suspend fun copyOrThrow(
        request: PlaylistCopyRequest,
        onProgress: (PlaylistCopyProgress) -> Unit,
    ): PlaylistCopyState {
        val destination = request.destination
        val existingTarget =
            (destination as? PlaylistCopyDestination.Existing)?.let {
                database.getPlaylistById(it.playlistId) ?: error("The playlist to copy into no longer exists")
            }
        val toYouTube = destination is PlaylistCopyDestination.NewYouTube || existingTarget?.playlist?.browseId != null

        onProgress(PlaylistCopyProgress(PlaylistCopyStage.READING))
        val source = readSource(request.source, toYouTube, onProgress)
        val (uploadable, localFiles) = source.matched.partition { !toYouTube || !it.isLocalFile }
        val failed =
            source.failed +
                localFiles.map {
                    CopyFailure(
                        title = it.metadata?.title ?: it.songId,
                        artist = it.metadata?.artists?.joinToString { artist -> artist.name }.orEmpty(),
                        reason = CopyFailureReason.LOCAL_FILE,
                    )
                }
        if (uploadable.isEmpty()) return PlaylistCopyState.Failure(failed)

        onProgress(PlaylistCopyProgress(PlaylistCopyStage.WRITING, total = uploadable.size))
        val thumbnailUrl = request.source.thumbnailUrl
        val written =
            when (destination) {
                is PlaylistCopyDestination.NewPersonal ->
                    writer.createPersonal(destination.name, destination.sourceSpotifyId, thumbnailUrl, uploadable)
                is PlaylistCopyDestination.NewYouTube ->
                    writer.createYouTube(destination.name, thumbnailUrl, uploadable)
                is PlaylistCopyDestination.Existing -> {
                    val added =
                        if (toYouTube) {
                            writer.appendYouTube(destination.playlistId, uploadable)
                        } else {
                            writer.appendPersonal(destination.playlistId, uploadable)
                        }
                    PlaylistWrite(destination.playlistId, added)
                }
            }
        return PlaylistCopyState.Success(PlaylistCopyResult(written.playlistId, written.added, failed))
    }

    private suspend fun readSource(
        source: PlaylistCopySource,
        toYouTube: Boolean,
        onProgress: (PlaylistCopyProgress) -> Unit,
    ): CopyMatchResult =
        when (source) {
            is PlaylistCopySource.Spotify -> {
                val tracks = spotifyReader.read(source.playlistId)
                if (toYouTube) {
                    matchSpotifyTracks(tracks, resolveSpotifyTrack) { completed ->
                        onProgress(PlaylistCopyProgress(PlaylistCopyStage.MATCHING, completed, tracks.size))
                    }
                } else {
                    val placeholders = tracks.mapNotNull { it.toPlaceholderMetadata() }
                    CopyMatchResult(placeholders.map { CopyTrack(songId = it.id, metadata = it) }, emptyList())
                }
            }
            is PlaylistCopySource.Personal -> {
                val tracks = personalReader.read(source.playlistId)
                if (toYouTube) matchPlaceholders(tracks, onProgress) else CopyMatchResult(tracks, emptyList())
            }
            is PlaylistCopySource.YouTube -> CopyMatchResult(youTubeReader.read(source.browseId), emptyList())
        }

    /** Replaces the Spotify placeholders among [tracks] with their YouTube songs and keeps the order. */
    private suspend fun matchPlaceholders(
        tracks: List<CopyTrack>,
        onProgress: (PlaylistCopyProgress) -> Unit,
    ): CopyMatchResult {
        val placeholders = tracks.filter { SpotifyPlaceholders.isPlaceholder(it.songId) && it.metadata != null }
        if (placeholders.isEmpty()) return CopyMatchResult(tracks, emptyList())

        val matches =
            resolveSpotifyTracks(placeholders.map { it.metadata!!.toPlaceholderSpotifyTrack() }, resolveSpotifyTrack) { completed ->
                onProgress(PlaylistCopyProgress(PlaylistCopyStage.MATCHING, completed, placeholders.size))
            }
        val matchByPlaceholder = placeholders.map { it.songId }.zip(matches).toMap()
        val matched = ArrayList<CopyTrack>(tracks.size)
        val failed = ArrayList<CopyFailure>()
        tracks.forEach { track ->
            when (val match = matchByPlaceholder[track.songId]) {
                null -> matched += track
                is TrackMatch.Found -> matched += CopyTrack(songId = match.metadata.id, metadata = match.metadata)
                TrackMatch.NotFound -> failed += track.toCopyFailure(CopyFailureReason.NO_MATCH)
                TrackMatch.SearchFailed -> failed += track.toCopyFailure(CopyFailureReason.SEARCH_FAILED)
            }
        }
        return CopyMatchResult(matched, failed)
    }

    private fun CopyTrack.toCopyFailure(reason: CopyFailureReason) =
        CopyFailure(
            title = metadata?.title ?: songId,
            artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
            reason = reason,
        )

    private val PlaylistCopySource.thumbnailUrl: String?
        get() =
            when (this) {
                is PlaylistCopySource.Spotify -> thumbnailUrl
                is PlaylistCopySource.YouTube -> thumbnailUrl
                is PlaylistCopySource.Personal -> thumbnailUrl
            }

    private companion object {
        const val TAG = "PlaylistCopy"
    }
}
