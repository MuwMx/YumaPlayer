package moe.rukamori.archivetune.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.datasource.cache.Cache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.App
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.TrackAnalysisEntity
import org.json.JSONObject
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TrackAnalyzerEntryPoint {
    fun database(): MusicDatabase
}

object TrackAnalyzer {
    private val analyzerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val memoryCache = ConcurrentHashMap<String, TrackAnalysisResult>()
    private val inFlight = ConcurrentHashMap<String, Deferred<TrackAnalysisResult?>>()
    private val _analysisEvents = MutableSharedFlow<Pair<String, TrackAnalysisResult>>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val analysisEvents: SharedFlow<Pair<String, TrackAnalysisResult>> = _analysisEvents.asSharedFlow()

    @Volatile
    var database: MusicDatabase? = null
        get() = field ?: resolveDatabase()

    private fun resolveDatabase(): MusicDatabase? {
        return runCatching {
            val app = App.instance
            EntryPointAccessors.fromApplication(
                app,
                TrackAnalyzerEntryPoint::class.java,
            ).database()
        }.getOrNull()?.also { database = it }
    }

    fun getCached(trackId: String): TrackAnalysisResult? = memoryCache[trackId]

    fun hasCached(trackId: String): Boolean = memoryCache.containsKey(trackId)

    fun putCached(trackId: String, result: TrackAnalysisResult) {
        memoryCache[trackId] = result
    }

    fun clearCache() {
        memoryCache.clear()
    }

