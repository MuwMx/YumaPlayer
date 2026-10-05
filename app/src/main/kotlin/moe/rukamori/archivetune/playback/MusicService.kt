/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import moe.rukamori.archivetune.audiodsp.AnalysisStore
import moe.rukamori.archivetune.audiodsp.AudioDeck
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeConfig
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.DeckController
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.DualForwardingPlayer
import moe.rukamori.archivetune.audiodsp.DualPlayerRoleHolder
import moe.rukamori.archivetune.cast.CastMediaItemResolver
import moe.rukamori.archivetune.cast.CastPlaybackRepository
import moe.rukamori.archivetune.cast.CastPlaybackRepositoryLocator
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsPreloadManager
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.PersistPlayerState
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.playback.audio.ServiceAudioPolicyHolder
import moe.rukamori.archivetune.playback.discord.DiscordHoldController
import moe.rukamori.archivetune.playback.discord.DiscordSyncOrchestrator
import moe.rukamori.archivetune.playback.engine.PlayerEngineHolder
import moe.rukamori.archivetune.playback.history.PlaybackHistoryStore
import moe.rukamori.archivetune.playback.host.ServiceConfigCollector
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.session.ServiceSessionHolder
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.ProxyAuth
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.widget.LoadWidgetInsightsUseCase
import okhttp3.OkHttpClient

