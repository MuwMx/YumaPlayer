/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyLikedSongsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SpotifyLibraryRepository,
    ) : ViewModel() {
        private val _tracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
        val tracks = _tracks.asStateFlow()

        private val _total = MutableStateFlow(0)
        val total = _total.asStateFlow()

        private val _isLoading = MutableStateFlow(true)
        val isLoading = _isLoading.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error = _error.asStateFlow()

        private var currentOffset = 0
        private var hasMore = true
        private var isFetchingMore = false

        init {
            loadLikedSongs()
        }

        fun refresh() {
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                resetAndLoadFirstChunk()
                _isRefreshing.value = false
            }
        }

        fun retry() = loadLikedSongs()

        fun clearError() {
            _error.value = null
        }

        fun loadMoreSongs() {
            if (isFetchingMore || !hasMore) return
            viewModelScope.launch(Dispatchers.IO) {
                isFetchingMore = true
                loadNextChunk()
                isFetchingMore = false
            }
        }

        private fun loadLikedSongs() {
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                resetAndLoadFirstChunk()
                _isLoading.value = false
            }
        }

        private suspend fun resetAndLoadFirstChunk() {
            currentOffset = 0
            hasMore = true
            _tracks.value = emptyList()
            loadNextChunk()
        }

        private suspend fun loadNextChunk() {
            _error.value = null

            val pageSize = 50
            val pagesPerChunk = 2
            val newTracks = mutableListOf<SpotifyTrack>()

            for (page in 0 until pagesPerChunk) {
                if (!hasMore) break
                val result = Spotify.likedSongs(limit = pageSize, offset = currentOffset).getOrNull()
                if (result == null || result.items.isEmpty()) {
                    hasMore = false
                    break
                }
                newTracks.addAll(result.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) })
                currentOffset += result.items.size
                _total.value = result.total
                if (currentOffset >= result.total || result.items.size < pageSize) {
                    hasMore = false
                    break
                }
            }

            if (newTracks.isNotEmpty()) {
                _tracks.value = _tracks.value + newTracks
            }
        }

        companion object {
            private const val PAGE_SIZE = 50
            private const val PARALLEL_GROUP_SIZE = 5
        }
    }