package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@Immutable
sealed interface SpotifyAlbumUiState {
    data object Loading : SpotifyAlbumUiState

    data class Success(
        val album: SpotifyAlbum,
        val tracks: List<SpotifyTrack>,
    ) : SpotifyAlbumUiState

    data class Error(
        val messageResId: Int,
    ) : SpotifyAlbumUiState
}

@HiltViewModel
class SpotifyAlbumViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val albumId: String =
            savedStateHandle.get<String>("albumId")
                .orEmpty()
                .removePrefix("spotify:album:")
                .removePrefix("spotify:")

        private val _uiState = MutableStateFlow<SpotifyAlbumUiState>(SpotifyAlbumUiState.Loading)
        val uiState: StateFlow<SpotifyAlbumUiState> = _uiState.asStateFlow()

        init {
            loadAlbum()
        }

        fun loadAlbum() {
            if (albumId.isBlank()) {
                _uiState.value = SpotifyAlbumUiState.Error(R.string.error_unknown)
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = SpotifyAlbumUiState.Loading
                Spotify.album(albumId)
                    .onSuccess { album ->
                        val tracks = album.tracks?.items.orEmpty()
                        _uiState.value = SpotifyAlbumUiState.Success(album = album, tracks = tracks)
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.value = SpotifyAlbumUiState.Error(R.string.error_unknown)
                    }
            }
        }
    }
