package moe.rukamori.archivetune.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import moe.rukamori.archivetune.data.repository.SettingsRepository
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.models.ParsedIntentAction
import moe.rukamori.archivetune.playback.joinTogether
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.ui.state.PlayerEvent
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLocalMediaId
import kotlinx.coroutines.launch


private val MascotAssets = listOf(
    moe.rukamori.archivetune.R.drawable.mascot_1,
    moe.rukamori.archivetune.R.drawable.mascot_2,
    moe.rukamori.archivetune.R.drawable.mascot_3,
    moe.rukamori.archivetune.R.drawable.mascot_4,
    moe.rukamori.archivetune.R.drawable.mascot_5,
    moe.rukamori.archivetune.R.drawable.mascot_6,
    moe.rukamori.archivetune.R.drawable.mascot_7,
    moe.rukamori.archivetune.R.drawable.mascot_8
)

/**
 * Единый источник правды и бизнес-логики для плеера Yuma.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val application: Application,
    private val connectionHolder: moe.rukamori.archivetune.playback.PlayerConnectionHolder,
    private val settingsRepository: SettingsRepository,
    private val lyricsHelper: LyricsHelper,
) : ViewModel() {
    private val playerConnection get() = connectionHolder.connection.value
    private val audioPlayer get() = playerConnection?.player

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val appearanceStateHolder = AppearanceStateHolder(
        coroutineScope = viewModelScope,
        dataStore = application.dataStore,
        settingsRepository = settingsRepository,
        uiStateProvider = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
    )

    val queueStateHolder = QueueStateHolder(
        coroutineScope = viewModelScope,
        connectionFlow = connectionHolder.connection,
        playerConnectionProvider = { playerConnection },
        audioPlayerProvider = { audioPlayer },
        updateUiState = { transform -> _uiState.update(transform) },
    )
    val queueState: StateFlow<QueueUiState> = queueStateHolder.queueState

    private val _playbackProgress = MutableStateFlow(0L)

    val progressMsProvider: () -> Long = { audioPlayer?.currentPosition ?: _playbackProgress.value }

    val lyricsDelegate = LyricsDelegate(
        application = application,
        coroutineScope = viewModelScope,
        lyricsHelper = lyricsHelper,
        playerConnectionProvider = { playerConnection },
        audioPlayerProvider = { audioPlayer },
        uiState = _uiState.asStateFlow(),
        updateUiState = { transform -> _uiState.update(transform) },
        playbackProgressProvider = { _playbackProgress.value },
    )

    val progressTicker = ProgressTicker(
        coroutineScope = viewModelScope,
        audioPlayerProvider = { audioPlayer },
        updatePlaybackProgress = { progressMs -> _playbackProgress.value = progressMs },
        updateDuration = { durationMs ->
            _uiState.update { current ->
                if (current.durationMs <= 0L || current.durationMs != durationMs) {
                    current.copy(durationMs = durationMs)
                } else {
                    current
                }
            }
        },
        onLyricsProgressUpdate = { progressMs -> lyricsDelegate.updateLyricsProgress(progressMs) },
    )

    var isUserSeeking: Boolean
        get() = progressTicker.isUserSeeking
        set(value) {
            progressTicker.isUserSeeking = value
        }

    val playbackStateHolder = PlaybackStateHolder(
        coroutineScope = viewModelScope,
        connectionFlow = connectionHolder.connection,
        playerConnectionProvider = { playerConnection },
        audioPlayerProvider = { audioPlayer },
        uiStateProvider = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        onResetLyrics = { lyricsDelegate.resetLyrics() },
        onManageTicker = { playing -> manageTicker(playing) },
    )

    private var likeJob: Job? = null
    private val _event = Channel<PlayerEvent>(Channel.BUFFERED)
    val event: Flow<PlayerEvent> = _event.receiveAsFlow()

    init {
        // Подписка на лирику из базы данных через холдер плеера
        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { connection -> connection.currentLyrics }
                .collect { cached ->
                    lyricsDelegate.onCurrentLyricsUpdated(
                        cached = cached,
                        audioPlayer = audioPlayer,
                        playbackProgressMs = _playbackProgress.value,
                        expectedMediaId = _uiState.value.trackUrl,
                        generation = lyricsDelegate.currentGeneration,
                    )
                }
        }

        viewModelScope.launch {
            settingsRepository.lyricsRomanizationPrefsFlow.collect { prefs ->
                _uiState.update { it.copy(lyricsRomanizationPrefs = prefs) }
                if (_uiState.value.lyricsList.isNotEmpty()) {
                    startRomanizationJob(_uiState.value.lyricsList)
                }
            }
        }
    }

    // ==========================================
    // Единая точка входа для UI-действий шторки
    // ==========================================
    fun handleAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.PlayPause -> playbackStateHolder.togglePlayPause()
            is PlayerAction.Next, is PlayerAction.SkipNext -> playbackStateHolder.playNext()
            is PlayerAction.Previous, is PlayerAction.SkipPrevious -> playbackStateHolder.playPrevious()
            is PlayerAction.PlayQueueItem -> queueStateHolder.playQueueItem(action.index)
            is PlayerAction.RemoveQueueItem -> queueStateHolder.removeQueueItem(action.index)
            is PlayerAction.MoveQueueItem -> queueStateHolder.moveQueueItem(action.from, action.to)
            is PlayerAction.ClearQueue -> queueStateHolder.clearQueue()
            is PlayerAction.ShuffleQueue -> playbackStateHolder.toggleShuffle()
            is PlayerAction.ToggleAutoMix -> {
                val enabled = !_uiState.value.isAutoMixEnabled
                _uiState.update { it.copy(isAutoMixEnabled = enabled) }
                if (enabled) {
                    playerConnection?.service?.onInfiniteQueueEnabled()
                } else {
                    playerConnection?.service?.onInfiniteQueueDisabled()
                }
            }
            is PlayerAction.Like, is PlayerAction.ToggleLike -> playbackStateHolder.toggleLike()
            is PlayerAction.Shuffle -> playbackStateHolder.toggleShuffle()
            is PlayerAction.Repeat -> playbackStateHolder.toggleRepeat()
            is PlayerAction.ToggleAutoDownload -> appearanceStateHolder.toggleAutoDownload()
            is PlayerAction.SearchLyrics -> refreshLyrics()
            is PlayerAction.Lyrics -> setLyricsVisible(true)
            is PlayerAction.StartSleepTimer -> playbackStateHolder.startSleepTimer(action.minutes)
            is PlayerAction.StopSleepTimer -> playbackStateHolder.stopSleepTimer()
            is PlayerAction.AdjustSleepTimer -> playbackStateHolder.adjustSleepTimer(action.minutes)
            is PlayerAction.ForceRefresh -> refreshLyrics()
            is PlayerAction.Share -> shareTrack()
            is PlayerAction.SetLyricsSyncOffset -> {
                _uiState.update { it.copy(lyricsSyncOffset = action.offsetMs) }
                audioPlayer?.currentPosition?.let { updateLyricsProgress(it) }
            }
            is PlayerAction.UpdateLyricsEditText -> {
                _uiState.update { it.copy(lyricsEditText = action.text) }
            }
            is PlayerAction.UpdateTranslateLanguage -> {
                _uiState.update { it.copy(lyricsTranslateLanguage = action.langCode) }
            }
            is PlayerAction.PrepareLyricsEdit -> {
                prepareLyricsEditText()
            }
            is PlayerAction.SaveLyrics -> {
                saveLyrics(action.text)
            }
            is PlayerAction.StartRadio -> playerConnection?.startRadioSeamlessly()
            is PlayerAction.ToggleCodecInfo -> appearanceStateHolder.toggleCodecInfo()
            is PlayerAction.ToggleAlbumCoverGlow -> appearanceStateHolder.toggleAlbumCoverGlow()
            is PlayerAction.UpdateColors -> {
                appearanceStateHolder.updateColors(action.vibrant, action.darkMuted, action.gradient)
            }
            is PlayerAction.Dismiss -> {
                _uiState.update { it.copy(trackUrl = "", title = "", artist = "", coverUrl = "", isPlaying = false) }
                audioPlayer?.stop()
                audioPlayer?.clearMediaItems()
            }
            is PlayerAction.TranslateLyrics -> translateLyrics(action.langCode, action.useAi)
            is PlayerAction.DeleteLyrics -> deleteLyricsCache()
            is PlayerAction.OpenAlbum -> {
                val metadata = playerConnection?.mediaMetadata?.value
                val albumId = metadata?.album?.id
                if (albumId != null) {
                    viewModelScope.launch {
                        requestSheetCollapse()
                        _event.send(PlayerEvent.Navigate("album/$albumId"))
                    }
                }
            }
            is PlayerAction.OpenArtist -> {
                val metadata = playerConnection?.mediaMetadata?.value
                val artistId = metadata?.artists?.firstOrNull()?.id
                if (artistId != null) {
                    viewModelScope.launch {
                        requestSheetCollapse()
                        _event.send(PlayerEvent.Navigate("artist/$artistId"))
                    }
                }
            }
            else -> { /* Обработка в UI или узкоспециализированных холдерах */ }
        }
    }

    fun requestSheetCollapse() {
        _uiState.update { it.copy(isSheetCollapseRequested = true) }
        viewModelScope.launch {
            delay(150)
            _uiState.update { it.copy(isSheetCollapseRequested = false) }
        }
    }


    fun adjustSleepTimer(minutes: Int) {
        playbackStateHolder.adjustSleepTimer(minutes)
    }

    fun onConnectStatusChanged(isActive: Boolean) {
        _uiState.update { it.copy(isConnectActive = isActive) }
    }

    fun onControlStatesChanged(shuffle: String, repeat: String) {
        _uiState.update {
            it.copy(shuffleState = shuffle, repeatState = repeat)
        }
    }

    fun startSleepTimer(minutes: Int) {
        playbackStateHolder.startSleepTimer(minutes)
    }

    fun stopSleepTimer() {
        playbackStateHolder.stopSleepTimer()
    }


    fun dismissWelcome() {
        appearanceStateHolder.dismissWelcome()
    }

    fun setAutoDownloadEnabled(enabled: Boolean) {
        appearanceStateHolder.setAutoDownloadEnabled(enabled)
    }

    fun setBlurBackgroundEnabled(enabled: Boolean) {
        appearanceStateHolder.setBlurBackgroundEnabled(enabled)
    }

    fun setImmersiveEnabled(enabled: Boolean) {
        appearanceStateHolder.setImmersiveEnabled(enabled)
    }

    fun onTrackChanged(
        title: String,
        artist: String,
        coverUrl: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        trackUrl: String
    ) {
        val current = _uiState.value
        val cleanCoverUrl = coverUrl.trim()

        if (current.trackUrl == trackUrl && trackUrl.isNotEmpty()) {
            val resolvedCoverUrl = cleanCoverUrl.takeIf(String::isNotBlank) ?: current.coverUrl
            _uiState.update {
                it.copy(isPlaying = isPlaying, isLiked = isLiked, title = title, artist = artist, coverUrl = resolvedCoverUrl)
            }
            if (current.isPlaying != isPlaying) {
                manageTicker(isPlaying)
            }
            return
        }

        lyricsDelegate.cancelJobs()

        _uiState.update {
            it.copy(
                title = title,
                artist = artist,
                trackUrl = trackUrl,
                isPlaying = isPlaying,
                isLiked = isLiked,
                lyricsList = emptyList(),
                lyricsError = null,
                currentLineIndex = -1,
                isLoadingLyrics = false,
                coverUrl = cleanCoverUrl,
            )
        }

        manageTicker(isPlaying)

        if (cleanCoverUrl.isEmpty()) {
            appearanceStateHolder.resetColors()
        }

        if (_uiState.value.isLyricsVisible && _uiState.value.isAutoDownloadEnabled) {
            fetchLyrics()
        }
    }

    fun addSearchHistory(query: String) {
        val pauseSearchHistory = settingsRepository.isSearchHistoryPaused()
        if (query.isNotEmpty() && !pauseSearchHistory) {
            playerConnection?.database?.query {
                insert(moe.rukamori.archivetune.db.entities.SearchHistory(query = query))
            }
        }
    }

    fun onPlaybackProgress(currentTimeSec: Int, durationSec: Int) {
        progressTicker.onPlaybackProgress(currentTimeSec, durationSec)
    }

    fun onSeekStarted() {
        progressTicker.onSeekStarted()
    }

    fun onSeekFinished() {
        progressTicker.onSeekFinished()
    }


    fun handleDeepLinkAction(action: ParsedIntentAction) {
        when (action) {
            is ParsedIntentAction.TogetherJoin -> {
                viewModelScope.launch {
                    val connection = playerConnection ?: return@launch
                    val displayName = Build.MODEL ?: "Yuma Player"
                    connection.service.joinTogether(action.uri.toString(), displayName)
                }
            }
            is ParsedIntentAction.Login -> {
                viewModelScope.launch {
                    requestSheetCollapse()
                    _event.send(PlayerEvent.Navigate(moe.rukamori.archivetune.ui.screens.buildLoginRoute(action.loginUrl)))
                }
            }
            is ParsedIntentAction.YouTubePlaylist -> {
                val playlistId = action.playlistId
                if (playlistId.startsWith("OLAK5uy_")) {
                    viewModelScope.launch(Dispatchers.IO) {
                        YouTube.albumSongs(playlistId)
                            .onSuccess { songs ->
                                songs.firstOrNull()?.album?.id?.let { browseId ->
                                    requestSheetCollapse()
                                    _event.send(PlayerEvent.Navigate("album/$browseId"))
                                }
                            }
                    }
                } else {
                    viewModelScope.launch {
                        requestSheetCollapse()
                        _event.send(PlayerEvent.Navigate("online_playlist/$playlistId"))
                    }
                }
            }
            is ParsedIntentAction.YouTubeAlbum -> {
                viewModelScope.launch {
                    requestSheetCollapse()
                    _event.send(PlayerEvent.Navigate("album/${action.browseId}"))
                }
            }
            is ParsedIntentAction.YouTubeArtist -> {
                viewModelScope.launch {
                    requestSheetCollapse()
                    _event.send(PlayerEvent.Navigate("artist/${action.artistId}"))
                }
            }
            is ParsedIntentAction.YouTubeVideo -> {
                viewModelScope.launch(Dispatchers.IO) {
                    YouTube.queue(listOf(action.videoId), action.playlistId)
                        .onSuccess { queued ->
                            val mediaItem = queued.firstOrNull { it.id == action.videoId }?.toMediaItem()
                                ?: queued.firstOrNull()?.toMediaItem()
                                ?: MediaItem.Builder()
                                    .setMediaId(action.videoId)
                                    .setUri(action.videoId)
                                    .setCustomCacheKey(action.videoId)
                                    .build()

                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(ListQueue(items = listOf(mediaItem)))
                            }
                        }
                }
            }
            is ParsedIntentAction.YouTubeWatchPlaylist -> {
                viewModelScope.launch(Dispatchers.IO) {
                    YouTube.playlist(action.playlistId)
                        .onSuccess { playlistPage ->
                            val endpoint = if (action.shuffle) {
                                playlistPage.playlist.shuffleEndpoint ?: playlistPage.playlist.playEndpoint
                            } else {
                                playlistPage.playlist.playEndpoint ?: playlistPage.playlist.shuffleEndpoint
                            }

                            withContext(Dispatchers.Main) {
                                endpoint?.let {
                                    playerConnection?.playQueue(YouTubeQueue.playlist(it))
                                } ?: run {
                                    viewModelScope.launch {
                                        requestSheetCollapse()
                                        _event.send(PlayerEvent.Navigate("online_playlist/${action.playlistId}"))
                                    }
                                }
                            }
                        }
                }
            }
            else -> {}
        }
    }

    fun deleteLyricsCache() {
        lyricsDelegate.deleteLyricsCache()
    }

    fun manageTicker(isPlaying: Boolean) {
        progressTicker.manageTicker(isPlaying)
    }

    fun findCurrentLineIndex(lyricsList: List<LyricsEntry>, progressMs: Long, syncOffset: Int): Int {
        return lyricsDelegate.findCurrentLineIndex(lyricsList, progressMs, syncOffset)
    }

    fun updateLyricsProgress(progressMs: Long) {
        lyricsDelegate.updateLyricsProgress(progressMs)
    }

    fun prepareLyricsEditText() {
        lyricsDelegate.prepareLyricsEditText()
    }

    fun saveLyrics(text: String) {
        lyricsDelegate.saveLyrics(text)
    }

    fun translateLyrics(langCode: String, useAi: Boolean) {
        lyricsDelegate.translateLyrics(langCode, useAi)
    }

    fun fetchLyrics(force: Boolean = false) {
        lyricsDelegate.fetchLyrics(force)
    }

    fun parseLyrics(rawLyrics: String, durationMs: Long): List<LyricsEntry> {
        return lyricsDelegate.parseLyrics(rawLyrics, durationMs)
    }

    fun setLyricsVisible(isVisible: Boolean) {
        lyricsDelegate.setLyricsVisible(isVisible)
    }

    fun setQueueVisible(visible: Boolean) {
        _uiState.update { it.copy(isQueueVisible = visible) }
    }

    fun refreshLyrics() {
        lyricsDelegate.refreshLyrics()
    }

    fun togglePlayPause() {
        playbackStateHolder.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        audioPlayer?.seekTo(positionMs)
        progressTicker.onSeekFinished()
        _playbackProgress.value = positionMs
    }

    fun playNext() {
        playbackStateHolder.playNext()
    }

    fun playPrevious() {
        playbackStateHolder.playPrevious()
    }

    fun toggleShuffle() {
        playbackStateHolder.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackStateHolder.toggleRepeat()
    }

    fun toggleLike() {
        playbackStateHolder.toggleLike()
    }

    private fun shareTrack() {
        val trackId = _uiState.value.trackUrl
        if (trackId.isEmpty()) return

        val shareUrl = "https://music.youtube.com/watch?v=$trackId"
        viewModelScope.launch {
            _event.send(PlayerEvent.ShareTrack(shareUrl))
        }
    }

    fun startRomanizationJob(entries: List<LyricsEntry>, generation: Long = lyricsDelegate.currentGeneration) {
        lyricsDelegate.startRomanizationJob(entries, generation)
    }
}
