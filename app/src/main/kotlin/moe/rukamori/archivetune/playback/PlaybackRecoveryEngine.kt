package moe.rukamori.archivetune.playback

import android.net.Network
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.RelatedSongMap
import moe.rukamori.archivetune.extensions.findNextMediaItemById
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber

sealed interface RecoveryDecision {
    data object Retry : RecoveryDecision
    data object SkipNext : RecoveryDecision
    data object Stop : RecoveryDecision
    data object WaitForNetwork : RecoveryDecision
    data class RequireLogin(val mediaId: String, val targetUrl: String) : RecoveryDecision
    data class ShowToast(val messageResId: Int) : RecoveryDecision
    data object None : RecoveryDecision
}

class PlaybackRecoveryEngine(
    private val scope: CoroutineScope,
    private val playerActions: PlayerActions,
    private val cacheOps: CacheOps,
    private val networkState: NetworkState,
    private val loginPrompt: LoginPrompt,
    private val databaseProvider: () -> MusicDatabase,
) {
    interface PlayerActions {
        val currentMediaItem: MediaItem?
        val currentMediaItemIndex: Int
        val currentPosition: Long
        val playWhenReady: Boolean
        val playbackState: Int
        val isPlaying: Boolean
        val playbackSuppressionReason: Int
        val nextMediaItemIndex: Int
        fun findNextMediaItemById(mediaId: String): MediaItem?
        fun prepare()
        fun play()
        fun pause()
        fun stop()
        fun seekTo(mediaItemIndex: Int, positionMs: Long)
        fun seekTo(positionMs: Long)
        fun evictMediaConnectionPools()
        val isCrossfading: Boolean
        fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean)
        fun registerRetryAttempt(mediaId: String): Boolean
        fun shouldAutoSkipOnError(): Boolean
    }

    interface CacheOps {
        val playerCache: Cache
        val downloadCache: Cache
        fun isTrackFullyCached(mediaId: String): Boolean
        fun invalidatePlaybackUrlCache(mediaId: String)
        fun removeExtractorPlaybackUrl(mediaId: String)
        fun getCachedFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue?
        fun getCachedExtractorFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue?
        fun getPlayerCacheDirectorySizeBytes(): Long
    }

    fun interface NetworkState {
        fun isNetworkConnected(): Boolean
    }

    interface LoginPrompt {
        fun isAppInForeground(): Boolean
        fun openLogin(mediaId: String, targetUrl: String)
    }

    interface Delegate : PlayerActions, CacheOps, NetworkState, LoginPrompt

    constructor(
        scope: CoroutineScope,
        delegate: Delegate,
        databaseProvider: () -> MusicDatabase,
    ) : this(
        scope = scope,
        playerActions = delegate,
        cacheOps = delegate,
        networkState = delegate,
        loginPrompt = delegate,
        databaseProvider = databaseProvider,
    )

    val waitingForNetworkConnection = MutableStateFlow(false)
    internal var consecutivePlaybackErr = 0
    private var networkRecoveryGeneration: Long = 0L
    private var lastRevivedNetworkGeneration: Long = -1L
    private var lastForceReviveTimeMs: Long = 0L
    private var lastActiveNetwork: Network? = null
    internal var networkStallRecoveryJob: Job? = null
    private var lastLoginRecoveryPrompt: Pair<String, Long>? = null

    val consecutivePlaybackErrorCount: Int
        get() = consecutivePlaybackErr

    fun cancelNetworkStallRecovery() {
        networkStallRecoveryJob?.cancel()
        networkStallRecoveryJob = null
    }

    fun resetConsecutivePlaybackErr() {
        consecutivePlaybackErr = 0
    }

    fun onNetworkStatusChanged(isConnected: Boolean, currentNetwork: Network?) {
        if (!isConnected || currentNetwork == null) {
            networkStallRecoveryJob?.cancel()
            networkStallRecoveryJob = null
            lastActiveNetwork = null
            networkRecoveryGeneration++
            return
        }

        val isNewNetwork = currentNetwork != lastActiveNetwork
        if (isNewNetwork) {
            lastActiveNetwork = currentNetwork
            networkRecoveryGeneration++
            playerActions.evictMediaConnectionPools()
        }

        val currentGen = networkRecoveryGeneration

        if (waitingForNetworkConnection.value) {
            waitingForNetworkConnection.value = false
            if (playerActions.currentMediaItem != null && playerActions.playWhenReady &&
                playerActions.playbackState == Player.STATE_IDLE
            ) {
                lastRevivedNetworkGeneration = currentGen
                playerActions.evictMediaConnectionPools()
                playerActions.prepare()
                playerActions.play()
                return
            }
        }

        if (currentGen == lastRevivedNetworkGeneration) {
            return
        }

        val currentItem = playerActions.currentMediaItem
        if (currentItem != null && playerActions.playWhenReady && !currentItem.mediaId.isLocalMediaId()) {
            val state = playerActions.playbackState
            if (state == Player.STATE_BUFFERING || state == Player.STATE_READY) {
                scheduleNetworkStallRecovery(currentGen)
            }
        }
    }

    private fun waitOnNetworkError() {
        networkStallRecoveryJob?.cancel()
        networkStallRecoveryJob = null
        waitingForNetworkConnection.value = true
    }

    private fun scheduleNetworkStallRecovery(gen: Long) {
        networkStallRecoveryJob?.cancel()
        val initialPos = playerActions.currentPosition
        val initialIndex = playerActions.currentMediaItemIndex
        val initialMediaId = playerActions.currentMediaItem?.mediaId ?: return

        networkStallRecoveryJob =
            scope.launch {
                delay(NETWORK_STALL_WINDOW_MS)
                if (gen != networkRecoveryGeneration || gen == lastRevivedNetworkGeneration) return@launch
                if (!networkState.isNetworkConnected()) return@launch
                if (!playerActions.playWhenReady) return@launch
                if (playerActions.currentMediaItemIndex != initialIndex || playerActions.currentMediaItem?.mediaId != initialMediaId) return@launch

                val currentState = playerActions.playbackState
                val currentPos = playerActions.currentPosition
                val isStalled =
                    when (currentState) {
                        Player.STATE_BUFFERING -> true
                        Player.STATE_READY -> currentPos <= initialPos && !playerActions.isPlaying &&
                            playerActions.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                        else -> false
                    }

                if (isStalled) {
                    revivePlaybackFromStall(gen)
                }
            }
    }

    fun forceRevivePlayback(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastForceReviveTimeMs < FORCE_REVIVE_DEBOUNCE_MS) return false
        lastForceReviveTimeMs = now
        networkRecoveryGeneration++
        networkStallRecoveryJob?.cancel()
        networkStallRecoveryJob = null
        revivePlaybackFromStall()
        return true
    }

    fun revivePlaybackFromStall(gen: Long = networkRecoveryGeneration) {
        lastRevivedNetworkGeneration = gen
        if (playerActions.currentMediaItem == null) return
        if (playerActions.isCrossfading) {
            playerActions.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        }
        playerActions.evictMediaConnectionPools()
        val mediaItemIndex = playerActions.currentMediaItemIndex
        val resumePosition = playerActions.currentPosition.coerceAtLeast(0L)
        playerActions.stop()
        playerActions.prepare()
        if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex >= 0) {
            playerActions.seekTo(mediaItemIndex, resumePosition)
        } else {
            playerActions.seekTo(resumePosition)
        }
        playerActions.evictMediaConnectionPools()
        playerActions.play()
    }

    fun onPlayerError(error: PlaybackException): RecoveryDecision {
        val currentMediaId = playerActions.currentMediaItem?.mediaId ?: return RecoveryDecision.None
        val isLocalMedia = currentMediaId.isLocalMediaId()
        val isFullyCachedMedia = cacheOps.isTrackFullyCached(currentMediaId)
        val hasAnyCachedData = hasAnyCachedData(currentMediaId, isFullyCachedMedia)

        val isConnectionError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                ((error.cause?.cause is PlaybackException) &&
                    (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

        if (!isLocalMedia && !isFullyCachedMedia) {
            if (!networkState.isNetworkConnected()) {
                waitOnNetworkError()
                return RecoveryDecision.WaitForNetwork
            } else if (isConnectionError) {
                cacheOps.invalidatePlaybackUrlCache(currentMediaId)
                if (forceRevivePlayback()) return RecoveryDecision.Retry
            }
        }

        if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
            scope.launch(Dispatchers.IO) {
                runCatching { cacheOps.downloadCache.removeResource(currentMediaId) }
                runCatching { cacheOps.playerCache.removeResource(currentMediaId) }
            }
        }

        val loginTarget = findLoginRecoveryTarget(error)
        if (loginTarget != null) {
            promptLoginRecovery(currentMediaId, loginTarget)
            return RecoveryDecision.RequireLogin(currentMediaId, loginTarget)
        }

        if (hasLoginRequiredException(error)) {
            return RecoveryDecision.ShowToast(R.string.playback_requires_youtube_music_confirmation)
        }

        val retryableStreamFailure = findRetryableStreamFailure(error)
        if (retryableStreamFailure != null) {
            if (retryPlaybackAfterStreamFailure(currentMediaId, isFullyCachedMedia, retryableStreamFailure)) {
                return RecoveryDecision.Retry
            }
        }

        if (!isLocalMedia && isCacheCorruptionError(error, hasAnyCachedData)) {
            val mediaItemIndex = playerActions.currentMediaItemIndex
            val resumePosition = playerActions.currentPosition.coerceAtLeast(0L)

            Timber.tag(TAG).w(
                "Cache corruption / truncated stream for %s (fullyCached=%b); purging caches then retrying",
                currentMediaId,
                isFullyCachedMedia,
            )

            cacheOps.invalidatePlaybackUrlCache(currentMediaId)
            cacheOps.removeExtractorPlaybackUrl(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)

            scope.launch(Dispatchers.IO) {
                runCatching { cacheOps.playerCache.removeResource(currentMediaId) }
                if (!isFullyCachedMedia) {
                    runCatching { cacheOps.downloadCache.removeResource(currentMediaId) }
                } else {
                    Timber.tag(TAG).w(
                        "Keeping offline download for %s; corruption may require manual re-download",
                        currentMediaId,
                    )
                }

                withContext(Dispatchers.Main) {
                    if (playerActions.registerRetryAttempt(currentMediaId)) {
                        playerActions.seekTo(mediaItemIndex, resumePosition)
                        playerActions.prepare()
                    } else {
                        if (playerActions.shouldAutoSkipOnError()) skipOnError() else stopOnError()
                    }
                }
            }
            return RecoveryDecision.Retry
        }

        if (!isLocalMedia && !isFullyCachedMedia && YTPlayerUtils.isBotDetectionException(error)) {
            cacheOps.invalidatePlaybackUrlCache(currentMediaId)
            cacheOps.removeExtractorPlaybackUrl(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            YTPlayerUtils.clearPlaybackAuthCaches()
            if (playerActions.registerRetryAttempt(currentMediaId)) {
                Timber.tag(TAG).i("Retrying playback for %s after bot-detection source error", currentMediaId)
                playerActions.prepare()
                return RecoveryDecision.Retry
            }
        }

        if (!isLocalMedia && !isFullyCachedMedia && YTPlayerUtils.isBadStreamPlayerResponseException(error)) {
            cacheOps.invalidatePlaybackUrlCache(currentMediaId)
            cacheOps.removeExtractorPlaybackUrl(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            if (playerActions.registerRetryAttempt(currentMediaId)) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        YTPlayerUtils.recoverFromBadStreamPlayerResponse(currentMediaId)
                    }.onFailure {
                        Timber.tag(TAG).w(
                            it,
                            "Failed to refresh stream session for %s after all stream clients failed",
                            currentMediaId,
                        )
                        reportException(it)
                    }
                    withContext(Dispatchers.Main) {
                        if (playerActions.currentMediaItem?.mediaId == currentMediaId) {
                            Timber.tag(TAG).i(
                                "Retrying playback for %s after refreshing stream session",
                                currentMediaId,
                            )
                            playerActions.prepare()
                        }
                    }
                }
                return RecoveryDecision.Retry
            }
        }

        if (!isLocalMedia && !isFullyCachedMedia && isRetryableRemoteParserFailure(error)) {
            cacheOps.invalidatePlaybackUrlCache(currentMediaId)
            cacheOps.removeExtractorPlaybackUrl(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            if (playerActions.registerRetryAttempt(currentMediaId)) {
                Timber.tag(TAG).i(
                    "Retrying playback for %s after parser source error %d",
                    currentMediaId,
                    error.errorCode,
                )
                playerActions.prepare()
                return RecoveryDecision.Retry
            }
        }

        return if (playerActions.shouldAutoSkipOnError()) {
            skipOnError()
            RecoveryDecision.SkipNext
        } else {
            stopOnError()
            RecoveryDecision.Stop
        }
    }

    fun skipOnError() {
        consecutivePlaybackErr += 2
        val nextWindowIndex = playerActions.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            playerActions.seekTo(nextWindowIndex, C.TIME_UNSET)
            playerActions.prepare()
            playerActions.play()
            return
        }

        playerActions.pause()
        consecutivePlaybackErr = 0
    }

    fun stopOnError() {
        playerActions.pause()
    }

    private fun retryPlaybackAfterStreamFailure(
        mediaId: String,
        isFullyCachedMedia: Boolean,
        responseException: HttpDataSource.InvalidResponseCodeException,
    ): Boolean {
        if (isFullyCachedMedia) return false

        val failedUrl = responseException.dataSpec.uri.toString()
        val requestProfile = StreamClientUtils.resolveRequestProfile(failedUrl)
        val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
        val extractorAuthFingerprint = EXTRACTOR_CACHE_FINGERPRINT_PREFIX + authFingerprint
        val cachedFailedUrl = cacheOps.getCachedFailedPlaybackUrl(mediaId, failedUrl)
        val cachedExtractorFailedUrl = cacheOps.getCachedExtractorFailedPlaybackUrl(mediaId, failedUrl)
        val failedExpiredUrl =
            YTPlayerUtils.isExpiredOrNearExpiredStreamUrl(failedUrl) ||
                (
                    cachedFailedUrl?.let {
                        !it.isValidFor(
                            authFingerprint = authFingerprint,
                            minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
                        )
                    } == true
                ) ||
                (
                    cachedExtractorFailedUrl?.let {
                        !it.isValidFor(
                            authFingerprint = extractorAuthFingerprint,
                            minimumRemainingMs = 0L,
                        )
                    } == true
                )

        cacheOps.invalidatePlaybackUrlCache(mediaId)
        cacheOps.removeExtractorPlaybackUrl(mediaId)
        YTPlayerUtils.invalidateCachedStreamUrls(mediaId)
        if (!failedExpiredUrl && cachedExtractorFailedUrl == null && requestProfile.clientKey.isNotEmpty()) {
            YTPlayerUtils.markStreamClientFailed(mediaId, requestProfile.clientKey, responseException.responseCode)
        }

        if (!playerActions.registerRetryAttempt(mediaId)) {
            return false
        }

        Timber.tag(TAG).i(
            "Retrying playback for %s after stream HTTP %d from %s failed",
            mediaId,
            responseException.responseCode,
            requestProfile.variantLabel,
        )
        playerActions.prepare()
        return true
    }

    suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
    ) {
        val database = databaseProvider()
        val song = database.song(mediaId).first()
        val mediaMetadata =
            withContext(Dispatchers.Main) {
                playerActions.findNextMediaItemById(mediaId)?.metadata
            } ?: return
        val duration =
            song?.song?.duration?.takeIf { it != -1 }
                ?: mediaMetadata.duration.takeIf { it != -1 }
                ?: (
                    playbackData?.videoDetails ?: YTPlayerUtils
                        .playerResponseForMetadata(mediaId)
                        .getOrNull()
                        ?.videoDetails
                )?.lengthSeconds?.toInt()
                ?: -1
        database.query {
            if (song == null) {
                insert(mediaMetadata.copy(duration = duration))
            } else if (song.song.duration == -1) {
                update(song.song.copy(duration = duration))
            }
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id,
                        )
                    }.forEach(::insert)
            }
        }
    }

    suspend fun trimPlayerCacheToBytes(limitBytes: Long) {
        if (limitBytes <= 0L) return

        withContext(Dispatchers.IO) {
            val playerCache = cacheOps.playerCache
            val currentSpace = runCatching { playerCache.cacheSpace }.getOrNull() ?: 0L
            var totalBytes = if (currentSpace > 0L) currentSpace else cacheOps.getPlayerCacheDirectorySizeBytes()
            if (totalBytes <= limitBytes) return@withContext

            data class Candidate(
                val key: String,
                val lastTouchTimestamp: Long,
                val sizeBytes: Long,
            )

            val candidates =
                runCatching {
                    playerCache.keys
                        .mapNotNull { key ->
                            runCatching {
                                val spans = playerCache.getCachedSpans(key)
                                if (spans.isEmpty()) return@runCatching null
                                val oldestTouch = spans.minOf { it.lastTouchTimestamp }
                                val sizeBytes = spans.sumOf { it.length }
                                Candidate(key = key, lastTouchTimestamp = oldestTouch, sizeBytes = sizeBytes)
                            }.getOrNull()
                        }.sortedBy { it.lastTouchTimestamp }
                }.getOrNull().orEmpty()

            for (candidate in candidates) {
                if (totalBytes <= limitBytes) break
                val removedSize = candidate.sizeBytes.coerceAtLeast(0L)
                runCatching { playerCache.removeResource(candidate.key) }
                totalBytes -= removedSize
            }
        }
    }

    fun promptLoginRecovery(
        mediaId: String,
        targetUrl: String,
    ) {
        if (!loginPrompt.isAppInForeground()) return

        val now = System.currentTimeMillis()
        val lastPrompt = lastLoginRecoveryPrompt
        if (lastPrompt?.first == mediaId && now - lastPrompt.second < LOGIN_RECOVERY_DEBOUNCE_MS) return
        lastLoginRecoveryPrompt = mediaId to now

        loginPrompt.openLogin(mediaId, targetUrl)
    }

    private fun findRetryableStreamFailure(
        error: PlaybackException,
    ): HttpDataSource.InvalidResponseCodeException? {
        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable is HttpDataSource.InvalidResponseCodeException &&
                throwable.responseCode in RETRYABLE_STREAM_RESPONSE_CODES
            ) {
                return throwable
            }
            throwable = throwable.cause
        }
        return null
    }

    private fun isRetryableRemoteParserFailure(error: PlaybackException): Boolean {
        if (
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ) {
            return true
        }

        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable.message?.contains("Skipping atom with length", ignoreCase = true) == true) {
                return true
            }
            throwable = throwable.cause
        }
        return false
    }

    private fun isCacheCorruptionError(
        error: PlaybackException,
        isContentCached: Boolean,
    ): Boolean {
        val isIoError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        val isContainerParseError =
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

        if (!isIoError && !isContainerParseError) {
            return false
        }

        var throwable: Throwable? = error.cause
        while (throwable != null) {
            when {
                throwable is IllegalStateException || throwable is IllegalArgumentException -> {
                    if (throwable.stackTrace.any { it.className.startsWith("androidx.media3.extractor") }) {
                        return true
                    }
                }

                isContainerParseError && isContentCached && throwable is ParserException -> {
                    return true
                }

                isContainerParseError && isContentCached &&
                    throwable.message?.let {
                        it.contains("Invalid integer size", ignoreCase = true) ||
                            it.contains("Skipping atom with length", ignoreCase = true) ||
                            it.contains("contentIsMalformed=true", ignoreCase = true)
                    } == true -> {
                    return true
                }
            }
            throwable = throwable.cause
        }
        return false
    }

    private fun hasAnyCachedData(mediaId: String, isFullyCached: Boolean): Boolean {
        return isFullyCached || runCatching {
            cacheOps.downloadCache.getCachedSpans(mediaId).isNotEmpty() ||
                cacheOps.playerCache.getCachedSpans(mediaId).isNotEmpty()
        }.getOrDefault(false)
    }

    private fun findLoginRecoveryTarget(error: PlaybackException): String? {
        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable is YTPlayerUtils.InvalidPlaybackLoginContextException) {
                return throwable.targetUrl
            }
            throwable = throwable.cause
        }
        return null
    }

    private fun hasLoginRequiredException(error: PlaybackException): Boolean {
        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable is YTPlayerUtils.LoginRequiredForPlaybackException) {
                return true
            }
            throwable = throwable.cause
        }
        return false
    }

    private companion object {
        const val TAG = "PlaybackRecoveryEngine"
        const val NETWORK_STALL_WINDOW_MS = 3_000L
        const val FORCE_REVIVE_DEBOUNCE_MS = 2_000L
        const val MAX_CONSECUTIVE_ERR = 5
        const val LOGIN_RECOVERY_DEBOUNCE_MS = 10_000L
        const val EXTRACTOR_CACHE_FINGERPRINT_PREFIX = "archivetune_extractor:"
        val RETRYABLE_STREAM_RESPONSE_CODES = setOf(403, 404, 410, 416)
    }
}
