/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortDescendingKey
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortType
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortTypeKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.LikedSongsSourceKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        savedStateHandle: SavedStateHandle,
        private val syncUtils: SyncUtils,
        private val spotifyLibraryRepository: SpotifyLibraryRepository,
    ) : ViewModel() {
        val playlist = savedStateHandle.get<String>("playlist")!!

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        init {
            viewModelScope.launch {
                spotifyLibraryRepository.restoreCachedLikedSongs()
            }
        }

        val isSpotifySource = context.dataStore.data.map { it[LikedSongsSourceKey] ?: false }.stateIn(viewModelScope, SharingStarted.Lazily, false)
        val spotifyTracks = spotifyLibraryRepository.likedSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        fun setSpotifySource(isSpotify: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                context.dataStore.edit { it[LikedSongsSourceKey] = isSpotify }
            }
        }

        private fun AutoPlaylistSongSortType.toSongSortType(): SongSortType =
            when (this) {
                AutoPlaylistSongSortType.CREATE_DATE -> SongSortType.CREATE_DATE
                AutoPlaylistSongSortType.NAME -> SongSortType.NAME
                AutoPlaylistSongSortType.ARTIST -> SongSortType.ARTIST
                AutoPlaylistSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val likedSongs =
            kotlinx.coroutines.flow.combine(
                context.dataStore.data
                    .map {
                        Triple(
                            it[AutoPlaylistSongSortTypeKey].toEnum(AutoPlaylistSongSortType.CREATE_DATE) to (
                                it[AutoPlaylistSongSortDescendingKey]
                                    ?: true
                            ),
                            it[HideExplicitKey] ?: false,
                            it[HideVideoKey] ?: false,
                        )
                    }.distinctUntilChanged(),
                isSpotifySource
            ) { (sortDesc, hideExplicit, hideVideo), isSpotify ->
                Triple(sortDesc, hideExplicit, hideVideo) to isSpotify
            }
                .flatMapLatest { (prefs, isSpotify) ->
                    val (sortDesc, hideExplicit, hideVideo) = prefs
                    val (sortType, descending) = sortDesc
                    val songSortType = sortType.toSongSortType()
                    when (playlist) {
                        "liked" -> {
                            if (isSpotify) {
                                spotifyLibraryRepository.likedSongs.map { tracks ->
                                    val songs = tracks.map { track ->
                                        Song(
                                            song = SongEntity(
                                                id = track.id,
                                                title = track.name,
                                                duration = track.durationMs / 1000,
                                                thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                                                albumId = track.album?.id,
                                                albumName = track.album?.name,
                                                isrc = track.externalIds?.isrc,
                                                explicit = track.explicit,
                                                liked = true,
                                                likedDate = null,
                                                inLibrary = null,
                                            ),
                                            artists = track.artists.map {
                                                ArtistEntity(
                                                    id = it.id ?: ArtistEntity.generateArtistId(),
                                                    name = it.name,
                                                    thumbnailUrl = null,
                                                )
                                            },
                                            album = track.album?.let {
                                                AlbumEntity(
                                                    id = it.id,
                                                    title = it.name,
                                                    songCount = 0,
                                                    duration = 0,
                                                )
                                            }
                                        )
                                    }
                                    when (songSortType) {
                                        SongSortType.CREATE_DATE, SongSortType.PLAY_TIME -> {
                                            if (descending) songs else songs.asReversed()
                                        }
                                        SongSortType.NAME -> {
                                            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                                            val sorted = songs.sortedWith(compareBy(collator) { it.song.title })
                                            if (descending) sorted.asReversed() else sorted
                                        }
                                        SongSortType.ARTIST -> {
                                            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                                            val sorted = songs.sortedWith(compareBy(collator) { song -> song.artists.joinToString(", ") { artist -> artist.name } })
                                            if (descending) sorted.asReversed() else sorted
                                        }
                                    }.filterExplicit(hideExplicit)
                                }
                            } else {
                                database.likedSongs(songSortType, descending, hideVideo, LikeSource.YTM).map { it.filterExplicit(hideExplicit) }
                            }
                        }

                        "downloaded" -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs.filter {
                                            downloads[it.id]?.state == Download.STATE_COMPLETED
                                        }
                                    }.map { songs ->
                                        when (songSortType) {
                                            SongSortType.CREATE_DATE -> {
                                                songs.sortedBy {
                                                    downloads[it.id]?.updateTimeMs ?: 0L
                                                }
                                            }

                                            SongSortType.NAME -> {
                                                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                                                songs.sortedWith(compareBy(collator) { it.song.title })
                                            }

                                            SongSortType.ARTIST -> {
                                                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                                                songs.sortedWith(compareBy(collator) { song ->
                                                    song.artists.joinToString(", ") { artist -> artist.name }
                                                })
                                            }

                                            SongSortType.PLAY_TIME -> {
                                                songs.sortedBy { it.song.totalPlayTime }
                                            }
                                        }.reversed(descending).filterExplicit(hideExplicit)
                                    }
                            }
                        }

                        else -> {
                            MutableStateFlow(emptyList())
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh() {
            if (_isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    when (playlist) {
                        "liked" -> {
                            if (isSpotifySource.value) {
                                spotifyLibraryRepository.refreshLikedSongs()
                            } else {
                                syncUtils.syncLikedSongs()
                            }
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun syncLikedSongs() {
            refresh()
        }
    }
