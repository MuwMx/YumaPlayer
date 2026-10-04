/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.database.SQLException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import moe.rukamori.archivetune.audiodsp.AnalysisStore
import moe.rukamori.archivetune.audiodsp.AudioDeck
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeConfig
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.DeckController
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.DualForwardingPlayer
import moe.rukamori.archivetune.audiodsp.DualPlayerRoleHolder
import moe.rukamori.archivetune.audiodsp.shouldUseLegacyPath
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.okhttp.OkHttpDataSource
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import moe.rukamori.archivetune.utils.isLowDataModeActive
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cast.CastMediaItemResolver
import moe.rukamori.archivetune.cast.CastPlaybackRepository
import moe.rukamori.archivetune.cast.CastPlaybackRepositoryLocator
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AudioOffload
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.AutoSkipNextOnErrorKey
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothKey
import moe.rukamori.archivetune.constants.AutomixEnabledKey
import moe.rukamori.archivetune.constants.AutomixTransitionDurationKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.DeviceMutePlaybackRecoveryVolumeKey
import moe.rukamori.archivetune.constants.DiscordShowWhenPausedKey
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.constants.EnableLastFMScrobblingKey
import moe.rukamori.archivetune.constants.FlacQuality
import moe.rukamori.archivetune.constants.FlacStreamingQualityKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.constants.HISTORY_DURATION_DEFAULT
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MAX
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MIN
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.HistoryDuration
import moe.rukamori.archivetune.constants.LastFMSessionKey
import moe.rukamori.archivetune.constants.LastFMUseNowPlaying
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.ListenBrainzEnabledKey
import moe.rukamori.archivetune.constants.ListenBrainzTokenKey
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.constants.MemoryCacheToggleKey
import moe.rukamori.archivetune.constants.PauseListenHistoryKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PermanentShuffleKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.constants.PlayerStreamClientKey
import moe.rukamori.archivetune.constants.PlayerVolumeKey
import moe.rukamori.archivetune.constants.RepeatModeKey
import moe.rukamori.archivetune.constants.ScrobbleDelayPercentKey
import moe.rukamori.archivetune.constants.ScrobbleDelaySecondsKey
import moe.rukamori.archivetune.constants.ScrobbleMinSongDurationKey
import moe.rukamori.archivetune.constants.ShowLyricsKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.TogetherClientIdKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.RelatedSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.collect
import moe.rukamori.archivetune.extensions.collectLatest
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.directorySizeBytes
import moe.rukamori.archivetune.extensions.findNextMediaItemById
import moe.rukamori.archivetune.extensions.mediaItems
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsQueue
import moe.rukamori.archivetune.spotify.SpotifyPlaylistQueue
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.extensions.toContinuationQueue
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.toPersistQueue
import moe.rukamori.archivetune.extensions.toQueue
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsPreloadManager
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.PersistPlayerState
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.models.toSong
import moe.rukamori.archivetune.moriextractor.ArchiveTuneExtractorException
import moe.rukamori.archivetune.moriextractor.StreamingExtractionManager
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.playback.queues.filterExplicit
import moe.rukamori.archivetune.playback.queues.filterVideo
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.together.TogetherPlaybackSync
import moe.rukamori.archivetune.ui.screens.settings.DiscordPresenceManager
import moe.rukamori.archivetune.ui.screens.settings.ListenBrainzManager
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.CoilBitmapLoader
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.ProxyAuth
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.enumPreference
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.getAsync
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.preference
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import moe.rukamori.archivetune.widget.LoadWidgetInsightsUseCase
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.EOFException
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.ConnectException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

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

    internal lateinit var audioManager: AudioManager
    internal var audioFocusRequest: AudioFocusRequest? = null
    internal var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    internal var wasPlayingBeforeAudioFocusLoss = false
    override var pauseOnDeviceMuteEnabled = false
    override var deviceMutePlaybackRecoveryVolumePercent = 0
    override var wasAutoPausedByDeviceMute = false
    internal var muteRecoveryObserver: ContentObserver? = null
    internal var lastDeviceMutePlaybackNoticeAtElapsedMs = 0L
    internal var hasAudioFocus = false
    internal val autoStartOnBluetoothEnabled: Boolean
        get() = serviceLifecycle.autoStartOnBluetoothEnabled
    private var wakeLock: PowerManager.WakeLock? = null
    internal var audioRouteRecoveryJob: Job? = null
    internal var audiblePlaybackRecoveryJob: Job? = null
    internal var lastAudioOutputDeviceSignature: String? = null
    internal var lastAudioRouteRecoveryRealtimeMs = 0L

    internal val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                if (addedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                if (removedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }
        }

    internal var scopeJob = Job()
    internal var scope = CoroutineScope(Dispatchers.Main + scopeJob)
    internal var ioScope = CoroutineScope(Dispatchers.IO + scopeJob)
    internal val serviceLifecycle: MusicServiceLifecycle by lazy {
        MusicServiceLifecycle(
            scope = scope,
            ioScope = ioScope,
            delegate = this,
        )
    }
    private val binder = MusicBinder()
    internal val hasBoundClients: Boolean
        get() = serviceLifecycle.hasBoundClients

    override lateinit var connectivityManager: ConnectivityManager
    override lateinit var connectivityObserver: NetworkConnectivityObserver
    override val isNetworkConnected = MutableStateFlow(false)
    val waitingForNetworkConnection: MutableStateFlow<Boolean>
        get() = playbackRecoveryEngine.waitingForNetworkConnection
    internal var networkStallRecoveryJob: Job?
        get() = playbackRecoveryEngine.networkStallRecoveryJob
        set(value) {
            playbackRecoveryEngine.networkStallRecoveryJob = value
        }

    override val playbackRecoveryEngine: PlaybackRecoveryEngine by lazy {
        PlaybackRecoveryEngine(
            scope = scope,
            playerActions = object : PlaybackRecoveryEngine.PlayerActions {
                override val currentMediaItem: MediaItem? get() = player.currentMediaItem
                override val currentMediaItemIndex: Int get() = player.currentMediaItemIndex
                override val currentPosition: Long get() = player.currentPosition
                override val playWhenReady: Boolean get() = player.playWhenReady
                override val playbackState: Int get() = player.playbackState
                override val isPlaying: Boolean get() = player.isPlaying
                override val playbackSuppressionReason: Int get() = player.playbackSuppressionReason
                override val nextMediaItemIndex: Int get() = player.nextMediaItemIndex
                override fun findNextMediaItemById(mediaId: String): MediaItem? = player.findNextMediaItemById(mediaId)
                override fun prepare() = player.prepare()
                override fun play() = player.play()
                override fun pause() = player.pause()
                override fun stop() = player.stop()
                override fun seekTo(mediaItemIndex: Int, positionMs: Long) = player.seekTo(mediaItemIndex, positionMs)
                override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
                override fun evictMediaConnectionPools() = this@MusicService.evictMediaConnectionPools()
                override val isCrossfading: Boolean get() = this@MusicService.isCrossfading
                override fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) =
                    this@MusicService.cancelCrossfade(resetVolume, resetPauseAtEnd)
                override fun registerRetryAttempt(mediaId: String): Boolean =
                    playbackStreamRecoveryTracker.registerRetryAttempt(mediaId)
                override fun shouldAutoSkipOnError(): Boolean =
                    dataStore.get(AutoSkipNextOnErrorKey, false)
            },
            cacheOps = object : PlaybackRecoveryEngine.CacheOps {
                override val playerCache: Cache get() = this@MusicService.playerCache
                override val downloadCache: Cache get() = this@MusicService.downloadCache
                override fun isTrackFullyCached(mediaId: String): Boolean = this@MusicService.isTrackFullyCached(mediaId)
                override fun invalidatePlaybackUrlCache(mediaId: String) = this@MusicService.invalidatePlaybackUrlCache(mediaId)
                override fun removeExtractorPlaybackUrl(mediaId: String) {
                    extractorPlaybackUrlCache.remove(mediaId)
                }
                override fun getCachedFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue? {
                    return (playbackUrlCache[mediaId]
                        ?: playbackUrlCache["${mediaId}_${PlaybackSource.YT_MUSIC.name}"]
                        ?: playbackUrlCache["${mediaId}_${PlaybackSource.FLAC.name}"])?.takeIf { it.url == failedUrl }
                }
                override fun getCachedExtractorFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue? {
                    return extractorPlaybackUrlCache[mediaId]?.takeIf { it.url == failedUrl }
                }
                override fun getPlayerCacheDirectorySizeBytes(): Long {
                    val cacheDir = StorageLocationRepository.cacheDirectory(this@MusicService, StorageFolderKind.SONG_CACHE)
                    return cacheDir.directorySizeBytes()
                }
            },
            networkState = { isNetworkConnected.value },
            loginPrompt = object : PlaybackRecoveryEngine.LoginPrompt {
                override fun isAppInForeground(): Boolean = this@MusicService.isAppInForeground()
                override fun openLogin(mediaId: String, targetUrl: String) {
                    val deepLink = Uri.parse("archivetune://login?url=${Uri.encode(targetUrl)}")
                    val intent =
                        Intent(Intent.ACTION_VIEW, deepLink, this@MusicService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    runCatching {
                        startActivity(intent)
                    }.onFailure {
                        Timber.e(it, "Failed to open login recovery for %s", mediaId)
                    }
                }
            },
            databaseProvider = { database },
        )
    }

    internal val audioQuality by enumPreference(
        this,
        AudioQualityKey,
        moe.rukamori.archivetune.constants.AudioQuality.AUTO,
    )
    internal val preferredStreamClient by enumPreference(
        this,
        PlayerStreamClientKey,
        PlayerStreamClient.ANDROID_VR,
    )
    internal val enableMemoryCache by preference(
        this,
        MemoryCacheToggleKey,
        true,
    )
    internal val playbackUrlCache = ConcurrentHashMap<String, AuthScopedCacheValue>()
    internal val losslessUrlCache = moe.rukamori.archivetune.playback.resolvers.StreamUrlCache()
    @Volatile
    override var currentPlaybackSource: PlaybackSource = PlaybackSource.YT_MUSIC
    @Volatile
    override var isLowDataEnabled: Boolean = true
    internal val extractorPlaybackUrlCache = ConcurrentHashMap<String, AuthScopedCacheValue>()
    internal val remotePlaybackTrackingUrlCache = ConcurrentHashMap<String, String>()
    internal val contentLengthCache = ConcurrentHashMap<String, Long>()
    internal fun invalidatePlaybackUrlCache(mediaId: String) {
        playbackUrlCache.remove(mediaId)
        playbackUrlCache.remove("${mediaId}_${PlaybackSource.YT_MUSIC.name}")
        playbackUrlCache.remove("${mediaId}_${PlaybackSource.FLAC.name}")
    }
    private fun invalidateLosslessUrlCache(mediaId: String) {
        losslessUrlCache.remove(mediaId)
        losslessUrlCache.remove("${mediaId}_${PlaybackSource.YT_MUSIC.name}")
        losslessUrlCache.remove("${mediaId}_${PlaybackSource.FLAC.name}")
    }
    internal val streamingExtractionManager by lazy {
        StreamingExtractionManager(
            bearerToken = moe.rukamori.archivetune.BuildConfig.EXTRACTOR_BEARER,
        )
    }
    internal val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(YouTube.streamOkHttpProxy)
            .proxyAuthenticator(ProxyAuth.proxyAuthenticator)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeMediaHost =
                    host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com")

                if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                chain.proceed(
                    StreamClientUtils
                        .applyRequestProfile(
                            request.newBuilder(),
                            requestProfile,
                        ).build(),
                )
            }.build()
    }
    internal val extractorMediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                        ).header("Accept", "*/*")
                        .build()
                chain.proceed(request)
            }.build()
    }

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
    internal var lastPresenceToken: String? = null

    @Volatile
    internal var pausedPresenceGate = PausedPresenceGate.FollowPreference

    @Volatile
    internal var discordServiceStopping = false

    @Volatile
    internal var lastDiscordPresenceDecision: DiscordPresenceDecision? = null

    @Volatile
    internal var activeDiscordHoldState: ActiveHoldState? = null

    internal var activeDiscordHoldTimeoutJob: Job? = null

    @Volatile
    internal var lastAppliedVisiblePresence: LastAppliedVisiblePresence? = null

    internal val discordSyncEpoch = AtomicLong(0L)
    internal val discordSyncRequests = Channel<DiscordSyncRequest>(Channel.CONFLATED)
    internal var discordSyncWorkerJob: Job? = null
    internal val pendingDiscordRefreshWaiters = mutableListOf<CompletableDeferred<Boolean>>()
    internal val discordRefreshWaitersMutex = Mutex()

    private val playbackStreamRecoveryTracker = PlaybackStreamRecoveryTracker()
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

    override val currentMediaMetadata = MutableStateFlow<moe.rukamori.archivetune.models.MediaMetadata?>(null)
    override val queueRestoreCompleted = MutableStateFlow(false)
    val infiniteQueueLoading = MutableStateFlow(false)
    internal var infiniteQueueJob: Job? = null
    override val playerInitialized = MutableStateFlow(false)
    override val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    override val currentFormat: kotlinx.coroutines.flow.Flow<FormatEntity?> =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.format(mediaMetadata?.id)
            }.flowOn(Dispatchers.IO)

    override val normalizeFactor = MutableStateFlow(1f)
    internal val audioNormalizationFactorCache = ConcurrentHashMap<String, Float>()
    override var audioNormalizationEnabled = true
    override var playerVolume = MutableStateFlow(1f)
    override val audioFocusVolumeFactor = MutableStateFlow(1f)
    internal var effectiveVolumeRampJob: Job? = null
    override var crossfadeEnabled = false
    override var automixEnabled = false
    override var automixTransitionPreset = "auto"
    internal var activeAutomixPlan: AutomixPlan? = null
    override var crossfadeDurationMs = 0L
    override var crossfadeGapless = false
    internal var crossfadeTriggerJob: Job? = null
    internal var activeCrossfadeScheduledKey: Pair<String, CrossfadeTarget>? = null
    internal var crossfadeJob: Job? = null
    internal lateinit var controller: DeckController
    val isControllerInitialized: Boolean get() = ::controller.isInitialized
    val activeDeck: AudioDeck get() = controller.activeDeck
    val transitionDeck: AudioDeck? get() = if (isControllerInitialized) controller.transitionDeck else null
    override var crossfadeConfig: CrossfadeConfig = CrossfadeConfig()
    internal val analysisStore: AnalysisStore get() = TrackAnalyzer

    internal val dualForwardingPlayer: DualForwardingPlayer
        get() = (controller as ExoDeckController).dualForwardingPlayer
    internal val dualPlayerRoleHolder: DualPlayerRoleHolder
        get() = if (isControllerInitialized) (controller as ExoDeckController).dualPlayerRoleHolder else DualPlayerRoleHolder()
    override val secondaryCrossfadePlayer: ExoPlayer?
        get() = if (isControllerInitialized) (controller as? ExoDeckController)?.secondaryCrossfadePlayer else null
    internal var secondaryCrossfadeTarget: CrossfadeTarget?
        get() = if (isControllerInitialized) (controller as? ExoDeckController)?.secondaryCrossfadeTarget else null
        set(value) { if (isControllerInitialized) (controller as? ExoDeckController)?.secondaryCrossfadeTarget = value }
    internal val reserveCrossfadePlayer: ExoPlayer?
        get() = if (isControllerInitialized) (controller as? ExoDeckController)?.reserveCrossfadePlayer else null

    internal var secondaryPreparationFailedMediaId: String? = null
    internal var hasPreparedSecondaryPlayer: Boolean = false
    internal var isCrossfading = false
    internal var crossfadeHandoffInProgress = false
    internal var crossfadeBaseVolume = 1f
    internal var crossfadeIncomingBaseVolume = 1f
    internal var crossfadeProgress = 0f
    internal var crossfadePlaybackRequested = false
    internal val djFilterByPlayer = ConcurrentHashMap<ExoPlayer, DjFilterAudioProcessor>()
    private var lyricsPreloadManager: LyricsPreloadManager? = null
    internal var streamPrefetcher: StreamPrefetcher? = null
    internal lateinit var playerListeners: MusicServicePlayerListeners

    internal val secondaryCrossfadeListener: Player.Listener
        get() = playerListeners.secondaryCrossfadeListener

    internal data class DiscordSyncRequest(
        val epoch: Long,
        val reason: String,
        val force: Boolean,
    )

    internal data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    internal class StaleDiscordSyncException : CancellationException("Stale Discord sync request")

    internal data class PendingHistoryFinalization(
        val sessionToken: Long,
        val eventId: Long?,
        val remoteRegistered: Boolean,
    )

    internal data class ImmediateHistoryResult(
        val eventId: Long?,
        val remoteRegistered: Boolean,
    )

    internal fun PlayerResponse.PlaybackTracking.remotePlaybackTrackingUrl(): String? =
        videostatsPlaybackUrl
            ?.baseUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

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

    private fun Throwable.isRequestTimeout(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SocketTimeoutException) return true
            if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    private fun Throwable.isNetworkConnectionFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ConnectException || current is UnknownHostException) return true
            current = current.cause
        }
        return false
    }

    lateinit var sleepTimer: AdvancedSleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: Cache

    @Inject
    @DownloadCache
    override lateinit var downloadCache: Cache

    internal val registeredCacheKeys = ConcurrentHashMap.newKeySet<String>()
    internal val automixCacheListener: Cache.Listener =
        object : Cache.Listener {
            override fun onSpanAdded(cache: Cache, span: CacheSpan) {
                val key = span.key
                Timber.tag(TAG).d("Automix cache onSpanAdded key=$key length=${span.length} cached=${cache.isCached(key, span.position, span.length)}")
                val mediaId =
                    if (key.startsWith(FLAC_CACHE_KEY_PREFIX)) {
                        key.removePrefix(FLAC_CACHE_KEY_PREFIX)
                    } else {
                        key
                    }
                if (mediaId.isNotBlank()) {
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

            override fun onSpanRemoved(cache: Cache, span: CacheSpan) {}

            override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {}
        }

    override lateinit var localPlayer: ExoPlayer
        internal set
    override lateinit var player: Player
        internal set

    internal fun transferAudioEffects(to: ExoPlayer) {
        localPlayer.removeListener(audioEffectPlayerListener)
        to.addListener(audioEffectPlayerListener)
    }
    private lateinit var castPlaybackRepository: CastPlaybackRepository
    private lateinit var mediaSession: MediaLibrarySession

    internal var isAudioEffectSessionOpened = false
    internal var openedAudioSessionId: Int? = null
    val eqCapabilities = MutableStateFlow<EqCapabilities?>(null)
    override val desiredEqSettings =
        MutableStateFlow(
            EqSettings(
                enabled = false,
                bandLevelsMb = emptyList(),
                outputGainEnabled = false,
                outputGainMb = 0,
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
            ),
        )

    internal var audioEffectsSessionId: Int? = null
    internal var audioEffectsInitializationJob: Job? = null
    internal var equalizer: Equalizer? = null
    internal var bassBoost: BassBoost? = null
    internal var virtualizer: Virtualizer? = null
    internal var loudnessEnhancer: LoudnessEnhancer? = null
    internal val audioEffectPlayerListener: Player.Listener
        get() = playerListeners.audioEffectPlayerListener

    internal var lastDiscordUpdateTime = 0L

    override var scrobbleManager: moe.rukamori.archivetune.utils.ScrobbleManager? = null

    private lateinit var widgetUpdater: MusicServiceWidgetUpdater

    val autoAddedMediaIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    internal var consecutivePlaybackErr: Int
        get() = playbackRecoveryEngine.consecutivePlaybackErrorCount
        set(value) {
            playbackRecoveryEngine.consecutivePlaybackErr = value
        }

    val maxSafeGainFactor = MAX_AUDIO_NORMALIZATION_FACTOR

    override val togetherSessionState =
        MutableStateFlow<moe.rukamori.archivetune.together.TogetherSessionState>(
            moe.rukamori.archivetune.together.TogetherSessionState.Idle,
        )
    internal var togetherServer: moe.rukamori.archivetune.together.TogetherServer? = null
    internal var togetherOnlineHost: moe.rukamori.archivetune.together.TogetherOnlineHost? = null
    internal var togetherClient: moe.rukamori.archivetune.together.TogetherClient? = null
    internal var togetherBroadcastJob: Job? = null
    internal var togetherOnlineConnectJob: Job? = null
    internal var togetherClientEventsJob: Job? = null
    internal var togetherHeartbeatJob: Job? = null
    internal var togetherClock: moe.rukamori.archivetune.together.TogetherClock? = null
    internal var togetherSelfParticipantId: String? = null
    internal var togetherAuthorityParticipantId: String? = null
    internal var togetherLastAppliedQueueHash: String? = null
    internal var togetherIsOnlineSession: Boolean = false

    @Volatile
    internal var togetherApplyingRemote: Boolean = false

    @Volatile
    internal var togetherSuppressEchoUntilElapsedMs: Long = 0L

    @Volatile
    internal var togetherLastAppliedRoomStateSentAtElapsedMs: Long = 0L

    @Volatile
    internal var togetherLastRemoteAppliedPlayWhenReady: Boolean? = null

    @Volatile
    internal var togetherLastRemoteAppliedIndex: Int = -1

    @Volatile
    internal var togetherLastSentControlAtElapsedMs: Long = 0L

    @Volatile
    internal var togetherLastSentControlAction: moe.rukamori.archivetune.together.ControlAction? = null

    @Volatile
    internal var togetherPendingGuestControl: TogetherPendingGuestControl? = null

    internal fun isTogetherApplyingRemote(): Boolean = togetherApplyingRemote

    internal val togetherHostId: String = "host"
    internal val togetherParticipantNames = ConcurrentHashMap<String, String>()
    internal var lastTogetherNoticeAtElapsedMs: Long = 0L
    internal var lastTogetherNoticeKey: String? = null

    internal data class TogetherPendingGuestControl(
        val desiredIsPlaying: Boolean? = null,
        val desiredIndex: Int? = null,
        val desiredTrackId: String? = null,
        val requestedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long,
    )

    internal fun showTogetherNotice(
        message: String,
        key: String? = null,
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        val normalizedKey = key ?: message
        if (normalizedKey == lastTogetherNoticeKey && now - lastTogetherNoticeAtElapsedMs < 1200L) return
        lastTogetherNoticeKey = normalizedKey
        lastTogetherNoticeAtElapsedMs = now
        scope.launch(SilentHandler) {
            Toast.makeText(this@MusicService, message, Toast.LENGTH_SHORT).show()
        }
    }

    internal fun showTogetherParticipantNotification(
        participantName: String,
        joined: Boolean,
    ) {
        MusicServiceNotification.showTogetherParticipantNotification(
            context = this,
            notificationManager = getSystemService(NotificationManager::class.java),
            participantName = participantName,
            joined = joined,
        )
    }

    internal suspend fun getOrCreateTogetherClientId(): String {
        val existing = dataStore.getAsync(TogetherClientIdKey)?.trim().orEmpty()
        if (existing.isNotBlank()) return existing
        val generated =
            java.util.UUID
                .randomUUID()
                .toString()
        dataStore.edit { prefs -> prefs[TogetherClientIdKey] = generated }
        return generated
    }

    internal fun ensureStartedAsForeground() = serviceLifecycle.ensureStartedAsForeground()

    internal fun promoteToStartedService() = serviceLifecycle.promoteToStartedService()

    internal fun cancelIdleStop() = serviceLifecycle.cancelIdleStop()

    internal fun hasResumablePlaybackNotification(): Boolean =
        serviceLifecycle.hasResumablePlaybackNotification()

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
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        setMediaNotificationProvider(
            ArchiveTuneMediaNotificationProvider(
                context = this,
                smallIconResId = R.drawable.small_icon,
            ),
        )

        player.repeatMode = REPEAT_MODE_OFF

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        serviceLifecycle.onCreate()
    }

    internal fun ensureScopesActive() {
        if (!scopeJob.isActive) {
            scopeJob = Job()
        }
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + scopeJob)
        }
        if (!ioScope.isActive) {
            ioScope = CoroutineScope(Dispatchers.IO + scopeJob)
        }
        startDiscordSyncWorker()
    }

    override fun cancelRestoredQueueHydration() {
        queuePersistenceStore.cancelRestoredQueueHydration()
        restoredQueueHydrationGeneration.incrementAndGet()
        restoredQueueBackfillJob?.cancel()
        restoredQueueBackfillJob = null
        isHydratingRestoredQueue = false
    }

    override fun ensurePresenceManager() {
        if (DiscordPresenceManager.isRunning() && lastPresenceToken != null) return

        // Launch in scope to avoid blocking
        scope.launch {
            // Don't start if Discord RPC is disabled in settings
            if (!dataStore.get(EnableDiscordRPCKey, true)) {
                if (DiscordPresenceManager.isRunning()) {
                    Timber.tag("MusicService").d("Discord RPC disabled → stopping presence manager")
                    try {
                        DiscordPresenceManager.stop()
                    } catch (_: Exception) {
                    }
                    lastPresenceToken = null
                }
                return@launch
            }

            val key: String = dataStore.get(DiscordTokenKey, "")
            if (key.isNullOrBlank()) {
                if (DiscordPresenceManager.isRunning()) {
                    Timber.tag("MusicService").d("No Discord OAuth session -> stopping presence manager")
                    try {
                        DiscordPresenceManager.stop()
                    } catch (_: Exception) {
                    }
                    lastPresenceToken = null
                }
                return@launch
            }

            if (DiscordPresenceManager.isRunning() && lastPresenceToken == key) {
                return@launch
            }

            try {
                DiscordPresenceManager.stop()
                DiscordPresenceManager.start(
                    context = this@MusicService,
                    token = key,
                )
                DiscordPresenceManager.setOnTransportInvalidated { reason ->
                    Timber.tag(DISCORD_SYNC_TAG).w(
                        "transport invalidated reason=%s; requesting forced sync",
                        reason,
                    )
                    requestDiscordSync(
                        reason = "transport_invalidated:$reason",
                        force = true,
                    )
                }
                Timber.tag("MusicService").d("Presence manager started")
                lastPresenceToken = key
                requestDiscordSync(
                    reason = "presence_manager_started",
                    force = true,
                )
            } catch (ex: Exception) {
                Timber.tag("MusicService").e(ex, "Failed to start presence manager")
            }
        }
    }


    internal fun calculateAudioNormalizationFactor(
        format: FormatEntity?,
        normalizeAudio: Boolean,
    ): Float {
        Timber.tag("AudioNormalization").d("Audio normalization enabled: $normalizeAudio")
        Timber
            .tag(
                "AudioNormalization",
            ).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

        if (!normalizeAudio) {
            Timber.tag("AudioNormalization").d("Normalization disabled - using factor 1.0")
            return 1f
        }

        val loudnessDb = format?.normalizationLoudnessDb()
        if (loudnessDb == null || !loudnessDb.isFinite()) {
            Timber.tag("AudioNormalization").w("Normalization enabled but no valid loudness data available - no normalization applied")
            return 1f
        }

        val rawFactor = 10f.pow(-loudnessDb / 20)
        val factor =
            if (rawFactor.isFinite()) {
                rawFactor.coerceIn(MIN_AUDIO_NORMALIZATION_FACTOR, MAX_AUDIO_NORMALIZATION_FACTOR)
            } else {
                1f
            }

        if (factor != rawFactor) {
            Timber.tag("AudioNormalization").d("Normalization factor clamped from $rawFactor to $factor")
        }
        Timber.tag("AudioNormalization").i("Applying normalization factor: $factor")
        return factor
    }

    override fun resolveAudioNormalizationFactor(
        mediaId: String?,
        format: FormatEntity?,
        normalizeAudio: Boolean,
    ): Float {
        val currentMediaId = mediaId?.takeIf { it.isNotBlank() } ?: return 1f
        if (!normalizeAudio) {
            return 1f
        }

        if (format?.id == currentMediaId) {
            val factor = calculateAudioNormalizationFactor(format, normalizeAudio = true)
            audioNormalizationFactorCache[currentMediaId] = factor
            return factor
        }

        return audioNormalizationFactorCache[currentMediaId] ?: 1f
    }

    private fun FormatEntity.normalizationLoudnessDb(): Float? =
        sequenceOf(perceptualLoudnessDb, loudnessDb)
            .mapNotNull { it?.toFloat() }
            .firstOrNull { it.isFinite() }

    fun hasAudioFocusForPlayback(): Boolean = hasAudioFocus

    override fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun registerBluetoothReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun unregisterBluetoothReceiver(receiver: BroadcastReceiver) {
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    internal fun forceRevivePlayback(): Boolean = playbackRecoveryEngine.forceRevivePlayback()

    internal fun revivePlaybackFromStall() {
        playbackRecoveryEngine.revivePlaybackFromStall()
    }

    internal fun evictMediaConnectionPools() {
        runCatching { mediaOkHttpClient.connectionPool.evictAll() }
        runCatching { extractorMediaOkHttpClient.connectionPool.evictAll() }
    }

    internal fun skipOnError() {
        playbackRecoveryEngine.skipOnError()
    }

    internal fun stopOnError() {
        playbackRecoveryEngine.stopOnError()
    }

    override fun updateNotification() {
        MusicServiceNotification.updateNotification(
            context = this,
            mediaSession = mediaSession,
            player = player,
            mediaMetadata = currentMediaMetadata.value ?: player.currentMetadata,
            hasMetadata = currentMediaMetadata.value != null || player.currentMediaItem != null,
            isLiked = currentSong.value?.song?.liked == true,
        )
    }

    fun refreshPlaybackNotification() {
        MusicServiceNotification.refreshPlaybackNotification(
            context = this,
            mediaSession = mediaSession,
            player = player,
            mediaMetadata = currentMediaMetadata.value ?: player.currentMetadata,
            hasMetadata = currentMediaMetadata.value != null || player.currentMediaItem != null,
            isLiked = currentSong.value?.song?.liked == true,
            onUpdateNotification = ::onUpdateNotification,
        )
    }

    internal suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
    ) {
        playbackRecoveryEngine.recoverSong(mediaId, playbackData)
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) = playQueueInternal(queue, playWhenReady)

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

    internal fun decodeBandLevelsMb(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
    }

    internal fun encodeBandLevelsMb(levelsMb: List<Int>): String =
        runCatching {
            EqualizerJson.json.encodeToString(levelsMb)
        }.getOrNull().orEmpty()

    fun applyEqFlatPreset() {
        ioScope.launch {
            val caps = eqCapabilities.value
            val bandCount =
                caps?.bandCount ?: equalizer?.let { readAudioEffectValue("equalizer band count") { it.numberOfBands.toInt() } } ?: 0
            val encoded = encodeBandLevelsMb(List(bandCount.coerceAtLeast(0)) { 0 })
            dataStore.edit { prefs ->
                prefs[EqualizerEnabledKey] = true
                prefs[EqualizerBandLevelsMbKey] = encoded
                prefs[EqualizerSelectedProfileIdKey] = "flat"
            }
        }
    }

    fun applySystemEqPreset(presetIndex: Int) {
        scope.launch {
            ensureAudioEffects(localPlayer.audioSessionId)
            val eq = equalizer ?: return@launch
            val maxPreset = readAudioEffectValue("equalizer preset count") { eq.numberOfPresets.toInt() } ?: 0
            if (presetIndex !in 0 until maxPreset) return@launch

            runCatching { eq.usePreset(presetIndex.toShort()) }.getOrNull() ?: return@launch

            val bandCount = readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt() } ?: 0
            val levels =
                (0 until bandCount).map { band ->
                    readAudioEffectValue("equalizer band level for band $band") {
                        eq.getBandLevel(band.toShort()).toInt()
                    } ?: 0
                }

            val encoded = encodeBandLevelsMb(levels)
            if (encoded.isBlank()) return@launch

            ioScope.launch {
                dataStore.edit { prefs ->
                    prefs[EqualizerEnabledKey] = true
                    prefs[EqualizerBandLevelsMbKey] = encoded
                    prefs[EqualizerSelectedProfileIdKey] = "system:$presetIndex"
                }
            }
        }
    }

    internal fun isCurrentPlaybackItemLocal(currentMediaMetadata: MediaMetadata): Boolean =
        currentSong.value?.song?.isLocal == true ||
            currentMediaMetadata.id.trim().isLocalMediaId() ||
            player.currentMediaItem
                ?.localConfiguration
                ?.uri
                ?.shouldBypassPlayerCache() == true

    internal fun onMediaItemTransitionInternal() {
        playerListeners.onMediaItemTransitionInternal()
    }

    internal fun onPlayerError(error: PlaybackException) {
        playbackRecoveryEngine.onPlayerError(error)
    }

    internal suspend fun handlePresenceAndListenBrainz(
        mediaId: String?,
        mediaMetadata: moe.rukamori.archivetune.models.MediaMetadata?,
        durationMs: Long,
        positionMs: Long,
        reason: String,
    ) {
        try {
            val song = if (mediaId != null) withContext(Dispatchers.IO) { database.song(mediaId).first() } else null
            val finalSong =
                resolvePresenceSong(
                    dbSong = song,
                    mediaMetadata = mediaMetadata,
                    durationMs = durationMs,
                ) ?: return

            try {
                val lbEnabled = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzEnabledKey, false) }
                val lbToken = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzTokenKey, "") }
                if (lbEnabled && !lbToken.isNullOrBlank()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            ListenBrainzManager.submitPlayingNow(this@MusicService, lbToken, finalSong, positionMs)
                        } catch (ie: Exception) {
                            Timber.tag("MusicService").v(ie, "ListenBrainz playing_now submit failed for $reason")
                        }
                    }
                }
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            Timber.tag("MusicService").v(e, "presence and listenbrainz follow-up work failed for $reason")
        }
    }

    private suspend fun trimPlayerCacheToBytes(limitBytes: Long) {
        playbackRecoveryEngine.trimPlayerCacheToBytes(limitBytes)
    }

    private fun resolveMediaItemForCast(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        if (uri.shouldBypassYouTubeResolver()) return mediaItem
        val dataSpec =
            DataSpec
                .Builder()
                .setUri(uri)
                .setKey(mediaItem.localConfiguration?.customCacheKey ?: mediaItem.mediaId)
                .build()
        val resolvedDataSpec =
            resolvePlaybackDataSpec(
                dataSpec = dataSpec,
                allowCacheShortCircuit = false,
            )
        return if (resolvedDataSpec.uri == uri) {
            mediaItem
        } else {
            mediaItem
                .buildUpon()
                .setUri(resolvedDataSpec.uri)
                .build()
        }
    }


    internal fun isExtractorPlaybackUri(uri: Uri): Boolean {
        val url = uri.toString()
        return extractorPlaybackUrlCache.values.any { it.url == url } ||
            uri.path?.startsWith("/api/play/") == true
    }

    private fun Uri.shouldBypassYouTubeResolver(): Boolean {
        val normalizedScheme = scheme?.lowercase(Locale.US)
        return normalizedScheme == "content" ||
            normalizedScheme == "file" ||
            normalizedScheme == "android.resource" ||
            normalizedScheme == "http" ||
            normalizedScheme == "https"
    }

    internal fun Uri.shouldBypassPlayerCache(): Boolean {
        val normalizedScheme = scheme?.lowercase(Locale.US)
        return normalizedScheme == "content" ||
            normalizedScheme == "file" ||
            normalizedScheme == "android.resource"
    }

    private fun deviceSupportsMimeType(mimeType: String): Boolean =
        runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)

    internal class SchemeRoutingDataSource(
        private val cachedFactory: DataSource.Factory,
        private val directFactory: DataSource.Factory,
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val normalizedScheme = dataSpec.uri.scheme?.lowercase(Locale.US)
            val selectedFactory =
                if (
                    normalizedScheme == "content" ||
                    normalizedScheme == "file" ||
                    normalizedScheme == "android.resource"
                ) {
                    directFactory
                } else {
                    cachedFactory
                }
            val selectedDataSource = selectedFactory.createDataSource()
            transferListeners.forEach(selectedDataSource::addTransferListener)
            delegate = selectedDataSource
            return selectedDataSource.open(dataSpec)
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = checkNotNull(delegate).read(buffer, offset, length)

        override fun getUri(): Uri? = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

        override fun close() {
            delegate?.close()
            delegate = null
        }
    }

    internal class ResolvedUrlRoutingDataSource(
        private val defaultFactory: DataSource.Factory,
        private val extractorFactory: DataSource.Factory,
        private val shouldUseExtractorFactory: (Uri) -> Boolean,
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val selectedFactory =
                if (shouldUseExtractorFactory(dataSpec.uri)) {
                    extractorFactory
                } else {
                    defaultFactory
                }
            val selectedDataSource = selectedFactory.createDataSource()
            transferListeners.forEach(selectedDataSource::addTransferListener)
            delegate = selectedDataSource
            return selectedDataSource.open(dataSpec)
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = checkNotNull(delegate).read(buffer, offset, length)

        override fun getUri(): Uri? = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

        override fun close() {
            delegate?.close()
            delegate = null
        }
    }

    override fun updateAudioOffload(enabled: Boolean) {
        val effectiveEnabled = enabled && !crossfadeEnabled
        runCatching {
            val builder = localPlayer.trackSelectionParameters.buildUpon()
            val audioOffloadPrefsClass = Class.forName("androidx.media3.common.AudioOffloadPreferences")
            val audioOffloadPrefsBuilderClass = Class.forName("androidx.media3.common.AudioOffloadPreferences\$Builder")

            val modeFieldName = if (effectiveEnabled) "AUDIO_OFFLOAD_MODE_ENABLED" else "AUDIO_OFFLOAD_MODE_DISABLED"
            val mode = audioOffloadPrefsClass.getField(modeFieldName).getInt(null)

            val prefsBuilder = audioOffloadPrefsBuilderClass.getDeclaredConstructor().newInstance()
            audioOffloadPrefsBuilderClass.getMethod("setAudioOffloadMode", Int::class.javaPrimitiveType).invoke(prefsBuilder, mode)
            val prefs = audioOffloadPrefsBuilderClass.getMethod("build").invoke(prefsBuilder)

            val setMethod =
                builder.javaClass.methods.firstOrNull { method ->
                    method.name == "setAudioOffloadPreferences" && method.parameterTypes.size == 1
                }
            if (setMethod != null) {
                setMethod.invoke(builder, prefs)
                localPlayer.trackSelectionParameters = builder.build()
            }
        }
        localPlayer.setOffloadEnabled(effectiveEnabled)
    }

    internal fun updateWakeLock() = serviceLifecycle.updateWakeLock()

    override fun acquireWakeLock() {
        runCatching { wakeLock?.acquire() }
    }

    override fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
    }

    override fun isWakeLockHeld(): Boolean = wakeLock?.isHeld == true

    private fun createPrimaryLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                PlaybackConstants.PRIMARY_MIN_BUFFER_MS,
                PlaybackConstants.PRIMARY_MAX_BUFFER_MS,
                PlaybackConstants.PRIMARY_BUFFER_FOR_PLAYBACK_MS,
                PlaybackConstants.PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            ).setPrioritizeTimeOverSizeThresholds(true)
            .build()

    internal fun createCrossfadeLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                PlaybackConstants.CROSSFADE_MIN_BUFFER_MS,
                PlaybackConstants.CROSSFADE_MAX_BUFFER_MS,
                PlaybackConstants.CROSSFADE_MIN_BUFFER_BEFORE_START_MS.toInt(),
                PlaybackConstants.CROSSFADE_MIN_BUFFER_BEFORE_START_MS.toInt(),
            ).setPrioritizeTimeOverSizeThresholds(true)
            .build()

    internal fun createRenderersFactory(djFilter: DjFilterAudioProcessor) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        SonicAudioProcessor(),
                        djFilter,
                    ),
                ).build()
        }

    internal fun djFilterFor(player: ExoPlayer?): DjFilterAudioProcessor? = player?.let { djFilterByPlayer[it] }

    internal fun resetDjFilters(vararg players: ExoPlayer?) {
        for (player in players) {
            djFilterFor(player)?.clearAutomation()
        }
    }

    internal fun handlePlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val mediaId = mediaItem.mediaId
        val thresholdMs = historyThresholdMs()
        val pendingSession = popPendingHistoryFinalization(mediaId)
        val alreadyPersistedForSession = pendingSession?.eventId != null || pendingSession?.remoteRegistered == true
        val reachedHistoryThreshold =
            playbackStats.totalPlayTimeMs >= thresholdMs &&
                !dataStore.get(PauseListenHistoryKey, false)
        val shouldPersistHistory = alreadyPersistedForSession || reachedHistoryThreshold

        if (shouldPersistHistory) {
            ioScope.launch {
                val pendingResult =
                    pendingSession?.let { session ->
                        historyRecordingJobs[session.sessionToken]
                            ?.let { deferred ->
                                runCatching { deferred.await() }
                                    .onFailure(::reportException)
                                    .getOrNull()
                            }?.let { result ->
                                session.copy(
                                    eventId = result.eventId ?: session.eventId,
                                    remoteRegistered = session.remoteRegistered || result.remoteRegistered,
                                )
                            }
                            ?: session
                    }

                val fallbackMetadata = mediaItem.metadata
                val eventId =
                    pendingResult?.eventId ?: insertPlaybackHistoryEvent(
                        mediaId = mediaId,
                        playTimeMs = playbackStats.totalPlayTimeMs,
                        mediaMetadata = fallbackMetadata,
                    )

                if (eventId != null) {
                    runCatching {
                        database.updateEventPlayTime(eventId, playbackStats.totalPlayTimeMs)
                    }.onFailure(::reportException)
                }

                try {
                    database.withTransaction {
                        incrementTotalPlayTime(mediaId, playbackStats.totalPlayTimeMs)
                    }
                } catch (_: SQLException) {
                } catch (throwable: Throwable) {
                    reportException(throwable)
                }

                if (pendingResult?.remoteRegistered != true) {
                    registerRemotePlaybackHistory(mediaId)
                }
            }

            ioScope.launch {
                try {
                    val song =
                        database.song(mediaId).first()
                            ?: return@launch

                    val lbEnabled = dataStore.get(ListenBrainzEnabledKey, false)
                    val lbToken = dataStore.get(ListenBrainzTokenKey, "")
                    if (lbEnabled && !lbToken.isNullOrBlank()) {
                        val endMs = System.currentTimeMillis()
                        val startMs = endMs - playbackStats.totalPlayTimeMs
                        try {
                            ListenBrainzManager.submitFinished(this@MusicService, lbToken, song, startMs, endMs)
                        } catch (ie: Exception) {
                            Timber.tag("MusicService").v(ie, "ListenBrainz finished submit failed")
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    internal fun currentPresenceSong(): Song? =
        resolvePresenceSong(
            dbSong = currentSong.value,
            mediaMetadata = player.currentMetadata,
            durationMs = player.duration,
        )

    private fun resolvePresenceSong(
        dbSong: Song?,
        mediaMetadata: MediaMetadata?,
        durationMs: Long,
    ): Song? {
        val metadataSong = mediaMetadata?.let { createTransientSongFromMedia(it) }
        val song =
            when {
                dbSong == null -> metadataSong
                metadataSong == null -> dbSong
                else -> dbSong.withPresenceMetadata(metadataSong)
            }

        return song.withResolvedPresenceDuration(durationMs)
    }

    private fun Song.withPresenceMetadata(metadataSong: Song): Song {
        val resolvedArtists =
            metadataSong.artists.takeIf { metadataArtists ->
                metadataArtists.any { it.hasRemotePresenceId() }
            } ?: artists

        return copy(
            song =
                song.copy(
                    thumbnailUrl = song.thumbnailUrl ?: metadataSong.song.thumbnailUrl,
                    albumId = song.albumId ?: metadataSong.song.albumId,
                    albumName = song.albumName ?: metadataSong.song.albumName,
                ),
            artists = resolvedArtists,
            album = album ?: metadataSong.album,
        )
    }

    private fun Song?.withResolvedPresenceDuration(durationMs: Long): Song? {
        val song = this ?: return null
        if (song.song.duration > 0 || durationMs <= 0) return song
        val durationSeconds =
            (durationMs / 1000L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        return song.copy(song = song.song.copy(duration = durationSeconds))
    }

    private fun ArtistEntity.hasRemotePresenceId(): Boolean = channelId.isRemotePresenceId() || id.isRemotePresenceId()

    private fun String?.isRemotePresenceId(): Boolean {
        val id = this?.trim()?.takeIf { it.isNotBlank() } ?: return false
        return !id.isLocalMediaId() &&
            !id.startsWith("LOCAL_ARTIST_") &&
            !id.startsWith("LA") &&
            !id.contains("privately_owned_artist", ignoreCase = true)
    }

    // Create a transient Song object from current Player MediaMetadata when the DB doesn't have it.
    internal fun createTransientSongFromMedia(media: MediaMetadata): Song {
        val songEntity =
            SongEntity(
                id = media.id,
                title = media.title,
                duration = media.duration,
                thumbnailUrl = media.thumbnailUrl,
                albumId = media.album?.id,
                albumName = media.album?.title,
                explicit = media.explicit,
                isLocal = media.id.isLocalMediaId(),
                isrc = media.isrc,
            )

        val artists =
            media.artists.map { artist ->
                val artistId = artist.id
                ArtistEntity(
                    id = artistId ?: "LA_unknown_${artist.name}",
                    name = artist.name,
                    thumbnailUrl = if (!artist.thumbnailUrl.isNullOrBlank()) artist.thumbnailUrl else media.thumbnailUrl,
                    isLocal = artistId == null || artistId.isLocalMediaId(),
                )
            }

        val album =
            media.album?.let { alb ->
                AlbumEntity(
                    id = alb.id,
                    playlistId = null,
                    title = alb.title,
                    year = null,
                    thumbnailUrl = media.thumbnailUrl,
                    themeColor = null,
                    songCount = 1,
                    duration = media.duration,
                    isLocal = media.id.isLocalMediaId(),
                )
            }

        return Song(
            song = songEntity,
            artists = artists,
            album = album,
            format = null,
        )
    }

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

    override fun computeEffectivePlayerVolume(
        volume: Float,
        factor: Float,
        focus: Float,
    ): Float = this.calculateEffectivePlayerVolume(volume, factor, focus)

    override fun applyEffectiveVolume(volume: Float) = this.updateEffectiveVolume(volume)

    override fun updateCurrentMediaMetadata(metadata: MediaMetadata?) {
        currentMediaMetadata.value = metadata
    }

    override fun dispatchDiscordSync(reason: String, force: Boolean) {
        this.requestDiscordSync(reason = reason, force = force)
    }

    override fun handleMediaNotificationDismissedWhilePausedInBackground() {
        pausedPresenceGate = PausedPresenceGate.HiddenByNotificationDismiss
        requestDiscordSync(
            reason = "notification_dismissed_while_paused_background",
            force = true,
        )
    }

    override fun initLyricsPreloadManager() {
        lyricsPreloadManager =
            LyricsPreloadManager(
                context = this,
                database = database,
                networkConnectivity = connectivityObserver,
                lyricsHelper = lyricsHelper,
            )
    }

    override fun parseEqSettings(prefs: Preferences): EqSettings =
        this.readEqSettingsFromPrefs(prefs)

    override fun applyEqSettings(settings: EqSettings) =
        this.applyEqSettingsToEffects(settings)

    override fun onDeviceMuteChanged() =
        this.handleDeviceMuteStateChanged(playbackRequestedWhileMuted = false)

    override fun removeMuteRecoveryObserver() =
        this.unregisterMuteRecoveryObserver()

    override fun schedulePlayerCrossfade() =
        this.scheduleCrossfade()

    override fun cancelPlayerCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) =
        this.cancelCrossfade(resetVolume = resetVolume, resetPauseAtEnd = resetPauseAtEnd)

    override fun registerCacheListener(key: String) =
        this.registerCacheListenerForKey(key)

    override suspend fun performTrimPlayerCache(limitBytes: Long) {
        playbackRecoveryEngine.trimPlayerCacheToBytes(limitBytes)
    }

    override fun createScrobbleManager(
        minSongDuration: Int,
        scrobbleDelayPercent: Float,
        scrobbleDelaySeconds: Int,
    ): moe.rukamori.archivetune.utils.ScrobbleManager =
        moe.rukamori.archivetune.utils.ScrobbleManager(
            ioScope,
            minSongDuration = minSongDuration,
            scrobbleDelayPercent = scrobbleDelayPercent,
            scrobbleDelaySeconds = scrobbleDelaySeconds,
        )

    override suspend fun restorePersistentState() {
        val persistedQueue = readPersistentObject<PersistQueue>(PERSISTENT_QUEUE_FILE)
        val persistedPlayerState = readPersistentObject<PersistPlayerState>(PERSISTENT_PLAYER_STATE_FILE)

        if (persistedQueue != null || persistedPlayerState != null) {
            isRestoringPersistentState = true
        }

        var restoredQueue = false
        try {
            persistedQueue?.let { queue ->
                restorePersistentQueue(queue)
                restoredQueue = true
            }
            persistedPlayerState?.let { playerState ->
                restorePersistentPlayerState(playerState, restoredQueue)
            }
        } finally {
            isRestoringPersistentState = false
        }
    }

    override fun clearQueuePersistenceFiles() =
        this.clearPersistedQueueFiles()

    override suspend fun saveQueueToStorage() =
        this.saveQueueToDisk()

    override fun startForegroundPlaybackService(): Boolean {
        val notification =
            try {
                MusicServiceNotification.buildForegroundNotification(this)
            } catch (e: Exception) {
                reportException(e)
                return false
            }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            reportException(e)
            false
        } catch (e: IllegalStateException) {
            reportException(e)
            false
        } catch (e: Exception) {
            reportException(e)
            false
        }
    }

    override fun stopForegroundService() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
        }
    }

    override fun stopSelfService() {
        stopSelf()
    }

    override fun startSelfService() {
        runCatching { startService(Intent(this, MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    override fun registerAudioDeviceCallback() {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(mainLooper))
        lastAudioOutputDeviceSignature = currentAudioOutputDeviceSignature()
    }

    override fun unregisterAudioDeviceCallback() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    override fun cancelStreamPrefetch() {
        this.cancelPrefetch()
    }

    override fun setDiscordServiceStopping(stopping: Boolean) {
        discordServiceStopping = stopping
    }

    override fun cancelEffectiveVolumeRamp() {
        effectiveVolumeRampJob?.cancel()
        effectiveVolumeRampJob = null
    }

    override fun cancelAudioRouteRecovery() {
        audioRouteRecoveryJob?.cancel()
    }

    override suspend fun stopTogether() {
        stopTogetherInternal()
    }

    override fun abandonPlaybackAudioFocus() {
        this.abandonAudioFocus()
    }

    override fun releaseServiceAudioEffects() {
        this.releaseAudioEffects()
    }

    override fun releaseReserveAndTransitionDecks() {
        if (isControllerInitialized) {
            (controller as? ExoDeckController)?.releaseReserveDeck()
            (controller as? ExoDeckController)?.releaseTransitionDeck()
        }
    }

    override fun removeDjFilterFromLocalPlayer() {
        runCatching { djFilterByPlayer.remove(localPlayer) }
    }

    override fun removePlayerListeners() {
        localPlayer.removeListener(playerListeners.audioEffectPlayerListener)
        player.removeListener(playerListeners)
        player.removeListener(sleepTimer)
    }

    override fun releasePlayer() {
        player.release()
    }

    override fun releaseAllCacheListeners() {
        this.unregisterAllCacheListeners()
    }

    override fun cancelScopeJob() {
        scopeJob.cancel()
    }

    override fun isTogetherHostSessionActive(): Boolean {
        val state = togetherSessionState.value
        return state is moe.rukamori.archivetune.together.TogetherSessionState.Hosting ||
            state is moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline ||
            (
                state is moe.rukamori.archivetune.together.TogetherSessionState.Joined &&
                    state.role is moe.rukamori.archivetune.together.TogetherRole.Host
            )
    }

    override fun isTogetherSessionIdle(): Boolean =
        togetherSessionState.value is moe.rukamori.archivetune.together.TogetherSessionState.Idle

    override fun resetTogetherSessionState() {
        togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
    }

    override fun isTogetherGuestSessionActive(): Boolean = this.isTogetherGuestSession()

    override fun stopAndClearPlaybackSession(clearPersistentState: Boolean) {
        this.stopAndClearPlayback(clearPersistentState)
    }

    // ── Widget Support ────────────────────────────────────────────────────────────

    fun updateWidget() {
        widgetUpdater.update()
    }

    private fun createMusicServicePlayerListeners(): MusicServicePlayerListeners {
        val service = this
        val playerDelegate =
            object : MusicServicePlayerListeners.PlayerDelegate {
                override val player: Player get() = service.player
                override val localPlayer: ExoPlayer get() = service.localPlayer
            }
        val queueDelegate =
            object : MusicServicePlayerListeners.QueueDelegate {
                override var currentQueue: Queue
                    get() = service.currentQueue
                    set(value) {
                        service.currentQueue = value
                    }
                override val suppressAutoPlayback: Boolean get() = service.suppressAutoPlayback
                override val isInitializingQueue: Boolean get() = service.isInitializingQueue
                override val autoAddedMediaIds: MutableSet<String> get() = service.autoAddedMediaIds
                override val dataStore: DataStore<Preferences> get() = service.dataStore
                override fun onInfiniteQueueEnabled() = service.onInfiniteQueueEnabled()
                override suspend fun saveQueueToDisk() = service.saveQueueToDisk()
                override fun applyCurrentFirstShuffleOrder() = service.applyCurrentFirstShuffleOrder()

                override val togetherSessionState: StateFlow<moe.rukamori.archivetune.together.TogetherSessionState>
                    get() = service.togetherSessionState
                override val togetherSuppressEchoUntilElapsedMs: Long get() = service.togetherSuppressEchoUntilElapsedMs
                override val togetherLastRemoteAppliedIndex: Int get() = service.togetherLastRemoteAppliedIndex
                override val togetherLastRemoteAppliedPlayWhenReady: Boolean? get() = service.togetherLastRemoteAppliedPlayWhenReady
                override fun isTogetherApplyingRemote(): Boolean = service.isTogetherApplyingRemote()
                override suspend fun applyRemoteRoomState(
                    roomState: moe.rukamori.archivetune.together.TogetherRoomState,
                    force: Boolean,
                ) {
                    service.applyRemoteRoomState(roomState, force)
                }
                override fun requestTogetherControl(action: moe.rukamori.archivetune.together.ControlAction) {
                    service.requestTogetherControl(action)
                }
            }
        val historyDelegate =
            object : MusicServicePlayerListeners.HistoryDelegate {
                override var historyThresholdJob: Job?
                    get() = service.historyThresholdJob
                    set(value) {
                        service.historyThresholdJob = value
                    }
                override val currentHistoryMediaId: String? get() = service.currentHistoryMediaId
                override fun beginHistorySession(mediaId: String?, forceNew: Boolean) {
                    service.beginHistorySession(mediaId, forceNew)
                }
                override fun updateHistoryTrackingPlaybackState() {
                    service.updateHistoryTrackingPlaybackState()
                }
                override fun enqueueCurrentHistorySessionForFinalization() {
                    service.enqueueCurrentHistorySessionForFinalization()
                }
                override fun onPlaybackStatsReady(
                    eventTime: AnalyticsListener.EventTime,
                    playbackStats: PlaybackStats,
                ) {
                    service.handlePlaybackStatsReady(eventTime, playbackStats)
                }
            }
        val widgetDelegate =
            object : MusicServicePlayerListeners.WidgetDelegate {
                override fun update() = service.widgetUpdater.update()
                override fun updateProgressTracking() = service.widgetUpdater.updateProgressTracking()
            }
        val metadataDelegate =
            object : MusicServicePlayerListeners.MetadataDelegate {
                override fun updateCurrentMediaMetadata(metadata: MediaMetadata?) {
                    service.currentMediaMetadata.value = metadata
                }
                override fun onLyricsSongChanged(currentIndex: Int, queue: List<MediaMetadata>) {
                    service.lyricsPreloadManager?.onSongChanged(currentIndex, queue)
                }
                override fun prefetchAround(index: Int) = service.prefetchAround(index)
                override fun cancelPrefetch() = service.cancelPrefetch()
                override fun kickOffUpcomingTrackAnalysis(index: Int) = service.kickOffUpcomingTrackAnalysis(index)
                override fun kickOffTrackAnalysis(mediaItem: MediaItem?) = service.kickOffTrackAnalysis(mediaItem)
                override fun registerCacheListenersForMediaItem(mediaItem: MediaItem?) =
                    service.registerCacheListenersForMediaItem(mediaItem)
                override fun recheckCacheReadinessForCurrentAndNext() =
                    service.recheckCacheReadinessForCurrentAndNext()
                override fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean =
                    service.isCurrentPlaybackItemLocal(metadata)
                override fun onScrobbleSongStart(metadata: MediaMetadata?, durationMs: Long) {
                    service.scrobbleManager?.onSongStart(metadata, duration = durationMs)
                }
                override fun onScrobbleSongStop() {
                    service.scrobbleManager?.onSongStop()
                }
                override fun onScrobblePlayerStateChanged(
                    isPlaying: Boolean,
                    metadata: MediaMetadata?,
                    durationMs: Long,
                ) {
                    service.scrobbleManager?.onPlayerStateChanged(isPlaying, metadata, duration = durationMs)
                }
                override fun ensurePresenceManager() = service.ensurePresenceManager()
                override fun requestDiscordSync(reason: String, force: Boolean) = service.requestDiscordSync(reason, force)
                override suspend fun submitPlayingNow(
                    mediaId: String?,
                    mediaMetadata: MediaMetadata?,
                    durationMs: Long,
                    positionMs: Long,
                    reason: String,
                ) {
                    service.handlePresenceAndListenBrainz(mediaId, mediaMetadata, durationMs, positionMs, reason)
                }
            }
        val notificationDelegate =
            object : MusicServicePlayerListeners.NotificationDelegate {
                override fun updateNotification() = service.updateNotification()
                override fun shouldKeepAudioEffectSessionOpen(): Boolean = service.shouldKeepAudioEffectSessionOpen()
                override fun ensureAudioFocusForActivePlayback(): Boolean = service.ensureAudioFocusForActivePlayback()
                override fun updateWakeLock() = service.updateWakeLock()
                override fun hasResumablePlaybackNotification(): Boolean = service.hasResumablePlaybackNotification()
                override fun cancelIdleStop() = service.cancelIdleStop()
                override fun promoteToStartedService() = service.promoteToStartedService()
                override fun ensureStartedAsForeground() = service.ensureStartedAsForeground()
                override fun scheduleStopIfIdle() = service.scheduleStopIfIdle()
                override fun handleDeviceMuteStateChanged(playbackRequestedWhileMuted: Boolean) {
                    service.handleDeviceMuteStateChanged(playbackRequestedWhileMuted)
                }
                override fun isDeviceMutedNow(): Boolean = service.isDeviceMutedNow()
                override fun unregisterMuteRecoveryObserver() = service.unregisterMuteRecoveryObserver()
                override var wasAutoPausedByDeviceMute: Boolean
                    get() = service.wasAutoPausedByDeviceMute
                    set(value) {
                        service.wasAutoPausedByDeviceMute = value
                    }
                override fun updateAudiblePlaybackRecovery() = service.updateAudiblePlaybackRecovery()
                override fun ensureAudiblePlaybackVolume(reason: String) = service.ensureAudiblePlaybackVolume(reason)
                override fun onPlaybackStreamMediaItemChanged(mediaId: String?) {
                    service.playbackStreamRecoveryTracker.onMediaItemChanged(mediaId)
                }
                override fun onPlaybackStreamRecovered(mediaId: String?) {
                    service.playbackStreamRecoveryTracker.onPlaybackRecovered(mediaId)
                }
                override fun reconcileAudioEffectSession() = service.reconcileAudioEffectSession()
                override fun onPlayerError(error: PlaybackException) = service.onPlayerError(error)
            }
        val crossfadeHooks =
            object : MusicServicePlayerListeners.CrossfadeHooks {
                override val secondaryCrossfadePlayer: ExoPlayer? get() = service.secondaryCrossfadePlayer
                override var secondaryPreparationFailedMediaId: String?
                    get() = service.secondaryPreparationFailedMediaId
                    set(value) {
                        service.secondaryPreparationFailedMediaId = value
                    }
                override var hasPreparedSecondaryPlayer: Boolean
                    get() = service.hasPreparedSecondaryPlayer
                    set(value) {
                        service.hasPreparedSecondaryPlayer = value
                    }
                override var isCrossfading: Boolean
                    get() = service.isCrossfading
                    set(value) {
                        service.isCrossfading = value
                    }
                override val crossfadeHandoffInProgress: Boolean get() = service.crossfadeHandoffInProgress
                override var crossfadePlaybackRequested: Boolean
                    get() = service.crossfadePlaybackRequested
                    set(value) {
                        service.crossfadePlaybackRequested = value
                    }
                override var crossfadeTriggerJob: Job?
                    get() = service.crossfadeTriggerJob
                    set(value) {
                        service.crossfadeTriggerJob = value
                    }
                override var activeCrossfadeScheduledKey: Pair<String, CrossfadeTarget>?
                    get() = service.activeCrossfadeScheduledKey
                    set(value) {
                        service.activeCrossfadeScheduledKey = value
                    }
                override fun scheduleCrossfade() = service.scheduleCrossfade()
                override fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) {
                    service.cancelCrossfade(resetVolume, resetPauseAtEnd)
                }
                override fun cancelSecondaryCrossfadePreparation() = service.cancelSecondaryCrossfadePreparation()
                override fun releaseSecondaryCrossfadePlayer() = service.releaseSecondaryCrossfadePlayer()
            }
        return MusicServicePlayerListeners(
            scope = service.scope,
            playerDelegate = playerDelegate,
            queueDelegate = queueDelegate,
            historyDelegate = historyDelegate,
            widgetDelegate = widgetDelegate,
            metadataDelegate = metadataDelegate,
            notificationDelegate = notificationDelegate,
            crossfadeHooks = crossfadeHooks,
        )
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
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
