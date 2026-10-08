/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback

import android.net.Network
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.cache.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.playback.recovery.NetworkStallReviver
import moe.rukamori.archivetune.playback.recovery.RecoveryMaintenanceOps
import moe.rukamori.archivetune.playback.recovery.StreamErrorRouter
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.YTPlayerUtils

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
    interface PlayerActions : NetworkStallReviver.PlayerActions {
        val nextMediaItemIndex: Int
        fun findNextMediaItemById(mediaId: String): MediaItem?
        fun pause()
        fun registerRetryAttempt(mediaId: String): Boolean
        fun shouldAutoSkipOnError(): Boolean
    }

    interface CacheOps {
        val playerCache: Cache
        val downloadCache: Cache
        fun isTrackFullyCached(mediaId: String): Boolean
        fun invalidatePlaybackUrlCache(mediaId: String)
        fun handleStreamFailureRecovery(mediaId: String)
        fun removeExtractorPlaybackUrl(mediaId: String)
        fun getCachedFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue?
        fun getCachedExtractorFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue?
        fun getPlayerCacheDirectorySizeBytes(): Long
    }

    fun interface NetworkState : NetworkStallReviver.NetworkState {
        override fun isNetworkConnected(): Boolean
    }

    interface LoginPrompt {
        fun isAppInForeground(): Boolean
        fun openLogin(mediaId: String, targetUrl: String)
    }

    interface Delegate : PlayerActions, CacheOps, NetworkState, LoginPrompt

    constructor(scope: CoroutineScope, delegate: Delegate, databaseProvider: () -> MusicDatabase) :
        this(scope, delegate, delegate, delegate, delegate, databaseProvider)

    private val networkStallReviver = NetworkStallReviver(scope, playerActions, networkState)
    private val streamErrorRouter = StreamErrorRouter(
        scope, playerActions, cacheOps, networkState, loginPrompt, networkStallReviver,
        getConsecutivePlaybackErr = { consecutivePlaybackErr },
        setConsecutivePlaybackErr = { consecutivePlaybackErr = it },
    )

    val waitingForNetworkConnection: MutableStateFlow<Boolean> get() = networkStallReviver.waitingForNetworkConnection
    internal var consecutivePlaybackErr = 0
    internal var networkStallRecoveryJob: Job?
        get() = networkStallReviver.networkStallRecoveryJob
        set(value) { networkStallReviver.networkStallRecoveryJob = value }
    val consecutivePlaybackErrorCount: Int get() = consecutivePlaybackErr

    fun cancelNetworkStallRecovery() = networkStallReviver.cancelNetworkStallRecovery()
    fun resetConsecutivePlaybackErr() { consecutivePlaybackErr = 0 }
    fun onNetworkStatusChanged(isConnected: Boolean, currentNetwork: Network?) =
        networkStallReviver.onNetworkStatusChanged(isConnected, currentNetwork)
    fun forceRevivePlayback(): Boolean = networkStallReviver.forceRevivePlayback()
    fun revivePlaybackFromStall() = networkStallReviver.revivePlaybackFromStall()
    fun revivePlaybackFromStall(gen: Long) = networkStallReviver.revivePlaybackFromStall(gen)
    fun onPlayerError(error: PlaybackException): RecoveryDecision = streamErrorRouter.onPlayerError(error)
    fun skipOnError() = streamErrorRouter.skipOnError()
    fun stopOnError() = streamErrorRouter.stopOnError()
    fun promptLoginRecovery(mediaId: String, targetUrl: String) =
        streamErrorRouter.promptLoginRecovery(mediaId, targetUrl)
    suspend fun recoverSong(mediaId: String, playbackData: YTPlayerUtils.PlaybackData? = null) =
        RecoveryMaintenanceOps.recoverSong(mediaId, databaseProvider, playerActions::findNextMediaItemById, playbackData)
    suspend fun trimPlayerCacheToBytes(limitBytes: Long) =
        RecoveryMaintenanceOps.trimPlayerCacheToBytes(limitBytes, cacheOps)

    val registeredCacheKeys: MutableSet<String>
        get() = RecoveryMaintenanceOps.registeredCacheKeys

    fun createAutomixCacheListener(
        tag: String = "MusicService",
        flacCacheKeyPrefix: String = "flac_",
        onSpanAdded: (cache: Cache, key: String, mediaId: String) -> Unit,
    ): Cache.Listener = RecoveryMaintenanceOps.createAutomixCacheListener(
        playerCache = cacheOps.playerCache,
        tag = tag,
        flacCacheKeyPrefix = flacCacheKeyPrefix,
        onSpanAdded = onSpanAdded,
    )
}
