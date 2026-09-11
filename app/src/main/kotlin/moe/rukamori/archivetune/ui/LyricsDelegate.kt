package moe.rukamori.archivetune.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.bush.translator.Language
import moe.rukamori.archivetune.ai.AiLyricsTranslator
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.lyrics.LrcParser
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.lyrics.LyricsTranslator
import moe.rukamori.archivetune.lyrics.Romanizer
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.utils.dataStore
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class LyricsDelegate(
    private val application: Application,
    private val coroutineScope: CoroutineScope,
    private val lyricsHelper: LyricsHelper,
    private val playerConnectionProvider: () -> PlayerConnection?,
    private val audioPlayerProvider: () -> Player?,
    private val uiState: StateFlow<PlayerUiState>,
    private val updateUiState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val playbackProgressProvider: () -> Long,
) {
    private var lyricsJob: Job? = null
    private var romanizationJob: Job? = null
    private val lyricsFetchGeneration = AtomicLong(0)

    val currentGeneration: Long
        get() = lyricsFetchGeneration.get()

    fun nextGeneration(): Long = lyricsFetchGeneration.incrementAndGet()

    fun cancelJobs() {
        lyricsFetchGeneration.incrementAndGet()
        lyricsJob?.cancel()
        romanizationJob?.cancel()
    }

    fun resetLyrics() {
        cancelJobs()
        updateUiState {
            it.copy(
                lyricsList = emptyList(),
                isSynced = false,
                currentLineIndex = -1,
                lyricsError = null,
                isLoadingLyrics = false,
            )
        }
    }

    fun deleteLyricsCache() {
        updateUiState {
            it.copy(lyricsList = emptyList(), currentLineIndex = -1, lyricsError = null)
        }
    }

    fun findCurrentLineIndex(lyricsList: List<LyricsEntry>, progressMs: Long, syncOffset: Int): Int {
        val adjustedProgressMs = (progressMs + syncOffset).coerceAtLeast(0L)
        return LrcParser.findCurrentLineIndex(lyricsList, adjustedProgressMs, 300L)
    }

    fun updateLyricsProgress(progressMs: Long) {
        val state = uiState.value
        if (state.lyricsList.isEmpty() || !state.isLyricsVisible) return

        if (!state.isSynced || state.lyricsList.all { it.time == -1L }) {
            if (state.currentLineIndex != -1) {
                updateUiState { it.copy(currentLineIndex = -1) }
            }
            return
        }

        val targetIndex = findCurrentLineIndex(state.lyricsList, progressMs, state.lyricsSyncOffset)

        if (targetIndex != state.currentLineIndex) {
            updateUiState { it.copy(currentLineIndex = targetIndex) }
        }
    }

    fun onCurrentLyricsUpdated(
        cached: LyricsEntity?,
        audioPlayer: Player?,
        playbackProgressMs: Long,
    ) {
        if (cached == null || cached.lyrics == LYRICS_NOT_FOUND || cached.lyrics.isBlank()) {
            updateUiState { current ->
                current.copy(
                    lyricsList = emptyList(),
                    isSynced = false,
                    isLoadingLyrics = false,
                    lyricsError = if (cached?.lyrics == LYRICS_NOT_FOUND) "lyrics_not_found" else null,
                    currentLineIndex = -1,
                )
            }
            return
        }

        val durationMs = audioPlayer?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
        val parsedLines = parseLyrics(cached.lyrics, durationMs)
        val isSynced = parsedLines.any { line -> line.time > 0 }
        startRomanizationJob(parsedLines)
        updateUiState { current ->
            val targetIndex = if (isSynced) {
                findCurrentLineIndex(parsedLines, playbackProgressMs, current.lyricsSyncOffset)
            } else {
                -1
            }
            current.copy(
                lyricsList = parsedLines,
                isSynced = isSynced,
                isLoadingLyrics = false,
                lyricsError = if (parsedLines.isEmpty()) "lyrics_not_found" else null,
                currentLineIndex = targetIndex,
            )
        }
    }

    fun prepareLyricsEditText() {
        val trackId = uiState.value.trackUrl
        if (trackId.isEmpty()) return
        coroutineScope.launch(Dispatchers.IO) {
            val db = playerConnectionProvider()?.database
            val cached = db?.getLyricsById(trackId)
            var rawText = cached?.lyrics ?: ""
            if (rawText.isBlank()) {
                rawText = uiState.value.lyricsList.joinToString("\n") { line ->
                    val min = line.time / 60000
                    val sec = (line.time % 60000) / 1000
                    val ms = (line.time % 1000) / 10
                    String.format(Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
                }
            }
            withContext(Dispatchers.Main) {
                updateUiState { it.copy(lyricsEditText = rawText) }
            }
        }
    }

    fun saveLyrics(text: String) {
        val connection = playerConnectionProvider() ?: return
        val metadata = connection.mediaMetadata.value ?: return
        val trackId = metadata.id
        if (trackId.isEmpty()) return
        val durationMs = audioPlayerProvider()?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
        coroutineScope.launch(Dispatchers.IO) {
            connection.database.query {
                replaceLyrics(
                    id = trackId,
                    lyrics = text,
                    source = LyricsEntity.Source.USER_EDIT.value
                )
            }
            val parsedLines = parseLyrics(text, durationMs)
            startRomanizationJob(parsedLines, lyricsFetchGeneration.get())
            withContext(Dispatchers.Main) {
                updateUiState { it.copy(lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.time > 0 }) }
            }
        }
    }

    fun translateLyrics(langCode: String, useAi: Boolean) {
        val connection = playerConnectionProvider() ?: return
        val metadata = connection.mediaMetadata.value ?: return
        val trackId = metadata.id
        if (trackId.isEmpty()) return
        val durationMs = audioPlayerProvider()?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L

        coroutineScope.launch(Dispatchers.IO) {
            if (useAi) {
                withContext(Dispatchers.Main) {
                    updateUiState { it.copy(isAiTranslating = true, aiTranslationError = null) }
                }
                try {
                    val db = connection.database
                    val cached = db.getLyricsById(trackId)
                    var rawText = cached?.lyrics ?: ""
                    if (rawText.isBlank()) {
                        rawText = uiState.value.lyricsList.joinToString("\n") { line ->
                            val min = line.time / 60000
                            val sec = (line.time % 60000) / 1000
                            val ms = (line.time % 1000) / 10
                            String.format(Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
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
                            source = LyricsEntity.Source.AI_TRANSLATION.value,
                        )
                    }

                    application.dataStore.edit { settings ->
                        settings[AiApiValidationStatusKey] = AiApiValidationStatus.SUCCESS.name
                    }
                    val parsedLines = parseLyrics(translatedLyrics, durationMs)
                    startRomanizationJob(parsedLines, lyricsFetchGeneration.get())
                    withContext(Dispatchers.Main) {
                        updateUiState { it.copy(isAiTranslating = false, lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.time > 0 }) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    application.dataStore.edit { settings ->
                        settings[AiApiValidationStatusKey] = AiApiValidationStatus.FAILED.name
                    }
                    withContext(Dispatchers.Main) {
                        updateUiState { it.copy(isAiTranslating = false, aiTranslationError = e.localizedMessage ?: e.toString()) }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    updateUiState { it.copy(isStandardTranslating = true, aiTranslationError = null) }
                }
                try {
                    val db = connection.database
                    val cached = db.getLyricsById(trackId)
                    var rawText = cached?.lyrics ?: ""
                    if (rawText.isBlank()) {
                        rawText = uiState.value.lyricsList.joinToString("\n") { line ->
                            val min = line.time / 60000
                            val sec = (line.time % 60000) / 1000
                            val ms = (line.time % 1000) / 10
                            String.format(Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
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
                            source = LyricsEntity.Source.AI_TRANSLATION.value,
                        )
                    }
                    val parsedLines = parseLyrics(translatedLyrics, durationMs)
                    startRomanizationJob(parsedLines, lyricsFetchGeneration.get())
                    withContext(Dispatchers.Main) {
                        updateUiState { it.copy(isStandardTranslating = false, lyricsList = parsedLines, isSynced = parsedLines.any { line -> line.time > 0 }) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateUiState { it.copy(isStandardTranslating = false, aiTranslationError = e.localizedMessage ?: e.toString()) }
                    }
                }
            }
        }
    }

    fun fetchLyrics(force: Boolean = false) {
        val currentState = uiState.value
        val trackUrl = currentState.trackUrl
        val title = currentState.title
        val artist = currentState.artist

        if (trackUrl.isEmpty() || title.isEmpty()) return

        if (force) {
            lyricsHelper.clearCache()
        }

        val generation = lyricsFetchGeneration.incrementAndGet()
        lyricsJob?.cancel()
        romanizationJob?.cancel()

        updateUiState { it.copy(isLoadingLyrics = true, lyricsError = null) }

        lyricsJob = coroutineScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val db = playerConnectionProvider()?.database
                val cached = if (force) null else db?.getLyricsById(trackUrl)

                if (cached != null) {
                    if (cached.lyrics == LyricsEntity.LYRICS_NOT_FOUND) {
                        withContext(Dispatchers.Main) {
                            updateUiState {
                                it.copy(
                                    isLoadingLyrics = false,
                                    lyricsError = "lyrics_not_found",
                                )
                            }
                        }
                    }
                    success = true
                    return@launch
                }

                val currentMetadata = playerConnectionProvider()?.mediaMetadata?.value
                val metadata = MediaMetadata(
                    id = trackUrl,
                    title = title,
                    artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
                    duration = (currentState.durationMs / 1000).toInt(),
                    album = currentMetadata?.album
                )

                val rawLyrics = withTimeoutOrNull(15000) {
                    lyricsHelper.getLyrics(metadata)
                } ?: ""

                ensureActive()
                if (generation != lyricsFetchGeneration.get()) return@launch

                if (rawLyrics.isNotBlank() && rawLyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                    playerConnectionProvider()?.database?.query {
                        replaceLyrics(
                            id = trackUrl,
                            lyrics = rawLyrics,
                            source = LyricsEntity.Source.REMOTE.value
                        )
                    }
                    success = true
                } else {
                    playerConnectionProvider()?.database?.query {
                        replaceLyrics(
                            id = trackUrl,
                            lyrics = LyricsEntity.LYRICS_NOT_FOUND,
                            source = LyricsEntity.Source.REMOTE.value
                        )
                    }
                    if (generation != lyricsFetchGeneration.get()) return@launch
                    withContext(Dispatchers.Main) {
                        updateUiState {
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
                    updateUiState {
                        it.copy(
                            lyricsError = "lyrics_error_loading"
                        )
                    }
                }
            } finally {
                if (generation == lyricsFetchGeneration.get()) {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.Main) {
                            if (generation == lyricsFetchGeneration.get() && !success) {
                                updateUiState { it.copy(isLoadingLyrics = false) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun parseLyrics(rawLyrics: String, durationMs: Long): List<LyricsEntry> {
        if (rawLyrics.isBlank() || rawLyrics == LyricsEntity.LYRICS_NOT_FOUND) {
            return emptyList()
        }

        val normalized = LrcParser.normalizeLyricsText(rawLyrics)

        val parsed = when {
            LrcParser.isTtml(normalized) -> {
                LrcParser.parseTtml(normalized, (durationMs / 1000).toInt())
            }
            LrcParser.isLineSyncedLrc(normalized) -> {
                LrcParser.parseLyrics(normalized)
            }
            else -> {
                normalized.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        LyricsEntry(time = -1L, text = line.trim())
                    }
            }
        }

        return LrcParser.insertInstrumentalBreaks(parsed, durationMs)
    }

    fun refreshLyrics() {
        fetchLyrics(force = true)
    }

    fun setLyricsVisible(isVisible: Boolean) {
        updateUiState {
            val targetIndex = if (!isVisible || !it.isSynced || it.lyricsList.all { line -> line.time == -1L }) {
                -1
            } else {
                findCurrentLineIndex(it.lyricsList, playbackProgressProvider(), it.lyricsSyncOffset)
            }
            it.copy(isLyricsVisible = isVisible, currentLineIndex = targetIndex)
        }
        if (isVisible && uiState.value.lyricsList.isEmpty()) {
            fetchLyrics()
        }
    }

    fun startRomanizationJob(entries: List<LyricsEntry>, generation: Long = lyricsFetchGeneration.get()) {
        romanizationJob?.cancel()
        if (entries.isEmpty()) return

        val prefs = uiState.value.lyricsRomanizationPrefs ?: LyricsRomanizationPreferences()
        if (!prefs.isEnabled) {
            entries.forEach { it.romanizedTextFlow.value = null }
            return
        }

        romanizationJob = coroutineScope.launch(Dispatchers.Default) {
            for (entry in entries) {
                ensureActive()
                if (lyricsFetchGeneration.get() != generation) return@launch

                val provided = Romanizer.providedRomanizedTextForEntry(entry, prefs)
                if (provided != null) {
                    entry.romanizedTextFlow.value = provided
                    continue
                }

                if (!Romanizer.shouldRomanizeLyricsLine(entry.text, prefs)) {
                    entry.romanizedTextFlow.value = null
                    continue
                }

                val romanized = Romanizer.romanizeLyricsLine(entry.text, prefs)
                if (lyricsFetchGeneration.get() == generation) {
                    entry.romanizedTextFlow.value = romanized
                }
            }
        }
    }
}