    suspend fun analyze(
        trackId: String,
        filePath: String,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyzeWithSource(trackId, durationSeconds, "filePath") {
        AudioDecoder.decode(filePath, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        file: File,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyzeWithSource(trackId, durationSeconds, "file") {
        AudioDecoder.decode(file, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        context: Context,
        uri: Uri,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyzeWithSource(trackId, durationSeconds, "uri:$uri") {
        AudioDecoder.decode(context, uri, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        cache: Cache,
        cacheKey: String,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyzeWithSource(trackId, durationSeconds, "cache:$cacheKey") {
        AudioDecoder.decode(cache, cacheKey, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        mediaDataSource: MediaDataSource,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyzeWithSource(trackId, durationSeconds, "dataSource") {
        AudioDecoder.decode(mediaDataSource, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        durationSeconds: Double? = null,
        decodeSamples: () -> FloatArray?,
    ): TrackAnalysisResult? = analyzeWithSource(trackId, durationSeconds, "unknown", decodeSamples)

    suspend fun analyzeWithSource(
        trackId: String,
        durationSeconds: Double? = null,
        source: String = "unknown",
        decodeSamples: () -> FloatArray?,
    ): TrackAnalysisResult? = withContext(Dispatchers.Default) {
        Timber.tag("TrackAnalyzer").d("Start analysis trackId=$trackId source=$source")
        memoryCache[trackId]?.let { return@withContext it }

        val activeDeferred = inFlight[trackId]
        if (activeDeferred != null) {
            return@withContext activeDeferred.await()
        }

        val deferred = analyzerScope.async(Dispatchers.Default) {
            try {
                val cachedDb = runCatching {
                    withContext(Dispatchers.IO) {
                        database?.trackAnalysisDao()?.get(trackId)
                    }
                }.getOrNull()

                if (cachedDb != null) {
                    val result = toResult(cachedDb)
                    Timber.tag("TrackAnalyzer").d("Room hit trackId=$trackId bpm=${result.bpm} mixOut=${result.mixOutTime}")
                    memoryCache[trackId] = result
                    _analysisEvents.tryEmit(trackId to result)
                    return@async result
                }

                val samples = runCatching { decodeSamples() }.getOrNull()
                if (samples == null || samples.isEmpty()) {
                    Timber.tag("TrackAnalyzer").w("Decode yielded no samples trackId=$trackId source=$source; caching empty result to unblock UI")
                    val empty = TrackAnalysisResult()
                    memoryCache[trackId] = empty
                    _analysisEvents.tryEmit(trackId to empty)
                    return@async empty
                }
                Timber.tag("TrackAnalyzer").d("Decode done trackId=$trackId samples=${samples.size} source=$source")

                val targetSampleRate = runCatching { TrackFeatures.sampleRate() }
                    .getOrDefault(AudioDecoder.TARGET_SAMPLE_RATE)
                val duration = if (durationSeconds != null && durationSeconds > 0.0) {
                    durationSeconds
                } else if (targetSampleRate > 0.0) {
                    samples.size / targetSampleRate
                } else {
                    0.0
                }

                val result = runCatching { TrackFeatures.analyze(samples, duration) }.getOrNull()
                if (result == null) {
                    Timber.tag("TrackAnalyzer").w("Native analyze returned null/crashed trackId=$trackId samples=${samples.size}; caching empty result to unblock UI")
                    val empty = TrackAnalysisResult()
                    memoryCache[trackId] = empty
                    _analysisEvents.tryEmit(trackId to empty)
                    return@async empty
                }
                Timber.tag("TrackAnalyzer").d("Native analyze done trackId=$trackId bpm=${result.bpm} mixOut=${result.mixOutTime}")
                memoryCache[trackId] = result
                _analysisEvents.tryEmit(trackId to result)
                analyzerScope.launch(Dispatchers.IO) {
                    runCatching {
                        database?.trackAnalysisDao()?.upsert(fromResult(trackId, result))
                    }
                }
                result
            } catch (e: Exception) {
                Timber.tag("TrackAnalyzer").e(e, "Analysis crashed trackId=$trackId source=$source; caching empty result to unblock UI")
                val empty = TrackAnalysisResult()
                memoryCache[trackId] = empty
                _analysisEvents.tryEmit(trackId to empty)
                empty
            } finally {
                inFlight.remove(trackId)
            }
        }

        inFlight[trackId] = deferred
        deferred.await()
    }

    fun analyzeAsync(
        trackId: String,
        durationSeconds: Double? = null,
        decodeSamples: () -> FloatArray?,
    ): Deferred<TrackAnalysisResult?> = analyzerScope.async(Dispatchers.Default) {
        analyze(trackId, durationSeconds, decodeSamples)
    }

    fun toResult(entity: TrackAnalysisEntity): TrackAnalysisResult = TrackAnalysisResult(
        bpm = entity.bpm,
        mixInTime = entity.mixInTime,
        mixOutTime = entity.mixOutTime,
        contentEndTime = entity.contentEndTime,
        firstBeat = entity.firstBeat,
        rawJson = "",
    )

    fun fromResult(
        trackId: String,
        result: TrackAnalysisResult,
        updatedAt: Long = System.currentTimeMillis(),
    ): TrackAnalysisEntity {
        val json = if (result.rawJson.isNotBlank()) runCatching { JSONObject(result.rawJson) }.getOrNull() else null
        val parsedBeatInterval = json?.optDouble("beatInterval", 0.0)?.takeIf { it.isFinite() && it > 0.0 }
            ?: if (result.bpm > 0.0) 60.0 / result.bpm else 0.0
        val parsedDynamicRangeDb = json?.optDouble("dynamicRangeDb", 0.0)?.takeIf { it.isFinite() } ?: 0.0
        val parsedLoudnessLufs = json?.optDouble("loudnessLufs", 0.0)?.takeIf { it.isFinite() } ?: 0.0

        return TrackAnalysisEntity(
            trackId = trackId,
            bpm = result.bpm,
            beatInterval = parsedBeatInterval,
            firstBeat = result.firstBeat,
            mixInTime = result.mixInTime,
            mixOutTime = result.mixOutTime,
            contentEndTime = result.contentEndTime,
            dynamicRangeDb = parsedDynamicRangeDb,
            loudnessLufs = parsedLoudnessLufs,
            updatedAt = updatedAt,
        )
    }
}

fun TrackAnalysisEntity.toResult(): TrackAnalysisResult = TrackAnalyzer.toResult(this)
