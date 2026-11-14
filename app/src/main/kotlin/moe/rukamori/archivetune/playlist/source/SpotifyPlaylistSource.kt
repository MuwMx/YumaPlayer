/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

/**
 * Spotify source for the "spotify_playlist/{playlistId}" route. Mirrors
 * SpotifyPlaylistViewModel's one-shot load (repository.playlist +
 * repository.playlistTracks); paging is exposed via capabilities even though
 * the current repository returns a complete list.
 */
class SpotifyPlaylistSource
    @Inject
    constructor(
        private val playlistId: String,
        private val repository: SpotifyLibraryRepository,
    ) : PlaylistTracksSource {
    override val id: String = "spotify:$playlistId"
    override val capabilities: PlaylistCapabilities = PlaylistCapabilities.Spotify

    private val state = MutableStateFlow(PlaylistTracksResult())
    override fun observe(): StateFlow<PlaylistTracksResult> = state.asStateFlow()

    override suspend fun refresh(): Result<Unit> =
        runCatching {
            if (playlistId.isBlank()) {
                state.value = state.value.copy(isLoading = false, error = "Missing Spotify playlist")
                return Result.success(Unit)
            }
            state.value = state.value.copy(isLoading = true, error = null)
            try {
                val playlist = repository.playlist(playlistId)
                val tracks: List<SpotifyTrack> = repository.playlistTracks(playlistId)
                state.value =
                    PlaylistTracksResult(
                        header =
                            PlaylistHeaderInfo(
                                id = playlist.id,
                                title = playlist.name,
                                author = playlist.owner?.displayName,
                                thumbnailUrl = playlist.images.firstOrNull()?.url,
                                songCount = playlist.tracks?.total,
                            ),
                        tracks = tracks.map { it.toPlaylistTrack() },
                    )
            } catch (error: Throwable) {
                reportException(error)
                state.value =
                    state.value.copy(
                        isLoading = false,
                        error = error.message,
                    )
            }
            Result.success(Unit)
        }
}
