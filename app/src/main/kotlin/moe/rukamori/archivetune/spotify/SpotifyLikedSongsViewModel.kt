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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

        private val _isLoadingMore = MutableStateFlow(false)
        val isLoadingMore = _isLoadingMore.asStateFlow()

        init {
            viewModelScope.launch {
                repository.likedSongs.collect { tracks ->
                    _tracks.value = tracks
                    _total.value = tracks.size
                    if (tracks.isNotEmpty()) _isLoading.value = false
                }
            }
            loadLikedSongs()
        }

        fun loadLikedSongs() {
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                _error.value = null
                try {
                    repository.restoreCachedLikedSongs()
                    if (repository.likedSongs.value.isEmpty()) {
                        repository.refreshLikedSongs()
                    } else {
                        _isLoading.value = false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    reportException(e)
                    _error.value = e.message
                    _isLoading.value = false
                }
            }
        }

        fun refresh() {
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    repository.refreshLikedSongs()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    reportException(e)
                    _error.value = e.message
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun retry() = loadLikedSongs()

        fun clearError() {
            _error.value = null
        }

        fun loadMoreSongs() {
            if (_isLoadingMore.value) return
            _isLoadingMore.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val offset = _tracks.value.size
                    val currentTotal = _total.value
                    if (currentTotal != 0 && offset >= currentTotal) return@launch
                    val page = repository.likedSongsPage(limit = 50, offset = offset)
                    if (page.items.isNotEmpty()) {
                        _tracks.value = _tracks.value + page.items
                    }
                    _total.value = page.total
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    reportException(e)
                    _error.value = e.message
                } finally {
                    _isLoadingMore.value = false
                }
            }
        }

    }