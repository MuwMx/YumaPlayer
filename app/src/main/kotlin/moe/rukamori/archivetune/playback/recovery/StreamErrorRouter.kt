/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.recovery

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.PlaybackRecoveryEngine
import moe.rukamori.archivetune.playback.RecoveryDecision
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber

class StreamErrorRouter(
    private val scope: CoroutineScope,
    private val playerActions: PlaybackRecoveryEngine.PlayerActions,
    private val cacheOps: PlaybackRecoveryEngine.CacheOps,
    private val networkState: PlaybackRecoveryEngine.NetworkState,
    private val loginPrompt: PlaybackRecoveryEngine.LoginPrompt,
    private val networkStallReviver: NetworkStallReviver,
    private val classifiers: PlaybackErrorClassifiers = PlaybackErrorClassifiers,
    private val maintenanceOps: RecoveryMaintenanceOps = RecoveryMaintenanceOps,
    private val getConsecutivePlaybackErr: () -> Int = { 0 },
    private val setConsecutivePlaybackErr: (Int) -> Unit = {},
) {
    private var lastLoginRecoveryPrompt: Pair<String, Long>? = null

    fun onPlayerError(error: PlaybackException): RecoveryDecision {
        val currentMediaId = playerActions.currentMediaItem?.mediaId ?: return RecoveryDecision.None
        val isLocalMedia = currentMediaId.isLocalMediaId()
        val isFullyCachedMedia = cacheOps.isTrackFullyCached(currentMediaId)
        val hasAnyCachedData =
            classifiers.hasAnyCachedData(
                mediaId = currentMediaId,
                isFullyCached = isFullyCachedMedia,
                cacheOps.downloadCache,
                cacheOps.playerCache,
            )

        val isConnectionError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                ((error.cause?.cause is PlaybackException) &&
                    (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

        if (!isLocalMedia && !isFullyCachedMedia) {
            if (!networkState.isNetworkConnected()) {
                networkStallReviver.waitOnNetworkError()
                return RecoveryDecision.WaitForNetwork
            } else if (isConnectionError) {
                cacheOps.invalidatePlaybackUrlCache(currentMediaId)
                if (networkStallReviver.forceRevivePlayback()) return RecoveryDecision.Retry
            }
        }

        if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
            scope.launch(Dispatchers.IO) {
                runCatching { cacheOps.downloadCache.removeResource(currentMediaId) }
                runCatching { cacheOps.playerCache.removeResource(currentMediaId) }
            }
        }

        val loginTarget = classifiers.findLoginRecoveryTarget(error)
        if (loginTarget != null) {
            promptLoginRecovery(currentMediaId, loginTarget)
            return RecoveryDecision.RequireLogin(currentMediaId, loginTarget)
        }

        if (classifiers.hasLoginRequiredException(error)) {
            return RecoveryDecision.ShowToast(R.string.playback_requires_youtube_music_confirmation)
        }

        val retryableStreamFailure = classifiers.findRetryableStreamFailure(error)
        if (retryableStreamFailure != null) {
            if (retryPlaybackAfterStreamFailure(currentMediaId, isFullyCachedMedia, retryableStreamFailure)) {
                return RecoveryDecision.Retry
            }
        }

        if (!isLocalMedia && classifiers.isCacheCorruptionError(error, hasAnyCachedData)) {
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

        if (!isLocalMedia && !isFullyCachedMedia && classifiers.isRetryableRemoteParserFailure(error)) {
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
        val nextErr = getConsecutivePlaybackErr() + 2
        setConsecutivePlaybackErr(nextErr)
        val nextWindowIndex = playerActions.nextMediaItemIndex

        if (nextErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            playerActions.seekTo(nextWindowIndex, C.TIME_UNSET)
            playerActions.prepare()
            playerActions.play()
            return
        }

        playerActions.pause()
        setConsecutivePlaybackErr(0)
    }

    fun stopOnError() {
        playerActions.pause()
    }

    fun retryPlaybackAfterStreamFailure(
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

    private companion object {
        const val TAG = "StreamErrorRouter"
        const val MAX_CONSECUTIVE_ERR = 5
        const val LOGIN_RECOVERY_DEBOUNCE_MS = 10_000L
        const val EXTRACTOR_CACHE_FINGERPRINT_PREFIX = "archivetune_extractor:"
    }
}
