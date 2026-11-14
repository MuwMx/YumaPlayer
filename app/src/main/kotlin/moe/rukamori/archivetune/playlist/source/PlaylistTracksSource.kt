/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.constants.PlaylistSongSortType

/**
 * What a playlist source can do. Drives UI gating in the unified playlist
 * detail screen: capabilities are honored by the UI AND enforced by the
 * implementations (calling an unsupported operation returns a failed
 * [Result], never throws silently).
 */
@Immutable
data class PlaylistCapabilities(
    val editable: Boolean = false,
    val reorderable: Boolean = false,
    val sortable: Boolean = false,
    val paginated: Boolean = false,
    val swipeDelete: Boolean = false,
    val supportsDownload: Boolean = false,
) {
    companion object {
        /** Room-backed local playlists: the full feature set. */
        val Room = PlaylistCapabilities(
            editable = true,
            reorderable = true,
            sortable = true,
            paginated = false,
            swipeDelete = true,
            supportsDownload = true,
        )

        /** InnerTube (YouTube Music) playlists: read-only, paginated, downloadable. */
        val InnerTube = PlaylistCapabilities(
            supportsDownload = true,
            paginated = true,
        )

        /** Spotify playlists: read-only display + paging. */
        val Spotify = PlaylistCapabilities(
            paginated = true,
        )

        /** Auto playlists (liked / downloaded): sortable by global preference. */
        val Auto = PlaylistCapabilities(
            sortable = true,
            supportsDownload = true,
        )

        /** Cache and Top playlists: pure read-only lists. */
        val ReadOnly = PlaylistCapabilities()
    }
}

/**
 * One row of a playlist, as the unified detail screen renders it.
 *
 * IMPORTANT DESIGN DECISION — wrapper, not flattening: [origin] keeps the
 * original typed item (PlaylistSong / SongItem / Song / SpotifyTrack /
 * CachedSong) so per-song menus stay type-accurate (SongMenu vs
 * YouTubeSongMenu), download badges read the right fields, and queue playback
 * (ListQueue / YouTubeQueue) keeps the origin. The display fields are the
 * lowest common denominator the shared UI needs.
 */
@Immutable
data class PlaylistTrack(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
    val origin: Any,
)

/** Lowest-common-denominator header info for the hero section. */
@Immutable
data class PlaylistHeaderInfo(
    val id: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int? = null,
)

/** Snapshot emitted by [PlaylistTracksSource.observe]. */
@Immutable
data class PlaylistTracksResult(
    val header: PlaylistHeaderInfo? = null,
    val tracks: List<PlaylistTrack> = emptyList(),
    val sortInfo: PlaylistSortInfo? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasContinuation: Boolean = false,
)

/**
 * A playlist's track list, abstracted over the backing store so the unified
 * playlist detail screen can render Local, Online, Spotify, Auto, Cache and
 * Top playlists with one code path.
 *
 * Room-backed sources are cold Flows over live queries; network-backed
 * sources are hot Flows owned by the implementation. Mutating operations only
 * exist for sources whose [capabilities] allow them.
 */
interface PlaylistTracksSource {
    val id: String
    val capabilities: PlaylistCapabilities

    fun observe(): Flow<PlaylistTracksResult>

    /** Loads the next page; no-op (success) when the source is not paginated. */
    suspend fun loadMore(): Result<Unit> = Result.success(Unit)

    /** Refreshes from the origin; supported by Live sources. */
    suspend fun refresh(): Result<Unit> = Result.success(Unit)

    /**
     * Open-time one-shot work: the initial network load for one-shot sources
     * (InnerTube, Spotify), or open-time hygiene for Room sources. Default:
     * no-op.
     */
    suspend fun ensureLoaded(): Result<Unit> = Result.success(Unit)

    /** Reorders [from] to [to] (indices into the current track list). */
    suspend fun reorder(
        from: Int,
        to: Int,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Source $id is not reorderable"))

    /** Removes the track at [position]. */
    suspend fun removeSong(
        position: Int,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Source $id does not support removal"))

    /**
     * Applies the sort selection when [PlaylistCapabilities.sortable]; the
     * implementation owns persistence (Room: per-playlist column, Auto:
     * DataStore). Returns a failed [Result] when unsupported.
     */
    suspend fun setSort(
        sortType: PlaylistSongSortType,
        descending: Boolean,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Source $id is not sortable"))
}

@Immutable
data class PlaylistSortInfo(
    val sortType: PlaylistSongSortType,
    val descending: Boolean,
)
