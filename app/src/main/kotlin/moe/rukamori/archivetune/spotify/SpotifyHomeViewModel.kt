package moe.rukamori.archivetune.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.models.SpotifyRecentItem
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import javax.inject.Inject

@HiltViewModel
class SpotifyHomeViewModel @Inject constructor(
    private val repository: SpotifyLibraryRepository,
    private val profileCache: SpotifyProfileCache,
) : ViewModel() {

    private val _screenState = MutableStateFlow<SpotifyHomeScreenState>(SpotifyHomeScreenState.Loading)
    val screenState: StateFlow<SpotifyHomeScreenState> = _screenState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var loadJob: Job? = null

    private val _navigationEvents = MutableSharedFlow<SpotifyHomeNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<SpotifyHomeNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        load()
    }

    fun onAction(action: SpotifyHomeAction) {
        when (action) {
            SpotifyHomeAction.Refresh -> load(force = true)
            is SpotifyHomeAction.ArtistClick -> resolveArtist(action.artist)
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

    private fun load(force: Boolean = false) {
        if (!force && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val currentJob = coroutineContext[Job]
            if (force) {
                _isRefreshing.value = true
            }

            val cachedData = profileCache.restoreFromDataStore()
            val hasCachedData = cachedData.recentItems.isNotEmpty() || cachedData.topTracks.isNotEmpty() || cachedData.frequentArtists.isNotEmpty()

            if (hasCachedData) {
                if (_screenState.value !is SpotifyHomeScreenState.Success) {
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
                }
            } else if (_screenState.value !is SpotifyHomeScreenState.Success) {
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
                var recentItems = emptyList<SpotifyRecentItem>()

                val topTracksDeferred = async { Spotify.topTracks(limit = 20) }
                val newReleasesDeferred = async { Spotify.newReleases(limit = 20) }
                val homeDeferred = async { Spotify.home(sectionItemsLimit = 10) }
                val topArtistsDeferred = async { Spotify.topArtists(limit = 20) }

                val topTracksResult = topTracksDeferred.await()
                val newReleasesResult = newReleasesDeferred.await()
                val homeResult = homeDeferred.await()
                val topArtistsResult = topArtistsDeferred.await()

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

                if (topTracksList.isEmpty() && cachedData.topTracks.isNotEmpty()) {
                    sections.add(
                        SpotifyHomeSection(
                            title = "spotify_top_tracks",
                            type = SectionType.TRACKS,
                            tracks = cachedData.topTracks
                        )
                    )
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

                homeResult.onSuccess { feed ->
                    feed.sections.forEach { raw ->
                        val isRecent = raw.isShortsOrRecent

                        if (isRecent && recentItems.isEmpty()) {
                            recentItems = raw.items.mapNotNull { item ->
                                when (item) {
                                    is SpotifyHomeFeedItem.Album -> SpotifyRecentItem.Album(
                                        id = item.id,
                                        title = item.name,
                                        subtitle = item.artists.joinToString { it.name },
                                        thumbnailUrl = item.imageUrl,
                                        artists = item.artists
                                    )
                                    is SpotifyHomeFeedItem.Playlist -> SpotifyRecentItem.Playlist(
                                        id = item.id,
                                        title = item.name,
                                        subtitle = item.ownerName.orEmpty(),
                                        thumbnailUrl = item.imageUrl,
                                        trackCount = item.totalCount
                                    )
                                    is SpotifyHomeFeedItem.Artist -> null
                                }
                            }
                        } else if (!isRecent) {
                            val converted = convertHomeSection(raw)
                            if (converted != null) {
                                sections.add(converted)
                            }
                        }
                    }
                }

                if (recentItems.isEmpty()) {
                    recentItems = cachedData.recentItems
                }

                if (recentItems.isNotEmpty() || topTracksList.isNotEmpty() || frequentArtists.isNotEmpty()) {
                    profileCache.persistToDataStore(
                        recentItems = recentItems,
                        topTracks = topTracksList.ifEmpty { cachedData.topTracks },
                        frequentArtists = frequentArtists,
                    )
                }

                if (sections.isEmpty() && recentItems.isEmpty() && frequentArtists.isEmpty()) {
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

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is Spotify.SpotifyException && e.statusCode == 401) {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.spotify_not_connected, notAuthenticated = true) }
                } else if (_screenState.value !is SpotifyHomeScreenState.Success) {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.error_unknown) }
                }
            } finally {
                if (loadJob === currentJob) {
                    _isRefreshing.value = false
                }
            }
        }
    }
}