internal typealias DiscordSyncRequest = moe.rukamori.archivetune.playback.discord.DiscordSyncRequest
internal typealias Quadruple<A, B, C, D> = moe.rukamori.archivetune.playback.discord.Quadruple<A, B, C, D>
internal typealias StaleDiscordSyncException = moe.rukamori.archivetune.playback.discord.StaleDiscordSyncException
internal typealias PendingHistoryFinalization = moe.rukamori.archivetune.playback.history.PendingHistoryFinalization
internal typealias ImmediateHistoryResult = moe.rukamori.archivetune.playback.history.ImmediateHistoryResult

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    MusicServiceLifecycle.Delegate {
    @Inject
    override lateinit var database: MusicDatabase

    @Inject
    override lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    internal lateinit var losslessStreamResolver: moe.rukamori.archivetune.playback.resolvers.LosslessStreamResolver

    @Inject
    internal lateinit var loadWidgetInsightsUseCase: LoadWidgetInsightsUseCase

    internal var scopeJob = Job()
    internal var scope = CoroutineScope(Dispatchers.Main + scopeJob)
    internal var ioScope = CoroutineScope(Dispatchers.IO + scopeJob)

    internal val configCollector by lazy {
        ServiceConfigCollector(
            context = this,
            scope = scope,
            database = database,
            dataStore = dataStore,
        )
    }

    internal val audioPolicyHolder by lazy {
        ServiceAudioPolicyHolder(
            context = this,
            scope = scope,
            ioScope = ioScope,
            dataStore = dataStore,
            onAudioOutputDeviceChanged = { onAudioOutputDeviceChanged() },
            ensureAudioEffects = { ensureAudioEffects(it) },
            desiredEqSettings = configCollector.desiredEqSettings,
        )
    }

    internal val playerEngineHolder = PlayerEngineHolder()
    internal val serviceSessionHolder = ServiceSessionHolder()

    internal var audioManager: AudioManager get() = audioPolicyHolder.audioManager; set(v) { audioPolicyHolder.audioManager = v }
    internal var audioFocusRequest: AudioFocusRequest? get() = audioPolicyHolder.audioFocusRequest; set(v) { audioPolicyHolder.audioFocusRequest = v }
    internal var lastAudioFocusState: Int get() = audioPolicyHolder.lastAudioFocusState; set(v) { audioPolicyHolder.lastAudioFocusState = v }
    internal var wasPlayingBeforeAudioFocusLoss: Boolean get() = audioPolicyHolder.wasPlayingBeforeAudioFocusLoss; set(v) { audioPolicyHolder.wasPlayingBeforeAudioFocusLoss = v }
    override var pauseOnDeviceMuteEnabled: Boolean get() = audioPolicyHolder.pauseOnDeviceMuteEnabled; set(v) { audioPolicyHolder.pauseOnDeviceMuteEnabled = v }
    override var deviceMutePlaybackRecoveryVolumePercent: Int get() = audioPolicyHolder.deviceMutePlaybackRecoveryVolumePercent; set(v) { audioPolicyHolder.deviceMutePlaybackRecoveryVolumePercent = v }
    override var wasAutoPausedByDeviceMute: Boolean get() = audioPolicyHolder.wasAutoPausedByDeviceMute; set(v) { audioPolicyHolder.wasAutoPausedByDeviceMute = v }
    internal var muteRecoveryObserver: ContentObserver? get() = audioPolicyHolder.muteRecoveryObserver; set(v) { audioPolicyHolder.muteRecoveryObserver = v }
    internal var lastDeviceMutePlaybackNoticeAtElapsedMs: Long get() = audioPolicyHolder.lastDeviceMutePlaybackNoticeAtElapsedMs; set(v) { audioPolicyHolder.lastDeviceMutePlaybackNoticeAtElapsedMs = v }
    internal var hasAudioFocus: Boolean get() = audioPolicyHolder.hasAudioFocus; set(v) { audioPolicyHolder.hasAudioFocus = v }
    internal val autoStartOnBluetoothEnabled: Boolean get() = serviceLifecycle.autoStartOnBluetoothEnabled
    private var wakeLock: PowerManager.WakeLock? = null
    internal var audioRouteRecoveryJob: Job? get() = audioPolicyHolder.audioRouteRecoveryJob; set(v) { audioPolicyHolder.audioRouteRecoveryJob = v }
    internal var audiblePlaybackRecoveryJob: Job? get() = audioPolicyHolder.audiblePlaybackRecoveryJob; set(v) { audioPolicyHolder.audiblePlaybackRecoveryJob = v }
    internal var lastAudioOutputDeviceSignature: String? get() = audioPolicyHolder.lastAudioOutputDeviceSignature; set(v) { audioPolicyHolder.lastAudioOutputDeviceSignature = v }
    internal var lastAudioRouteRecoveryRealtimeMs: Long get() = audioPolicyHolder.lastAudioRouteRecoveryRealtimeMs; set(v) { audioPolicyHolder.lastAudioRouteRecoveryRealtimeMs = v }

    internal val audioDeviceCallback: AudioDeviceCallback get() = audioPolicyHolder.audioDeviceCallback

    internal val serviceLifecycle: MusicServiceLifecycle by lazy {
        MusicServiceLifecycle(
            scope = scope,
            ioScope = ioScope,
            delegate = this,
        )
    }
    private val binder = MusicBinder()
    internal val hasBoundClients: Boolean get() = serviceLifecycle.hasBoundClients

    override lateinit var connectivityManager: ConnectivityManager
    override lateinit var connectivityObserver: NetworkConnectivityObserver
    override val isNetworkConnected = MutableStateFlow(false)
    val waitingForNetworkConnection: MutableStateFlow<Boolean> get() = playbackRecoveryEngine.waitingForNetworkConnection
    internal var networkStallRecoveryJob: Job? get() = playbackRecoveryEngine.networkStallRecoveryJob; set(value) { playbackRecoveryEngine.networkStallRecoveryJob = value }

    override val playbackRecoveryEngine: PlaybackRecoveryEngine by lazy {
        createPlaybackRecoveryEngine()
    }

    internal val audioQuality: AudioQuality get() = configCollector.audioQuality
    internal val preferredStreamClient: PlayerStreamClient get() = configCollector.preferredStreamClient
    internal val enableMemoryCache: Boolean get() = configCollector.enableMemoryCache
    internal val playbackUrlCache get() = playerEngineHolder.playbackUrlCache
    internal val losslessUrlCache get() = playerEngineHolder.losslessUrlCache
    @Volatile override var currentPlaybackSource: PlaybackSource = PlaybackSource.YT_MUSIC
    @Volatile override var isLowDataEnabled: Boolean = true
    internal val extractorPlaybackUrlCache get() = playerEngineHolder.extractorPlaybackUrlCache
    internal val remotePlaybackTrackingUrlCache get() = playerEngineHolder.remotePlaybackTrackingUrlCache
    internal val contentLengthCache get() = playerEngineHolder.contentLengthCache
    internal fun invalidatePlaybackUrlCache(mediaId: String) = playerEngineHolder.invalidatePlaybackUrlCache(mediaId)
    private fun invalidateLosslessUrlCache(mediaId: String) = playerEngineHolder.invalidateLosslessUrlCache(mediaId)
    internal val streamingExtractionManager get() = playerEngineHolder.streamingExtractionManager
    internal val mediaOkHttpClient: OkHttpClient get() = playerEngineHolder.mediaOkHttpClient
    internal val extractorMediaOkHttpClient: OkHttpClient get() = playerEngineHolder.extractorMediaOkHttpClient

    internal var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null
    @Volatile
    internal var isInitializingQueue = false
    internal var playQueueJob: Job? = null
    @Volatile
    override var isRestoringPersistentState = false

    @Volatile
    internal var isHydratingRestoredQueue = false
    internal val restoredQueueHydrationGeneration = AtomicLong(0L)
    internal var restoredQueueBackfillJob: Job? = null

    @Volatile
    internal var suppressAutoPlayback = false

    internal val discordHoldController = DiscordHoldController(this)
    internal val discordSyncOrchestrator by lazy { DiscordSyncOrchestrator(this, discordHoldController) }

    internal var activeDiscordHoldState: ActiveHoldState? get() = discordHoldController.activeHoldState; set(v) { discordHoldController.activeHoldState = v }
    internal var activeDiscordHoldTimeoutJob: Job? get() = discordHoldController.activeHoldTimeoutJob; set(v) { discordHoldController.activeHoldTimeoutJob = v }
    internal var lastAppliedVisiblePresence: LastAppliedVisiblePresence? get() = discordHoldController.lastAppliedVisiblePresence; set(v) { discordHoldController.lastAppliedVisiblePresence = v }
    internal var lastDiscordPresenceDecision: DiscordPresenceDecision? get() = discordHoldController.lastDecision; set(v) { discordHoldController.lastDecision = v }
    internal var pausedPresenceGate: PausedPresenceGate get() = discordHoldController.pausedPresenceGate; set(v) { discordHoldController.pausedPresenceGate = v }
    internal var discordServiceStopping: Boolean get() = discordSyncOrchestrator.discordServiceStopping; set(v) { discordSyncOrchestrator.discordServiceStopping = v }
    internal var lastPresenceToken: String? get() = discordSyncOrchestrator.lastPresenceToken; set(v) { discordSyncOrchestrator.lastPresenceToken = v }
    internal val discordSyncEpoch: AtomicLong get() = discordSyncOrchestrator.discordSyncEpoch
    internal val discordSyncRequests: Channel<DiscordSyncRequest> get() = discordSyncOrchestrator.discordSyncRequests
    internal var discordSyncWorkerJob: Job? get() = discordSyncOrchestrator.discordSyncWorkerJob; set(v) { discordSyncOrchestrator.discordSyncWorkerJob = v }
    internal val pendingDiscordRefreshWaiters: MutableList<CompletableDeferred<Boolean>> get() = discordSyncOrchestrator.pendingDiscordRefreshWaiters
    internal val discordRefreshWaitersMutex: Mutex get() = discordSyncOrchestrator.discordRefreshWaitersMutex

    internal val playbackStreamRecoveryTracker = PlaybackStreamRecoveryTracker()
    internal val queuePersistenceStore: QueuePersistenceStore by lazy {
        createQueuePersistenceStore()
    }
    internal var nextHistorySessionToken = 0L
    internal var currentHistorySessionToken = 0L
    internal var currentHistoryMediaId: String? = null
    internal var currentHistoryAccumulatedPlayMs = 0L
    internal var currentHistoryStartedAtElapsedMs: Long? = null
    internal var currentHistoryEventId: Long? = null
    internal var currentHistoryRemoteRegistered = false
    internal var currentHistoryImmediateAttempted = false
    internal var currentHistorySessionQueued = false
    internal var historyThresholdJob: Job? = null
    internal val pendingHistoryFinalizations = mutableMapOf<String, MutableList<PendingHistoryFinalization>>()
    internal val historyRecordingJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<ImmediateHistoryResult>>()

    override val currentMediaMetadata: MutableStateFlow<moe.rukamori.archivetune.models.MediaMetadata?> get() = configCollector.currentMediaMetadata
    override val queueRestoreCompleted = MutableStateFlow(false)
    val infiniteQueueLoading = MutableStateFlow(false)
    internal var infiniteQueueJob: Job? = null
    override val playerInitialized = MutableStateFlow(false)
    override val currentSong: StateFlow<Song?> get() = configCollector.currentSong
    override val currentFormat: kotlinx.coroutines.flow.Flow<FormatEntity?> get() = configCollector.currentFormat

    override val normalizeFactor: MutableStateFlow<Float> get() = audioPolicyHolder.normalizeFactor
    internal val audioNormalizationFactorCache: ConcurrentHashMap<String, Float> get() = audioPolicyHolder.audioNormalizationFactorCache
    override var audioNormalizationEnabled: Boolean get() = audioPolicyHolder.audioNormalizationEnabled; set(v) { audioPolicyHolder.audioNormalizationEnabled = v }
    override var playerVolume: MutableStateFlow<Float> get() = audioPolicyHolder.playerVolume; set(v) { audioPolicyHolder.playerVolume = v }
    override val audioFocusVolumeFactor: MutableStateFlow<Float> get() = audioPolicyHolder.audioFocusVolumeFactor
    internal var effectiveVolumeRampJob: Job? get() = audioPolicyHolder.effectiveVolumeRampJob; set(v) { audioPolicyHolder.effectiveVolumeRampJob = v }
    override var crossfadeEnabled = false
    override var automixEnabled = false
    override var automixTransitionPreset = "auto"
    internal var activeAutomixPlan: AutomixPlan? = null
    override var crossfadeDurationMs = 0L
    override var crossfadeGapless = false
    internal var crossfadeTriggerJob: Job? = null
    internal var activeCrossfadeScheduledKey: Pair<String, CrossfadeTarget>? = null
    internal var crossfadeJob: Job? = null
    internal var controller: DeckController get() = playerEngineHolder.controller; set(v) { playerEngineHolder.controller = v }
    val isControllerInitialized: Boolean get() = playerEngineHolder.isControllerInitialized
    val activeDeck: AudioDeck get() = playerEngineHolder.activeDeck
    val transitionDeck: AudioDeck? get() = playerEngineHolder.transitionDeck
    override var crossfadeConfig: CrossfadeConfig = CrossfadeConfig()
    internal val analysisStore: AnalysisStore get() = TrackAnalyzer

    internal val dualForwardingPlayer: DualForwardingPlayer get() = playerEngineHolder.dualForwardingPlayer
    internal val dualPlayerRoleHolder: DualPlayerRoleHolder get() = playerEngineHolder.dualPlayerRoleHolder
    override val secondaryCrossfadePlayer: ExoPlayer? get() = playerEngineHolder.secondaryCrossfadePlayer
    internal var secondaryCrossfadeTarget: CrossfadeTarget? get() = playerEngineHolder.secondaryCrossfadeTarget; set(v) { playerEngineHolder.secondaryCrossfadeTarget = v }
    internal val reserveCrossfadePlayer: ExoPlayer? get() = playerEngineHolder.reserveCrossfadePlayer

    internal var secondaryPreparationFailedMediaId: String? = null
    internal var hasPreparedSecondaryPlayer: Boolean = false
    internal var isCrossfading = false
    internal var crossfadeHandoffInProgress = false
    internal var crossfadeBaseVolume = 1f
    internal var crossfadeIncomingBaseVolume = 1f
    internal var crossfadeProgress = 0f
    internal var crossfadePlaybackRequested = false
    internal val djFilterByPlayer: ConcurrentHashMap<ExoPlayer, DjFilterAudioProcessor> get() = playerEngineHolder.djFilterByPlayer
    internal var lyricsPreloadManager: LyricsPreloadManager? = null
    internal var streamPrefetcher: StreamPrefetcher? = null
    internal lateinit var playerListeners: MusicServicePlayerListeners

    internal val secondaryCrossfadeListener: Player.Listener get() = playerListeners.secondaryCrossfadeListener

    override fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                processInfo.processName == packageName
        }
    }

    internal fun promptLoginRecovery(
        mediaId: String,
        targetUrl: String,
    ) {
        playbackRecoveryEngine.promptLoginRecovery(mediaId, targetUrl)
    }

    lateinit var sleepTimer: AdvancedSleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: Cache

    @Inject
    @DownloadCache
    override lateinit var downloadCache: Cache

    internal val registeredCacheKeys: MutableSet<String> get() = playbackRecoveryEngine.registeredCacheKeys

    internal val automixCacheListener: Cache.Listener by lazy {
        playbackRecoveryEngine.createAutomixCacheListener(
            tag = TAG,
            flacCacheKeyPrefix = FLAC_CACHE_KEY_PREFIX,
        ) { cache, key, mediaId ->
            if (secondaryPreparationFailedMediaId == mediaId) {
                secondaryPreparationFailedMediaId = null
                scope.launch { scheduleCrossfade() }
            }
            if (cache !== playerCache && isFullyCached(cache, key)) {
                if (!TrackAnalyzer.hasCached(mediaId)) {
                    kickOffTrackAnalysis(mediaId)
                }
            }
        }
    }

    override var localPlayer: ExoPlayer get() = playerEngineHolder.localPlayer; internal set(value) { playerEngineHolder.localPlayer = value }
    override var player: Player get() = playerEngineHolder.player; internal set(value) { playerEngineHolder.player = value }

    internal fun transferAudioEffects(to: ExoPlayer) {
        playerEngineHolder.transferAudioEffects(to, audioEffectPlayerListener)
    }
    private lateinit var castPlaybackRepository: CastPlaybackRepository
    internal val mediaSession: MediaLibrarySession get() = serviceSessionHolder.mediaSession

    internal var isAudioEffectSessionOpened: Boolean get() = audioPolicyHolder.isAudioEffectSessionOpened; set(v) { audioPolicyHolder.isAudioEffectSessionOpened = v }
    internal var openedAudioSessionId: Int? get() = audioPolicyHolder.openedAudioSessionId; set(v) { audioPolicyHolder.openedAudioSessionId = v }
    val eqCapabilities: MutableStateFlow<EqCapabilities?> get() = audioPolicyHolder.eqCapabilities
    override val desiredEqSettings: MutableStateFlow<EqSettings> get() = audioPolicyHolder.desiredEqSettings
    internal var audioEffectsSessionId: Int? get() = audioPolicyHolder.audioEffectsSessionId; set(v) { audioPolicyHolder.audioEffectsSessionId = v }
    internal var audioEffectsInitializationJob: Job? get() = audioPolicyHolder.audioEffectsInitializationJob; set(v) { audioPolicyHolder.audioEffectsInitializationJob = v }
    internal var equalizer: Equalizer? get() = audioPolicyHolder.equalizer; set(v) { audioPolicyHolder.equalizer = v }
    internal var bassBoost: BassBoost? get() = audioPolicyHolder.bassBoost; set(v) { audioPolicyHolder.bassBoost = v }
    internal var virtualizer: Virtualizer? get() = audioPolicyHolder.virtualizer; set(v) { audioPolicyHolder.virtualizer = v }
    internal var loudnessEnhancer: LoudnessEnhancer? get() = audioPolicyHolder.loudnessEnhancer; set(v) { audioPolicyHolder.loudnessEnhancer = v }
    internal val audioEffectPlayerListener: Player.Listener get() = playerListeners.audioEffectPlayerListener

    internal var lastDiscordUpdateTime = 0L

    override var scrobbleManager: moe.rukamori.archivetune.utils.ScrobbleManager? = null

    internal lateinit var widgetUpdater: MusicServiceWidgetUpdater

    val autoAddedMediaIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    internal var consecutivePlaybackErr: Int get() = playbackRecoveryEngine.consecutivePlaybackErrorCount; set(value) { playbackRecoveryEngine.consecutivePlaybackErr = value }

    val maxSafeGainFactor: Float get() = audioPolicyHolder.maxSafeGainFactor

    override val togetherSessionState: MutableStateFlow<moe.rukamori.archivetune.together.TogetherSessionState> get() = configCollector.togetherSessionState
    internal var togetherServer: moe.rukamori.archivetune.together.TogetherServer? get() = configCollector.togetherServer; set(v) { configCollector.togetherServer = v }
    internal var togetherOnlineHost: moe.rukamori.archivetune.together.TogetherOnlineHost? get() = configCollector.togetherOnlineHost; set(v) { configCollector.togetherOnlineHost = v }
    internal var togetherClient: moe.rukamori.archivetune.together.TogetherClient? get() = configCollector.togetherClient; set(v) { configCollector.togetherClient = v }
    internal var togetherBroadcastJob: Job? get() = configCollector.togetherBroadcastJob; set(v) { configCollector.togetherBroadcastJob = v }
    internal var togetherOnlineConnectJob: Job? get() = configCollector.togetherOnlineConnectJob; set(v) { configCollector.togetherOnlineConnectJob = v }
    internal var togetherClientEventsJob: Job? get() = configCollector.togetherClientEventsJob; set(v) { configCollector.togetherClientEventsJob = v }
    internal var togetherHeartbeatJob: Job? get() = configCollector.togetherHeartbeatJob; set(v) { configCollector.togetherHeartbeatJob = v }
    internal var togetherClock: moe.rukamori.archivetune.together.TogetherClock? get() = configCollector.togetherClock; set(v) { configCollector.togetherClock = v }
    internal var togetherSelfParticipantId: String? get() = configCollector.togetherSelfParticipantId; set(v) { configCollector.togetherSelfParticipantId = v }
    internal var togetherAuthorityParticipantId: String? get() = configCollector.togetherAuthorityParticipantId; set(v) { configCollector.togetherAuthorityParticipantId = v }
    internal var togetherLastAppliedQueueHash: String? get() = configCollector.togetherLastAppliedQueueHash; set(v) { configCollector.togetherLastAppliedQueueHash = v }
    internal var togetherIsOnlineSession: Boolean get() = configCollector.togetherIsOnlineSession; set(v) { configCollector.togetherIsOnlineSession = v }
    internal var togetherApplyingRemote: Boolean get() = configCollector.togetherApplyingRemote; set(v) { configCollector.togetherApplyingRemote = v }
    internal var togetherSuppressEchoUntilElapsedMs: Long get() = configCollector.togetherSuppressEchoUntilElapsedMs; set(v) { configCollector.togetherSuppressEchoUntilElapsedMs = v }
    internal var togetherLastAppliedRoomStateSentAtElapsedMs: Long get() = configCollector.togetherLastAppliedRoomStateSentAtElapsedMs; set(v) { configCollector.togetherLastAppliedRoomStateSentAtElapsedMs = v }
    internal var togetherLastRemoteAppliedPlayWhenReady: Boolean? get() = configCollector.togetherLastRemoteAppliedPlayWhenReady; set(v) { configCollector.togetherLastRemoteAppliedPlayWhenReady = v }
    internal var togetherLastRemoteAppliedIndex: Int get() = configCollector.togetherLastRemoteAppliedIndex; set(v) { configCollector.togetherLastRemoteAppliedIndex = v }
    internal var togetherLastSentControlAtElapsedMs: Long get() = configCollector.togetherLastSentControlAtElapsedMs; set(v) { configCollector.togetherLastSentControlAtElapsedMs = v }
    internal var togetherLastSentControlAction: moe.rukamori.archivetune.together.ControlAction? get() = configCollector.togetherLastSentControlAction; set(v) { configCollector.togetherLastSentControlAction = v }

    internal fun isTogetherApplyingRemote(): Boolean = configCollector.isTogetherApplyingRemote()
    internal val togetherHostId: String get() = configCollector.togetherHostId

    internal fun showTogetherParticipantNotification(participantName: String, joined: Boolean) =
        serviceSessionHolder.showTogetherParticipantNotification(this, participantName, joined)

    internal suspend fun getOrCreateTogetherClientId(): String = configCollector.getOrCreateTogetherClientId()
    internal fun ensureStartedAsForeground() = serviceLifecycle.ensureStartedAsForeground()
    internal fun promoteToStartedService() = serviceLifecycle.promoteToStartedService()
    internal fun cancelIdleStop() = serviceLifecycle.cancelIdleStop()
    internal fun hasResumablePlaybackNotification(): Boolean = serviceLifecycle.hasResumablePlaybackNotification()
    internal fun scheduleStopIfIdle() = serviceLifecycle.scheduleStopIfIdle()

    override fun onCreate() {
        super.onCreate()
        ProxyAuth.init()
        ensureScopesActive()

        try {
            MusicServiceNotification.createNotificationChannels(
                context = this,
                notificationManager = getSystemService(NotificationManager::class.java),
            )
        } catch (e: Exception) {
            reportException(e)
        }

        val localDjFilter = DjFilterAudioProcessor()
        playerListeners = createMusicServicePlayerListeners()
        localPlayer =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setRenderersFactory(createRenderersFactory(localDjFilter))
                .setLoadControl(createPrimaryLoadControl())
                .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    playbackAudioAttributes(),
                    false,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setDeviceVolumeControlEnabled(true)
                .build()
                .apply {
                    addAnalyticsListener(PlaybackStatsListener(false, playerListeners))
                    addListener(playerListeners.audioEffectPlayerListener)
                    setOffloadEnabled(false)
                }
        djFilterByPlayer[localPlayer] = localDjFilter
        castPlaybackRepository = CastPlaybackRepositoryLocator.get(this)
        val basePlayer =
            castPlaybackRepository
                .createPlayer(
                    context = this,
                    localPlayer = localPlayer,
                    mediaItemResolver = CastMediaItemResolver(::resolveMediaItemForCast),
                )
        val initialDualForwardingPlayer = DualForwardingPlayer(basePlayer)
        val initialDeck = ExoDeck(player = localPlayer, djFilter = localDjFilter)
        controller =
            ExoDeckController(
                service = this,
                initialDeck = initialDeck,
                dualForwardingPlayer = initialDualForwardingPlayer,
            )
        player =
            initialDualForwardingPlayer.apply {
                addListener(playerListeners)
                sleepTimer = AdvancedSleepTimer(scope, this)
                addListener(sleepTimer)
            }
        playerInitialized.value = true
        widgetUpdater =
            MusicServiceWidgetUpdater(
                service = this,
                player = player,
                scope = scope,
                loadWidgetInsights = loadWidgetInsightsUseCase,
            )

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioManager.setAllowedCapturePolicy(android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        }
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArchiveTune:Playback")
                .also { it.setReferenceCounted(false) }
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        serviceSessionHolder.buildMediaLibrarySession(
            service = this,
            player = player,
            callback = mediaLibrarySessionCallback,
            scope = scope,
        )
        setMediaNotificationProvider(
            serviceSessionHolder.createNotificationProvider(this),
        )

        player.repeatMode = REPEAT_MODE_OFF

        serviceSessionHolder.initSessionTokenAndController(this, MusicService::class.java)

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        serviceLifecycle.onCreate()
    }

    internal fun ensureScopesActive() {
        if (!scopeJob.isActive) scopeJob = Job()
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main + scopeJob)
        if (!ioScope.isActive) ioScope = CoroutineScope(Dispatchers.IO + scopeJob)
        startDiscordSyncWorker()
    }

    override fun cancelRestoredQueueHydration() {
        queuePersistenceStore.cancelRestoredQueueHydration()
        restoredQueueHydrationGeneration.incrementAndGet()
        restoredQueueBackfillJob?.cancel()
        restoredQueueBackfillJob = null
        isHydratingRestoredQueue = false
    }

    override fun ensurePresenceManager() = ensurePresenceManagerInternal()

    internal fun calculateAudioNormalizationFactor(format: FormatEntity?, normalizeAudio: Boolean): Float =
        moe.rukamori.archivetune.playback.calculateAudioNormalizationFactor(format, normalizeAudio)

    override fun resolveAudioNormalizationFactor(mediaId: String?, format: FormatEntity?, normalizeAudio: Boolean): Float =
        moe.rukamori.archivetune.playback.resolveAudioNormalizationFactor(mediaId, format, normalizeAudio)

    fun hasAudioFocusForPlayback(): Boolean = audioPolicyHolder.hasAudioFocusForPlayback()

    override fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun registerBluetoothReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(receiver, filter, RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
    }

    override fun unregisterBluetoothReceiver(receiver: BroadcastReceiver) { runCatching { unregisterReceiver(receiver) } }

    internal fun forceRevivePlayback(): Boolean = playbackRecoveryEngine.forceRevivePlayback()
    internal fun revivePlaybackFromStall() = playbackRecoveryEngine.revivePlaybackFromStall()
    internal fun evictMediaConnectionPools() = playerEngineHolder.evictMediaConnectionPools()
    internal fun skipOnError() = playbackRecoveryEngine.skipOnError()
    internal fun stopOnError() = playbackRecoveryEngine.stopOnError()

    override fun updateNotification() {
        serviceSessionHolder.updateNotification(
            context = this, player = player, mediaMetadata = currentMediaMetadata.value ?: player.currentMetadata,
            hasMetadata = currentMediaMetadata.value != null || player.currentMediaItem != null, isLiked = currentSong.value?.song?.liked == true,
        )
    }

    fun refreshPlaybackNotification() {
        serviceSessionHolder.refreshPlaybackNotification(
            context = this, player = player, mediaMetadata = currentMediaMetadata.value ?: player.currentMetadata,
            hasMetadata = currentMediaMetadata.value != null || player.currentMediaItem != null, isLiked = currentSong.value?.song?.liked == true,
            onUpdateNotification = ::onUpdateNotification,
        )
    }

    internal suspend fun recoverSong(mediaId: String, playbackData: YTPlayerUtils.PlaybackData? = null) =
        playbackRecoveryEngine.recoverSong(mediaId, playbackData)

    fun playQueue(queue: Queue, playWhenReady: Boolean = true) = playQueueInternal(queue, playWhenReady)
    fun startRadioSeamlessly() = startRadioSeamlesslyInternal()
    fun clearAutomix() = clearAutomixInternal()
    fun clearQueue() = clearQueueInternal()
    fun onInfiniteQueueDisabled() = onInfiniteQueueDisabledInternal()
    fun onInfiniteQueueEnabled() = onInfiniteQueueEnabledInternal()
    fun stopAndClearPlayback(clearPersistentState: Boolean = false) = stopAndClearPlaybackInternal(clearPersistentState)
    fun playNext(items: List<MediaItem>) = playNextInternal(items)
    fun addToQueue(items: List<MediaItem>) = addToQueueInternal(items)
    fun playFromVoiceSearch(query: String) = playFromVoiceSearchInternal(query)
    internal fun toggleLibrary() = toggleLibraryInternal()
    fun toggleLike() = toggleLikeInternal()
    fun toggleStartRadio() = toggleStartRadioInternal()

    internal fun decodeBandLevelsMb(raw: String?): List<Int> = audioPolicyHolder.decodeBandLevelsMb(raw)
    internal fun encodeBandLevelsMb(levelsMb: List<Int>): String = audioPolicyHolder.encodeBandLevelsMb(levelsMb)
    fun applyEqFlatPreset() = audioPolicyHolder.applyEqFlatPreset()
    fun applySystemEqPreset(presetIndex: Int) = audioPolicyHolder.applySystemEqPreset(presetIndex) { localPlayer.audioSessionId }

    internal fun isCurrentPlaybackItemLocal(currentMediaMetadata: MediaMetadata): Boolean =
        currentSong.value?.song?.isLocal == true || currentMediaMetadata.id.trim().isLocalMediaId() || player.currentMediaItem?.localConfiguration?.uri?.shouldBypassPlayerCache() == true

    internal fun onMediaItemTransitionInternal() = playerListeners.onMediaItemTransitionInternal()
    internal fun onPlayerError(error: PlaybackException) = playbackRecoveryEngine.onPlayerError(error)

    internal suspend fun handlePresenceAndListenBrainz(
        mediaId: String?, mediaMetadata: moe.rukamori.archivetune.models.MediaMetadata?, durationMs: Long, positionMs: Long, reason: String,
    ) = handlePresenceAndListenBrainzInternal(mediaId, mediaMetadata, durationMs, positionMs, reason)

    override fun updateAudioOffload(enabled: Boolean) {
        playerEngineHolder.updateAudioOffload(enabled, crossfadeEnabled)
    }

    internal fun updateWakeLock() = serviceLifecycle.updateWakeLock()

    override fun acquireWakeLock() {
        runCatching { wakeLock?.acquire() }
    }

    override fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
    }

    override fun isWakeLockHeld(): Boolean = wakeLock?.isHeld == true

    private fun createPrimaryLoadControl(): DefaultLoadControl = playerEngineHolder.createPrimaryLoadControl()

    internal fun createCrossfadeLoadControl(): DefaultLoadControl = playerEngineHolder.createCrossfadeLoadControl()

    internal fun createRenderersFactory(djFilter: DjFilterAudioProcessor) =
        playerEngineHolder.createRenderersFactory(this, djFilter)

    internal fun djFilterFor(player: ExoPlayer?): DjFilterAudioProcessor? = playerEngineHolder.djFilterFor(player)

    internal fun resetDjFilters(vararg players: ExoPlayer?) {
        playerEngineHolder.resetDjFilters(*players)
    }

    internal fun handlePlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) = PlaybackHistoryStore(this).handlePlaybackStatsReady(eventTime, playbackStats)

    internal fun currentPresenceSong(): Song? =
        resolvePresenceSong(
            dbSong = currentSong.value,
            mediaMetadata = player.currentMetadata,
            durationMs = player.duration,
        )

    internal fun createTransientSongFromMedia(media: MediaMetadata): Song =
        moe.rukamori.archivetune.playback.createTransientSongFromMedia(media)

    override fun onDestroy() {
        serviceLifecycle.onDestroy {
            super.onDestroy()
        }
    }

    override fun onBind(intent: Intent?): android.os.IBinder? =
        serviceLifecycle.onBind(intent, super.onBind(intent))

    override fun onUnbind(intent: Intent?): Boolean {
        serviceLifecycle.onUnbind(intent)
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        serviceLifecycle.onRebind(intent)
        super.onRebind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        serviceLifecycle.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        serviceLifecycle.onGetSession(controllerInfo)

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int =
        serviceLifecycle.onStartCommand(intent, flags, startId) {
            super.onStartCommand(intent, flags, startId)
        }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        serviceLifecycle.onUpdateNotification(session, startInForegroundRequired) { keepInForeground ->
            try {
                super.onUpdateNotification(session, keepInForeground)
            } catch (e: IllegalStateException) {
                reportException(e)
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }

    override val dataStore: DataStore<Preferences> get() = (this as Context).dataStore

    override val serviceBinder: IBinder get() = binder

    override val mediaLibrarySession: MediaLibrarySession get() = mediaSession

    override fun computeEffectivePlayerVolume(volume: Float, factor: Float, focus: Float): Float = this.calculateEffectivePlayerVolume(volume, factor, focus)
    override fun applyEffectiveVolume(volume: Float) = this.updateEffectiveVolume(volume)
    override fun updateCurrentMediaMetadata(metadata: MediaMetadata?) { currentMediaMetadata.value = metadata }
    override fun dispatchDiscordSync(reason: String, force: Boolean) = this.requestDiscordSync(reason = reason, force = force)
    override fun handleMediaNotificationDismissedWhilePausedInBackground() {
        pausedPresenceGate = PausedPresenceGate.HiddenByNotificationDismiss
        requestDiscordSync(reason = "notification_dismissed_while_paused_background", force = true)
    }

    override fun initLyricsPreloadManager() {
        lyricsPreloadManager = LyricsPreloadManager(context = this, database = database, networkConnectivity = connectivityObserver, lyricsHelper = lyricsHelper)
    }

    override fun parseEqSettings(prefs: Preferences): EqSettings = this.readEqSettingsFromPrefs(prefs)
    override fun applyEqSettings(settings: EqSettings) = this.applyEqSettingsToEffects(settings)
    override fun onDeviceMuteChanged() = this.handleDeviceMuteStateChanged(playbackRequestedWhileMuted = false)
    override fun removeMuteRecoveryObserver() = this.unregisterMuteRecoveryObserver()
    override fun schedulePlayerCrossfade() = this.scheduleCrossfade()
    override fun cancelPlayerCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) = this.cancelCrossfade(resetVolume = resetVolume, resetPauseAtEnd = resetPauseAtEnd)
    override fun registerCacheListener(key: String) = this.registerCacheListenerForKey(key)
    override suspend fun performTrimPlayerCache(limitBytes: Long) = playbackRecoveryEngine.trimPlayerCacheToBytes(limitBytes)
    override fun createScrobbleManager(minSongDuration: Int, scrobbleDelayPercent: Float, scrobbleDelaySeconds: Int): moe.rukamori.archivetune.utils.ScrobbleManager =
        moe.rukamori.archivetune.utils.ScrobbleManager(ioScope, minSongDuration = minSongDuration, scrobbleDelayPercent = scrobbleDelayPercent, scrobbleDelaySeconds = scrobbleDelaySeconds)

    override suspend fun restorePersistentState() {
        val persistedQueue = readPersistentObject<PersistQueue>(PERSISTENT_QUEUE_FILE)
        val persistedPlayerState = readPersistentObject<PersistPlayerState>(PERSISTENT_PLAYER_STATE_FILE)
        if (persistedQueue != null || persistedPlayerState != null) isRestoringPersistentState = true
        var restoredQueue = false
        try {
            persistedQueue?.let { queue -> restorePersistentQueue(queue); restoredQueue = true }
            persistedPlayerState?.let { playerState -> restorePersistentPlayerState(playerState, restoredQueue) }
        } finally {
            isRestoringPersistentState = false
        }
    }

    override fun clearQueuePersistenceFiles() = this.clearPersistedQueueFiles()
    override suspend fun saveQueueToStorage() = this.saveQueueToDisk()
    override fun startForegroundPlaybackService(): Boolean = serviceSessionHolder.startForegroundPlaybackService(this)
    override fun stopForegroundService() = serviceSessionHolder.stopForegroundService(this)
    override fun stopSelfService() = stopSelf()
    override fun startSelfService() { runCatching { startService(Intent(this, MusicService::class.java)) }.onFailure { reportException(it) } }
    override fun registerAudioDeviceCallback() = audioPolicyHolder.registerAudioDeviceCallback(mainLooper, currentAudioOutputDeviceSignature())
    override fun unregisterAudioDeviceCallback() = audioPolicyHolder.unregisterAudioDeviceCallback()
    override fun cancelStreamPrefetch() = this.cancelPrefetch()
    override fun setDiscordServiceStopping(stopping: Boolean) { discordServiceStopping = stopping }
    override fun cancelEffectiveVolumeRamp() = audioPolicyHolder.cancelEffectiveVolumeRamp()
    override fun cancelAudioRouteRecovery() = audioPolicyHolder.cancelAudioRouteRecovery()
    override suspend fun stopTogether() = stopTogetherInternal()
    override fun abandonPlaybackAudioFocus() = this.abandonAudioFocus()
    override fun releaseServiceAudioEffects() = this.releaseAudioEffects()
    override fun releaseReserveAndTransitionDecks() = playerEngineHolder.releaseReserveAndTransitionDecks()
    override fun removeDjFilterFromLocalPlayer() = playerEngineHolder.removeDjFilterFromLocalPlayer()
    override fun removePlayerListeners() {
        localPlayer.removeListener(playerListeners.audioEffectPlayerListener)
        player.removeListener(playerListeners)
        player.removeListener(sleepTimer)
    }

    override fun releasePlayer() = playerEngineHolder.releasePlayer()
    override fun releaseAllCacheListeners() = this.unregisterAllCacheListeners()
    override fun cancelScopeJob() = scopeJob.cancel()
    override fun isTogetherHostSessionActive(): Boolean = configCollector.isTogetherHostSessionActive()
    override fun isTogetherSessionIdle(): Boolean = configCollector.isTogetherSessionIdle()
    override fun resetTogetherSessionState() = configCollector.resetTogetherSessionState()
    override fun isTogetherGuestSessionActive(): Boolean = this.isTogetherGuestSession()
    override fun stopAndClearPlaybackSession(clearPersistentState: Boolean) = this.stopAndClearPlayback(clearPersistentState)

    // ── Widget Support ────────────────────────────────────────────────────────────

    fun updateWidget() = widgetUpdater.update()

    inner class MusicBinder : Binder() {
        val service: MusicService get() = this@MusicService
    }

    companion object {
        internal fun shouldStopServiceOnTaskRemoved(
            stopMusicOnTaskClearEnabled: Boolean,
            isHostSessionActive: Boolean,
            isPlaybackInactive: Boolean,
        ): Boolean = PlaybackConstants.shouldStopServiceOnTaskRemoved(
            stopMusicOnTaskClearEnabled,
            isHostSessionActive,
            isPlaybackInactive,
        )

        const val ROOT = "root"
        const val HOME = "home"
        const val HOME_QUICK_PICKS = "home_quick_picks"
        const val HOME_FORGOTTEN_FAVORITES = "home_forgotten_favorites"
        const val HOME_KEEP_LISTENING = "home_keep_listening"
        const val HOME_SUGGESTED_SONGS = "home_suggested_songs"
        const val HOME_MIXES_AND_RADIOS = "home_mixes_and_radios"
        const val QUICK_PICKS = "quick_picks"
        const val RECENT = "recent"
        const val LIKED = "liked"
        const val DOWNLOADED = "downloaded"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val ONLINE_PLAYLIST = "online_playlist"

        internal const val TAG = "MusicService"
        internal const val AUDIO_EFFECT_INITIALIZATION_MAX_ATTEMPTS = 4
        internal const val AUDIO_EFFECT_INITIALIZATION_RETRY_DELAY_MS = 250L
        internal const val DISCORD_SYNC_TAG = "DiscordSync"
        internal const val DISCORD_HOLD_TIMEOUT_MS = 7_000L
        const val CHANNEL_ID = MusicServiceNotification.CHANNEL_ID
        const val ACTION_MEDIA_NOTIFICATION_DISMISSED =
            MusicServiceNotification.ACTION_MEDIA_NOTIFICATION_DISMISSED
        const val EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT =
            MusicServiceNotification.EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT
        const val NOTIFICATION_ID = MusicServiceNotification.NOTIFICATION_ID
        internal const val TOGETHER_NOTIFICATION_CHANNEL_ID =
            MusicServiceNotification.TOGETHER_NOTIFICATION_CHANNEL_ID
        internal const val TOGETHER_PARTICIPANT_NOTIFICATION_ID =
            MusicServiceNotification.TOGETHER_PARTICIPANT_NOTIFICATION_ID
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = PlaybackConstants.CHUNK_LENGTH
        val RETRYABLE_STREAM_RESPONSE_CODES = setOf(403, 404, 410, 416)
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val AUDIO_ROUTE_CHANGE_DEBOUNCE_MS = 350L
        const val AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS = 200L
        const val AUDIO_ROUTE_RECOVERY_MIN_INTERVAL_MS = 1_500L
        const val AUDIO_ROUTE_RECOVERY_RESUME_DELAY_MS = 150L
        const val DEVICE_MUTE_PLAYBACK_NOTICE_INTERVAL_MS = 1_200L
        const val MIN_AUDIO_FOCUS_VOLUME_FACTOR = 0.2f
        const val MIN_AUDIO_NORMALIZATION_FACTOR = 0.25f
        const val MAX_AUDIO_NORMALIZATION_FACTOR = 1.414f
        const val EFFECTIVE_VOLUME_RAMP_FRAME_MS = 16L
        const val EFFECTIVE_VOLUME_RAMP_UP_MS = 350L
        const val EFFECTIVE_VOLUME_RAMP_DOWN_MS = 180L
        const val EFFECTIVE_VOLUME_RAMP_MIN_DELTA = 0.015f
        const val MIN_CROSSFADE_DURATION_MS = PlaybackConstants.MIN_CROSSFADE_DURATION_MS
        const val CROSSFADE_END_GUARD_MS = PlaybackConstants.CROSSFADE_END_GUARD_MS
        const val CROSSFADE_PREPARE_AHEAD_MS = PlaybackConstants.CROSSFADE_PREPARE_AHEAD_MS
        const val CROSSFADE_READY_TIMEOUT_MS = PlaybackConstants.CROSSFADE_READY_TIMEOUT_MS
        const val CROSSFADE_BUFFERING_TIMEOUT_MS = PlaybackConstants.CROSSFADE_BUFFERING_TIMEOUT_MS
        const val CROSSFADE_HANDOFF_READY_TIMEOUT_MS = PlaybackConstants.CROSSFADE_HANDOFF_READY_TIMEOUT_MS
        const val CROSSFADE_HANDOFF_BUFFER_MS = PlaybackConstants.CROSSFADE_HANDOFF_BUFFER_MS
        const val CROSSFADE_HANDOFF_SEEK_GUARD_MS = PlaybackConstants.CROSSFADE_HANDOFF_SEEK_GUARD_MS
        const val CROSSFADE_MIN_BUFFER_BEFORE_START_MS = PlaybackConstants.CROSSFADE_MIN_BUFFER_BEFORE_START_MS
        const val CROSSFADE_MAX_BUFFER_BEFORE_START_MS = PlaybackConstants.CROSSFADE_MAX_BUFFER_BEFORE_START_MS
        const val PRIMARY_MIN_BUFFER_MS = PlaybackConstants.PRIMARY_MIN_BUFFER_MS
        const val PRIMARY_MAX_BUFFER_MS = PlaybackConstants.PRIMARY_MAX_BUFFER_MS
        const val PRIMARY_BUFFER_FOR_PLAYBACK_MS = PlaybackConstants.PRIMARY_BUFFER_FOR_PLAYBACK_MS
        const val PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = PlaybackConstants.PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        const val CROSSFADE_MIN_BUFFER_MS = PlaybackConstants.CROSSFADE_MIN_BUFFER_MS
        const val CROSSFADE_MAX_BUFFER_MS = PlaybackConstants.CROSSFADE_MAX_BUFFER_MS
        const val CROSSFADE_FRAME_MS = PlaybackConstants.CROSSFADE_FRAME_MS
        const val MIN_AUDIBLE_EFFECTIVE_VOLUME = 0.01f
        const val STUCK_MUTED_VOLUME_EPSILON = 0.001f
        const val AUDIBLE_PLAYBACK_VOLUME_CHECK_MS = 2_000L
        const val NETWORK_STALL_WINDOW_MS = 3_000L
        private const val FORCE_REVIVE_DEBOUNCE_MS = 2_000L
        internal const val ArchiveTuneExtractorCacheFingerprintPrefix = "archivetune_extractor:"
        internal const val ArchiveTuneExtractorCacheTtlMs = 5 * 60 * 1000L
    }
}
