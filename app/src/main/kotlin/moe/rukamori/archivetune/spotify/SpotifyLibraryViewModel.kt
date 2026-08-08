/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.utils.SyncUtils
import javax.inject.Inject

@HiltViewModel
class SpotifyLibraryViewModel
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
        private val syncUtils: SyncUtils,
    ) : ViewModel() {
        val playlists: StateFlow<List<SpotifyPlaylist>> =
            repository.playlists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val isRefreshing: StateFlow<Boolean> =
            repository.isRefreshing.stateIn(viewModelScope, SharingStarted.Lazily, false)

        val errorMessage: StateFlow<String?> =
            repository.errorMessage.stateIn(viewModelScope, SharingStarted.Lazily, null)

        val likedSongsTotal: StateFlow<Int> =
            repository.likedSongsTotal.stateIn(viewModelScope, SharingStarted.Lazily, 0)

        init {
            viewModelScope.launch(Dispatchers.IO) {
                syncUtils.trySpotifyAutoSync()
                repository.restoreCachedPlaylists()
                repository.refreshLikedSongsTotal()
            }
        }

        fun refreshPlaylists() {
            viewModelScope.launch(Dispatchers.IO) {
                syncUtils.trySpotifyAutoSync(authoritative = true)
                repository.refreshPlaylists()
            }
        }
    }
