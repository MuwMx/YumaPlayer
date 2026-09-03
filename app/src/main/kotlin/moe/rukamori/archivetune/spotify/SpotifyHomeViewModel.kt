package moe.rukamori.archivetune.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.models.SpotifyRecentItem
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedSection
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import javax.inject.Inject

sealed interface SpotifyHomeScreenState {
    data object Loading : SpotifyHomeScreenState
    data class Success(
        val sections: List<SpotifyHomeSection>,
        val recentItems: List<SpotifyRecentItem> = emptyList(),
        val frequentArtists: List<SpotifyArtist> = emptyList()
    ) : SpotifyHomeScreenState
    data object Empty : SpotifyHomeScreenState
    data class Error(val messageResId: Int, val notAuthenticated: Boolean = false) : SpotifyHomeScreenState
}

sealed interface SpotifyHomeNavigationEvent {
    data class OpenAlbum(val browseId: String) : SpotifyHomeNavigationEvent
    data class OpenArtist(val id: String) : SpotifyHomeNavigationEvent
}

sealed interface SpotifyHomeAction {
    data object Refresh : SpotifyHomeAction
    data class AlbumClick(val album: SpotifyAlbum) : SpotifyHomeAction
    data class ArtistClick(val artist: SpotifyArtist) : SpotifyHomeAction
}

