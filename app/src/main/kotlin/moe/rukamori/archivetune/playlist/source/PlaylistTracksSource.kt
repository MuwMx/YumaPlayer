/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.constants.PlaylistSongSortType

/**
 * What a playlist source can do. Drives UI gating in the unified playlist
 * detail screen: capabilities are honored by the UI AND enforced by the
 * implementations (calling an unsupported operation returns a failed
 * [Result], never throws silently).
 */
data class PlaylistCapabilities(
    val editable: Boolean = false,
    val reorderable: Boolean = false,
    val sortable: Boolean = false,
    val paginated: Boolean = false,
    val swipeDelete: Boolean = false,
    val supportsDownload: Boolean = false,
    /** Hero shows an add-to-queue button (Auto/Cache/Top; NOT Local/Online/Spotify). */
    val supportsQueue: Boolean = false,
    /** Rows show their 1-based rank (TopPlaylistScreen.kt:415 albumIndex). */
    val showsTrackNumbers: Boolean = false,
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
            supportsQueue = false,
        )

        /** InnerTube (YouTube Music) playlists: read-only, paginated, downloadable. */
        val InnerTube = PlaylistCapabilities(
            supportsDownload = true,
            paginated = true,
        )

        /** Spotify playlists: read-only display (no server-side pagination). */
        val Spotify = PlaylistCapabilities(
            paginated = false,
        )

        /** Auto playlists (liked / downloaded): sortable by global preference. */
        val Auto = PlaylistCapabilities(
            sortable = true,
            supportsDownload = true,
            supportsQueue = true,
        )

        /** Cache playlists: read-only + vault-like sort (legacy persisted the cache
         *  sort to SongSortTypeKey; CACHE SORT SLOT — do not lose this). */
        val Cache = PlaylistCapabilities(
            sortable = true,
            supportsQueue = true,
        )

        /** The bare read-only baseline — the unified UiState's pre-observe
         *  default; no route uses it directly anymore (cache sorts via
         *  [Cache], Top's real shape is [Top] with downloads + ranks). */
        val ReadOnly = PlaylistCapabilities()

        /** Read-only AND downloadable — the Top playlist's real shape.
         *  Rows carry their 1-based rank (TopPlaylistScreen.kt:415 passes
         *  albumIndex = index + 1 — a Top chart without numbers is wrong). */
        val Top = PlaylistCapabilities(
            supportsDownload = true,
            supportsQueue = true,
            showsTrackNumbers = true,
        )
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
data class PlaylistHeaderInfo(
    val id: String,
    val title: String,
    val author: String? = null,
    /**
     * The author's channel id when known — the hero renders the author as a
     * clickable link into the artist page when set (legacy Online hero,
     * OnlinePlaylistHeroSection.kt:120-142). Spotify owners have no artist
     * page, so that source leaves it null.
     */
    val authorId: String? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int? = null,
    /** Total playlist length for the hero's duration chip; null = no chip. */
    val totalDurationMs: Long? = null,
    /**
     * Playlist description (legacy Online 160-171 / Spotify
     * SpotifyPlaylistHeader 165-178 heroes). Only the network sources
     * populate it; null/blank hides the line.
     */
    val description: String? = null,
)

/** Snapshot emitted by [PlaylistTracksSource.observe]. */
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

data class PlaylistSortInfo(
    val sortType: PlaylistSongSortType,
    val descending: Boolean,
)
