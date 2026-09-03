package moe.rukamori.archivetune.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import moe.rukamori.archivetune.data.repository.SettingsRepository
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.models.ParsedIntentAction
import moe.rukamori.archivetune.playback.AdvancedSleepTimer
import moe.rukamori.archivetune.playback.joinTogether
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.ui.state.PlayerEvent
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState
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
import moe.rukamori.archivetune.lyrics.LyricsTranslator
import moe.rukamori.archivetune.ai.AiLyricsTranslator
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.db.entities.formattedQuality
import moe.rukamori.archivetune.db.entities.formattedBitrate
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLocalMediaId
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import me.bush.translator.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong


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
    private val sleepTimer get() = playerConnection?.service?.sleepTimer
    private val audioPlayer get() = playerConnection?.player

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            isBlurBackgroundEnabled = settingsRepository.isBlurBackgroundEnabled(),
            isAutoDownloadEnabled = settingsRepository.isAutoDownloadLyricsEnabled(),
            isImmersiveEnabled = settingsRepository.isImmersiveEnabled(),
            showCodecInfo = settingsRepository.isShowCodecInfoEnabled(),
            isAlbumCoverGlowEnabled = settingsRepository.isAlbumCoverGlowEnabled(),
            vibrantColor = PreferenceStore.get(LastVibrantColorKey) ?: android.graphics.Color.WHITE,
            darkMutedColor = PreferenceStore.get(LastDarkMutedColorKey) ?: android.graphics.Color.parseColor("#282828"),
            gradientColor = PreferenceStore.get(LastGradientColorKey) ?: android.graphics.Color.parseColor("#121212"),
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _queueState = MutableStateFlow(QueueUiState())
    val queueState: StateFlow<QueueUiState> = _queueState.asStateFlow()


    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.NoUpdate)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    val progressMsProvider: () -> Long = { audioPlayer?.currentPosition ?: _playbackProgress.value }

    private var lastFetchedTrackKey: String = ""
    private var isUserSeeking = false
    private var tickerJob: Job? = null
    private var lyricsJob: Job? = null
    private var romanizationJob: Job? = null
    private val lyricsFetchGeneration = AtomicLong(0)
    private var likeJob: Job? = null
    private val _event = Channel<PlayerEvent>(Channel.BUFFERED)
    val event: Flow<PlayerEvent> = _event.receiveAsFlow()

    init {
        val cachedVibrant = PreferenceStore.get(LastVibrantColorKey)
        val cachedDarkMuted = PreferenceStore.get(LastDarkMutedColorKey)
        val cachedGradient = PreferenceStore.get(LastGradientColorKey)
        if (cachedVibrant != null || cachedDarkMuted != null || cachedGradient != null) {
            _uiState.update { current ->
                current.copy(
                    vibrantColor = cachedVibrant ?: current.vibrantColor,
                    darkMutedColor = cachedDarkMuted ?: current.darkMutedColor,
                    gradientColor = cachedGradient ?: current.gradientColor,
                )
            }
        }
        // Подписка на лирику из базы данных через холдер плеера
        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { connection -> connection.currentLyrics }
                .collect { cached ->
                    if (cached != null) {
                        val durationMs = audioPlayer?.duration?.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
                        val parsedLines = parseLyrics(cached.lyrics, durationMs)
                        val isSynced = parsedLines.any { line -> line.time > 0 }
                        startRomanizationJob(parsedLines, lyricsFetchGeneration.get())
                        _uiState.update {
                            val targetIndex = if (isSynced) findCurrentLineIndex(parsedLines, _playbackProgress.value, it.lyricsSyncOffset) else -1
                            it.copy(
                                lyricsList = parsedLines,
                                isSynced = isSynced,
                                isLoadingLyrics = false,
                                lyricsError = if (parsedLines.isEmpty()) "lyrics_not_found" else null,
                                currentLineIndex = targetIndex
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                lyricsList = emptyList(),
                                isSynced = false,
                                isLoadingLyrics = false,
                                lyricsError = null,
                                currentLineIndex = -1
                            )
                        }
                    }
                }
        }

        // Таймер сна
        viewModelScope.launch {
                // Подписываемся на поток оставшихся секунд напрямую из таймера
                connectionHolder.connection
                    .filterNotNull()
                    .flatMapLatest { it.service.sleepTimer.remainingSeconds }
                    .collect { seconds ->
                        _uiState.update {
                            it.copy(sleepTimerRemainingSeconds = if (seconds > 0L) seconds.toInt() else null)
                        }
                    }
        }

        // 1. Подписка на метаданные трека через холдер плеера
        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { connection -> connection.mediaMetadata }
                .collect { metadata ->
                    if (metadata == null) {
                        _uiState.update { it.copy(trackUrl = "", title = "", artist = "", coverUrl = "", isPlaying = false) }
                        return@collect
                    }
                    val title = metadata.title
                    val artist = metadata.artists.joinToString { it.name }

                    val resolvedDuration = if (metadata.duration > 0) {
                        metadata.duration * 1000L
                    } else {
                        audioPlayer?.duration?.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
                    }

                    val coverUrl = metadata.thumbnailUrl ?: ""

                    val oldId = _uiState.value.trackUrl
                    val newId = metadata.id
                    if (oldId != newId && oldId.isNotEmpty()) {
                        lyricsFetchGeneration.incrementAndGet()
                        lyricsJob?.cancel()
                        romanizationJob?.cancel()
                        _uiState.update {
                            it.copy(
                                lyricsList = emptyList(),
                                isSynced = false,
                                currentLineIndex = -1,
                                lyricsError = null,
                                isLoadingLyrics = false
                            )
                        }
                    }

                    _uiState.update { currentUi ->
                        currentUi.copy(
                            title = title,
                            artist = artist,
                            trackUrl = metadata.id,
                            durationMs = resolvedDuration,
                            coverUrl = coverUrl,
                        )
                    }
                }
        }

        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { it.playbackState }
                .collect { playbackState ->
                    val realDuration = audioPlayer?.duration?.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET }
                    _uiState.update { current ->
                        current.copy(
                            isLoading = playbackState == Player.STATE_BUFFERING,
                            durationMs = if (realDuration != null && current.durationMs <= 0L) realDuration else current.durationMs
                        )
                    }
                }
        }

        // 2. Подписка на состояние лайка через холдер плеера
        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { connection -> connection.currentSong }
                .collect { song ->
                    _uiState.update { currentUi ->
                        currentUi.copy(isLiked = song?.song?.liked == true)
                    }
                }
        }
        // 3. Подписка на состояние воспроизведения (играет/пауза) для тикера сикбара
        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { connection -> connection.isPlaying }
                .collect { playing ->
                    _uiState.update { it.copy(isPlaying = playing) }
                    manageTicker(playing) // Запуск/остановка тикера
                }
        }

        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { connection ->
                    kotlinx.coroutines.flow.combine(
                        connection.currentFormat,
                        connection.audioFormat
                    ) { dbFormat, liveFormat ->
                        if (!liveFormat.isNullOrBlank() && liveFormat != "UNKNOWN") {
                            liveFormat
                        } else if (dbFormat != null) {
                            val isLossless = dbFormat.codecLabel() == "FLAC" || dbFormat.codecLabel() == "ALAC"
                            val quality = if (isLossless) {
                                dbFormat.formattedQuality()
                            } else {
                                dbFormat.formattedBitrate()
                            }
                            "${dbFormat.codecLabel()} | $quality"
                        } else {
                            ""
                        }
                    }
                }
                .collect { format ->
                    if (format.isNotEmpty()) {
                        _uiState.update { it.copy(codecInfo = format) }
                    }
                }
        }

        // 1. Подписка на шаффл
        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { it.shuffleModeEnabled }
                .collect { enabled ->
                    _uiState.update { it.copy(shuffleState = if (enabled) "on" else "off") }
                }
        }

        // 2. Подписка на повтор
        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { it.repeatMode }
                .collect { mode ->
                    val state = when (mode) {
                        Player.REPEAT_MODE_OFF -> "off"
                        Player.REPEAT_MODE_ONE -> "one"
                        Player.REPEAT_MODE_ALL -> "all"
                        else -> "off"
                    }
                    _uiState.update { it.copy(repeatState = state) }
                }
        }

        val isFirstLaunch = settingsRepository.isFirstLaunch()
        _uiState.update { it.copy(shouldShowWelcome = isFirstLaunch) }

        viewModelScope.launch {
            settingsRepository.lyricsRomanizationPrefsFlow.collect { prefs ->
                _uiState.update { it.copy(lyricsRomanizationPrefs = prefs) }
                if (_uiState.value.lyricsList.isNotEmpty()) {
                    startRomanizationJob(_uiState.value.lyricsList, lyricsFetchGeneration.get())
                }
            }
        }

        viewModelScope.launch {
            connectionHolder.connection
                .flatMapLatest { connection ->
                    if (connection != null) {
                        combine(
                            connection.queueWindows,
                            connection.currentWindowIndex,
                            connection.queueTitle
                        ) { windows, index, title ->
                            QueueUiState(
                                queueWindows = windows,
                                currentWindowIndex = index,
                                title = title,
                                songCount = windows.size,
                                queueDurationMs = windows.sumOf { (it.mediaItem.metadata?.duration ?: 0).toLong() } * 1000L
                            )
                        }
                    } else {
                        flowOf(QueueUiState())
                    }
                }
                .collect { state ->
                    _queueState.value = state
                }
        }

    }

    // ==========================================
    // Единая точка входа для UI-действий шторки
    // ==========================================
    fun handleAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.PlayPause -> togglePlayPause()
            is PlayerAction.Next, is PlayerAction.SkipNext -> playNext()
            is PlayerAction.Previous, is PlayerAction.SkipPrevious -> playPrevious()
            is PlayerAction.PlayQueueItem -> audioPlayer?.seekToDefaultPosition(action.index)
            is PlayerAction.RemoveQueueItem -> audioPlayer?.removeMediaItem(action.index)
            is PlayerAction.MoveQueueItem -> audioPlayer?.moveMediaItem(action.from, action.to)
            is PlayerAction.ClearQueue -> playerConnection?.clearQueue()
            is PlayerAction.ShuffleQueue -> toggleShuffle()
            is PlayerAction.ToggleAutoMix -> {
                val enabled = !_uiState.value.isAutoMixEnabled
                _uiState.update { it.copy(isAutoMixEnabled = enabled) }
                if (enabled) {
                    playerConnection?.service?.onInfiniteQueueEnabled()
                } else {
                    playerConnection?.service?.onInfiniteQueueDisabled()
                }
            }
            is PlayerAction.Like, is PlayerAction.ToggleLike -> toggleLike()
            is PlayerAction.Shuffle -> toggleShuffle()
            is PlayerAction.Repeat -> toggleRepeat()
            is PlayerAction.ToggleAutoDownload -> setAutoDownloadEnabled(!_uiState.value.isAutoDownloadEnabled)
            is PlayerAction.SearchLyrics -> refreshLyrics()
            is PlayerAction.Lyrics -> setLyricsVisible(true)
            is PlayerAction.StartSleepTimer -> startSleepTimer(action.minutes)
            is PlayerAction.StopSleepTimer -> stopSleepTimer()
            is PlayerAction.AdjustSleepTimer -> adjustSleepTimer(action.minutes)
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
            is PlayerAction.ToggleCodecInfo -> {
                val newValue = !_uiState.value.showCodecInfo
                settingsRepository.setShowCodecInfoEnabled(newValue)
                _uiState.update { it.copy(showCodecInfo = newValue) }
            }
            is PlayerAction.ToggleAlbumCoverGlow -> {
                val newValue = !_uiState.value.isAlbumCoverGlowEnabled
                settingsRepository.setAlbumCoverGlowEnabled(newValue)
                _uiState.update { it.copy(isAlbumCoverGlowEnabled = newValue) }
            }
            is PlayerAction.UpdateColors -> {
                _uiState.update {
                    it.copy(
                        vibrantColor = action.vibrant,
                        darkMutedColor = action.darkMuted,
                        gradientColor = action.gradient,
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    application.dataStore.edit { prefs ->
                        prefs[LastVibrantColorKey] = action.vibrant
                        prefs[LastDarkMutedColorKey] = action.darkMuted
                        prefs[LastGradientColorKey] = action.gradient
                    }
                }
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
        val timer = sleepTimer ?: return
        val currentSeconds = timer.remainingSeconds.value
        val newMinutes = (currentSeconds / 60) + minutes
        if (newMinutes > 0) {
            timer.start(newMinutes.toInt())
        } else {
            timer.stop()
        }
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
        sleepTimer?.start(minutes)
    }

    fun stopSleepTimer() {
        sleepTimer?.stop()
    }


    fun dismissWelcome() {
        settingsRepository.setFirstLaunch(false)
        _uiState.update { it.copy(shouldShowWelcome = false) }
    }

    fun setAutoDownloadEnabled(enabled: Boolean) {
        settingsRepository.setAutoDownloadLyricsEnabled(enabled)
        _uiState.update { it.copy(isAutoDownloadEnabled = enabled) }
    }

    fun setBlurBackgroundEnabled(enabled: Boolean) {
        settingsRepository.setBlurBackgroundEnabled(enabled)
        _uiState.update { it.copy(isBlurBackgroundEnabled = enabled) }
     }

    fun setImmersiveEnabled(enabled: Boolean) {
        settingsRepository.setImmersiveEnabled(enabled)
        _uiState.update { it.copy(isImmersiveEnabled = enabled) }
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

        if (current.trackUrl == trackUrl && trackUrl.isNotEmpty()) {
            _uiState.update {
                it.copy(isPlaying = isPlaying, isLiked = isLiked, title = title, artist = artist)
            }
            if (current.isPlaying != isPlaying) {
                manageTicker(isPlaying)
            }
            return
        }

        lyricsFetchGeneration.incrementAndGet()
        lyricsJob?.cancel()
        romanizationJob?.cancel()

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
                coverUrl = coverUrl,
            )
        }

        manageTicker(isPlaying)

        if (coverUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    vibrantColor = android.graphics.Color.WHITE,
                    darkMutedColor = android.graphics.Color.parseColor("#282828"),
                    gradientColor = android.graphics.Color.parseColor("#121212"),
                )
            }
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
        if (!isUserSeeking) {
            val progressMs = currentTimeSec * 1000L
            val durationMs = durationSec * 1000L
            _playbackProgress.value = progressMs
            _uiState.update {
                it.copy(durationMs = durationMs)
            }
            updateLyricsProgress(progressMs)
        }
    }

    fun onSeekStarted() {
        isUserSeeking = true
    }

    fun onSeekFinished() {
        isUserSeeking = false
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
        _uiState.update {
            it.copy(lyricsList = emptyList(), currentLineIndex = -1, lyricsError = null)
        }
    }

    private fun manageTicker(isPlaying: Boolean) {
        tickerJob?.cancel()
        if (isPlaying) {
            tickerJob = viewModelScope.launch {
                while (isActive) {
                    val player = audioPlayer
                    if (player != null && !isUserSeeking) {
                        val position = player.currentPosition
                        _playbackProgress.value = position
                        val duration = player.duration
                        val validDuration = if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) duration else null
                        if (validDuration != null) {
                            _uiState.update { current ->
                                if (current.durationMs <= 0L || current.durationMs != validDuration) {
                                    current.copy(durationMs = validDuration)
                                } else {
                                    current
                                }
                            }
                        }
                        updateLyricsProgress(position)
                    }
                    delay(250) // 4 обновления в секунду достаточно для плавной отрисовки без перегрузки CPU
                }
            }
        }
    }

    private fun findCurrentLineIndex(lyricsList: List<LyricsEntry>, progressMs: Long, syncOffset: Int): Int {
        val adjustedProgressMs = (progressMs + syncOffset).coerceAtLeast(0L)
        return moe.rukamori.archivetune.lyrics.LyricsUtils.findCurrentLineIndex(lyricsList, adjustedProgressMs, 300L)
    }

    private fun updateLyricsProgress(progressMs: Long) {
        val state = _uiState.value
        if (state.lyricsList.isEmpty() || !state.isLyricsVisible) return

        if (!state.isSynced || state.lyricsList.all { it.time == -1L }) {
            if (state.currentLineIndex != -1) {
                _uiState.update { it.copy(currentLineIndex = -1) }
            }
            return
        }

        val targetIndex = findCurrentLineIndex(state.lyricsList, progressMs, state.lyricsSyncOffset)

        if (targetIndex != state.currentLineIndex) {
            _uiState.update { it.copy(currentLineIndex = targetIndex) }
        }
    }

    private fun prepareLyricsEditText() {
        val trackId = _uiState.value.trackUrl
        if (trackId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val db = playerConnection?.database
            val cached = db?.getLyricsById(trackId)
            var rawText = cached?.lyrics ?: ""
            if (rawText.isBlank()) {
                rawText = _uiState.value.lyricsList.joinToString("\n") { line ->
                    val min = line.time / 60000
                    val sec = (line.time % 60000) / 1000
                    val ms = (line.time % 1000) / 10
                    String.format(java.util.Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(lyricsEditText = rawText) }
            }
        }
    }

    private fun saveLyrics(text: String) {
        val metadata = playerConnection?.mediaMetadata?.value ?: return
        val trackId = metadata.id ?: return
        if (trackId.isEmpty()) return
        val durationMs = audioPlayer?.duration?.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
        viewModelScope.launch(Dispatchers.IO) {
            playerConnection?.database?.query {
                replaceLyrics(
                    id = trackId,
                    lyrics = text,
                    source = moe.rukamori.archivetune.db.entities.LyricsEntity.Source.USER_EDIT.value
                )
            }
            val parsedLines = parseLyrics(text, durationMs)
            startRomanizationJob(parsedLines, lyricsFetchGeneration.get())
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.time > 0 }) }
            }
        }
    }

    private fun translateLyrics(langCode: String, useAi: Boolean) {
        val metadata = playerConnection?.mediaMetadata?.value ?: return
        val trackId = metadata.id ?: return
        if (trackId.isEmpty()) return
        val durationMs = audioPlayer?.duration?.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L

        viewModelScope.launch(Dispatchers.IO) {
            if (useAi) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isAiTranslating = true, aiTranslationError = null) }
                }
                try {
                    val db = playerConnection?.database ?: return@launch
                    val cached = db.getLyricsById(trackId)
                    var rawText = cached?.lyrics ?: ""
                    if (rawText.isBlank()) {
                        rawText = _uiState.value.lyricsList.joinToString("\n") { line ->
                            val min = line.time / 60000
                            val sec = (line.time % 60000) / 1000
                            val ms = (line.time % 1000) / 10
                            String.format(java.util.Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
                        }
                    }
                    if (rawText.isBlank()) {
                        throw IllegalStateException("Lyrics are empty")
                    }

                    val prefs = application.dataStore.data.first()
                    val translatedLyrics = AiLyricsTranslator().translate(
                        config = AiServiceConfig(
                            provider = prefs[AiProviderKey].toEnum(AiProvider.NONE),
                            apiKey = prefs[AiApiKeyKey].orEmpty(),
                            customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
                            model = if (prefs[AiProviderKey].toEnum(AiProvider.NONE) == AiProvider.CUSTOM) {
                                prefs[AiCustomModelKey].orEmpty()
                            } else {
                                prefs[AiSelectedModelKey].orEmpty()
                            },
                        ),
                        lyrics = rawText,
                        targetLanguage = langCode.ifBlank { "ENGLISH" },
                    )

                    db.query {
                        replaceLyrics(
                            id = trackId,
                            lyrics = translatedLyrics,
                            source = moe.rukamori.archivetune.db.entities.LyricsEntity.Source.AI_TRANSLATION.value,
                        )
                    }

                    application.dataStore.edit { settings ->
                        settings[AiApiValidationStatusKey] = AiApiValidationStatus.SUCCESS.name
                    }
                    val parsedLines = parseLyrics(translatedLyrics, durationMs)
                    startRomanizationJob(parsedLines, lyricsFetchGeneration.get())
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isAiTranslating = false, lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.time > 0 }) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    application.dataStore.edit { settings ->
                        settings[AiApiValidationStatusKey] = AiApiValidationStatus.FAILED.name
                    }
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isAiTranslating = false, aiTranslationError = e.localizedMessage ?: e.toString()) }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isStandardTranslating = true, aiTranslationError = null) }
                }
                try {
                    val db = playerConnection?.database ?: return@launch
                    val cached = db.getLyricsById(trackId)
                    var rawText = cached?.lyrics ?: ""
                    if (rawText.isBlank()) {
                        rawText = _uiState.value.lyricsList.joinToString("\n") { line ->
                            val min = line.time / 60000
                            val sec = (line.time % 60000) / 1000
                            val ms = (line.time % 1000) / 10
                            String.format(java.util.Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
                        }
                    }
                    if (rawText.isBlank()) {
                        throw IllegalStateException("Lyrics are empty")
                    }

                    val lang = try {
                        Language(langCode)
                    } catch (e: Exception) {
                        null
                    }
                    if (lang == null) {
                        throw IllegalArgumentException("Unsupported language code: $langCode")
                    }

                    val translatedLyrics = LyricsTranslator.translate(rawText, lang)
                    db.query {
                        replaceLyrics(
                            id = trackId,
                            lyrics = translatedLyrics,
                            source = moe.rukamori.archivetune.db.entities.LyricsEntity.Source.AI_TRANSLATION.value,
                        )
                    }
                    val parsedLines = parseLyrics(translatedLyrics, durationMs)
                    startRomanizationJob(parsedLines, lyricsFetchGeneration.get())
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isStandardTranslating = false, lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.time > 0 }) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isStandardTranslating = false, aiTranslationError = e.localizedMessage ?: e.toString()) }
                    }
                }
            }
        }
    }

    fun fetchLyrics(force: Boolean = false) {
        val currentState = _uiState.value
        val trackUrl = currentState.trackUrl
        val title = currentState.title
        val artist = currentState.artist

        if (trackUrl.isEmpty() || title.isEmpty()) return

        val generation = lyricsFetchGeneration.incrementAndGet()
        lyricsJob?.cancel()
        romanizationJob?.cancel()

        _uiState.update { it.copy(isLoadingLyrics = true, lyricsError = null) }

        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val db = playerConnection?.database
                val cached = if (force) null else db?.getLyricsById(trackUrl)
                
                if (cached != null && cached.lyrics != moe.rukamori.archivetune.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
                    success = true
                    return@launch
                }

                // 👇 АДАПТИРУЙ под свою сигнатуру MediaMetadata
                val currentMetadata = playerConnection?.mediaMetadata?.value
                val metadata = moe.rukamori.archivetune.models.MediaMetadata(
                    id = trackUrl,
                    title = title,
                    artists = listOf(moe.rukamori.archivetune.models.MediaMetadata.Artist(id = null,  name = artist)),
                    duration = (currentState.durationMs / 1000).toInt(),
                    album = currentMetadata?.album
                )

                val rawLyrics = kotlinx.coroutines.withTimeoutOrNull(15000) {
                    lyricsHelper.getLyrics(metadata)
                } ?: ""

                ensureActive()
                if (generation != lyricsFetchGeneration.get()) return@launch

                if (rawLyrics.isNotBlank()) {
                    playerConnection?.database?.query {
                        replaceLyrics(
                            id = trackUrl,
                            lyrics = rawLyrics,
                            source = moe.rukamori.archivetune.db.entities.LyricsEntity.Source.REMOTE.value
                        )
                    }
                    success = true
                } else {
                    if (generation != lyricsFetchGeneration.get()) return@launch
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                lyricsError = "lyrics_not_found"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation != lyricsFetchGeneration.get()) return@launch
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            lyricsError = "lyrics_error_loading"
                        )
                    }
                }
            } finally {
                if (generation == lyricsFetchGeneration.get()) {
                    withContext(kotlinx.coroutines.NonCancellable) {
                        withContext(Dispatchers.Main) {
                            if (generation == lyricsFetchGeneration.get() && !success) {
                                _uiState.update { it.copy(isLoadingLyrics = false) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseLyrics(rawLyrics: String, durationMs: Long): List<LyricsEntry> {
        if (rawLyrics.isBlank() || rawLyrics == moe.rukamori.archivetune.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
            return emptyList()
        }

        val normalized = moe.rukamori.archivetune.lyrics.LyricsUtils.normalizeLyricsText(rawLyrics)

        val parsed = when {
            moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml(normalized) -> {
                moe.rukamori.archivetune.lyrics.LyricsUtils.parseTtml(normalized, (durationMs / 1000).toInt())
            }
            moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc(normalized) -> {
                moe.rukamori.archivetune.lyrics.LyricsUtils.parseLyrics(normalized)
            }
            else -> {
                normalized.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        LyricsEntry(time = -1L, text = line.trim())
                    }
            }
        }

        return moe.rukamori.archivetune.lyrics.LyricsUtils.insertInstrumentalBreaks(parsed, durationMs)
    }

    fun setLyricsVisible(isVisible: Boolean) {
        _uiState.update { 
            val targetIndex = if (!isVisible || !it.isSynced || it.lyricsList.all { line -> line.time == -1L }) -1 else findCurrentLineIndex(it.lyricsList, _playbackProgress.value, it.lyricsSyncOffset)
            it.copy(isLyricsVisible = isVisible, currentLineIndex = targetIndex) 
        }
        if (isVisible && _uiState.value.lyricsList.isEmpty()) {
            fetchLyrics()
        }
    }

    fun refreshLyrics() {
        fetchLyrics(force = true)
    }

    fun togglePlayPause() {
        val player = audioPlayer ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        if (player.playWhenReady) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        audioPlayer?.seekTo(positionMs)
        isUserSeeking = false
        _playbackProgress.value = positionMs
    }

    fun playNext() {
        playerConnection?.seekToNext()
    }

    fun playPrevious() {
        playerConnection?.seekToPrevious()
    }

    fun toggleShuffle() {
        val player = audioPlayer ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val player = audioPlayer ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleLike() {
        playerConnection?.toggleLike()
    }

    private fun shareTrack() {
        val trackId = _uiState.value.trackUrl
        if (trackId.isEmpty()) return

        val shareUrl = "https://music.youtube.com/watch?v=$trackId"
        viewModelScope.launch {
            _event.send(PlayerEvent.ShareTrack(shareUrl))
        }
    }

    private fun startRomanizationJob(entries: List<moe.rukamori.archivetune.lyrics.LyricsEntry>, generation: Long) {
        romanizationJob?.cancel()
        if (entries.isEmpty()) return

        val prefs = _uiState.value.lyricsRomanizationPrefs ?: moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences()
        if (!prefs.isEnabled) {
            entries.forEach { it.romanizedTextFlow.value = null }
            return
        }

        romanizationJob = viewModelScope.launch(Dispatchers.Default) {
            for (entry in entries) {
                ensureActive()
                if (lyricsFetchGeneration.get() != generation) return@launch

                val provided = moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedTextForEntry(entry, prefs)
                if (provided != null) {
                    entry.romanizedTextFlow.value = provided
                    continue
                }

                if (!moe.rukamori.archivetune.lyrics.LyricsUtils.shouldRomanizeLyricsLine(entry.text, prefs)) {
                    entry.romanizedTextFlow.value = null
                    continue
                }

                val romanized = moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeLyricsLine(entry.text, prefs)
                if (lyricsFetchGeneration.get() == generation) {
                    entry.romanizedTextFlow.value = romanized
                }
            }
        }
    }
}
