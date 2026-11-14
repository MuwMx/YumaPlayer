/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
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
import moe.rukamori.archivetune.constants.HideYtmLikedSongsKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
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
    ) : ViewModel() {
        val playlist = savedStateHandle.get<String>("playlist")!!

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        private fun AutoPlaylistSongSortType.toSongSortType(): SongSortType =
            when (this) {
                AutoPlaylistSongSortType.CREATE_DATE -> SongSortType.CREATE_DATE
                AutoPlaylistSongSortType.NAME -> SongSortType.NAME
                AutoPlaylistSongSortType.ARTIST -> SongSortType.ARTIST
                AutoPlaylistSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val likedSongs =
            context.dataStore.data
                .map {
                    Pair(
                        listOf(
                            it[AutoPlaylistSongSortTypeKey].toEnum(AutoPlaylistSongSortType.CREATE_DATE),
                            it[AutoPlaylistSongSortDescendingKey] ?: true,
                            it[HideYtmLikedSongsKey] ?: false,
                        ),
                        Pair(
                            it[HideExplicitKey] ?: false,
                            it[HideVideoKey] ?: false,
                        ),
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (configs, hideFlags) ->
                    val sortType = configs[0] as AutoPlaylistSongSortType
                    val descending = configs[1] as Boolean
                    val hideYtmLikedSongs = configs[2] as Boolean
                    val (hideExplicit, hideVideo) = hideFlags
                    val songSortType = sortType.toSongSortType()
                    when (playlist) {
                        "liked" -> {
                            val source = if (hideYtmLikedSongs) LikeSource.SPOTIFY else null
                            database.likedSongs(songSortType, descending, hideVideo, source).map { it.filterExplicit(hideExplicit) }
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
                            syncUtils.syncLikedSongs()
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
