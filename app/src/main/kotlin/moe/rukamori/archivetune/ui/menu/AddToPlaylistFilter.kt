/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import moe.rukamori.archivetune.db.entities.Playlist
import java.time.LocalDateTime
import java.util.Locale

private fun preferredAddTargetPlaylist(
    current: Playlist,
    candidate: Playlist,
): Playlist {
    val bySongCount = candidate.songCount.compareTo(current.songCount)
    if (bySongCount != 0) {
        return if (bySongCount > 0) candidate else current
    }

    val byLastUpdated = compareNullableDates(candidate.playlist.lastUpdateTime, current.playlist.lastUpdateTime)
    if (byLastUpdated != 0) {
        return if (byLastUpdated > 0) candidate else current
    }

    val byCreatedAt = compareNullableDates(candidate.playlist.createdAt, current.playlist.createdAt)
    if (byCreatedAt != 0) {
        return if (byCreatedAt > 0) candidate else current
    }

    if (candidate.playlist.isEditable != current.playlist.isEditable) {
        return if (candidate.playlist.isEditable) candidate else current
    }

    return if (candidate.id < current.id) candidate else current
}

internal fun playlistsForAddToPlaylist(playlists: List<Playlist>): List<Playlist> =
    playlists
        .asSequence()
        .filter { it.playlist.isEditable || it.playlist.browseId != null }
        .groupBy { it.playlist.browseId ?: it.id }
        .values
        .map { candidates ->
            candidates.reduce(::preferredAddTargetPlaylist)
        }.toList()

internal enum class AddToPlaylistSortOption {
    RECENTLY_MODIFIED,
    RECENTLY_CREATED,
    MOST_PLAYED,
}

internal fun visiblePlaylistsForAddToPlaylist(
    playlists: List<Playlist>,
    sortOption: AddToPlaylistSortOption,
    query: String,
    playlistPlayCounts: Map<String, Long> = emptyMap(),
): List<Playlist> {
    val normalizedQuery = query.trim()
    val filteredPlaylists =
        playlistsForAddToPlaylist(playlists).filter { playlist ->
            normalizedQuery.isBlank() ||
                playlist.playlist.name.contains(normalizedQuery, ignoreCase = true)
        }

    return filteredPlaylists.sortedWith { first, second ->
        when (sortOption) {
            AddToPlaylistSortOption.RECENTLY_MODIFIED -> {
                compareNullableDates(
                    second.playlist.lastUpdateTime ?: second.playlist.createdAt,
                    first.playlist.lastUpdateTime ?: first.playlist.createdAt,
                )
            }

            AddToPlaylistSortOption.RECENTLY_CREATED -> {
                compareNullableDates(second.playlist.createdAt, first.playlist.createdAt)
            }

            AddToPlaylistSortOption.MOST_PLAYED -> {
                compareValues(
                    playlistPlayCounts[second.id] ?: 0L,
                    playlistPlayCounts[first.id] ?: 0L,
                ).takeIf { it != 0 }
                    ?: compareNullableDates(
                        second.playlist.lastUpdateTime ?: second.playlist.createdAt,
                        first.playlist.lastUpdateTime ?: first.playlist.createdAt,
                    )
            }
        }.takeIf { it != 0 }
            ?: compareValues(
                first.playlist.name.lowercase(Locale.getDefault()),
                second.playlist.name.lowercase(Locale.getDefault()),
            )
    }
}

internal fun compareNullableDates(
    first: LocalDateTime?,
    second: LocalDateTime?,
): Int {
    if (first == second) return 0
    if (first == null) return -1
    if (second == null) return 1
    return first.compareTo(second)
}
