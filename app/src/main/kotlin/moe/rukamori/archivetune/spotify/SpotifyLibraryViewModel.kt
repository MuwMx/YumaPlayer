/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.SyncUtils
import java.text.Collator
import javax.inject.Inject

internal val SPOTIFY_LIBRARY_PLAYLIST_SORT_TYPES = listOf(
    PlaylistSortType.CUSTOM,
    PlaylistSortType.NAME,
    PlaylistSortType.SONG_COUNT,
)

internal const val SPOTIFY_LIBRARY_SORT_TYPE_KEY = "spotify_library_sort_type"
internal const val SPOTIFY_LIBRARY_SORT_DESCENDING_KEY = "spotify_library_sort_descending"
internal const val SPOTIFY_LIBRARY_GRID_VIEW_KEY = "spotify_library_grid_view"

internal fun parseSpotifyLibraryPlaylistSortType(raw: Any?): PlaylistSortType =
    when (raw) {
        is PlaylistSortType -> raw.takeIf { it in SPOTIFY_LIBRARY_PLAYLIST_SORT_TYPES } ?: PlaylistSortType.CUSTOM
        is String -> runCatching { PlaylistSortType.valueOf(raw) }.getOrNull()
            ?.takeIf { it in SPOTIFY_LIBRARY_PLAYLIST_SORT_TYPES }
            ?: PlaylistSortType.CUSTOM
        else -> PlaylistSortType.CUSTOM
    }

internal fun sortSpotifyLibraryPlaylists(
    playlists: List<SpotifyPlaylist>,
    sortType: PlaylistSortType,
    descending: Boolean,
): List<SpotifyPlaylist> {
    if (playlists.isEmpty()) return emptyList()
    if (sortType != PlaylistSortType.NAME && sortType != PlaylistSortType.SONG_COUNT) {
        return playlists.toList()
    }

    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

    val comparator = Comparator<Pair<Int, SpotifyPlaylist>> { a, b ->
        val (indexA, playlistA) = a
        val (indexB, playlistB) = b

        when (sortType) {
            PlaylistSortType.NAME -> {
                val nameComparison = collator.compare(playlistA.name, playlistB.name)
                val primary = if (descending) -nameComparison else nameComparison
                if (primary != 0) primary else indexA.compareTo(indexB)
            }
            PlaylistSortType.SONG_COUNT -> {
                val countA = playlistA.tracks?.total
                val countB = playlistB.tracks?.total
                when {
                    countA == null && countB == null -> indexA.compareTo(indexB)
                    countA == null -> 1
                    countB == null -> -1
                    else -> {
                        val countComparison = countA.compareTo(countB)
                        val primary = if (descending) -countComparison else countComparison
                        if (primary != 0) primary else indexA.compareTo(indexB)
                    }
                }
            }
            else -> indexA.compareTo(indexB)
        }
    }

    return playlists.mapIndexed { index, playlist -> index to playlist }
        .sortedWith(comparator)
        .map { it.second }
}

@HiltViewModel
class SpotifyLibraryViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: SpotifyLibraryRepository,
        private val syncUtils: SyncUtils,
    ) : ViewModel() {
        suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> = repository.playlistTracks(playlistId)

        val playlists: StateFlow<List<SpotifyPlaylist>> =
            repository.playlists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val isRefreshing: StateFlow<Boolean> =
            repository.isRefreshing.stateIn(viewModelScope, SharingStarted.Lazily, false)

        val errorMessage: StateFlow<String?> =
            repository.errorMessage.stateIn(viewModelScope, SharingStarted.Lazily, null)

        val likedSongsTotal: StateFlow<Int> =
            repository.likedSongsTotal.stateIn(viewModelScope, SharingStarted.Lazily, 0)

        private val _isGridView =
            MutableStateFlow(savedStateHandle.get<Boolean>(SPOTIFY_LIBRARY_GRID_VIEW_KEY) ?: false)
        val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

        private val initialSortType =
            parseSpotifyLibraryPlaylistSortType(savedStateHandle.get<Any>(SPOTIFY_LIBRARY_SORT_TYPE_KEY))

        private val _sortType = MutableStateFlow(initialSortType)
        val sortType: StateFlow<PlaylistSortType> = _sortType.asStateFlow()

        private val initialSortDescending =
            savedStateHandle.get<Boolean>(SPOTIFY_LIBRARY_SORT_DESCENDING_KEY) ?: false

        private val _sortDescending = MutableStateFlow(initialSortDescending)
        val sortDescending: StateFlow<Boolean> = _sortDescending.asStateFlow()

        val sortedPlaylists: StateFlow<List<SpotifyPlaylist>> =
            combine(repository.playlists, _sortType, _sortDescending) { playlists, type, descending ->
                sortSpotifyLibraryPlaylists(playlists, type, descending)
            }
                .flowOn(Dispatchers.Default)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )

        init {
            viewModelScope.launch(Dispatchers.IO) {
                syncUtils.trySpotifyAutoSync()
                repository.restoreCachedPlaylists()
                repository.restoreCachedLikedSongs()
                repository.refreshLikedSongsTotal()
            }
        }

        fun refreshPlaylists() {
            viewModelScope.launch(Dispatchers.IO) {
                syncUtils.trySpotifyAutoSync(authoritative = true)
                repository.refreshPlaylists()
            }
        }

        fun setSortType(type: PlaylistSortType) {
            val targetType = if (type in SPOTIFY_LIBRARY_PLAYLIST_SORT_TYPES) type else PlaylistSortType.CUSTOM
            savedStateHandle[SPOTIFY_LIBRARY_SORT_TYPE_KEY] = targetType.name
            _sortType.value = targetType
        }

        fun setSortDescending(descending: Boolean) {
            savedStateHandle[SPOTIFY_LIBRARY_SORT_DESCENDING_KEY] = descending
            _sortDescending.value = descending
        }

        fun setGridView(enabled: Boolean) {
            savedStateHandle[SPOTIFY_LIBRARY_GRID_VIEW_KEY] = enabled
            _isGridView.value = enabled
        }
    }
