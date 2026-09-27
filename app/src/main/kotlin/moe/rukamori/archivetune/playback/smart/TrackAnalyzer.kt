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
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(filePath, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        file: File,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(file, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        context: Context,
        uri: Uri,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(context, uri, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        cache: Cache,
        cacheKey: String,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(cache, cacheKey, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        mediaDataSource: MediaDataSource,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(mediaDataSource, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        durationSeconds: Double? = null,
        decodeSamples: () -> FloatArray?,
    ): TrackAnalysisResult? = withContext(Dispatchers.Default) {
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
                    memoryCache[trackId] = result
                    _analysisEvents.tryEmit(trackId to result)
                    return@async result
                }

                val samples = decodeSamples() ?: return@async null
                if (samples.isEmpty()) return@async null

                val targetSampleRate = runCatching { TrackFeatures.sampleRate() }
                    .getOrDefault(AudioDecoder.TARGET_SAMPLE_RATE)
                val duration = if (durationSeconds != null && durationSeconds > 0.0) {
                    durationSeconds
                } else if (targetSampleRate > 0.0) {
                    samples.size / targetSampleRate
                } else {
                    0.0
                }

                val result = TrackFeatures.analyze(samples, duration) ?: return@async null
                memoryCache[trackId] = result
                _analysisEvents.tryEmit(trackId to result)
                analyzerScope.launch(Dispatchers.IO) {
                    runCatching {
                        database?.trackAnalysisDao()?.upsert(fromResult(trackId, result))
                    }
                }
                result
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