@HiltViewModel
class SpotifyHomeViewModel @Inject constructor(
    private val repository: SpotifyLibraryRepository,
    private val profileCache: SpotifyProfileCache,
) : ViewModel() {

    private val _screenState = MutableStateFlow<SpotifyHomeScreenState>(SpotifyHomeScreenState.Loading)
    val screenState: StateFlow<SpotifyHomeScreenState> = _screenState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<SpotifyHomeNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<SpotifyHomeNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        load()
    }

    fun onAction(action: SpotifyHomeAction) {
        when (action) {
            SpotifyHomeAction.Refresh -> load()
            is SpotifyHomeAction.AlbumClick -> resolveAlbum(action.album)
            is SpotifyHomeAction.ArtistClick -> resolveArtist(action.artist)
        }
    }

    private fun resolveAlbum(album: SpotifyAlbum) {
        viewModelScope.launch(Dispatchers.IO) {
            val browseId = YouTube.search(album.name, YouTube.SearchFilter.FILTER_ALBUM)
                .getOrNull()
                ?.items
                ?.firstOrNull() as? AlbumItem
            if (browseId != null) {
                _navigationEvents.emit(SpotifyHomeNavigationEvent.OpenAlbum(browseId.browseId))
            }
        }
    }

    private fun resolveArtist(artist: SpotifyArtist) {
        viewModelScope.launch(Dispatchers.IO) {
            val artistItem = YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST)
                .getOrNull()
                ?.items
                ?.firstOrNull() as? ArtistItem
            if (artistItem != null) {
                _navigationEvents.emit(SpotifyHomeNavigationEvent.OpenArtist(artistItem.id))
            }
        }
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedData = profileCache.restoreFromDataStore()
            if (cachedData.recentItems.isNotEmpty() || cachedData.topTracks.isNotEmpty() || cachedData.frequentArtists.isNotEmpty()) {
                val cachedSections = mutableListOf<SpotifyHomeSection>()
                if (cachedData.topTracks.isNotEmpty()) {
                    cachedSections.add(
                        SpotifyHomeSection(
                            title = "spotify_top_tracks",
                            type = SectionType.TRACKS,
                            tracks = cachedData.topTracks
                        )
                    )
                }
                _screenState.update {
                    SpotifyHomeScreenState.Success(
                        sections = cachedSections,
                        recentItems = cachedData.recentItems,
                        frequentArtists = cachedData.frequentArtists,
                    )
                }
            } else {
                _screenState.update { SpotifyHomeScreenState.Loading }
            }

            try {
                val session = repository.restoreSession()
                if (!session.isAuthenticated) {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.spotify_not_connected, notAuthenticated = true) }
                    return@launch
                }

                val sections = mutableListOf<SpotifyHomeSection>()
                var frequentArtists = emptyList<SpotifyArtist>()

                val topTracksDeferred = async { Spotify.topTracks(limit = 20) }
                val newReleasesDeferred = async { Spotify.newReleases(limit = 20) }
                val homeDeferred = async { Spotify.home(sectionItemsLimit = 10) }
                val topArtistsDeferred = async { Spotify.topArtists(limit = 20) }
                val recentlyPlayedDeferred = async { Spotify.recentlyPlayed(limit = 20) }

                val topTracksResult = topTracksDeferred.await()
                val newReleasesResult = newReleasesDeferred.await()
                val homeResult = homeDeferred.await()
                val topArtistsResult = topArtistsDeferred.await()
                val recentlyPlayedResult = recentlyPlayedDeferred.await()

                var topTracksList = emptyList<SpotifyTrack>()
                topTracksResult.onSuccess { topTracks ->
                    if (topTracks.items.isNotEmpty()) {
                        topTracksList = topTracks.items
                        sections.add(
                            SpotifyHomeSection(
                                title = "spotify_top_tracks",
                                type = SectionType.TRACKS,
                                tracks = topTracks.items
                            )
                        )
                    }
                }

                newReleasesResult.onSuccess { newReleases ->
                    val albums = newReleases.albums?.items.orEmpty()
                    if (albums.isNotEmpty()) {
                        sections.add(
                            SpotifyHomeSection(
                                title = "spotify_new_releases",
                                type = SectionType.ALBUMS,
                                albums = albums
                            )
                        )
                    }
                }

                topArtistsResult.onSuccess { topArtists ->
                    frequentArtists = topArtists.items
                }
                if (frequentArtists.isEmpty()) {
                    frequentArtists = cachedData.frequentArtists
                }

                val fetchedRecentItems = recentlyPlayedResult.getOrNull()?.items?.mapNotNull { item ->
                    val track = item.track
                    val album = track.album
                    if (album != null && album.id.isNotEmpty()) {
                        SpotifyRecentItem.Album(
                            id = album.id,
                            title = album.name,
                            subtitle = album.artists.joinToString { it.name },
                            thumbnailUrl = album.images.firstOrNull()?.url,
                            artistName = album.artists.firstOrNull()?.name.orEmpty()
                        )
                    } else {
                        null
                    }
                }?.distinctBy { it.id }.orEmpty()

                val recentItems = if (fetchedRecentItems.isNotEmpty()) {
                    fetchedRecentItems
                } else {
                    cachedData.recentItems
                }

                homeResult.onSuccess { feed ->
                    feed.sections.forEach { raw ->
                        val isRecent = raw.sectionUri.contains("recent", ignoreCase = true) ||
                            raw.title?.contains("Jump back in", ignoreCase = true) == true || 
                            raw.title?.contains("Recently", ignoreCase = true) == true ||
                            raw.title?.contains("Недавно", ignoreCase = true) == true ||
                            raw.title?.contains("Снова в деле", ignoreCase = true) == true ||
                            raw.title?.contains("Недавние", ignoreCase = true) == true ||
                            raw.title?.contains("Прослушано", ignoreCase = true) == true
                        if (!isRecent) {
                            val converted = convertHomeSection(raw)
                            if (converted != null) {
                                sections.add(converted)
                            }
                        }
                    }
                }

                if (recentItems.isNotEmpty() || topTracksList.isNotEmpty() || frequentArtists.isNotEmpty()) {
                    profileCache.persistToDataStore(
                        recentItems = recentItems,
                        topTracks = topTracksList.ifEmpty { cachedData.topTracks },
                        frequentArtists = frequentArtists,
                    )
                }

                if (sections.isEmpty() && recentItems.isEmpty()) {
                    _screenState.update { SpotifyHomeScreenState.Empty }
                } else {
                    _screenState.update {
                        SpotifyHomeScreenState.Success(
                            sections = sections,
                            recentItems = recentItems,
                            frequentArtists = frequentArtists,
                        )
                    }
                }

            } catch (e: Exception) {
                if (e is Spotify.SpotifyException && e.statusCode == 401) {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.spotify_not_connected, notAuthenticated = true) }
                } else if (_screenState.value !is SpotifyHomeScreenState.Success) {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.error_unknown) }
                }
            }
        }
    }

    private fun convertHomeSection(feedSection: SpotifyHomeFeedSection): SpotifyHomeSection? {
        val title = feedSection.title ?: return null

        val playlists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Playlist>()
        val albums = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Album>()
        val artists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Artist>()

        val counts = arrayOf(
            SectionType.PLAYLISTS to playlists.size,
            SectionType.ALBUMS to albums.size,
            SectionType.ARTISTS to artists.size,
        )
        val (dominant, size) = counts.maxByOrNull { it.second } ?: return null
        if (size == 0) return null

        return when (dominant) {
            SectionType.PLAYLISTS -> SpotifyHomeSection(
                title = title,
                type = SectionType.PLAYLISTS,
                playlists = playlists.map {
                    SpotifyPlaylist(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        images = listOfNotNull(it.imageUrl?.let { url -> SpotifyImage(url, null, null) }),
                        owner = it.ownerName?.let { owner -> SpotifyPlaylistOwner(id = "", displayName = owner) },
                        tracks = SpotifyPlaylistTracksRef(total = it.totalCount),
                        uri = it.uri
                    )
                }
            )
            SectionType.ALBUMS -> SpotifyHomeSection(
                title = title,
                type = SectionType.ALBUMS,
                albums = albums.map {
                    SpotifyAlbum(
                        id = it.id,
                        name = it.name,
                        albumType = it.albumType,
                        artists = it.artists,
                        images = listOfNotNull(it.imageUrl?.let { url -> SpotifyImage(url, null, null) }),
                        uri = it.uri
                    )
                }
            )
            SectionType.ARTISTS -> SpotifyHomeSection(
                title = title,
                type = SectionType.ARTISTS,
                artists = artists.map {
                    SpotifyArtist(
                        id = it.id,
                        name = it.name,
                        images = listOfNotNull(it.imageUrl?.let { url -> SpotifyImage(url, null, null) }),
                        uri = it.uri
                    )
                }
            )
            else -> null
        }
    }
}
