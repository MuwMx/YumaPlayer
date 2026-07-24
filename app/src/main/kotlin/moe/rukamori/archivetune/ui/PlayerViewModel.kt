package moe.rukamori.archivetune.ui

import android.app.Application
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import moe.rukamori.archivetune.data.repository.SettingsRepository
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.models.ParsedIntentAction
import moe.rukamori.archivetune.playback.AdvancedSleepTimer
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.LyricLine
import moe.rukamori.archivetune.ui.state.PlayerEvent
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.extractSeedColor
import moe.rukamori.archivetune.ui.theme.generateDarkColorSchemeFromSeed
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.lyrics.LyricsTranslator
import moe.rukamori.archivetune.ai.AiLyricsTranslator
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import me.bush.translator.Language
import kotlinx.coroutines.CancellationException
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
    private val sleepTimer get() = playerConnection?.service?.sleepTimer
    private val audioPlayer get() = playerConnection?.player

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            isBlurBackgroundEnabled = settingsRepository.isBlurBackgroundEnabled(),
            isAutoDownloadEnabled = settingsRepository.isAutoDownloadLyricsEnabled(),
            isImmersiveEnabled = settingsRepository.isImmersiveEnabled(),
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()


    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.NoUpdate)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var lastFetchedTrackKey: String = ""
    private var isUserSeeking = false
    private var tickerJob: Job? = null
    private var lyricsJob: Job? = null
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
                    if (cached != null) {
                        val parsedLines = parseLyrics(cached.lyrics)
                        _uiState.update {
                            it.copy(
                                lyricsList = parsedLines,
                                isSynced = parsedLines.any { line -> line.timeMs > 0 },
                                lyricsError = if (parsedLines.isEmpty()) "lyrics_not_found" else null
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
                    if (metadata == null) return@collect
                    val title = metadata.title
                    val artist = metadata.artists.joinToString { it.name }

                    _uiState.update { currentUi ->
                        currentUi.copy(
                            title = title,
                            artist = artist,
                            trackUrl = metadata.id,
                            durationMs = metadata.duration * 1000L
                        )
                    }

                    loadCoverAndPalette(metadata.thumbnailUrl ?: "", title, artist)
                }
        }

        viewModelScope.launch {
            connectionHolder.connection
                .filterNotNull()
                .flatMapLatest { it.playbackState }
                .collect { playbackState ->
                    _uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
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
    }

    // ==========================================
    // Единая точка входа для UI-действий шторки
    // ==========================================
    fun handleAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.PlayPause -> togglePlayPause()
            is PlayerAction.Next, is PlayerAction.SkipNext -> playNext()
            is PlayerAction.Previous, is PlayerAction.SkipPrevious -> playPrevious()
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
            is PlayerAction.TranslateLyrics -> {
                translateLyrics(action.langCode, action.useAi)
            }
            else -> { /* Временный фолбэк для остальных экшенов */ }
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

        if (current.title == title && current.artist == artist) {
            _uiState.update {
                it.copy(isPlaying = isPlaying, isLiked = isLiked, trackUrl = trackUrl)
            }
            if (current.isPlaying != isPlaying) {
                manageTicker(isPlaying)
            }
            return
        }

        _uiState.update {
            it.copy(
                title = title,
                artist = artist,
                trackUrl = trackUrl,
                isPlaying = isPlaying,
                isLiked = isLiked,
                lyricsList = emptyList(),
                lyricsError = null,
                currentLineIndex = -1
            )
        }

        manageTicker(isPlaying)

        if (coverUrl.isNotEmpty()) {
            loadCoverAndPalette(coverUrl, title, artist)
        } else {
            _uiState.update {
                it.copy(
                    vibrantColor = android.graphics.Color.WHITE,
                    darkMutedColor = android.graphics.Color.parseColor("#282828"),
                    gradientColor = android.graphics.Color.parseColor("#121212")
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
            _uiState.update {
                it.copy(progressMs = progressMs, durationMs = durationMs)
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
                                    _event.send(PlayerEvent.Navigate("album/$browseId"))
                                }
                            }
                    }
                } else {
                    viewModelScope.launch {
                        _event.send(PlayerEvent.Navigate("online_playlist/$playlistId"))
                    }
                }
            }
            is ParsedIntentAction.YouTubeAlbum -> {
                viewModelScope.launch {
                    _event.send(PlayerEvent.Navigate("album/${action.browseId}"))
                }
            }
            is ParsedIntentAction.YouTubeArtist -> {
                viewModelScope.launch {
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
                        _uiState.update { current ->
                            current.copy(progressMs = player.currentPosition)
                        }
                        updateLyricsProgress(position)
                    }
                    delay(250) // 4 обновления в секунду достаточно для плавной отрисовки без перегрузки CPU
                }
            }
        }
    }

    private fun updateLyricsProgress(progressMs: Long) {
        val state = _uiState.value
        if (state.lyricsList.isEmpty() || !state.isLyricsVisible || !state.isSynced) return

        val adjustedProgressMs = (progressMs + state.lyricsSyncOffset).coerceAtLeast(0L)

        var targetIndex = -1
        for (i in state.lyricsList.indices) {
            if (adjustedProgressMs >= state.lyricsList[i].timeMs) {
                targetIndex = i
            } else {
                break
            }
        }

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
                    val min = line.timeMs / 60000
                    val sec = (line.timeMs % 60000) / 1000
                    val ms = (line.timeMs % 1000) / 10
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
        viewModelScope.launch(Dispatchers.IO) {
            playerConnection?.database?.query {
                replaceLyrics(
                    id = trackId,
                    lyrics = text,
                    source = moe.rukamori.archivetune.db.entities.LyricsEntity.Source.USER_EDIT.value
                )
            }
            val parsedLines = parseLyrics(text)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.timeMs > 0 }) }
            }
        }
    }

    private fun translateLyrics(langCode: String, useAi: Boolean) {
        val metadata = playerConnection?.mediaMetadata?.value ?: return
        val trackId = metadata.id ?: return
        if (trackId.isEmpty()) return

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
                            val min = line.timeMs / 60000
                            val sec = (line.timeMs % 60000) / 1000
                            val ms = (line.timeMs % 1000) / 10
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
                    val parsedLines = parseLyrics(translatedLyrics)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isAiTranslating = false, lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.timeMs > 0 }) }
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
                            val min = line.timeMs / 60000
                            val sec = (line.timeMs % 60000) / 1000
                            val ms = (line.timeMs % 1000) / 10
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
                    val parsedLines = parseLyrics(translatedLyrics)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isStandardTranslating = false, lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.timeMs > 0 }) }
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

    private fun loadCoverAndPalette(url: String, targetTitle: String, targetArtist: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(application)
                    .data(url)
                    .allowHardware(false) // Обязательно false для анализа пикселей
                    .build()

                val result = application.imageLoader.execute(request)
                val bitmap = result.image?.toBitmap()

                if (bitmap != null) {
                    val seedColor = extractSeedColor(bitmap)
                    val scheme = generateDarkColorSchemeFromSeed(seedColor)

                    withContext(Dispatchers.Main) {
                        if (_uiState.value.title == targetTitle && _uiState.value.artist == targetArtist) {
                            _uiState.update {
                                it.copy(
                                    coverUrl = url, // Сохраняем только чистый URL
                                    vibrantColor = scheme.primary.toArgb(),
                                    darkMutedColor = scheme.secondaryContainer.toArgb(),
                                    gradientColor = scheme.primaryContainer.toArgb()
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            coverUrl = url,
                            vibrantColor = android.graphics.Color.WHITE,
                            darkMutedColor = android.graphics.Color.parseColor("#282828"),
                            gradientColor = android.graphics.Color.parseColor("#121212")
                        )
                    }
                }
            }
        }
    }
    fun fetchLyrics() {
        val currentState = _uiState.value
        val trackUrl = currentState.trackUrl
        val title = currentState.title
        val artist = currentState.artist

        if (trackUrl.isEmpty() || title.isEmpty()) return

        _uiState.update { it.copy(isLoadingLyrics = true, lyricsError = null) }

        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 👇 АДАПТИРУЙ под свою сигнатуру MediaMetadata
                val metadata = moe.rukamori.archivetune.models.MediaMetadata(
                    id = trackUrl,
                    title = title,
                    artists = listOf(moe.rukamori.archivetune.models.MediaMetadata.Artist(id = null,  name = artist)),
                    duration = (currentState.durationMs / 1000).toInt(),
                    album = null
                )

                val rawLyrics = lyricsHelper.getLyrics(metadata)
                val parsedLines = parseLyrics(rawLyrics)

                if (rawLyrics.isNotBlank() && rawLyrics != moe.rukamori.archivetune.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
                    playerConnection?.database?.query {
                        replaceLyrics(
                            id = trackUrl,
                            lyrics = rawLyrics,
                            source = moe.rukamori.archivetune.db.entities.LyricsEntity.Source.REMOTE.value
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            lyricsList = parsedLines,
                            isSynced = parsedLines.any { line -> line.timeMs > 0 },
                            isLoadingLyrics = false,
                            lyricsError = if (parsedLines.isEmpty()) "lyrics_not_found" else null,
                            currentLineIndex = -1
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLoadingLyrics = false,
                            lyricsError = "lyrics_error_loading"
                        )
                    }
                }
            }
        }
    }

    private fun parseLyrics(rawLyrics: String): List<LyricLine> {
        if (rawLyrics.isBlank() || rawLyrics == moe.rukamori.archivetune.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
            return emptyList()
        }

        val lines = mutableListOf<LyricLine>()
        val lrcPattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.+)""")

        rawLyrics.lines().forEach { line ->
            val match = lrcPattern.find(line)
            if (match != null) {
                val (min, sec, ms, text) = match.destructured
                val timeMs = (min.toLong() * 60 + sec.toLong()) * 1000 +
                        (if (ms.length == 2) ms.toLong() * 10 else ms.toLong())
                val cleanText = text.trim()

                lines.add(
                    LyricLine(
                        text = cleanText,
                        timeMs = timeMs,
                        startChar = 0,
                        endChar = cleanText.length,
                    )
                )
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun setLyricsVisible(isVisible: Boolean) {
        _uiState.update { it.copy(isLyricsVisible = isVisible) }
        if (isVisible && _uiState.value.lyricsList.isEmpty()) {
            fetchLyrics()
        }
    }

    fun refreshLyrics() {
        fetchLyrics()
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
        _uiState.update { it.copy(progressMs = positionMs) }
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
}
