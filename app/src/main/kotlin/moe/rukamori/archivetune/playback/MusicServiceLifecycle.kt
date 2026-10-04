/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback

import android.app.Service
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiodsp.CrossfadeConfig
import moe.rukamori.archivetune.audiodsp.shouldUseLegacyPath
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AudioOffload
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothKey
import moe.rukamori.archivetune.constants.AutomixEnabledKey
import moe.rukamori.archivetune.constants.AutomixTransitionDurationKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.DeviceMutePlaybackRecoveryVolumeKey
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.constants.EnableLastFMScrobblingKey
import moe.rukamori.archivetune.constants.LastFMSessionKey
import moe.rukamori.archivetune.constants.LastFMUseNowPlaying
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.PlayerVolumeKey
import moe.rukamori.archivetune.constants.RepeatModeKey
import moe.rukamori.archivetune.constants.ScrobbleDelayPercentKey
import moe.rukamori.archivetune.constants.ScrobbleDelaySecondsKey
import moe.rukamori.archivetune.constants.ScrobbleMinSongDurationKey
import moe.rukamori.archivetune.constants.ShowLyricsKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.collect
import moe.rukamori.archivetune.extensions.collectLatest
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.ScrobbleManager
import moe.rukamori.archivetune.utils.get
import timber.log.Timber
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class MusicServiceLifecycle(
    private val scope: CoroutineScope,
    private val ioScope: CoroutineScope,
    private val delegate: Delegate,
) {
    interface Delegate {
        val dataStore: DataStore<Preferences>
        val player: Player
        val localPlayer: ExoPlayer
        val secondaryCrossfadePlayer: ExoPlayer?
        val serviceBinder: IBinder
        val mediaLibrarySession: MediaLibrarySession

        val playerVolume: MutableStateFlow<Float>
        val normalizeFactor: MutableStateFlow<Float>
        val audioFocusVolumeFactor: MutableStateFlow<Float>
        fun computeEffectivePlayerVolume(volume: Float, factor: Float, focus: Float): Float
        fun applyEffectiveVolume(volume: Float)
        fun updateAudioOffload(enabled: Boolean)

        val currentSong: StateFlow<Song?>
        val currentMediaMetadata: MutableStateFlow<MediaMetadata?>
        val currentFormat: Flow<FormatEntity?>
        fun updateNotification()
        fun updateCurrentMediaMetadata(metadata: MediaMetadata?)
        fun dispatchDiscordSync(reason: String, force: Boolean = false)
        fun ensurePresenceManager()
        fun isAppInForeground(): Boolean
        fun handleMediaNotificationDismissedWhilePausedInBackground()

        val database: MusicDatabase
        val lyricsHelper: LyricsHelper
        fun initLyricsPreloadManager()

        val desiredEqSettings: MutableStateFlow<EqSettings>
        fun parseEqSettings(prefs: Preferences): EqSettings
        fun applyEqSettings(settings: EqSettings)
        fun resolveAudioNormalizationFactor(mediaId: String?, format: FormatEntity?, normalizeAudio: Boolean): Float
        var audioNormalizationEnabled: Boolean

        var currentPlaybackSource: PlaybackSource
        var isLowDataEnabled: Boolean
        var pauseOnDeviceMuteEnabled: Boolean
        var wasAutoPausedByDeviceMute: Boolean
        var deviceMutePlaybackRecoveryVolumePercent: Int
        fun onDeviceMuteChanged()
        fun removeMuteRecoveryObserver()

        var crossfadeConfig: CrossfadeConfig
        var crossfadeEnabled: Boolean
        var crossfadeDurationMs: Long
        var crossfadeGapless: Boolean
        var automixEnabled: Boolean
        var automixTransitionPreset: String
        val togetherSessionState: StateFlow<TogetherSessionState>
        fun schedulePlayerCrossfade()
        fun cancelPlayerCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean)

        val downloadCache: Cache
        fun registerCacheListener(key: String)
        suspend fun performTrimPlayerCache(limitBytes: Long)

        var scrobbleManager: ScrobbleManager?
        fun createScrobbleManager(minSongDuration: Int, scrobbleDelayPercent: Float, scrobbleDelaySeconds: Int): ScrobbleManager

        val playerInitialized: StateFlow<Boolean>
        val queueRestoreCompleted: MutableStateFlow<Boolean>
        var isRestoringPersistentState: Boolean
        suspend fun restorePersistentState()
        fun cancelRestoredQueueHydration()
        fun clearQueuePersistenceFiles()
        suspend fun saveQueueToStorage()

        fun startForegroundPlaybackService(): Boolean
        fun stopForegroundService()
        fun stopSelfService()
        fun startSelfService()
        fun registerBluetoothReceiver(receiver: BroadcastReceiver)
        fun unregisterBluetoothReceiver(receiver: BroadcastReceiver)
        fun hasBluetoothConnectPermission(): Boolean
        fun acquireWakeLock()
        fun releaseWakeLock()
        fun isWakeLockHeld(): Boolean
        fun registerAudioDeviceCallback()
        fun unregisterAudioDeviceCallback()
        val connectivityObserver: NetworkConnectivityObserver
        val connectivityManager: android.net.ConnectivityManager
        val playbackRecoveryEngine: PlaybackRecoveryEngine
        val isNetworkConnected: MutableStateFlow<Boolean>

        fun cancelStreamPrefetch()
        fun setDiscordServiceStopping(stopping: Boolean)
        fun cancelEffectiveVolumeRamp()
        fun cancelAudioRouteRecovery()
        suspend fun stopTogether()
        fun abandonPlaybackAudioFocus()
        fun releaseServiceAudioEffects()
        fun releaseReserveAndTransitionDecks()
        fun removeDjFilterFromLocalPlayer()
        fun removePlayerListeners()
        fun releasePlayer()
        fun releaseAllCacheListeners()
        fun cancelScopeJob()

        fun isTogetherGuestSessionActive(): Boolean
        fun isTogetherHostSessionActive(): Boolean
        fun isTogetherSessionIdle(): Boolean
        fun resetTogetherSessionState()
        fun stopAndClearPlaybackSession(clearPersistentState: Boolean)
    }

    var autoStartOnBluetoothEnabled: Boolean = false
        private set
    private var bluetoothReceiverRegistered = false

    private var wakelockEnabled = false
    private var audioDeviceCallbackRegistered = false

    var hasBoundClients: Boolean = false
        private set

    private var idleStopJob: Job? = null
    private var hasCalledStartForeground = false

    private val bluetoothReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
                if (!autoStartOnBluetoothEnabled) return

                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

                val isAudioDevice =
                    try {
                        val majorClass = device.bluetoothClass?.majorDeviceClass
                        majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                            majorClass == BluetoothClass.Device.Major.WEARABLE
                    } catch (_: SecurityException) {
                        true
                    }

                if (!isAudioDevice) return

                scope.launch {
                    delay(1500)
                    handleBluetoothAutoStart()
                }
            }
        }

    private fun handleBluetoothAutoStart() {
        if (delegate.isTogetherGuestSessionActive()) return
        val player = delegate.player

        if (player.currentMediaItem != null &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
        ) {
            if (!player.playWhenReady) {
                player.play()
            }
            return
        }

        if (player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        }
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        if (!delegate.hasBluetoothConnectPermission()) return

        delegate.registerBluetoothReceiver(bluetoothReceiver)
        bluetoothReceiverRegistered = true
    }

    private fun unregisterBluetoothReceiver() {
        if (!bluetoothReceiverRegistered) return
        delegate.unregisterBluetoothReceiver(bluetoothReceiver)
        bluetoothReceiverRegistered = false
    }

    fun updateWakeLock() {
        val shouldHold = wakelockEnabled && delegate.player.isPlaying
        if (shouldHold && !delegate.isWakeLockHeld()) {
            delegate.acquireWakeLock()
        } else if (!shouldHold && delegate.isWakeLockHeld()) {
            delegate.releaseWakeLock()
        }
    }

    fun ensureStartedAsForeground() {
        if (hasCalledStartForeground) return
        val started = delegate.startForegroundPlaybackService()
        if (started) {
            hasCalledStartForeground = true
        }
    }

    fun promoteToStartedService() {
        delegate.startSelfService()
    }

    fun cancelIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = null
    }

    fun hasResumablePlaybackNotification(): Boolean =
        MusicServiceNotification.hasResumablePlaybackNotification(delegate.player)

    private fun stopForegroundAndSelf() {
        cancelIdleStop()
        delegate.stopForegroundService()
        hasCalledStartForeground = false
        delegate.stopSelfService()
    }

    fun scheduleStopIfIdle() {
        if (hasBoundClients) return
        if (hasResumablePlaybackNotification()) {
            cancelIdleStop()
            promoteToStartedService()
            ensureStartedAsForeground()
            return
        }
        if (!delegate.isTogetherSessionIdle()) {
            cancelIdleStop()
            return
        }

        val state = delegate.player.playbackState
        val delayMs =
            when (state) {
                Player.STATE_ENDED, Player.STATE_IDLE -> 30_000L
                else -> 60_000L
            }

        cancelIdleStop()
        idleStopJob =
            scope.launch {
                delay(delayMs)
                if (hasBoundClients) return@launch
                if (hasResumablePlaybackNotification()) return@launch
                if (!delegate.isTogetherSessionIdle()) return@launch
                stopForegroundAndSelf()
            }
    }

    fun onCreate() {
        ioScope.launch {
            runCatching { delegate.downloadCache.keys }.getOrNull()?.forEach { key ->
                delegate.registerCacheListener(key)
            }
        }

        delegate.registerAudioDeviceCallback()
        audioDeviceCallbackRegistered = true

        if (!hasCalledStartForeground) ensureStartedAsForeground()

        delegate.updateNotification()

        scope.launch(Dispatchers.IO) {
            val prefs = delegate.dataStore.data.first()
            val repeatMode = prefs[RepeatModeKey] ?: REPEAT_MODE_OFF
            val volume = (prefs[PlayerVolumeKey] ?: 1f).coerceIn(0f, 1f)
            val offload = prefs[AudioOffload] ?: false
            val crossfadePrefEnabled = prefs[CrossfadeEnabledKey] ?: false
            withContext(Dispatchers.Main) {
                delegate.player.repeatMode = repeatMode
                delegate.playerVolume.value = volume
                delegate.updateAudioOffload(offload && !crossfadePrefEnabled)
            }
        }

        scope.launch {
            delegate.connectivityObserver.networkStatus.collect { isConnected ->
                delegate.isNetworkConnected.value = isConnected
                val currentNetwork = runCatching { delegate.connectivityManager.activeNetwork }.getOrNull()
                delegate.playbackRecoveryEngine.onNetworkStatusChanged(isConnected, currentNetwork)
            }
        }

        scope.launch {
            delegate.dataStore.data
                .map { it[PlaybackSourceKey]?.toEnum(PlaybackSource.YT_MUSIC) ?: PlaybackSource.YT_MUSIC }
                .collect { delegate.currentPlaybackSource = it }
        }

        scope.launch {
            delegate.dataStore.data
                .map { it[LowDataModeKey] ?: true }
                .collect { delegate.isLowDataEnabled = it }
        }

        combine(delegate.playerVolume, delegate.normalizeFactor, delegate.audioFocusVolumeFactor) { playerVolume, normalizeFactor, audioFocusVolumeFactor ->
            delegate.computeEffectivePlayerVolume(playerVolume, normalizeFactor, audioFocusVolumeFactor)
        }.collectLatest(scope) { finalVolume ->
            delegate.applyEffectiveVolume(finalVolume)
        }

        delegate.playerVolume.debounce(1000).collect(ioScope) { volume ->
            delegate.dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        delegate.currentSong.collect(scope) { song ->
            delegate.updateNotification()
            delegate.dispatchDiscordSync(
                reason =
                    if (song == null) {
                        "current_song_cleared"
                    } else {
                        "current_song_changed"
                    },
            )
            if (song != null && delegate.player.playWhenReady && delegate.player.playbackState == Player.STATE_READY) {
                delegate.ensurePresenceManager()
            }
        }

        combine(
            delegate.currentMediaMetadata.distinctUntilChangedBy { it?.id },
            delegate.dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(ioScope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && delegate.database
                    .lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyrics = delegate.lyricsHelper.getLyrics(mediaMetadata)
                delegate.database.query {
                    insertLyricsIfAbsent(
                        id = mediaMetadata.id,
                        lyrics = lyrics,
                    )
                }
            }
        }

        delegate.dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                delegate.localPlayer.skipSilenceEnabled = it
                delegate.secondaryCrossfadePlayer?.skipSilenceEnabled = it
            }

        delegate.dataStore.data
            .map { it[PauseOnDeviceMuteKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                delegate.pauseOnDeviceMuteEnabled = enabled
                if (!enabled) {
                    delegate.wasAutoPausedByDeviceMute = false
                    delegate.removeMuteRecoveryObserver()
                } else {
                    delegate.onDeviceMuteChanged()
                }
            }

        delegate.dataStore.data
            .map { (it[DeviceMutePlaybackRecoveryVolumeKey] ?: 0).coerceIn(0, 100) }
            .distinctUntilChanged()
            .collectLatest(scope) { percent ->
                delegate.deviceMutePlaybackRecoveryVolumePercent = percent
            }

        delegate.dataStore.data
            .map { it[AutoStartOnBluetoothKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                autoStartOnBluetoothEnabled = enabled
                if (enabled) {
                    registerBluetoothReceiver()
                } else {
                    unregisterBluetoothReceiver()
                }
            }

        combine(
            delegate.dataStore.data.map { it[AudioOffload] ?: false },
            delegate.dataStore.data.map { it[CrossfadeEnabledKey] ?: false },
        ) { offloadEnabled, crossfadeEnabled ->
            offloadEnabled to crossfadeEnabled
        }.distinctUntilChanged()
            .collectLatest(scope) { (offloadEnabled, crossfadeEnabled) ->
                val effectiveOffload = offloadEnabled && !crossfadeEnabled
                delegate.updateAudioOffload(effectiveOffload)
                if (effectiveOffload) {
                    val skipSilenceEnabled = delegate.dataStore.get(SkipSilenceKey, false)
                    if (skipSilenceEnabled) {
                        delegate.dataStore.edit { it[SkipSilenceKey] = false }
                        delegate.localPlayer.skipSilenceEnabled = false
                    }
                }
            }

        combine(delegate.dataStore.data, delegate.togetherSessionState) { prefs, togetherState ->
            val enabled = prefs[CrossfadeEnabledKey] ?: false
            val durationSeconds = prefs[CrossfadeDurationKey] ?: 5f
            val gapless = prefs[CrossfadeGaplessKey] ?: true
            val automix = prefs[AutomixEnabledKey] ?: false
            val automixTransitionPreset = prefs[AutomixTransitionDurationKey] ?: "auto"
            val effectiveEnabled = enabled && togetherState is TogetherSessionState.Idle
            val durationMs = (durationSeconds.coerceIn(0f, 10f) * 1000f).roundToLong().coerceAtLeast(0L)
            CrossfadeConfig(
                durationMs = durationMs,
                automixEnabled = automix,
                preset = automixTransitionPreset,
                gapless = gapless,
                enabled = effectiveEnabled,
            )
        }.distinctUntilChanged()
            .collectLatest(scope) { config ->
                delegate.crossfadeConfig = config
                delegate.crossfadeEnabled = config.enabled
                delegate.crossfadeDurationMs = config.durationMs
                delegate.crossfadeGapless = config.gapless
                delegate.automixEnabled = config.automixEnabled
                delegate.automixTransitionPreset = config.preset
                if (delegate.crossfadeEnabled && !shouldUseLegacyPath(delegate.crossfadeDurationMs)) {
                    delegate.schedulePlayerCrossfade()
                } else {
                    delegate.cancelPlayerCrossfade(resetVolume = true, resetPauseAtEnd = true)
                }
            }

        delegate.dataStore.data
            .map { it[WakelockKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                wakelockEnabled = enabled
                updateWakeLock()
            }

        delegate.initLyricsPreloadManager()

        delegate.dataStore.data
            .map { prefs -> delegate.parseEqSettings(prefs) }
            .distinctUntilChanged()
            .collectLatest(scope) { settings ->
                delegate.desiredEqSettings.value = settings
                delegate.applyEqSettings(settings)
            }

        combine(
            delegate.currentMediaMetadata
                .map { it?.id }
                .distinctUntilChanged(),
            delegate.currentFormat,
            delegate.dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { mediaId, format, normalizeAudio ->
            normalizeAudio to delegate.resolveAudioNormalizationFactor(mediaId, format, normalizeAudio)
        }.distinctUntilChanged()
            .collectLatest(scope) { (normalizeAudio, factor) ->
                delegate.audioNormalizationEnabled = normalizeAudio
                delegate.normalizeFactor.value = factor
            }

        delegate.dataStore.data
            .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest(scope) { (key, enabled) ->
                delegate.dispatchDiscordSync(
                    reason =
                        when {
                            !enabled -> "discord_rpc_disabled"
                            key.isNullOrBlank() -> "discord_token_missing"
                            else -> "discord_token_or_toggle_changed"
                        },
                    force = !enabled || key.isNullOrBlank(),
                )
                if (!key.isNullOrBlank() && enabled) {
                    if (delegate.player.playbackState == Player.STATE_READY && delegate.player.playWhenReady) {
                        delegate.currentSong.value?.let {
                            delegate.ensurePresenceManager()
                        }
                    }
                }
            }

        delegate.dataStore.data
            .map { prefs ->
                (prefs[SmartTrimmerKey] ?: false) to (prefs[MaxSongCacheSizeKey] ?: 1024)
            }.debounce(300)
            .distinctUntilChanged()
            .collectLatest(ioScope) { (enabled, maxSongCacheSizeMb) ->
                if (!enabled) return@collectLatest
                if (maxSongCacheSizeMb <= 0 || maxSongCacheSizeMb == -1) return@collectLatest
                val bytesPerMb = 1024L * 1024L
                val safeSizeMb = maxSongCacheSizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
                val limitBytes = safeSizeMb * bytesPerMb
                delegate.performTrimPlayerCache(limitBytes)
            }

        delegate.dataStore.data
            .map { preferences ->
                val serviceConfig = LastFmServiceConfig.fromPreferences(preferences)
                Triple(
                    preferences[EnableLastFMScrobblingKey] ?: false,
                    !preferences[LastFMSessionKey].isNullOrBlank(),
                    serviceConfig.initialized,
                )
            }.debounce(300)
            .distinctUntilChanged()
            .collect(scope) { (enabled, hasSession, serviceConfigured) ->
                val shouldEnable = enabled && hasSession && serviceConfigured
                if (shouldEnable && delegate.scrobbleManager == null) {
                    val delayPercent = delegate.dataStore.get(ScrobbleDelayPercentKey, LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT)
                    val minSongDuration = delegate.dataStore.get(ScrobbleMinSongDurationKey, LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION)
                    val delaySeconds = delegate.dataStore.get(ScrobbleDelaySecondsKey, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS)

                    delegate.scrobbleManager =
                        delegate.createScrobbleManager(
                            minSongDuration = minSongDuration,
                            scrobbleDelayPercent = delayPercent,
                            scrobbleDelaySeconds = delaySeconds,
                        )
                    delegate.scrobbleManager?.useNowPlaying = delegate.dataStore.get(LastFMUseNowPlaying, false)
                } else if (!shouldEnable && delegate.scrobbleManager != null) {
                    delegate.scrobbleManager?.destroy()
                    delegate.scrobbleManager = null
                }
            }

        delegate.dataStore.data
            .map { it[LastFMUseNowPlaying] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                delegate.scrobbleManager?.useNowPlaying = it
            }

        delegate.dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                    prefs[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                    prefs[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
                )
            }.distinctUntilChanged()
            .collect(scope) { (delayPercent, minSongDuration, delaySeconds) ->
                delegate.scrobbleManager?.let {
                    it.scrobbleDelayPercent = delayPercent
                    it.minSongDuration = minSongDuration
                    it.scrobbleDelaySeconds = delaySeconds
                }
            }

        scope.launch(Dispatchers.IO) {
            runCatching {
                if (delegate.dataStore.get(PersistentQueueKey, true)) {
                    delegate.playerInitialized.first { it }
                    delegate.restorePersistentState()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.tag(MusicService.TAG).w(error, "Failed to restore persisted queue, clearing data")
                delegate.isRestoringPersistentState = false
                delegate.cancelRestoredQueueHydration()
                delegate.clearQueuePersistenceFiles()
            }
            withContext(Dispatchers.Main) {
                delegate.queueRestoreCompleted.value = true
            }
        }

        scope.launch {
            while (isActive) {
                delay(if (delegate.player.isPlaying) 10.seconds else 30.seconds)
                val shouldSave = withContext(Dispatchers.IO) { delegate.dataStore.get(PersistentQueueKey, true) }
                if (shouldSave && delegate.player.mediaItemCount > 0) {
                    delegate.saveQueueToStorage()
                }
            }
        }
    }

    fun onDestroy(callSuper: () -> Unit) {
        delegate.cancelStreamPrefetch()
        delegate.setDiscordServiceStopping(true)
        delegate.dispatchDiscordSync(
            reason = "service_destroy",
            force = true,
        )
        callSuper()
        delegate.cancelEffectiveVolumeRamp()
        delegate.cancelPlayerCrossfade(resetVolume = false, resetPauseAtEnd = true)
        delegate.cancelAudioRouteRecovery()
        if (audioDeviceCallbackRegistered) {
            delegate.unregisterAudioDeviceCallback()
            audioDeviceCallbackRegistered = false
        }
        unregisterBluetoothReceiver()
        delegate.removeMuteRecoveryObserver()
        try {
            scope.launch { delegate.stopTogether() }
        } catch (_: Exception) {
        }
        try {
            delegate.connectivityObserver.unregister()
        } catch (_: Exception) {
        }
        delegate.abandonPlaybackAudioFocus()
        try {
            delegate.releaseServiceAudioEffects()
        } catch (_: Exception) {
        }
        try {
            if (delegate.dataStore.get(PersistentQueueKey, true) && delegate.player.mediaItemCount > 0) {
                runBlocking {
                    delegate.saveQueueToStorage()
                }
            }
        } catch (_: Exception) {
        }
        try {
            delegate.mediaLibrarySession.release()
        } catch (_: Exception) {
        }
        try {
            if (delegate.isWakeLockHeld()) delegate.releaseWakeLock()
        } catch (_: Exception) {
        }
        try {
            delegate.releaseReserveAndTransitionDecks()
            delegate.removeDjFilterFromLocalPlayer()
            delegate.removePlayerListeners()
            delegate.releasePlayer()
            delegate.releaseAllCacheListeners()
        } catch (_: Exception) {
        }
        delegate.cancelScopeJob()
    }

    fun onBind(intent: Intent?, superBinder: IBinder?): IBinder? {
        hasBoundClients = true
        cancelIdleStop()
        val result = superBinder ?: delegate.serviceBinder
        val player = delegate.player
        if (player.mediaItemCount > 0 && player.currentMediaItem != null) {
            delegate.updateCurrentMediaMetadata(player.currentMetadata)
            scope.launch {
                delay(50)
                delegate.updateNotification()
            }
        }
        return result
    }

    fun onUnbind(intent: Intent?) {
        hasBoundClients = false
        scheduleStopIfIdle()
    }

    fun onRebind(intent: Intent?) {
        hasBoundClients = true
        cancelIdleStop()
    }

    fun onTaskRemoved(rootIntent: Intent?) {
        val stopMusicOnTaskClearEnabled = delegate.dataStore.get(StopMusicOnTaskClearKey, false)

        try {
            val isHostSessionActive = delegate.isTogetherHostSessionActive()
            val player = delegate.player
            val isPlaybackInactive = player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0

            if (PlaybackConstants.shouldStopServiceOnTaskRemoved(stopMusicOnTaskClearEnabled, isHostSessionActive, isPlaybackInactive)) {
                if (stopMusicOnTaskClearEnabled) {
                    delegate.setDiscordServiceStopping(true)
                    delegate.dispatchDiscordSync(
                        reason = "task_removed_stop_music_on_task_clear",
                        force = true,
                    )
                    runCatching { delegate.stopAndClearPlaybackSession(clearPersistentState = true) }
                    stopForegroundAndSelf()
                    return
                }

                if (isHostSessionActive && isPlaybackInactive) {
                    delegate.setDiscordServiceStopping(true)
                    delegate.dispatchDiscordSync(
                        reason = "task_removed_host_inactive",
                        force = true,
                    )
                    runCatching { scope.launch { delegate.stopTogether() } }
                    runCatching { delegate.resetTogetherSessionState() }
                    delegate.stopSelfService()
                    return
                }
            }

            if (delegate.dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                runBlocking { delegate.saveQueueToStorage() }
            }
        } catch (_: Exception) {
        }
    }

    fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        delegate.mediaLibrarySession

    private fun handleMediaNotificationDismissed(intent: Intent) {
        MusicServiceNotification.handleMediaNotificationDismissed(
            intent = intent,
            isPlaying = delegate.player.isPlaying,
            isForeground = delegate.isAppInForeground(),
            onDismissedWhilePausedInBackground = {
                delegate.handleMediaNotificationDismissedWhilePausedInBackground()
            },
        )
    }

    fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
        callSuper: () -> Unit,
    ): Int {
        if (intent?.action == MusicService.ACTION_MEDIA_NOTIFICATION_DISMISSED) {
            handleMediaNotificationDismissed(intent)
            return Service.START_NOT_STICKY
        }

        ensureStartedAsForeground()
        val player = delegate.player
        when (intent?.action) {
            "moe.rukamori.archivetune.WIDGET_PLAY_PAUSE" -> {
                if (player.isPlaying) player.pause() else player.play()
            }

            "moe.rukamori.archivetune.WIDGET_SKIP_NEXT" -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                    player.prepare()
                    player.play()
                }
            }

            "moe.rukamori.archivetune.WIDGET_SKIP_PREV" -> {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPrevious()
                    player.prepare()
                    player.play()
                }
            }
        }
        callSuper()
        return Service.START_NOT_STICKY
    }

    fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
        callSuper: (Boolean) -> Unit,
    ) {
        val keepInForeground = startInForegroundRequired || hasResumablePlaybackNotification()
        if (keepInForeground && !hasCalledStartForeground) {
            ensureStartedAsForeground()
        }
        callSuper(keepInForeground)
    }
}
