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
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

/**
 * Spotify source for the "spotify_playlist/{playlistId}" route. Mirrors
 * SpotifyPlaylistViewModel's one-shot load (repository.playlist +
 * repository.playlistTracks); the hero thumbnail uses the legacy picker
 * (SpotifyMapper.getPlaylistThumbnail, highest-res —
 * SpotifyPlaylistScreen.kt:123-126) and the hero carries the loaded-tracks
 * duration like the legacy header (SpotifyPlaylistHeader.kt:156-163).
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

    override suspend fun refresh(): Result<Unit> = load(isRefresh = true)

    override suspend fun ensureLoaded(): Result<Unit> = load(isRefresh = false)

    private suspend fun load(isRefresh: Boolean): Result<Unit> =
        runCatching {
            if (playlistId.isBlank()) {
                state.value = state.value.copy(isLoading = false, error = "Missing Spotify playlist")
                return Result.success(Unit)
            }
            state.value =
                state.value.copy(
                    isLoading = !isRefresh && state.value.tracks.isEmpty(),
                    isRefreshing = isRefresh,
                    error = null,
                )
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
                                thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist),
                                songCount = playlist.tracks?.total,
                                totalDurationMs = tracks.sumOf { it.durationMs.toLong() },
                                description = playlist.description,
                            ),
                        tracks = tracks.map { it.toPlaylistTrack() },
                    )
            } catch (error: Throwable) {
                reportException(error)
                state.value =
                    state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message,
                    )
            }
            Result.success(Unit)
        }
}
