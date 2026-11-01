/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import moe.rukamori.archivetune.models.MediaMetadata

sealed interface PlaylistCopySource {
    data class Spotify(
        val playlistId: String,
        val name: String,
        val thumbnailUrl: String?,
    ) : PlaylistCopySource

    data class YouTube(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
    ) : PlaylistCopySource

    data class Personal(
        val playlistId: String,
        val name: String,
        val thumbnailUrl: String? = null,
    ) : PlaylistCopySource
}

sealed interface PlaylistCopyDestination {
    data class NewPersonal(
        val name: String,
        val sourceSpotifyId: String? = null,
    ) : PlaylistCopyDestination

    data class NewYouTube(
        val name: String,
    ) : PlaylistCopyDestination

    data class Existing(
        val playlistId: String,
    ) : PlaylistCopyDestination
}

data class PlaylistCopyRequest(
    val source: PlaylistCopySource,
    val destination: PlaylistCopyDestination,
)

data class CopyTrack(
    val songId: String,
    val metadata: MediaMetadata?,
    val isLocalFile: Boolean = false,
)

enum class CopyFailureReason {
    NO_MATCH,

    SEARCH_FAILED,

    LOCAL_FILE,
}

data class CopyFailure(
    val title: String,
    val artist: String,
    val reason: CopyFailureReason,
)

enum class PlaylistCopyStage {
    READING,
    MATCHING,
    WRITING,
}

data class PlaylistCopyProgress(
    val stage: PlaylistCopyStage,
    val completed: Int = 0,
    val total: Int = 0,
)

data class PlaylistCopyResult(
    val playlistId: String,
    val added: Int,
    val failed: List<CopyFailure>,
)

sealed interface PlaylistCopyState {
    data object Idle : PlaylistCopyState

    data class Running(
        val progress: PlaylistCopyProgress,
    ) : PlaylistCopyState

    data class Success(
        val result: PlaylistCopyResult,
    ) : PlaylistCopyState

    data class Failure(
        val failed: List<CopyFailure> = emptyList(),
    ) : PlaylistCopyState
}
